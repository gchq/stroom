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
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// write_documentation: a pipeline's documentation, an index's, and an evaluation's.
class TestWriteDocumentationTool {

    private static final String BUILD = "bb";
    private static final String MARKDOWN = "## Purpose and data\n\nx\n";

    private final DocumentationWorld world = new DocumentationWorld();
    private final WriteDocumentationTool tool = world.documentationTool();
    private final ToolCallContext context = BuildToolCalls.context();

    @Test
    void aKeptMappingNeedsStreamsAndAnXsltWithoutOneASection() {
        world.pipeline(BUILD, "Acme", "p");
        final KeptMappings.KeptXslt kept = kept("translation", Map.of("mapping", Map.of()));
        Mockito.when(world.keptMappings.keptXslt("p")).thenReturn(Optional.of(kept));

        assertThat(world.refusal(tool, context, arguments("p", "Created"))).startsWith("Give stream_ids");

        // Left out, they are the streams the pipeline last stepped clean on, from the build's record.
        world.world.records().update(BUILD, new DocRef(PipelineDoc.TYPE, "p", "Acme"),
                entry -> entry.put("stepped_streams", List.of(41, 42)));
        Mockito.when(world.sections.fieldMappingSection(any(), anyString(), any(), any(), any(), anyString(), any()))
                .thenThrow(new ToolRefusal("generated"));

        assertThat(world.refusal(tool, context, arguments("p", "Created"))).isEqualTo("generated");
        Mockito.verify(world.sections).fieldMappingSection(any(), eq(BUILD), any(), eq(kept), eq(List.of(41L, 42L)),
                anyString(), any());

        Mockito.when(world.keptMappings.keptXslt("p")).thenReturn(Optional.empty());
        assertThat(world.refusal(tool, context, arguments("p", "Created")))
                .contains("needs a '## Field mapping' section");
    }

    @Test
    void theDocIsWrittenBesideThePipelineTitledWithItsErrorsAndItsChangePending() {
        world.pipeline(BUILD, "Acme", "p");
        Mockito.when(world.keptMappings.keptXslt("p")).thenReturn(Optional.of(kept("translation",
                Map.of("mapping", Map.of()))));
        Mockito.when(world.sections.fieldMappingSection(any(), anyString(), any(), any(), any(), anyString(), any()))
                .thenReturn("The generated section.");
        final Map<String, Object> args = new LinkedHashMap<>(Map.of("build", BUILD, "pipeline_uuid", "p",
                "markdown", "# ACME-V9 title\n\n## Purpose and data\n\nDoors.\n\n## Field mapping\n\nMine.\n",
                "stream_ids", List.of(5)));

        final Map<String, Object> made = world.call(tool, context, DocumentationWorld.args(args));

        assertThat(made).containsEntry("written", "created").containsEntry("field_mapping", "The generated section.")
                .containsKeys("link", "user_actions", "next");
        final String text = world.world.body(String.valueOf(made.get("uuid")));
        // Its title is its name; the Field mapping section is generated; the errors measured on the sample streams.
        assertThat(text).startsWith("# Acme\n\n## Purpose and data\n\nDoors.")
                .contains("## Field mapping\n\nThe generated section.")
                .doesNotContain("Mine.")
                .contains("## Errors\n\nProcessing the sample streams produced no errors.")
                .contains("| Unreleased | ")
                .contains("| jane (agent: Stroom 7.13.0, A test client) | Created |");
        assertThat(world.world.guard().buildOf(new DocRef(DocumentationDoc.TYPE, String.valueOf(made.get("uuid")))))
                .contains(BUILD);

        // An update says what changed, for its version control.
        world.facts.docs.add(FakeBuildFacts.doc(DocumentationDoc.TYPE, String.valueOf(made.get("uuid")), "Acme"));
        assertThat(world.refusal(tool, context, DocumentationWorld.args(args)))
                .startsWith("This updates the pipeline's documentation: give change");
        args.put("change", "Mapped CODE_TO_TOKEN");
        final Map<String, Object> updated = world.call(tool, context, DocumentationWorld.args(args));

        assertThat(updated).containsEntry("written", "updated").containsEntry("uuid", made.get("uuid"));
        assertThat(DocumentationVersions.pendingOf(world.world.body(String.valueOf(made.get("uuid")))))
                .extracting(entry -> entry.get("change")).containsExactly("Created", "Mapped CODE_TO_TOKEN");
    }

