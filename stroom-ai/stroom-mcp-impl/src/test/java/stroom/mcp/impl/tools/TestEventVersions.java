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
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.pipeline.shared.XsltDoc;
import stroom.xmlschema.shared.XmlSchemaDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/// The event-logging version a translation writes: the user's choice from those Stroom holds, the newest
/// recommended. Seen in testing: a default of 3.5.2 on a Stroom that didn't hold it.
class TestEventVersions {

    private static final String CSV = "time,user,action\n2026-10-01T10:00:00Z,alice,logon\n"
                                       + "2026-10-01T10:05:00Z,bob,logoff\n";
    private static final ToolCall CALL = new ToolCall("1", "draft_translation_mapping", "{}");

    private static final String FORK_NS = "file://xml/schema/test-fork/events";
    private static final String FORK_SYSTEM_ID = "file://test-fork-events-v1.2.0.xsd";
    private static final String FORK_XSD = TranslationWorld.resource("test-fork-events-v1.2.0.xsd");

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
                world.world::guard, world.world::records, () -> world.inference);
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
                world.world::guard, world.world::records, () -> world.inference);
        assertThat(later.chosen(context, "b9")).isEmpty();
    }

    @Test
    void aVersionHeldInTwoDocumentsIsAskedAboutEvenAsTheOnlyVersion() {
        // Seen in testing: event-logging 4.1.0 imported twice, with different UUIDs; the agent was told there was
        // nothing to ask, and Stroom validated with whichever it listed first.
        holding("4.1.0");
        twoDocuments("EVENTS", "ACME", "<xs:schema/>", "<xs:schema><!-- edited --></xs:schema>");

        final EventVersions.Target asked = world.eventVersions.target(CALL, context, null, "b1");

        assertThat(asked.version()).isNull();
        assertThat(asked.question()).containsEntry("status", "needs_guidance");
        assertThat(asked.question().get("options")).asList()
                .extracting(option -> ((Map<?, ?>) option).get("uuid"), option -> ((Map<?, ?>) option).get("path"),
                        option -> ((Map<?, ?>) option).get("schema_group"))
                .containsExactly(tuple("doc-a", "/XML Schemas/doc-a", "EVENTS"),
                        tuple("doc-b", "/XML Schemas/doc-b", "ACME"));
        assertThat((String) asked.question().get("hint")).contains(EventVersions.DUPLICATES)
                .contains("schema_version= the UUID");
        assertThat(world.eventVersions.settled(context, "b1", "4.1.0")).hasValueSatisfying(said ->
                assertThat(said).doesNotContain("no version to ask about"));

        // Answered with the UUID: settled, remembered, and read from that document.
        assertThat(world.eventVersions.target(CALL, context, "doc-b", "b1").version()).isEqualTo("4.1.0");
        assertThat(world.eventVersions.chosenDoc(context, "b1", "4.1.0")).contains("doc-b");
        assertThat(world.eventVersions.target(CALL, context, null, "b1").version()).isEqualTo("4.1.0");
        assertThat(world.eventVersions.settled(context, "b1", "4.1.0")).hasValueSatisfying(said ->
                assertThat(said).contains("chose its XML schema document /XML Schemas/doc-b")
                        .doesNotContain("no version to ask about"));
        world.eventVersions.eventSchema(context, "b1", "4.1.0");
        Mockito.verify(world.heldSchemas).eventSchema("4.1.0", "doc-b");
        assertThat(world.eventVersions.chosenDocs(context, "b1"))
                .isEqualTo(Map.of("file://event-logging-v4.1.0.xsd", "doc-b"));
    }

    @Test
    void aVersionGivenThatTwoDocumentsHoldIsAskedWhichInTheFormAndRemembered() {
        holding("4.1.0", "3.5.2");
        twoDocuments("EVENTS", "ACME", "<xs:schema/>", "<xs:schema><!-- edited --></xs:schema>");
        world.forms = true;
        final String label = "/XML Schemas/doc-b (UUID doc-b, schema group ACME)";
        world.answers.put(EventVersions.docQuestion("4.1.0", 2), label);

        assertThat(world.eventVersions.target(CALL, context, "4.1.0", "b1").version()).isEqualTo("4.1.0");

        assertThat(world.eventVersions.chosenDoc(context, "b1", "4.1.0")).contains("doc-b");
        // Not asked again (no answer is left to give).
        world.answers.clear();
        assertThat(world.eventVersions.target(CALL, context, "4.1.0", "b1").version()).isEqualTo("4.1.0");
        // Another version has no document to choose.
        assertThat(world.eventVersions.chosenDoc(context, "b1", "3.5.2")).isEmpty();
    }

    @Test
    void thePipelineIsGivenTheChosenDocumentsSchemaGroupWhenTheGroupsTellThemApart() {
        holding("4.1.0");
        twoDocuments("EVENTS", "ACME", "<xs:schema/>", "<xs:schema><!-- edited --></xs:schema>");
        world.eventVersions.remember(context, "b1", "4.1.0", EventVersions.GIVEN, "doc-b");

        assertThat(world.eventVersions.schemaGroupFor(context, "b1")).contains("ACME");
    }

    @Test
    void twoDocumentsInOneSchemaGroupWithDifferentContentAreRefusedForThePipeline() {
        // Stroom's schema filter can't be told which to take: refused rather than validate with either.
        holding("4.1.0");
        twoDocuments("EVENTS", "EVENTS", "<xs:schema/>", "<xs:schema><!-- edited --></xs:schema>");
        world.eventVersions.remember(context, "b1", "4.1.0", EventVersions.GIVEN, "doc-b");

        assertThatThrownBy(() -> world.eventVersions.schemaGroupFor(context, "b1"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("shares its schema group")
                .hasMessageContaining("delete the one not wanted, or give the chosen one a schema group");
    }

    @Test
    void identicalCopiesNeedNoSchemaGroup() {
        // Whichever Stroom takes validates alike.
        holding("4.1.0");
        twoDocuments("EVENTS", "EVENTS", "<xs:schema/>", "<xs:schema/>");
        world.eventVersions.remember(context, "b1", "4.1.0", EventVersions.GIVEN, "doc-b");

        assertThat(world.eventVersions.schemaGroupFor(context, "b1")).isEmpty();
    }

    @Test
    void aSavedTranslationKeepsTheDocumentChosenAndIsRegeneratedFromIt() {
        holding("4.1.0");
        world.twoSchemaDocuments();
        world.eventVersions.remember(context, "acme-v1", "4.1.0", EventVersions.GIVEN, "doc-b");
        final String code = XsltGenerator.generate(TranslationWorld.parsed(), TranslationWorld.SCHEMA, "4.1.0")
                .xslt();

        final Map<String, Object> saved = world.docs.createXslt(new ToolCall("1", SaveXsltTool.NAME, "{}"), context,
                "acme-v1", "ACME-Events", code, TranslationDocs.Source.of(TranslationWorld.parsed()), null);

        final Map<String, Object> payload = KeptMapping.readMapping(world.xslt(String.valueOf(saved.get("uuid")))
                .getDescription()).orElseThrow().payload();
        assertThat(payload).containsEntry("schema_version", "4.1.0")
                .containsEntry(EventVersions.KEPT_SCHEMA_UUID, "doc-b");
        // Regenerated (e.g. for a hand-edit check) with no build to hand: from the kept document, not the first.
        Mockito.clearInvocations(world.heldSchemas);
        world.keptCode.regenerate(payload, code);
        Mockito.verify(world.heldSchemas).eventSchema("4.1.0", "doc-b");
        Mockito.verify(world.heldSchemas, Mockito.never()).eventSchema("4.1.0");
    }

    @Test
    void theKeptDocumentComesBeforeTheBuildsChoiceAndOneNoLongerHeldIsPassedOver() {
        holding("4.1.0");
        world.twoSchemaDocuments();
        world.eventVersions.remember(context, "b1", "4.1.0", EventVersions.GIVEN, "doc-b");

        world.eventVersions.keptSchema(context, "b1", Map.of(EventVersions.KEPT_SCHEMA_UUID, "doc-a"), "4.1.0");
        Mockito.verify(world.heldSchemas).eventSchema("4.1.0", "doc-a");
        // Kept before that document was deleted: the build's choice.
        world.eventVersions.keptSchema(context, "b1", Map.of(EventVersions.KEPT_SCHEMA_UUID, "gone"), "4.1.0");
        Mockito.verify(world.heldSchemas).eventSchema("4.1.0", "doc-b");
        assertThat(world.eventVersions.keptDoc(Map.of(EventVersions.KEPT_SCHEMA_UUID, "gone"), "4.1.0")).isEmpty();
        // Nothing kept and no build choice: the first, as Stroom takes.
        world.eventVersions.keptSchema(Map.of(), "4.1.0");
        Mockito.verify(world.heldSchemas).eventSchema("4.1.0");
    }

    @Test
    void aSchemaGroupNamesTheDocumentStroomValidatesWith() {
        world.twoSchemaDocuments();

        assertThat(world.eventVersions.docsInGroup("ACME"))
                .isEqualTo(Map.of("file://event-logging-v4.1.0.xsd", "doc-b"));
        assertThat(world.eventVersions.docsInGroup(" EVENTS ")).containsValue("doc-a");
        assertThat(world.eventVersions.docsInGroup("OTHER")).isEmpty();
        assertThat(world.eventVersions.docsInGroup(null)).isEmpty();
    }

    @Test
    void aForkTheEnvironmentsTranslationsWriteIsTakenWithoutAsking() {
        // Seen in testing: an environment whose Events are all in its own fork; the agent asked the user which
        // namespace to write, then dropped the import that writes the fork.
        holdingFork();
        world.world.folder("System/" + BuildWorld.WORKSPACE + "/b1");
        Mockito.when(world.inference.infer(Mockito.any(), Mockito.any())).thenReturn(Optional.of(
                new EventsTargetInference.Inferred(FORK_NS, "the import 'Common' writes it")));

        final EventVersions.Target target = world.eventVersions.target(CALL, context, null, "b1");

        assertThat(target.version()).isEqualTo("1.2.0");
        assertThat(world.eventVersions.chosenDocs(context, "b1")).isEqualTo(Map.of(FORK_SYSTEM_ID, "fork-1"));
        // The pipeline validates with the fork's schema group.
        assertThat(world.eventVersions.schemaGroupFor(context, "b1")).contains("EVENTS");
        assertThat(world.eventVersions.settled(context, "b1", "1.2.0")).hasValueSatisfying(said -> assertThat(said)
                .contains("environment's fork").contains(FORK_NS).contains("don't ask the user")
                .contains("needs no change to the schema filter"));
        // Kept in the build's record, with the fork's document, for later sessions.
        assertThat(world.world.records().eventLoggingVersion("b1")).contains(Map.of("version", "1.2.0", "how",
                EventVersions.INFERRED, "uuid", "fork-1"));
        // And not asked again.
        Mockito.reset(world.inference);
        assertThat(world.eventVersions.target(CALL, context, null, "b1").version()).isEqualTo("1.2.0");
        assertThat(world.eventVersions.chosenDocs(context, "b1")).isEqualTo(Map.of(FORK_SYSTEM_ID, "fork-1"));
    }

    @Test
    void withNothingInferredTheForkIsOfferedBesideEventLogging() {
        holdingFork();

        final EventVersions.Target asked = world.eventVersions.target(CALL, context, null, "b1");

        assertThat(asked.version()).isNull();
        assertThat(asked.question()).containsEntry("question", EventVersions.FORK_QUESTION)
                .containsEntry("options", List.of("4.1.0"));
        assertThat(asked.question().get("forks")).isEqualTo(List.of(Map.of("uuid", "fork-1", "namespace", FORK_NS,
                "path", "/XML Schemas/Test fork")));
        assertThat((String) asked.question().get("hint")).contains("schema_version= the fork's uuid");
        // Given back as the fork's UUID, it is settled.
        assertThat(world.eventVersions.target(CALL, context, "fork-1", "b1").version()).isEqualTo("1.2.0");
        assertThat(world.eventVersions.chosenDocs(context, "b1")).isEqualTo(Map.of(FORK_SYSTEM_ID, "fork-1"));
    }

    @Test
    void theForkPickedInTheFormIsRemembered() {
        holdingFork();
        world.forms = true;
        world.answers.put(EventVersions.FORK_QUESTION, "/XML Schemas/Test fork (this environment's fork, namespace "
                                                       + FORK_NS + ")");

        assertThat(world.eventVersions.target(CALL, context, null, "b1").version()).isEqualTo("1.2.0");

        assertThat(world.eventVersions.chosenDocs(context, "b1")).isEqualTo(Map.of(FORK_SYSTEM_ID, "fork-1"));
        assertThat(world.eventVersions.settled(context, "b1", "1.2.0")).hasValueSatisfying(said ->
                assertThat(said).contains("The user chose the environment's fork"));
    }

    @Test
    void aForksCodeGivesTheForksVersionAndDocument() {
        // Before, the version of a saved translation was read from an event-logging system id only: a fork's code
        // was taken for the newest event-logging version, so its document wasn't kept and it regenerated in
        // event-logging:3.
        holdingFork();
        final String code = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns=\"" + FORK_NS
                            + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"3.0\">"
                            + "<xsl:template match=\"/\"><Events xsi:schemaLocation=\"" + FORK_NS + " "
                            + FORK_SYSTEM_ID + "\" Version=\"1.2.0\"/></xsl:template></xsl:stylesheet>";

        assertThat(world.eventVersions.forkWrittenBy(code)).map(XmlSchemaDoc::getUuid).contains("fork-1");
        assertThat(world.eventVersions.translationVersion(null, code)).isEqualTo("1.2.0");
        // A version kept with the mapping comes first; event-logging's code names no fork.
        Mockito.when(world.heldSchemas.translationVersion("3.5.2", code)).thenReturn("3.5.2");
        assertThat(world.eventVersions.translationVersion("3.5.2", code)).isEqualTo("3.5.2");
        assertThat(world.eventVersions.forkWrittenBy(code.replace(FORK_NS, "event-logging:3"))).isEmpty();
        assertThat(world.eventVersions.forkWrittenBy(null)).isEmpty();
    }

    @Test
    void whereTheEnvironmentWritesEventLoggingTheForkIsNotOffered() {
        holdingFork();
        Mockito.when(world.inference.infer(Mockito.any(), Mockito.any())).thenReturn(Optional.of(
                new EventsTargetInference.Inferred("event-logging:3", "the template's XSLT writes it")));

        // The one event-logging version: nothing to ask.
        assertThat(world.eventVersions.target(CALL, context, null, "b1").version()).isEqualTo("4.1.0");
        assertThat(world.eventVersions.chosenDocs(context, "b1")).isEmpty();
    }

    // Event-logging 4.1.0 held in two XML schema documents, doc-a and doc-b.
    private void twoDocuments(final String groupA, final String groupB, final String xsdA, final String xsdB) {
        final String systemId = "file://event-logging-v4.1.0.xsd";
        final List<XmlSchemaDoc> docs = List.of(
                XmlSchemaDoc.builder().uuid("doc-a").name("doc-a").systemId(systemId).schemaGroup(groupA).data(xsdA)
                        .build(),
                XmlSchemaDoc.builder().uuid("doc-b").name("doc-b").systemId(systemId).schemaGroup(groupB).data(xsdB)
                        .build());
        Mockito.when(world.heldSchemas.documents(systemId)).thenReturn(docs);
        Mockito.when(world.heldSchemas.all()).thenReturn(Map.of(systemId, docs));
        Mockito.when(world.heldSchemas.path(Mockito.any())).thenAnswer(inv ->
                "/XML Schemas/" + ((XmlSchemaDoc) inv.getArgument(0)).getName());
    }

    // The test fork of event-logging 4.1.0 (versioned 1.2.0), in the EVENTS schema group beside event-logging.
    private void holdingFork() {
        final XmlSchemaDoc fork = XmlSchemaDoc.builder().uuid("fork-1").name("Test fork").systemId(FORK_SYSTEM_ID)
                .namespaceURI(FORK_NS).schemaGroup("EVENTS").data(FORK_XSD).build();
        Mockito.when(world.heldSchemas.forks()).thenReturn(List.of(fork));
        Mockito.when(world.heldSchemas.forksIn(FORK_NS)).thenReturn(List.of(fork));
        Mockito.when(world.heldSchemas.eventSchema(fork)).thenReturn(EventSchema.parse(FORK_XSD)
                .withSystemId(FORK_SYSTEM_ID));
        Mockito.when(world.heldSchemas.path(Mockito.any())).thenAnswer(inv ->
                "/XML Schemas/" + ((XmlSchemaDoc) inv.getArgument(0)).getName());
    }

    private void holding(final String... versions) {
        world.versions.clear();
        world.versions.addAll(List.of(versions));
    }
}
