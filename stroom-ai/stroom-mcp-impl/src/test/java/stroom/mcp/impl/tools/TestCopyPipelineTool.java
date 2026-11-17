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

import stroom.ai.api.ElasticIndexPresence.Presence;
import stroom.ai.api.ElasticIndexPresence.State;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// copy_pipeline, the tool.
class TestCopyPipelineTool {

    private static final String BUILD = PipelineWorld.BUILD;
    private static final String ELASTIC_PIPELINE = "Acme-Events-V1.0-Indexing";
    private static final DocRef CLUSTER = new DocRef("ElasticCluster", "c", "ES_DEV");
    private static final DocRef TEMPLATE = new DocRef(PipelineDoc.TYPE, PipelineWorld.TEMPLATE_UUID,
            "Event Data (Text)");

    private final PipelineWorld pipelines = new PipelineWorld();
    private final CopyPipelineTool tool = pipelines.copyTool();

    @Test
    void copyingATemplateIsRefusedInFavourOfInheriting() {
        pipelines.template(TEMPLATE.getUuid(), TEMPLATE.getName(), PipelineLayers.layer("t"));
        Mockito.when(pipelines.templates.templateReason(TEMPLATE.getUuid()))
                .thenReturn("it is one of the template pipelines");

        final ToolResult refused = call("{\"build\": \"acme-v2\", \"source_uuid\": \"" + TEMPLATE.getUuid()
                                        + "\", \"new_name\": \"Copy of template\"}", BuildToolCalls.context());

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).startsWith("'Event Data (Text)' is a template (it is one of the template "
                                              + "pipelines): templates are inherited, not copied.")
                .contains("create_pipeline(name=..., template_uuid='" + TEMPLATE.getUuid() + "')");
        assertNothingCopied();
    }

    @Test
    void aPipelineCopyLeavesOutTheDocumentsSetPropertiesReplaces() {
        // Seen in e2e: v2's XSLT was made first, then v1's was copied and renamed to the same name, which the build
        // refused.
        source();

        final BuildToolCalls.FormUser user = BuildToolCalls.declining();
        call("{\"build\": \"acme-v2\", \"source_uuid\": "
                                                                  + "\"p1\", \"new_name\": \"ACME-V2 - Indexing\", "
                                                                  + "\"rename\": {\"V1\": \"V2\"}, "
                                                                  + "\"set_properties\": [{\"element\": "
                                                                  + "\"xsltFilter\", \"name\": \"xslt\", "
                                                                  + "\"doc_uuid\": \"x2\", \"doc_type\": "
                                                                  + "\"XSLT\"}]}", user.context());

        assertThat(user.shown()).singleElement().asString().containsPattern("- copied documents: ACME-V2(\n|$)");
    }

    @Test
    void aNewVersionIsTheOriginalWithItsOwnDocumentsCopiedAndRewired() {
        source();
        final ToolCallContext context = BuildToolCalls.answering(Map.of("value", true));

        final Map<String, Object> made = PipelineWorld.json(call("{\"build\": \"" + BUILD + "\", \"source_uuid\": "
                                                                 + "\"p1\", \"new_name\": \"ACME-V2 - Indexing\", "
                                                                 + "\"rename\": {\"V1\": \"V2\"}, "
                                                                 + "\"set_properties\": \"splitFilter.splitCount=5\"}",
                context));

        final PipelineDoc copy = pipelines.pipelineNamed("ACME-V2 - Indexing");
        final PipelineDoc source = pipelines.pipelineDoc("p1");
        assertThat(copy.getParentPipeline()).isEqualTo(TEMPLATE);
        assertThat(copy.getDescription()).isEqualTo("Indexes Acme.");
        // The same element changes as the original's.
        assertThat(copy.getPipelineData().getAddedElements()).isEqualTo(source.getPipelineData()
                .getAddedElements());
        final XsltDoc xslt = (XsltDoc) docNamed("ACME-V2-XSLT");
        final TextConverterDoc converter = (TextConverterDoc) docNamed("ACME-V2");
        assertThat(xslt.getData()).isEqualTo("<xsl:stylesheet/>");
        assertThat(converter.getData()).isEqualTo("<dataSplitter/>");
        assertThat(PipelineDocs.addedProperties(copy)).containsExactly(
                new PipelineProperty("xsltFilter", "xslt", new PipelinePropertyValue(new DocRef(XsltDoc.TYPE,
                        xslt.getUuid(), "ACME-V2-XSLT"))),
                new PipelineProperty("dsParser", "textConverter", new PipelinePropertyValue(new DocRef(
                        TextConverterDoc.TYPE, converter.getUuid(), "ACME-V2"))),
                new PipelineProperty("splitFilter", "splitCount", new PipelinePropertyValue(5)));
        // A new version, promoted alongside the original.
        assertThat(pipelines.world.records().entry(BUILD, copy.getUuid())).containsEntry("version_of", "p1");
        assertThat(pipelines.world.records().entry(BUILD, xslt.getUuid())).containsEntry("version_of", "x1");
        assertThat(made).containsEntry("copy_of", "ACME-V1 - Indexing").containsKey("next");
        assertThat(made.get("copied_documents")).isEqualTo(List.of(
                Map.of("type", "XSLT", "uuid", xslt.getUuid(), "name", "ACME-V2-XSLT"),
                Map.of("type", "TextConverter", "uuid", converter.getUuid(), "name", "ACME-V2")));
    }

    @Test
    void aPromotedPipelineIsAlwaysCopiedAsANewVersionWithoutAsking() {
        // Decided by the user (2026-11-17): no change in place of a promoted pipeline, so nothing to choose; the
        // names are proposed and confirmed in the one form.
        source();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> made = PipelineWorld.json(call("{\"build\": \"" + BUILD + "\", \"source_uuid\": "
                                                                 + "\"p1\", \"new_name\": \"ACME-V1 - Indexing\"}",
                user.context()));

        assertThat(user.shown()).singleElement().asString().startsWith("Copy pipeline 'ACME-V1 - Indexing' as a "
                                                                       + "new version, 'ACME-V2 - Indexing'");
        assertThat(made).containsEntry("name", "ACME-V2 - Indexing");
        assertThat(pipelines.world.records().entry(BUILD, String.valueOf(made.get("uuid"))))
                .containsEntry("version_of", "p1");
    }

    @Test
    void aWorkingCopyOfAPromotedPipelineIsRefused() {
        source();
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final ToolResult refused = call("{\"build\": \"" + BUILD + "\", \"source_uuid\": \"p1\", \"new_name\": "
                                        + "\"ACME-V1 - Indexing\", \"working_copy\": true}", user.context());

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).contains("is promoted", "only a new version of it is copied", "Nothing was copied");
        assertThat(user.shown()).isEmpty();
        assertThat(pipelines.pipelineNamed("ACME-V2 - Indexing")).isNull();
    }

    @Test
    void aNewVersionTheBuildMadeAlreadyUnderTheOriginalsNameIsRefused() {
        // An aggregated feed's group XSLT made by build_translation_xslt before its new versions were named for
        // them: run, not copied again, so its name would be the original's in the new version.
        source();
        final DocRef made = pipelines.world.inBuild(BUILD, XsltDoc.TYPE, "ACME-V1-XSLT", "x1-version");
        pipelines.world.docs.put("x1-version", XsltDoc.builder().uuid("x1-version").name("ACME-V1-XSLT")
                .version("v0").data("<xsl:stylesheet/>").build());
        pipelines.world.records().update(BUILD, made, entry -> entry.put("version_of", "x1"));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final ToolResult refused = call("{\"build\": \"" + BUILD + "\", \"source_uuid\": \"p1\", \"new_name\": "
                                        + "\"ACME-V2 - Indexing\"}", user.context());

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).startsWith("A new version of ACME-V1 - Indexing needs names of its own")
                .contains("'ACME-V1-XSLT' (x1-version)", "have the original's", "Rename each in Stroom",
                        "Nothing was copied");
        assertThat(user.shown()).isEmpty();
        assertThat(pipelines.pipelineNamed("ACME-V2 - Indexing")).isNull();
    }

    @Test
    void withoutFormsTheAgentIsGivenTheProposedNamesToPutToTheUser() {
        source();

        final Map<String, Object> asked = PipelineWorld.json(call("{\"build\": \"" + BUILD + "\", \"source_uuid\": "
                                                                  + "\"p1\", \"new_name\": \"ACME-V1 - Indexing\"}",
                BuildToolCalls.context()));

        assertThat(asked).containsEntry("status", "needs_names");
        @SuppressWarnings("unchecked") final Map<String, Object> names = (Map<String, Object>) asked.get(
                "new_version_names");
        assertThat(names).containsEntry("pipeline", "ACME-V2 - Indexing");
        assertThat(String.valueOf(asked.get("hint"))).contains("Nothing was copied");
        assertThat(pipelines.pipelineNamed("ACME-V2 - Indexing")).isNull();
    }

    @ParameterizedTest
    @MethodSource("originalNames")
    void aNewVersionUnderAnyOfTheOriginalsNamesIsRefused(final String key, final String name, final String said) {
        elasticSource("acme-events-v1");
        final Map<String, Object> answer = new LinkedHashMap<>(Map.of("value", true));
        answer.put(key, name);

        final ToolResult refused = call("{\"build\": \"" + BUILD + "\", \"source_uuid\": \"p1\", \"new_name\": \""
                                        + ELASTIC_PIPELINE + "\"}", BuildToolCalls.answering(answer));

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).startsWith("A new version of " + ELASTIC_PIPELINE + " needs names of its own")
                .contains(said)
                .contains("Nothing was copied");
        assertNothingCopied();
    }

    @Test
    void aNewVersionOfAnElasticsearchIndexingPipelineGetsAnIndexOfItsOwnCheckedFree() {
        elasticSource("acme-events-v1");
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final Map<String, Object> made = PipelineWorld.json(call("{\"build\": \"" + BUILD + "\", \"source_uuid\": "
                                                                 + "\"p1\", \"new_name\": \"" + ELASTIC_PIPELINE
                                                                 + "\"}", user.context()));

        assertThat(user.shown().getLast()).contains("- index: its own, 'acme-events-v2' (was 'acme-events-v1')")
                .contains("Acme-Events-V2.0-Indexing instead");
        final PipelineDoc copy = pipelines.pipelineNamed("Acme-Events-V1.1-Indexing");
        assertThat(made).containsEntry("name", "Acme-Events-V1.1-Indexing")
                .containsEntry("index_name", "acme-events-v2");
        assertThat(PipelineDocs.addedProperties(copy)).contains(new PipelineProperty("elasticIndexingFilter",
                "indexName", new PipelinePropertyValue("acme-events-v2")));
        assertThat(docNamed("Acme-Events-V1.1-Indexing-XSLT")).isNotNull();
        // An Elastic Index doc for the new index, beside the old one.
        final ElasticIndexDoc indexDoc = (ElasticIndexDoc) docNamed("Acme-Events-V1.1");
        assertThat(indexDoc.getIndexName()).isEqualTo("acme-events-v2");
        assertThat(indexDoc.getClusterRef()).isEqualTo(CLUSTER);
        assertThat(pipelines.world.records().entry(BUILD, indexDoc.getUuid())).containsEntry("version_of", "es1");
        assertThat(((ElasticIndexDoc) pipelines.world.docs.get("es1")).getIndexName()).isEqualTo("acme-events-v1");
        // Looked up before anything was copied.
        assertThat(pipelines.lookedUp).containsExactly("acme-events-v2");
    }

    @ParameterizedTest
    @MethodSource("takenIndexes")
    void aNewVersionsIndexNameMatchingAnythingIsRefusedBeforeAnythingIsCopied(final String index,
                                                                             final String lookedUp,
                                                                             final Presence presence,
                                                                             final boolean otherPipeline,
                                                                             final String said,
                                                                             final String free) {
        elasticSource(index);
        if (presence != null) {
            pipelines.presences.put(lookedUp, presence);
        }
        if (otherPipeline) {
            pipelines.productionPipeline("other", "Other-Indexing", TEMPLATE, PipelineLayers.layer("other")
                    .property("elasticIndexingFilter", "indexName", "acme-events-v2"));
        }

        final ToolResult refused = call("{\"build\": \"" + BUILD + "\", \"source_uuid\": \"p1\", \"new_name\": \""
                                        + ELASTIC_PIPELINE + "\"}", BuildToolCalls.answering(Map.of("value", true)));

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).contains("(looked up as '" + lookedUp + "') is not free")
                .contains(said)
                .contains("Nothing was copied")
                .contains("'" + free + "' is free")
                .doesNotContain("DELETE");
        assertNothingCopied();
    }

    static Stream<Arguments> originalNames() {
        return Stream.of(
                Arguments.of(CopyPipelineTool.NEW_NAME_ARG, ELASTIC_PIPELINE, "pipeline name '" + ELASTIC_PIPELINE
                                                                              + "'"),
                Arguments.of("doc:x1", "Acme-Events-V1.0-Indexing-XSLT", "document name "
                                                                         + "'Acme-Events-V1.0-Indexing-XSLT'"),
                Arguments.of("index_name", "acme-events-v1", "index name 'acme-events-v1'"),
                Arguments.of("index_doc:es1", "Acme-Events-V1.0", "Elastic Index doc name 'Acme-Events-V1.0'"));
    }

    static Stream<Arguments> takenIndexes() {
        return Stream.of(
                // Even an empty index.
                Arguments.of("acme-events-v1", "acme-events-v2", new Presence(State.PRESENT, List.of(),
                        List.of("acme-events-v2"), List.of(), 0L, null), false, "it matches an index "
                                                                                + "(acme-events-v2), holding 0 "
                                                                                + "document(s)", "acme-events-v3"),
                // A data stream, through the name's variable.
                Arguments.of("acme-{type}-v1", "acme-*-v2", new Presence(State.PRESENT, List.of("acme-web-v2"),
                        List.of(), List.of(), 12L, null), false, "it matches a data stream (acme-web-v2), holding "
                                                                 + "12 document(s)", "acme-{type}-v3"),
                Arguments.of("acme-events-v1", "acme-events-v2", new Presence(State.PRESENT, List.of(), List.of(),
                        List.of("acme-events-v2"), null, null), false, "it matches an alias (acme-events-v2), "
                                                                       + "holding a number of documents that "
                                                                       + "couldn't be counted", "acme-events-v3"),
                // Another Stroom pipeline's index, though nothing is on the cluster yet.
                Arguments.of("acme-events-v1", "acme-events-v2", null, true, "pipeline 'Other-Indexing' writes to "
                                                                             + "'acme-events-v2'", "acme-events-v3"),
                // Not known is never free.
                Arguments.of("acme-events-v1", "acme-events-v2", Presence.unknown("unauthorized"), false,
                        "couldn't be looked up on cluster ES_DEV (unauthorized)", "acme-events-v3"));
    }

    // p1, a promoted Elasticsearch indexing pipeline writing to the index on ES_DEV with its own XSLT, and the
    // promoted Elastic Index doc naming the index.
    private void elasticSource(final String index) {
        pipelines.template(TEMPLATE.getUuid(), TEMPLATE.getName(), PipelineLayers.layer(TEMPLATE.getUuid())
                .chain("xsltFilter", "XSLTFilter", "elasticIndexingFilter", "ElasticIndexingFilter"));
        final String folder = pipelines.world.folder("System/Feeds/Acme").uuid;
        pipelines.world.add(XsltDoc.TYPE, "Acme-Events-V1.0-Indexing-XSLT", folder, "x1");
        pipelines.world.docs.put("x1", XsltDoc.builder().uuid("x1").name("Acme-Events-V1.0-Indexing-XSLT")
                .version("v0").data("<xsl:stylesheet/>").build());
        pipelines.productionPipeline("p1", ELASTIC_PIPELINE, TEMPLATE, PipelineLayers.layer("p1")
                .property("xsltFilter", "xslt", new DocRef(XsltDoc.TYPE, "x1", "Acme-Events-V1.0-Indexing-XSLT"))
                .property("elasticIndexingFilter", "indexName", index)
                .property("elasticIndexingFilter", "cluster", CLUSTER));
        pipelines.world.add(ElasticIndexDoc.TYPE, "Acme-Events-V1.0", folder, "es1");
        pipelines.world.docs.put("es1", ElasticIndexDoc.builder().uuid("es1").name("Acme-Events-V1.0")
                .version("v0").indexName(index).clusterRef(CLUSTER).build());
    }

    // No pipeline, XSLT or Elastic Index doc made (the build's record may be).
    private void assertNothingCopied() {
        assertThat(pipelines.world.created).noneMatch(made -> made.startsWith(PipelineDoc.TYPE + ":")
                                                              || made.startsWith(XsltDoc.TYPE + ":")
                                                              || made.startsWith(ElasticIndexDoc.TYPE + ":"));
    }


    private void source() {
        pipelines.template(TEMPLATE.getUuid(), TEMPLATE.getName(), PipelineLayers.layer(TEMPLATE.getUuid())
                .chain("dsParser", "DSParser", "splitFilter", "SplitFilter", "xsltFilter", "XSLTFilter"));
        pipelines.world.add(XsltDoc.TYPE, "ACME-V1-XSLT", pipelines.world.folder("System/Feeds/Acme").uuid, "x1");
        pipelines.world.docs.put("x1", XsltDoc.builder().uuid("x1").name("ACME-V1-XSLT").version("v0")
                .data("<xsl:stylesheet/>").build());
        pipelines.world.add(TextConverterDoc.TYPE, "ACME-V1", pipelines.world.folder("System/Feeds/Acme").uuid, "t1");
        pipelines.world.docs.put("t1", TextConverterDoc.builder().uuid("t1").name("ACME-V1").version("v0")
                .data("<dataSplitter/>").build());
        final PipelineDoc doc = pipelines.productionPipeline("p1", "ACME-V1 - Indexing", TEMPLATE,
                PipelineLayers.layer("p1")
                        .element("extraFilter", "XSLTFilter")
                        .link("xsltFilter", "extraFilter")
                        .property("xsltFilter", "xslt", new DocRef(XsltDoc.TYPE, "x1", "ACME-V1-XSLT"))
                        .property("dsParser", "textConverter", new DocRef(TextConverterDoc.TYPE, "t1", "ACME-V1")));
        pipelines.world.docs.put("p1", doc.copy().description("Indexes Acme.").build());
    }

    private Object docNamed(final String name) {
        return pipelines.world.nodes.values().stream()
                .filter(node -> name.equals(node.name) && pipelines.world.tags(node.uuid).contains(WriteGuard.MANAGED))
                .map(node -> pipelines.world.docs.get(node.uuid))
                .findFirst()
                .orElseThrow();
    }

    private ToolResult call(final String arguments, final ToolCallContext context) {
        return tool.execute(BuildToolCalls.call(CopyPipelineTool.NAME, arguments), context);
    }
}