    @Test
    void unknownIsNotAcceptedAsABenignError() {
        // Seen: the user asked to agree to "3 of 50 records" as Unknown, with nothing to say which events they were.
        assertThat(world.refusal(tool, context, "{\"build\": \"bb\", \"pipeline_uuid\": \"p\", \"markdown\": "
                                                + "\"## Purpose\\n\", \"accept_errors\": [{\"element\": "
                                                + "\"translationFilter\", \"reason\": \"odd actions\", \"example\": "
                                                + "\"3 of 50 records come out as EventDetail/Unknown\"}]}"))
                .contains("aren't an error to accept");
    }

    @Test
    void anErrorTheUserAcceptsIsRecordedOnceTheyConfirmIt() {
        world.pipeline(BUILD, "Acme", "p");
        world.facts.stages.put("p", "other");
        final String arguments = "{\"build\": \"bb\", \"pipeline_uuid\": \"p\", \"markdown\": \"## Purpose and data\\n"
                                 + "\\nx\\n\\n## Field mapping\\n\\nHand written.\\n\", \"accept_errors\": "
                                 + "[{\"element\": \"decorationFilter\", \"example\": \"No HR record for user "
                                 + "svc-backup\", \"reason\": \"service accounts have none\", \"matches\": \"No HR "
                                 + "record for user svc-*\"}]}";

        // Without a form the user can't confirm it: nothing is written.
        assertThat(world.refusal(tool, context, arguments)).startsWith("Not done: Record 1 kind(s) of error as "
                                                                      + "benign for pipeline 'Acme'");
        assertThat(world.world.created).noneMatch(made -> made.startsWith(DocumentationDoc.TYPE));

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final Map<String, Object> made = world.call(tool, user.context(), arguments);

        assertThat(user.shown()).singleElement().asString().contains("decorationFilter: No HR record for user "
                                                                     + "svc-* -- service accounts have none");

        final String text = world.world.body(String.valueOf(made.get("uuid")));
        assertThat(text).contains("Accepted as benign by the user, so not raised again:")
                .contains("- `decorationFilter`: No HR record for user svc-backup (2026-10-22): service accounts "
                          + "have none")
                .contains("<!-- stroom-mcp accepted errors");
    }

    @Test
    void writeDocumentationAsksForDescriptionsBeforeWritingAnything() {
        world.pipeline(BUILD, "Acme", "p");
        world.documentation(BUILD, "Acme", "d", "## Field mapping\n\n<!-- stroom-mcp field descriptions\n"
                                                + "{\"kept\": \"Kept before.\"}\n-->");
        Mockito.when(world.keptMappings.keptXslt("p")).thenReturn(Optional.of(kept("index", Map.of())));
        Mockito.when(world.sections.fieldMappingSection(any(), anyString(), any(), any(), any(), anyString(), any()))
                .thenAnswer(inv -> {
                    final Descriptions described = inv.getArgument(6);
                    for (final String field : List.of("kept", "new")) {
                        described.of(field, "EventDetail/Send/Data", List.of("v"), "schema words");
                    }
                    return "section";
                });
        final String before = world.world.body("d");

        final Map<String, Object> asked = world.call(tool, context, "{\"build\": \"bb\", \"pipeline_uuid\": "
                                                                    + "\"p\", \"markdown\": \"## Purpose and "
                                                                    + "data\\n\\nx\\n\", \"change\": \"Changed\", "
                                                                    + "\"stream_ids\": [5]}");

        // Only the field not described before; what the agent said earlier is kept with the doc.
        assertThat(asked).containsEntry("status", "needs_descriptions");
        assertThat(((List<?>) asked.get("fields")).stream()
                .map(field -> String.valueOf(((Map<?, ?>) field).get("field"))))
                .containsExactly("new");
        assertThat(String.valueOf(asked.get("hint"))).contains("field_descriptions").contains("Nothing is written yet");
        assertThat(world.world.body("d")).isEqualTo(before);
    }

