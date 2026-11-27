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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.HelpersXslt;
import stroom.ai.impl.tools.generation.ModuleChecks;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// An aggregated feed's shared modules edited by hand in Stroom since the tools saved them (decided by the user,
/// 2026-11-24): a build that writes one asks first, before anything is written, whether to keep the edit (read into
/// the module's mapping, what the build adds beside it), discard it, or stop; without a form it is refused, saying the
/// choices. A common module's values as edited are what a later group is split and documented from, a hand-replaced
/// Generator placeholder no placeholder; helpers edited by hand are told from an older generator's; the group's own
/// hand edit is decided before the modules are written; and promotion's checks say a module edited by hand.
class TestModuleEdits {

    private static final String BUILD = "pega-v1";
    private static final String WRAPPER = """
            [{"path": "EventTime/TimeCreated", "field": "timestamp"},
             {"path": "EventSource/System/Name", "value": "PEGA"},
             {"path": "EventSource/System/Environment", "value": "Test"},
             {"path": "EventSource/Generator", "value": "Pega"},
             {"path": "EventSource/Device/HostName", "field": "hostname"}]""";
    private static final String SAMPLE = record("PegaSecurityEvent",
            "{\\\"eventType\\\": \\\"login\\\", \\\"operatorId\\\": \\\"alice\\\"}")
                                         + record("PegaIntegration",
            "Request: <Envelope><Header><CorrelationId>c-1</CorrelationId></Header></Envelope>");
    private static final String EDITOR = "john.smith";
    private static final long EDITED_AT = Instant.parse("2026-11-20T10:00:00Z").toEpochMilli();
    private static final String COMMON_QUESTION = "The common module was edited by hand";
    private static final String HELPERS_QUESTION = "The feed's helpers were edited by hand";

