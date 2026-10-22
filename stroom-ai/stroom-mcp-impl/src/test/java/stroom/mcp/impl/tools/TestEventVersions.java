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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The event-logging version a translation writes: the user's choice from those Stroom holds, the newest
/// recommended (the Stroom MCP server's `tests/test_event_versions.py`, its tool cases). Seen: the server defaulted
/// to 3.5.2 on a Stroom that didn't hold it.
class TestEventVersions {

    private static final String CSV = "time,user,action\n2026-10-01T10:00:00Z,alice,logon\n"
                                       + "2026-10-01T10:05:00Z,bob,logoff\n";
    private static final ToolCall CALL = new ToolCall("1", "draft_translation_mapping", "{}");

    private final TranslationWorld world = new TranslationWorld();
    private final ToolCallContext context = world.context();

    @Test
    void withoutFormsTheDraftAsksFirstOfferingTheNewest() {
        holding("4.1.0", "4.0.2", "3.5.2");

        final Map<String, Object> asked = world.call(world.draftTool(), Map.of("stream_ids", List.of(7),
                "source_name", "Acme"));

        assertThat(asked).containsEntry("status", "needs_guidance")
                .containsEntry("options", List.of("4.1.0", "4.0.2", "3.5.2"))
                .containsEntry("recommended", "4.1.0");
        assertThat((String) asked.get("hint")).contains("schema_version=");
        // Nothing is read before the user answers.
        Mockito.verifyNoInteractions(world.sampleStreamTexts);
    }

    @Test
    void theUsersPickInTheFormIsDraftedForAndNotAskedAgain() {
        holding("4.1.0", "3.5.2");
        world.forms = true;
        world.answers.put(EventVersions.VERSION_QUESTION, "4.1.0 (newest installed, recommended)");

        final Map<String, Object> drafted = world.call(world.draftTool(), Map.of("samples", Map.of("acme.csv", CSV),
                "source_name", "Acme", "build", "b1"));

        assertThat(drafted).containsEntry("schema_version", "4.1.0");
        assertThat((String) drafted.get("hint")).contains("schema_version=this schema_version");
        // Seen in Ask Stroom AI: told only schema_version 4.1.0, the agent asked the user again, 4.1.0 the one
        // option.
        assertThat(drafted).containsEntry("schema_version_settled", "The user chose event-logging 4.1.0 in the "
                                                                    + "server's form: don't ask them for the version "
                                                                    + "again.");
        assertThat((String) drafted.get("hint")).startsWith((String) drafted.get("schema_version_settled"));
        // build_translation_xslt, given no schema_version, writes the version chosen for the build: not asked
        // again (no answer is left to give).
        world.answers.clear();
        assertThat(world.eventVersions.target(CALL, world.context(), null, "b1").version()).isEqualTo("4.1.0");
        // Another build is asked about afresh.
        world.answers.put(EventVersions.VERSION_QUESTION, "3.5.2");
        assertThat(world.eventVersions.target(CALL, world.context(), null, "b2").version()).isEqualTo("3.5.2");
    }