    @Test
    void anXsltEditedSinceItsMappingIsDocumentedAsItRunsOrFromTheAgentsOwnSection() {
        // Seen: TWITTER's XSLT saved by hand (save_xslt) with mentions and links in Data arrays, and the Field
        // mapping section generated from the mapping kept before them: links left out, mentions shown joined. The
        // section is generated from the XSLT as it runs (FieldMappingSections), not refused.
        world.pipeline(BUILD, "Acme", "p");
        final KeptMappings.KeptXslt kept = kept("translation", Map.of("mapping", Map.of()));
        Mockito.when(world.keptMappings.keptXslt("p")).thenReturn(Optional.of(kept));
        Mockito.when(world.drift.editDrift(kept)).thenReturn(Optional.of("its XSLT was saved as written by hand "
                                                                         + "(save_xslt) since its mapping was kept."));
        final Map<String, Object> args = new LinkedHashMap<>(Map.of("build", BUILD, "pipeline_uuid", "p",
                "markdown", "## Purpose and data\n\nTweets.\n\n## Field mapping\n\n(Generated on save.)\n",
                "stream_ids", List.of(5), "change", "Created"));

        // A placeholder isn't a section of its own: the section is generated.
        Mockito.when(world.sections.fieldMappingSection(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("| `EventDetail/Create/Document/Data[@Name='links']` | as it runs |");
        final Map<String, Object> generated = world.call(tool, context, DocumentationWorld.args(args));
        assertThat(world.world.body(String.valueOf(generated.get("uuid")))).contains("as it runs");
        Mockito.verify(world.sections).fieldMappingSection(any(), any(), any(), any(), any(), any(), any());
        Mockito.clearInvocations(world.sections);

        // One written from the XSLT is kept as it is (another pipeline, edited alike).
        world.pipeline(BUILD, "Acme2", "q");
        Mockito.when(world.keptMappings.keptXslt("q")).thenReturn(Optional.of(kept));
        args.put("pipeline_uuid", "q");
        args.put("markdown", "## Purpose and data\n\nTweets.\n\n## Field mapping\n\n| XPath | From |\n"
                             + "| --- | --- |\n| `EventDetail/Create/Document/Data[@Name='links']` | text |\n");
        final Map<String, Object> made = world.call(tool, context, DocumentationWorld.args(args));

        assertThat(world.world.body(String.valueOf(made.get("uuid"))))
                .contains("| `EventDetail/Create/Document/Data[@Name='links']` | text |");
        assertThat(made).doesNotContainKey("field_mapping");
        Mockito.verifyNoInteractions(world.sections);
    }

    @Test
    void aDescriptionIsAskedForAgainOnceItsFieldComesFromElsewhere() {
        // Seen: TWITTER's Description kept "the tweet's text, including mentions and links" after both were taken
        // out of it.
        world.pipeline(BUILD, "Acme", "p");
        world.documentation(BUILD, "Acme", "d", "## Field mapping\n\n<!-- stroom-mcp field descriptions\n"
                                                + "{\"Description\": \"The tweet's text.\", \"Id\": \"Its id.\"}\n-->"
                                                + "\n\n<!-- stroom-mcp field description sources\n"
                                                + "{\"Description\": \"text\", \"Id\": \"id\"}\n-->");
        Mockito.when(world.keptMappings.keptXslt("p")).thenReturn(Optional.of(kept("index", Map.of())));
        Mockito.when(world.sections.fieldMappingSection(any(), anyString(), any(), any(), any(), anyString(), any()))
                .thenAnswer(inv -> {
                    final Descriptions described = inv.getArgument(6);
                    described.of("Description", "replace(text, '@\\w+', '')", List.of("Facts"), "schema words");
                    described.of("Id", "id", List.of("1"), "schema words");
                    return "section";
                });
        final String call = "{\"build\": \"bb\", \"pipeline_uuid\": \"p\", \"markdown\": \"## Purpose and data\\n"
                            + "\\nx\\n\", \"change\": \"Changed\", \"stream_ids\": [5]";

        final Map<String, Object> asked = world.call(tool, context, call + "}");

        assertThat(asked).containsEntry("status", "needs_descriptions");
        assertThat((List<?>) asked.get("fields")).singleElement().satisfies(field -> assertThat(stringMap(field))
                .containsEntry("field", "Description")
                .containsEntry("from", "replace(text, '@\\w+', '')")
                .containsEntry("was", "The tweet's text."));
        assertThat(String.valueOf(asked.get("hint"))).contains("describe it as it is now");

        world.call(tool, context, call + ", \"field_descriptions\": {\"Description\": \"The tweet's text, its "
                                  + "mentions taken out.\"}}");

        // Kept with where it came from now.
        assertThat(world.world.body("d")).contains("\"Description\": \"The tweet's text, its mentions taken out.\"")
                .contains("<!-- stroom-mcp field description sources\n{\n \"Description\": \"replace(text, "
                          + "'@\\\\w+', '')\",\n \"Id\": \"id\"\n}\n-->");
    }

    @Test
    void aNewDocumentationDocSaysPlainlyItWasCreated() {
        // Seen (Delinea SecretServer): a doc just created came back as updated: false, and the agent spent two or
        // three calls checking it had been written.
        world.pipeline(BUILD, "ACME-Events", "p");

        final Map<String, Object> made = world.call(tool, context, "{\"build\": \"bb\", \"pipeline_uuid\": \"p\", "
                                                                   + "\"markdown\": \"## Findings\\n\\nClean.\\n\", "
                                                                   + "\"evaluation\": true}");

        assertThat(made).containsEntry("written", "created").containsEntry("name", "ACME-Events - Evaluation")
                .doesNotContainKey("updated");
        assertThat(String.valueOf(made.get("hint"))).contains("promote_build puts it beside pipeline 'ACME-Events'");
        assertThat(world.world.body(String.valueOf(made.get("uuid")))).startsWith("# ACME-Events - Evaluation\n\n"
                                                                                 + "## Findings\n\nClean.");
    }

    @Test
    void aPromotedPipelinesDocumentationIsChangedThroughAWorkingCopy() {
        // Seen in VS Code: documented after promotion, a second doc of its name was made in a new workspace folder
        // and the promoted one left as it was.
        final String feeds = world.world.folder("System/Feeds/Acme").uuid;
        world.world.add(PipelineDoc.TYPE, "ACME-Events", feeds, "prod");
        final String beside = world.world.add(DocumentationDoc.TYPE, "ACME-Events", feeds, "prod-doc").uuid;
        world.world.docs.put(beside, ((DocumentationDoc) world.world.docs.get(beside)).copy()
                .data(DocumentationVersions.withPending("# ACME-Events\n\nOld.", "", "Created", "jane", "",
                        Instant.parse("2026-10-01T00:00:00Z"))).build());
        world.facts.stages.put("prod", "other");

        final Map<String, Object> made = world.call(tool, context, "{\"build\": \"bb\", \"pipeline_uuid\": \"prod\", "
                                                                   + "\"markdown\": \"## Purpose and data\\n\\nNew."
                                                                   + "\\n\", \"change\": \"Clarified\"}");

        assertThat(made).containsEntry("written", "created, as a working copy of the promoted doc (written back "
                                                  + "over it at promotion)")
                .containsEntry("working_copy_of", Map.of("type", DocumentationDoc.TYPE, "uuid", "prod-doc", "name",
                        "ACME-Events"));
        assertThat(String.valueOf(made.get("next"))).contains("is promoted, so its documentation is changed through "
                                                              + "this working copy in build 'bb'");
        final DocRef copy = new DocRef(DocumentationDoc.TYPE, String.valueOf(made.get("uuid")), "ACME-Events");
        assertThat(world.world.guard().copyOf(copy)).contains("prod-doc");
        // Its change log carries on: the promoted doc's pending change, then this one.
        assertThat(DocumentationVersions.pendingOf(world.world.body(copy.getUuid())))
                .extracting(entry -> entry.get("change")).containsExactly("Created", "Clarified");
    }

    @Test
    void documentingAnIndexIgnoresStreamIdsButNotAcceptedErrors() {
        // Seen: refused for stream_ids, the agent sent its whole markdown again without them.
        final IndexDocumentation indexing = Mockito.mock(IndexDocumentation.class);
        Mockito.when(indexing.documentIndex(any(), any(), any())).thenReturn(Map.of("type", "Documentation"));
        final WriteDocumentationTool withIndexing = new WriteDocumentationTool(world.docs, world.sections, indexing,
                world.keptMappings, world.pipelineShapes, Mockito.mock(GeneratedDrift.class),
                world.world.documentAccess, world.places, world.writes, world.world.guard(), world.translation.consent,
                world.translation.plan);

        final Map<String, Object> result = world.call(withIndexing, context, "{\"build\": \"bb\", \"index_uuid\": "
                                                                             + "\"i\", \"markdown\": \"" + "## Purpose"
                                                                             + " and data\\n\\nx\\n\", \"change\": "
                                                                             + "\"Created\", \"stream_ids\": [1]}");

        assertThat(String.valueOf(((Map<?, ?>) result.get("ignored")).get("stream_ids")))
                .contains("surveyed through Stroom");
        // The user's word that errors are benign isn't dropped unsaid.
        assertThat(world.refusal(withIndexing, context, "{\"build\": \"bb\", \"index_uuid\": \"i\", \"markdown\": "
                                                        + "\"x\", \"accept_errors\": [{\"element\": \"x\", "
                                                        + "\"example\": \"boom\", \"reason\": \"known\"}]}"))
                .startsWith("accept_errors are for a pipeline");
    }

    @Test
    void anIndexTheBuildMadeIsDocumentedAsItsIndexingPipeline() {
        // Seen: asked to document the indexing pipeline, the agent documented the build's new index doc instead (a
        // minute's survey), then the pipeline as well.
        world.world.inBuild(BUILD, ElasticIndexDoc.TYPE, "TWITTER-EVENTS", "i");
        world.world.docs.put("i", ElasticIndexDoc.builder().uuid("i").name("TWITTER-EVENTS").version("v0")
                .indexName("stroom-ecs-twitter-v1").build());
        world.facts.docs.add(FakeBuildFacts.doc(ElasticIndexDoc.TYPE, "i", "TWITTER-EVENTS"));
        world.pipeline(BUILD, "TWITTER-Indexing-V1.0", "p");
        final MergedPipeline merged = Mockito.mock(MergedPipeline.class);
        Mockito.when(merged.properties()).thenReturn(Map.of(new MergedPipeline.ElementProperty("indexer",
                "indexName"), "stroom-ecs-twitter-v1"));
        Mockito.when(world.pipelineShapes.merged("p")).thenReturn(merged);

        assertThat(world.refusal(tool, context, "{\"build\": \"bb\", \"index_uuid\": \"i\", \"markdown\": \"x\"}"))
                .contains("pipeline_uuid='p' ('TWITTER-Indexing-V1.0')");

        // Asked for by the user: the index doc itself as well. It goes on, to the user's confirmation.
        final BuildToolCalls.FormUser user = BuildToolCalls.declining();
        assertThat(world.refusal(tool, user.context(), "{\"build\": \"bb\", \"index_uuid\": \"i\", "
                                                      + "\"markdown\": \"x\", \"index_itself\": true}"))
                .startsWith("The user did not agree to:");

        assertThat(user.forms()).singleElement().satisfies(asked -> assertThat(asked.get("summary"))
                .isEqualTo("Document the existing Elastic Index doc 'TWITTER-EVENTS' (System/MCP Workspace/bb)"));
    }

    @Test
    void aLayoutIsOneOfThoseThereAre() {
        assertThat(world.refusal(tool, context, "{\"build\": \"bb\", \"pipeline_uuid\": \"p\", \"markdown\": \"x\", "
                                                + "\"field_mapping_layout\": \"wide\"}"))
                .isEqualTo("field_mapping_layout is one of ['by_event_type', 'compact'], not 'wide'");
        assertThat(world.refusal(tool, context, "{\"build\": \"bb\", \"pipeline_uuid\": \"p\", \"markdown\": \" \"}"))
                .startsWith("The documentation is empty");
        assertThat(world.refusal(tool, context, "{\"build\": \"bb\", \"pipeline_uuid\": \"p\", \"markdown\": \"x\", "
                                                + "\"field_descriptions\": [1]}"))
                .isEqualTo("field_descriptions is an object, {field: description}");
    }

    @Test
    void theSourceFieldsAreListedWithWhatTheDocumentationSays() {
        final Map<String, Object> notes = Map.of("fields", List.of(Map.of("field", "status", "meaning",
                "Logon status | code", "values", Map.of("0xC000006A", "failed: bad password"))));

        assertThat(WriteDocumentationTool.sourceFieldsMarkdown(notes)).isEqualTo(
                "### Source fields\n\nWhat the source's documentation says each field holds.\n\n| Source field | "
                + "Meaning | Codes |\n| --- | --- | --- |\n| `status` | Logon status / code | `0xC000006A`: failed: "
                + "bad password |\n");
        assertThat(WriteDocumentationTool.sourceFieldsMarkdown(Map.of("fields", List.of()))).isEmpty();
    }

    @Test
    void itIsAWriteApprovedAsAWhole() {
        // write_documentation confirms only accepted errors and an index doc itself, so over /mcp a call is approved
        // as a whole.
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.confirmsItself()).isFalse();
        assertThat(tool.usesRunSlot()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stringMap(final Object value) {
        return (Map<String, Object>) value;
    }

    private static KeptMappings.KeptXslt kept(final String kind, final Map<String, Object> payload) {
        return new KeptMappings.KeptXslt(new KeptMapping.Kept(kind, payload), "translationFilter",
                XsltDoc.builder().uuid("x").name("Acme").data("").build());
    }

    private static String arguments(final String pipelineUuid, final String change) {
        return DocumentationWorld.args(Map.of("build", BUILD, "pipeline_uuid", pipelineUuid, "markdown", MARKDOWN,
                "change", change));
    }
}
