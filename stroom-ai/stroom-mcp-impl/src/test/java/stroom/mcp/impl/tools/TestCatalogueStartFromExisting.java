/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.mcp.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/// `catalogue_event_types`' first call on a build whose feed a pipeline already translates (decided by the user,
/// 2026-11-24): the user chooses, in a form, whether the build starts from that pipeline or drafts a new translation;
/// the choice is kept in the build record and the plan's next follows it. Without a form, the reply names both routes
/// for the agent to ask in the chat, and the answer comes back as `start_from`.
class TestCatalogueStartFromExisting {

    private static final String BUILD = "acme";

    private final BuildWorld world = new BuildWorld();
    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final SampleStreamTexts sampleStreamTexts = SampleStreamTextsMocks.mock();
    private final Consent consent = new Consent(() -> Mockito.mock(AiAuditLog.class));
    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, world.guard(), consent, new BuildMemory());
    private final EventVersions eventVersions = new EventVersions(heldSchemas, consent, new BuildMemory(),
            world::guard, world::records, () -> Mockito.mock(EventsTargetInference.class));
    private final RecordSourceNotesTool notesTool = new RecordSourceNotesTool(world.guard(), world.documentAccess,
            new DocumentWrites(world.documentAccess), eventVersions, plan);
    private final TranslationBuilds builds = Mockito.mock(TranslationBuilds.class);
    private final ExistingTranslations existing = Mockito.mock(ExistingTranslations.class);
    private final CatalogueEventTypesTool tool = new CatalogueEventTypesTool(notesTool, sampleStreamTexts, consent,
            plan, builds, world::records, new AtomicLong(1_800_000_000_000L)::get);

    @BeforeEach
    void setUp() {
        tool.setExistingTranslations(() -> existing);
        facts.sources = List.of("ACME");
        facts.docs.add(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"));
        facts.existing = Map.of("name", "ACME-V1.0-Events", "uuid", "prod-ev");
        final Map<String, Object> described = new LinkedHashMap<>(facts.existing);
        described.put("built_by_tools", false);
        described.put("changed", "2026-03-02");
        described.put("changed_by", "jbloggs");
        described.put("event_types", 4);
        described.put("examples", List.of("1001", "1002", "1003"));
        Mockito.when(existing.describe(Mockito.anyMap())).thenReturn(described);
        Mockito.when(builds.surveyStatesInBuild(BUILD)).thenReturn(List.of(Map.of("feed", "ACME")));
        // The build record as the facts read it: what the tool remembers, the plan follows.
        world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD);
    }

    @Test
    void theUserChoosesToStartFromTheExistingPipelineAndTheBuildIsRoutedToItsCopy() {
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("choice",
                CatalogueEventTypesTool.START_EXISTING));
        final Map<String, Object> result = call(Map.of("build", BUILD), user.context());

        assertThat(result).containsEntry("status", "start_from_existing");
        // The form said what the pipeline is: who wrote it, when, and its event types.
        assertThat(String.join("\n", user.shown())).contains(CatalogueEventTypesTool.START_QUESTION,
                "'ACME-V1.0-Events' (not built by Stroom AI: its XSLT was written by hand, last changed 2026-03-02 "
                + "by jbloggs; 4 event types, e.g. 1001, 1002, 1003)", "the original isn't touched");
        final Map<String, Object> kept = world.records().startFrom(BUILD).orElseThrow();
        assertThat(kept).containsEntry("choice", ExistingTranslations.EXISTING)
                .containsEntry("pipeline", Map.of("name", "ACME-V1.0-Events", "uuid", "prod-ev"));
        // The plan, told so, sends the agent to copy it.
        facts.startFrom = kept;
        facts.catalogued = false;
        assertThat(String.valueOf(plan.nextStep(BUILD, null).orElseThrow().get("call"))).contains("copy_pipeline",
                "source_uuid=prod-ev");
    }

    @Test
    void theUserChoosingToDraftGetsTheEvidenceAndIsNotAskedAgain() {
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("choice",
                CatalogueEventTypesTool.START_DRAFT));
        Mockito.when(sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", "time=2026-11-06T09:00:00Z action=login user=u1\n"), List.of()));
        final Map<String, Object> result = call(Map.of("build", BUILD, "stream_ids", List.of(7)), user.context());

        assertThat(result).containsEntry("status", "listed");
        assertThat(world.records().startFrom(BUILD).orElseThrow()).containsEntry("choice",
                ExistingTranslations.DRAFT);
        facts.startFrom = world.records().startFrom(BUILD).orElseThrow();
        assertThat(plan.existingToOffer(BUILD)).isEmpty();
    }

    @Test
    void withoutAFormBothRoutesAreNamedForTheChatAndTheAnswerComesBackAsStartFrom() {
        final Map<String, Object> asked = call(Map.of("build", BUILD), BuildToolCalls.context());

        assertThat(asked).containsEntry("status", "needs_choice");
        assertThat(map(asked.get("routes"))).containsOnlyKeys(ExistingTranslations.EXISTING,
                ExistingTranslations.DRAFT);
        assertThat(String.valueOf(asked.get("hint"))).contains("ask the user in the chat", "start_from=existing",
                "start_from=draft");
        assertThat(world.records().startFrom(BUILD)).isEmpty();

        final Map<String, Object> answered = call(Map.of("build", BUILD, "start_from", "existing"),
                BuildToolCalls.context());
        assertThat(answered).containsEntry("status", "start_from_existing");
        assertThat(world.records().startFrom(BUILD).orElseThrow()).containsEntry("choice",
                ExistingTranslations.EXISTING);
    }

    @Test
    void anAnswerThatIsNeitherRouteIsRefused() {
        assertThat(text(Map.of("build", BUILD, "start_from", "maybe"), BuildToolCalls.context()))
                .contains("start_from is the user's answer: existing or draft");
    }

    private Map<String, Object> call(final Map<String, Object> arguments, final ToolCallContext context) {
        final String text = text(arguments, context);
        assertThat(text).as("a JSON result").startsWith("{");
        return BuildToolCalls.json(text);
    }

    private String text(final Map<String, Object> arguments, final ToolCallContext context) {
        return tool.execute(BuildToolCalls.call(CatalogueEventTypesTool.NAME, JsonUtil.writeValueAsString(arguments,
                false)), context).text();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value) {
        return (Map<String, Object>) value;
    }
}