    private final TranslationWorld world = new TranslationWorld();
    private final BuildTranslationXsltTool tool = world.buildTool();
    private final List<String> written = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world.facts.ownRaw = List.of(1L);
        Mockito.when(world.sampleStreamTexts.read(List.of(1L))).thenAnswer(inv -> new SampleStreamTexts.Texts(
                Map.of("stream 1", SAMPLE), List.of()));
        world.world.inBuild(BUILD, DocumentationDoc.TYPE, "PEGA source types", "r");
        Mockito.when(world.registries.registry(eq(BUILD), eq("PEGA"))).thenAnswer(inv -> Optional.of(written.isEmpty()
                ? registry()
                : new SourceTypeRegistries.Found(new SurveyRecord("r", "PEGA source types", written.getLast()), false,
                        SourceTypeRegistry.read(written.getLast()))));
        Mockito.when(world.registries.writeRegistry(any(), eq(BUILD), eq("PEGA"), any(), anyString()))
                .thenAnswer(inv -> {
                    written.add(inv.getArgument(4));
                    return new SurveyRecord("r", "PEGA source types", inv.getArgument(4));
                });
        world.world.add(XsltDoc.TYPE, "Event Meta", world.world.folder("System/Shared").uuid, "u-meta");
        world.world.docs.put("u-meta", XsltDoc.builder().uuid("u-meta").name("Event Meta")
                .data("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                      + "<xsl:template name=\"event-meta\"><Meta/></xsl:template></xsl:stylesheet>").build());
    }

    @Test
    void anEditedCommonModuleInTheBuildIsKeptBesideTheSharedCallTheBuildAdds() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final String common = xsltsInBuild().get("PEGA common");
        editByHand(common, code -> code.replace("<Environment>Prod</Environment>",
                "<Environment>Production</Environment>"));
        world.forms = true;
        world.answers.put("Source type group 'PEGA integration'", "Into the same feed (PEGA)");
        world.answers.put(COMMON_QUESTION, ModuleEdits.KEEP);

        final Map<String, Object> saved = world.call(tool, args(withSharedCall(integration()), "PEGA integration",
                List.of("PegaIntegration")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(world.asked).filteredOn(intro -> intro.startsWith(COMMON_QUESTION)).singleElement()
                .satisfies(question -> assertThat(question).contains("'PEGA common' was changed in Stroom since the "
                                                                     + "tools saved it (by john.smith, 2026-11-20)",
                        "EventSource/System/Environment is now \"Production\" (was \"Prod\")",
                        "This build needs to add group 'PEGA integration''s shared call(s) to it"));
        // The module keeps the edit, the shared call added beside it, its kept mapping saying what it holds.
        assertThat(xsltsInBuild().get("PEGA common")).isEqualTo(common);
        final XsltDoc module = world.xslt(common);
        assertThat(module.getData()).contains("<Environment>Production</Environment>")
                .doesNotContain("<Environment>Prod</Environment>");
        assertThat(ModuleChecks.hrefs(module.getData())).anySatisfy(href -> assertThat(href).contains("u-meta"));
        assertThat(value(keptMapping(module), "EventSource/System/Environment")).isEqualTo("Production");
        assertThat(XsltVersionHistory.asGenerated(module.getDescription(), module.getData())).contains(true);
        assertThat(module.getDescription()).contains("Its hand edit read into its mapping");
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).contains("The user kept the edit"));
    }

    @Test
    void stopOrNoFormSavesNothingAndDiscardGoesBackToTheGeneratedCode() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final String common = xsltsInBuild().get("PEGA common");
        final String edited = editByHand(common, code -> code.replace("<Environment>Prod</Environment>",
                "<Environment>Production</Environment>"));
        final int registryWrites = written.size();
        final Map<String, Object> integration = args(withSharedCall(integration()), "PEGA integration",
                List.of("PegaIntegration"));

        // No form: refused, saying the choices, nothing written.
        final Map<String, Object> refused = world.call(tool, integration);

        assertThat(String.valueOf(refused.get("error"))).contains("Nothing was saved: " + COMMON_QUESTION,
                "hand_edit_choices={'PEGA common': 'keep' to keep the edit, or 'overwrite' to discard it}");
        assertNothingWritten(common, edited, registryWrites);

        // Stop, in a form: nothing written.
        world.forms = true;
        world.answers.put(COMMON_QUESTION, ModuleEdits.STOP);
        assertThat(String.valueOf(world.call(tool, integration).get("error")))
                .contains("Nothing was saved: the user chose to stop, leaving 'PEGA common' as edited by hand");
        assertNothingWritten(common, edited, registryWrites);

        // Discarded, in the chat: the module goes back to the generated code, with the shared call.
        world.forms = false;
        world.answers.clear();
        final Map<String, Object> discarding = new LinkedHashMap<>(integration);
        discarding.put("hand_edit_choices", Map.of("PEGA common", "overwrite"));
        final Map<String, Object> saved = world.call(tool, discarding);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final XsltDoc module = world.xslt(common);
        assertThat(module.getData()).contains("<Environment>Prod</Environment>").doesNotContain("Production");
        assertThat(ModuleChecks.hrefs(module.getData())).anySatisfy(href -> assertThat(href).contains("u-meta"));
        assertThat(module.getDescription()).contains("Its hand edit discarded (the user's choice)");
    }

    @Test
    void aPromotedCommonModulesEditIsKeptInItsNewVersionAndTheGroupsDocsCarryIt() {
        final Map<String, String> promoted = promotedFeed();
        final String common = promoted.get("PEGA common");
        final String edited = editByHand(common, code -> code.replace("<Environment>Prod</Environment>",
                "<Environment>Production</Environment>"));
        final Map<String, Object> again = args(withSharedCall(integration()), "PEGA integration",
                List.of("PegaIntegration"));
        again.remove("own_output_feed");
        again.put("hand_edit_choices", Map.of("PEGA common", "keep"));
        // What the group writes, for its sample check and its documentation: the module's values as edited.
        assertThat(ToolJson.dumps(world.groups.plan(BUILD, world.groups.registry(BUILD, List.of("PEGA")),
                "PEGA integration", List.of("PegaIntegration"), List.of(), mapping(integration()),
                TranslationWorld.SCHEMA, TranslationWorld.VERSION, null).effective().toJson(DumpMode.COMPACT)))
                .contains("Production");

        final Map<String, Object> saved = world.call(tool, again);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA common V1.1", "PEGA PEGA integration V1.1");
        final XsltDoc version = world.xslt(xsltsInBuild().get("PEGA common V1.1"));
        assertThat(version.getData()).contains("<Environment>Production</Environment>");
        assertThat(ModuleChecks.hrefs(version.getData())).anySatisfy(href -> assertThat(href).contains("u-meta"));
        assertThat(value(keptMapping(version), "EventSource/System/Environment")).isEqualTo("Production");
        // Production left as it was, the edit and all.
        assertThat(world.xslt(common).getData()).isEqualTo(edited);
    }

    @Test
    void aDiscardedPromotedEditGoesBackToTheGeneratedCodeInTheNewVersion() {
        final Map<String, String> promoted = promotedFeed();
        final String common = promoted.get("PEGA common");
        final String edited = editByHand(common, code -> code.replace("<Environment>Prod</Environment>",
                "<Environment>Production</Environment>"));
        final Map<String, Object> again = args(withSharedCall(integration()), "PEGA integration",
                List.of("PegaIntegration"));
        again.remove("own_output_feed");
        world.forms = true;
        world.answers.put(COMMON_QUESTION, ModuleEdits.DISCARD);

        final Map<String, Object> saved = world.call(tool, again);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(world.asked).anySatisfy(intro -> assertThat(intro).startsWith(COMMON_QUESTION));
        final XsltDoc version = world.xslt(xsltsInBuild().get("PEGA common V1.1"));
        assertThat(version.getData()).contains("<Environment>Prod</Environment>").doesNotContain("Production");
        assertThat(world.xslt(common).getData()).isEqualTo(edited);
    }

    @Test
    void aHandReplacedGeneratorPlaceholderIsReadFromTheCodeAndNotAskedAgain() {
        // The first group's Generator names its source types: the module says the placeholder until the feed's own
        // is given. The user replaces it by hand in Stroom.
        world.call(tool, args(withGenerator(security(), "Pega platform (PEGA Security)"), "PEGA security",
                List.of("PegaSecurityEvent")));
        final String common = xsltsInBuild().get("PEGA common");
        assertThat(world.xslt(common).getData()).contains(FeedGenerators.PLACEHOLDER);
        final String edited = editByHand(common, code -> code.replace(FeedGenerators.PLACEHOLDER,
                "PEGA audit trail"));

        final Map<String, Object> saved = world.call(tool, args(withoutGenerator(integration()), "PEGA integration",
                List.of("PegaIntegration")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final List<String> notes = JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes"));
        assertThat(notes).noneSatisfy(note -> assertThat(note).contains(FeedGenerators.PLACEHOLDER + "' as its "
                                                                        + "Generator"))
                .anySatisfy(note -> assertThat(note).contains("split and documented from its values as edited",
                        "EventSource/Generator \"PEGA audit trail\""));
        // Nothing written to the module: the build adds nothing to it.
        assertThat(world.xslt(common).getData()).isEqualTo(edited);
        assertThat(world.xslt(xsltsInBuild().get("PEGA PEGA integration V1.0")).getData())
                .contains("pega_common_generator");
    }

    @Test
    void anEditedHelpersModuleInTheBuildIsAskedAboutAndKeptOrDiscarded() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final String helpers = xsltsInBuild().get("PEGA helpers");
        final String edited = editByHand(helpers, code -> code.replace("A record's time zone as Stroom reads one",
                "A record's time zone, as our site reads one"));
        world.forms = true;
        world.answers.put(HELPERS_QUESTION, ModuleEdits.KEEP);

        final Map<String, Object> kept = world.call(tool, args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));

        assertThat(kept.get("problems")).as("%s", kept).isEqualTo(List.of());
        assertThat(world.asked).filteredOn(intro -> intro.startsWith(HELPERS_QUESTION)).singleElement()
                .satisfies(question -> assertThat(question).contains("'PEGA helpers' was changed in Stroom",
                        "write the generator's helpers (version " + HelpersXslt.VERSION + ") over them",
                        "nothing is added to it"));
        assertThat(world.xslt(helpers).getData()).isEqualTo(edited);

        // Kept for the helpers as edited: not asked again. Discarded, in the chat: the generator's again.
        world.asked.clear();
        assertThat(world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent"))).get("problems"))
                .isEqualTo(List.of());
        assertThat(world.asked).noneSatisfy(intro -> assertThat(intro).startsWith(HELPERS_QUESTION));
        final Map<String, Object> discarding = args(security(), "PEGA security", List.of("PegaSecurityEvent"));
        discarding.put("hand_edit_choices", Map.of("PEGA helpers", "overwrite"));
        final Map<String, Object> discarded = world.call(tool, discarding);

        assertThat(discarded.get("problems")).as("%s", discarded).isEqualTo(List.of());
        assertThat(world.xslt(helpers).getData()).isEqualTo(HelpersXslt.generate());
    }

    @Test
    void aPromotedHelpersEditIsToldFromAnOlderGeneratorsHelpers() {
        final Map<String, String> promoted = promotedFeed();
        final String helpers = promoted.get("PEGA helpers");
        final String current = world.xslt(helpers).getData();
        final Map<String, Object> again = args(security(), "PEGA security", List.of("PegaSecurityEvent"));
        again.remove("own_output_feed");

        // An edit of the generator's own version: asked; kept, nothing versioned, the common module left too.
        final String edited = editByHand(helpers, code -> code.replace("A record's time zone as Stroom reads one",
                "A record's time zone, as our site reads one"));
        final Map<String, Object> keeping = new LinkedHashMap<>(again);
        keeping.put("hand_edit_choices", Map.of("PEGA helpers", "keep"));
        assertThat(String.valueOf(world.call(tool, again).get("error"))).contains(HELPERS_QUESTION);
        final Map<String, Object> kept = world.call(tool, keeping);

        assertThat(kept.get("problems")).as("%s", kept).isEqualTo(List.of());
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA PEGA security V1.1");
        assertThat(world.xslt(helpers).getData()).isEqualTo(edited);

        // An older generator's helpers, as the tools saved them: out of date, a new version made, nothing asked.
        final String older = current.replaceFirst("version=\"" + HelpersXslt.VERSION + "\"", "version=\"0\"");
        savedByTheTools(helpers, older);
        assertThat(world.call(tool, again).get("problems")).isEqualTo(List.of());
        assertThat(xsltsInBuild()).containsKeys("PEGA helpers V1.1", "PEGA common V1.1");
    }

    @Test
    void anOlderGeneratorsHelpersEditedByHandCantBeKept() {
        final Map<String, String> promoted = promotedFeed();
        final String helpers = promoted.get("PEGA helpers");
        editByHand(helpers, code -> code.replaceFirst("version=\"" + HelpersXslt.VERSION + "\"", "version=\"0\""));
        final Map<String, Object> again = args(security(), "PEGA security", List.of("PegaSecurityEvent"));
        again.remove("own_output_feed");
        again.put("hand_edit_choices", Map.of("PEGA helpers", "keep"));

        assertThat(String.valueOf(world.call(tool, again).get("error"))).contains("The edit can't be kept",
                "helpers version 0", "hand_edit_choices={'PEGA helpers': 'overwrite' to discard it}");
        assertThat(xsltsInBuild()).isEmpty();

        again.put("hand_edit_choices", Map.of("PEGA helpers", "overwrite"));
        assertThat(world.call(tool, again).get("problems")).isEqualTo(List.of());
        assertThat(world.xslt(xsltsInBuild().get("PEGA helpers V1.1")).getData()).isEqualTo(HelpersXslt.generate());
    }

    @Test
    void theGroupsOwnHandEditIsDecidedBeforeTheFeedsModulesAreWritten() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final String common = xsltsInBuild().get("PEGA common");
        final String commonCode = world.xslt(common).getData();
        editByHand(xsltsInBuild().get("PEGA PEGA security V1.0"), code -> code.replace("<TypeId>Login</TypeId>",
                "<TypeId>LoginByHand</TypeId>"));

        // The save would add a shared call to the common module, and undoes the group's hand edit: refused, the
        // module not written.
        final Map<String, Object> refused = world.call(tool, args(withSharedCall(security()), "PEGA security",
                List.of("PegaSecurityEvent")));

        assertThat(String.valueOf(refused.get("error"))).contains("was edited by hand since the server saved it");
        assertThat(world.xslt(common).getData()).isEqualTo(commonCode);
    }

    @Test
    void promotionChecksSayAModuleEditedByHandAndAnOlderGeneratorsHelpersNot() {
        final Map<String, String> promoted = promotedFeed();
        final GeneratedDrift drift = new GeneratedDrift(world.keptCode, world.history, world.world.pipelineShapes,
                world.world.documentAccess, world.docs);
        final String group = world.xslt(promoted.get("PEGA PEGA security V1.0")).getData();
        assertThat(drift.moduleEdits(group)).isEmpty();

        savedByTheTools(promoted.get("PEGA helpers"), world.xslt(promoted.get("PEGA helpers")).getData()
                .replaceFirst("version=\"" + HelpersXslt.VERSION + "\"", "version=\"0\""));
        editByHand(promoted.get("PEGA common"), code -> code.replace("<Generator>Pega</Generator>",
                "<Generator>Pega by hand</Generator>"));

        assertThat(drift.moduleEdits(group)).containsOnlyKeys(promoted.get("PEGA common"))
                .hasEntrySatisfying(promoted.get("PEGA common"), why -> assertThat(why).contains(
                        "its imported module 'PEGA common' was edited by hand in Stroom since the tools saved it",
                        "EventSource/Generator is now \"Pega by hand\" (was \"Pega\")", "It runs as edited"));
    }

    // --- the world ---

    private void assertNothingWritten(final String common, final String edited, final int registryWrites) {
        assertThat(world.xslt(common).getData()).isEqualTo(edited);
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA PEGA security V1.0", "PEGA common", "PEGA helpers");
        assertThat(written).hasSize(registryWrites);
    }

    // The XSLT changed by a user in Stroom's editor: its code, and who saved it when; its description (where the
    // tools record what they saved) as it was.
    private String editByHand(final String uuid, final UnaryOperator<String> edit) {
        final XsltDoc doc = world.xslt(uuid);
        final String after = edit.apply(doc.getData());
        assertThat(after).as("the edit applies").isNotEqualTo(doc.getData());
        world.world.docs.put(uuid, doc.copy().data(after).updateUser(EDITOR).updateTimeMs(EDITED_AT).build());
        return after;
    }

    // The XSLT as the tools saved it: its code, and the tools' record of it.
    private void savedByTheTools(final String uuid, final String code) {
        final XsltDoc doc = world.xslt(uuid);
        final String description = XsltVersionHistory.withPending(doc.getDescription(), null, "jane",
                "An older generator's code", "stroom-mcp 0.1", Instant.parse("2026-10-01T00:00:00Z"), code);
        world.world.docs.put(uuid, doc.copy().data(code).description(description).build());
    }

    // The security and integration groups as a first build promoted them, beside the feed.
    private Map<String, String> promotedFeed() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        world.call(tool, args(integration(), "PEGA integration", List.of("PegaIntegration")));
        final Map<String, String> docs = xsltsInBuild();
        assertThat(docs).containsOnlyKeys("PEGA PEGA security V1.0", "PEGA PEGA integration V1.0", "PEGA common",
                "PEGA helpers");
        final String folder = world.world.folder("System/Feeds/PEGA").uuid;
        docs.values().forEach(uuid -> world.world.move(uuid, folder));
        world.world.add("Pipeline", "PEGA PEGA security", folder, "o-security");
        world.world.add("Pipeline", "PEGA PEGA integration", folder, "o-integration");
        written.add(withRows(written.getLast(), row -> {
            if ("PegaSecurityEvent".equals(row.get("name"))) {
                row.put("pipeline", Map.of("name", "PEGA PEGA security", "uuid", "o-security"));
            } else if ("PegaIntegration".equals(row.get("name"))) {
                row.put("pipeline", Map.of("name", "PEGA PEGA integration", "uuid", "o-integration"));
            }
        }));
        return docs;
    }

    private static String withRows(final String registry, final Consumer<Map<String, Object>> change) {
        final Map<String, Object> state = new LinkedHashMap<>(SourceTypeRegistry.read(registry));
        final List<Map<String, Object>> rows = new ArrayList<>();
        JsonValues.maps(state.get("rows")).forEach(row -> {
            final Map<String, Object> copy = new LinkedHashMap<>(row);
            change.accept(copy);
            rows.add(copy);
        });
        state.put("rows", rows);
        return SourceTypeRegistry.render(state);
    }

    private static Map<String, Object> args(final Map<String, Object> mapping,
                                            final String group,
                                            final List<String> sourceTypes) {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("mapping", mapping);
        args.put("schema_version", TranslationWorld.VERSION);
        args.put("build", BUILD);
        args.put("feeds", List.of("PEGA"));
        args.put("group", group);
        args.put("source_types", sourceTypes);
        args.put("own_output_feed", false);
        return args;
    }

    private static Map<String, Object> security() {
        return json("""
                {"input": "json", "json_layout": "lines", "json_fields": ["message"],
                 "common": %s,
                 "ignored_fields": ["app"],
                 "events": [{"name": "login", "when": [{"field": "message.eventType", "equals": "login"}],
                             "fields": [{"path": "EventDetail/TypeId", "value": "Login"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/User/Id", "field": "message.operatorId"}]},
                            {"name": "other",
                             "fields": [{"path": "EventDetail/TypeId", "field": "message.eventType"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "operator",
                                         "field": "message.operatorId"}]}]}""".formatted(WRAPPER));
    }

    private static Map<String, Object> integration() {
        return json("""
                {"input": "json", "json_layout": "lines", "xml_fields": ["soap"],
                 "extract": [{"field": "message", "regex": "^(Request|Response): (.*)$", "names": ["direction", "soap"],
                              "flags": "s"}],
                 "common": %s,
                 "ignored_fields": ["app"],
                 "events": [{"name": "call",
                             "fields": [{"path": "EventDetail/TypeId", "field": "direction"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/Device/HostName", "field": "hostname"},
                                        {"path": "EventDetail/Authenticate/Data", "data_name": "correlation",
                                         "field": "soap/Envelope/Header/CorrelationId"}]}]}"""
                .formatted(WRAPPER.replace("\"PEGA\"", "\"PEGA Integration\"")));
    }

    private static Map<String, Object> withSharedCall(final Map<String, Object> mapping) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        out.put("shared", List.of(Map.of("href", SourceTypeGroups.href("u-meta", "Event Meta"), "template",
                "event-meta", "at", "Meta")));
        return out;
    }

    private static Map<String, Object> withGenerator(final Map<String, Object> mapping, final String generator) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        out.put("common", JsonValues.maps(mapping.get("common")).stream()
                .map(entry -> "EventSource/Generator".equals(entry.get("path"))
                        ? Map.<String, Object>of("path", "EventSource/Generator", "value", generator)
                        : entry)
                .toList());
        return out;
    }

    private static Map<String, Object> withoutGenerator(final Map<String, Object> mapping) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        out.put("common", JsonValues.maps(mapping.get("common")).stream()
                .filter(entry -> !"EventSource/Generator".equals(entry.get("path")))
                .toList());
        return out;
    }

    private static String record(final String sourceType, final String message) {
        return "{\"timestamp\": \"2026-09-28T10:00:00.000Z\", \"hostname\": \"h1\", \"sourcetype\": \""
               + sourceType + "\", \"app\": \"pega\", \"message\": \"" + message + "\"}\n";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(final String text) {
        return (Map<String, Object>) ToolJson.loads(text);
    }

    private static TranslationMapping mapping(final Map<String, Object> json) {
        return TranslationMapping.parse(json);
    }

    private static SourceTypeRegistries.Found registry() {
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", "PEGA");
        state.put("field", "sourcetype");
        state.put("environment", "Prod");
        state.put("system_name", "PEGA");
        state.put("rows", List.of(row("PegaSecurityEvent", "PEGA Security"), row("PegaIntegration", null),
                row("PegaDB", null)));
        return new SourceTypeRegistries.Found(new SurveyRecord("r", "PEGA source types",
                SourceTypeRegistry.render(state)), false, state);
    }

    private static Map<String, Object> row(final String name, final String systemName) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("match", "value");
        row.put("value", name);
        row.put("system_name", systemName);
        row.put("status", SourceTypeRegistry.PARKED);
        return row;
    }

    private Map<String, String> xsltsInBuild() {
        final Map<String, String> out = new LinkedHashMap<>();
        world.world.guard().folderContents(BUILD).stream()
                .filter(doc -> XsltDoc.TYPE.equals(doc.type()))
                .forEach(doc -> out.put(doc.name(), doc.uuid()));
        return out;
    }

    private static TranslationMapping keptMapping(final XsltDoc xslt) {
        return TranslationMapping.parse(KeptMapping.readMapping(xslt.getDescription()).orElseThrow().payload()
                .get("mapping"));
    }

    private static String value(final TranslationMapping mapping, final String path) {
        return mapping.common().stream()
                .filter(entry -> path.equals(entry.strippedPath()))
                .map(FieldMapping::value)
                .findFirst()
                .orElse(null);
    }
}