    @Test
    void aVersionStroomDoesntHoldIsRefusedWithThoseItDoes() {
        holding("4.1.0", "4.0.2");

        assertThatThrownBy(() -> world.eventVersions.target(CALL, context, "3.5.2", "b1"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("no event-logging schema v3.5.2; it holds 4.1.0, 4.0.2");
    }

    @Test
    void a4xVersionIsOfferedAsMuchAs3xOne() {
        // Seen: refused 3.5.2, an agent took 3.4.2, the nearest 3.x, where 4.1.0 was installed: namespace
        // event-logging:3 read as a 3.x schema.
        holding("4.1.0", "3.4.2");

        assertThatThrownBy(() -> world.eventVersions.target(CALL, context, "3.5.2", "b1"))
                .hasMessageContaining("not the nearest to the one asked for")
                .hasMessageContaining("4.x as much as 3.x, writes namespace event-logging:3");
        final EventVersions.Target asked = world.eventVersions.target(CALL, context, null, "b1");
        assertThat((String) asked.question().get("hint"))
                .contains("4.x as much as 3.x, writes namespace event-logging:3");
    }

    @Test
    void withOneVersionInstalledNothingIsAsked() {
        holding("4.0.2");
        world.forms = true;

        // No answer is set: a form would be declined, and refused.
        assertThat(world.eventVersions.target(CALL, context, null, "b1").version()).isEqualTo("4.0.2");
        assertThat(world.eventVersions.settled(context, "b1", "4.0.2")).hasValueSatisfying(said ->
                assertThat(said).contains("only event-logging version this Stroom holds"));
        // Given by the agent: nothing to add.
        world.eventVersions.remember(context, "b2", "4.0.2", EventVersions.GIVEN);
        assertThat(world.eventVersions.settled(context, "b2", "4.0.2")).isEmpty();
    }

    @Test
    void noEventLoggingSchemaAtAllSaysToImportIt() {
        holding();

        assertThatThrownBy(() -> world.eventVersions.target(CALL, context, null, "b1"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("holds no event-logging XML schema");
    }

    @Test
    void anExistingTranslationKeepsItsOwnVersion() {
        holding("4.1.0", "3.5.2");
        final String code = "<Events xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.5.2.xsd\" "
                            + "Version=\"3.5.2\">";

        // Kept with it.
        assertThat(world.eventVersions.translationVersion("4.0.2", code)).isEqualTo("4.0.2");
        // The one its code writes.
        assertThat(world.eventVersions.translationVersion(null, code)).isEqualTo("3.5.2");
        // Else the newest.
        assertThat(world.eventVersions.translationVersion(null, "<xsl:stylesheet/>")).isEqualTo("4.1.0");
    }

    @Test
    void aSavedTranslationRecordsTheVersionItWasGeneratedFor() {
        // Seen before: the kept mapping said the configured 3.5.2 for an XSLT generated for 4.1.0, so the save
        // warned of a hand edit, and regenerating from it would have changed the version back.
        holding("4.1.0", "4.0.2");
        final String code = XsltGenerator.generate(TranslationWorld.parsed(), TranslationWorld.SCHEMA, "4.0.2")
                .xslt();

        final Map<String, Object> saved = world.docs.createXslt(new ToolCall("1", SaveXsltTool.NAME, "{}"), context,
                "acme-v1", "ACME-Events", code,
                TranslationDocs.Source.of(TranslationWorld.parsed()), null);

        final XsltDoc doc = world.xslt(String.valueOf(saved.get("uuid")));
        assertThat(KeptMapping.readMapping(doc.getDescription()).orElseThrow().payload())
                .containsEntry("schema_version", "4.0.2");
        assertThat(saved).containsEntry("matches_mapping", true);
    }

    @Test
    void changesToASavedXsltKeepItsVersionAndAskNothing() {
        holding("4.1.0", "3.5.2");
        final Map<String, Object> made = world.call(world.buildTool(), Map.of("mapping", TranslationWorld.mapping(),
                "schema_version", "3.5.2", "build", "acme-v1", "name", "ACME-Events"));
        final String uuid = String.valueOf(((Map<?, ?>) made.get("saved")).get("uuid"));
        // Forms on, no answer to give: a question would be refused.
        world.forms = true;

        final Map<String, Object> result = world.call(world.buildTool(), Map.of("changes",
                Map.of("unmatched", "warn"), "uuid", uuid));

        assertThat(result).containsEntry("schema_version", "3.5.2").containsKey("saved");
        // An XSLT that names no version (made empty) is asked about like a new translation (in a session that chose
        // none).
        final TranslationWorld fresh = new TranslationWorld();
        fresh.versions.clear();
        fresh.versions.addAll(List.of("4.1.0", "3.5.2"));
        final String empty = fresh.inBuild("acme-v1", XsltDoc.TYPE, "Empty").getUuid();
        final Map<String, Object> asked = fresh.call(fresh.buildTool(), Map.of("mapping", TranslationWorld.mapping(),
                "uuid", empty));
        assertThat(asked).containsEntry("status", "needs_guidance").containsEntry("recommended", "4.1.0");
    }

    @Test
    void theVersionChosenForABuildIsKeptInItsRecordForLaterSessions() {
        // The build layer keeps per-build state in the build's record: another session (or node) is not asked
        // again what draft_translation_mapping asked.
        holding("4.1.0", "3.5.2");
        world.world.folder("System/" + BuildWorld.WORKSPACE + "/b1");
        world.eventVersions.remember(context, "b1", "3.5.2", EventVersions.FORM);

        final EventVersions later = new EventVersions(world.heldSchemas, world.consent, new BuildMemory(),
                world.world::guard, world.world::records);
        assertThat(later.chosen(context, "b1")).contains("3.5.2");
        assertThat(later.settled(context, "b1", "3.5.2")).hasValueSatisfying(said ->
                assertThat(said).contains("in the server's form"));
        assertThat(world.world.records().eventLoggingVersion("b1")).contains(Map.of("version", "3.5.2", "how",
                "form"));
    }

    @Test
    void aBuildWithNoFolderYetKeepsItsVersionForTheSessionOnly() {
        holding("4.1.0", "3.5.2");

        world.eventVersions.remember(context, "b9", "3.5.2", EventVersions.GIVEN);

        assertThat(world.eventVersions.chosen(context, "b9")).contains("3.5.2");
        // No folder (nor record) made for it.
        assertThat(world.world.guard().buildFolder("b9", false)).isEmpty();
        final EventVersions later = new EventVersions(world.heldSchemas, world.consent, new BuildMemory(),
                world.world::guard, world.world::records);
        assertThat(later.chosen(context, "b9")).isEmpty();
    }

    private void holding(final String... versions) {
        world.versions.clear();
        world.versions.addAll(List.of(versions));
    }
}
