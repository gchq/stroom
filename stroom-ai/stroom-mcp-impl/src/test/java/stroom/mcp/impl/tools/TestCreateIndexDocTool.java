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

import stroom.ai.api.ElasticClusterTests.TestResult;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.docref.DocRef;
import stroom.index.shared.AddField;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.LuceneIndexDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.datasource.FieldType;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// create_index_doc: the build's index doc, made from a field plan or given the plan's new fields.
class TestCreateIndexDocTool {

    private final IndexingWorld world = new IndexingWorld();
    private DocRef cluster;

    @BeforeEach
    void cluster() {
        cluster = world.cluster("dev", "ES_DEV");
        Mockito.when(world.clusterTests.test(any())).thenReturn(new TestResult(true, "ES_DEV 8.15"));
    }

    @Test
    void theIndexDocReplyNamesTheIndexAsTheUserCorrectedIt() {
        // Seen in Ask Stroom AI: the user renamed the index in the form, the doc took it, and the reply kept the old
        // name, which the agent then offered back to the user as what the doc held.
        final Map<String, Object> made = call(elastic("stroom-twitter-onboard-test"), world.forms(question ->
                Map.of("name", "TWITTER-ECS", "index_name", "stroom-ecs-twitter-v1")));
        assertThat(made.get("index name")).isEqualTo("stroom-ecs-twitter-v1");
        final ElasticIndexDoc doc = (ElasticIndexDoc) world.world.docs.get(String.valueOf(made.get("uuid")));
        assertThat(doc.getIndexName()).isEqualTo("stroom-ecs-twitter-v1");
        assertThat(doc.getClusterRef().getUuid()).isEqualTo("dev");
        assertThat(made.get("test")).isEqualTo(Map.of("result", Map.of("ok", true, "message", "ES_DEV 8.15")));
    }

    @Test
    void anIndexDocGivenAsTheClusterIsNamedWithItsCluster() {
        // Seen: the uuid of the FortiOS-V1 Elastic Index (offered as an index to follow) given as cluster_uuid, and
        // Stroom's 500 "Document not found" passed on.
        world.elasticIndex("a96e", "FortiOS-V1", "ecs-fortios-v1", new DocRef("ElasticCluster", "c1", "ES_PROD"),
                false);
        final Map<String, Object> arguments = elastic("fw-v1");
        arguments.put("cluster_uuid", "a96e");
        assertThat(refused(arguments)).isEqualTo("a96e is the Elastic Index doc 'FortiOS-V1', not an Elastic "
                                                 + "Cluster: its cluster is 'ES_PROD' (cluster_uuid=c1). Give that, "
                                                 + "or another from find_elastic_clusters.");
    }

    @Test
    void aNameTheUserChangesInTheIndexDocFormGoesToThePipelineToo() {
        pipelineWriting("acme-test");
        final Map<String, Object> wrong = elastic("acme-v1");
        assertThat(refused(wrong)).contains("writes to 'acme-test', and no index doc names it yet: give "
                                            + "index_name='acme-test'");
        final Map<String, Object> made = call(elastic("acme-test"), world.forms(question ->
                Map.of("name", "ACME-ES", "index_name", "acme-prod-v1")));
        assertThat(((ElasticIndexDoc) world.world.docs.get(String.valueOf(made.get("uuid")))).getIndexName())
                .isEqualTo("acme-prod-v1");
        assertThat(String.valueOf(made.get("pipelines_renamed"))).contains("ACME-Indexing");
        assertThat(String.valueOf(PipelineDocs.addedProperties(world.processing.pipelineDoc("ix")).getLast()
                .getValue().getString())).isEqualTo("acme-prod-v1");
    }

    @Test
    void theClusterTheExistingIndexDocsUseIsTaken() {
        world.elasticIndex("k", "Keycloak", "ecs-keycloak-v1", cluster, false);
        final Map<String, Object> arguments = elastic("acme-v1");
        arguments.remove("cluster_uuid");
        call(arguments, world.forms(question -> null));
        assertThat(world.asked).singleElement().asString()
                .contains("\n- cluster: ES_DEV (the cluster the existing Elastic Index docs use)");
    }

    @Test
    void aLuceneIndexDocGetsThePlansFields() {
        final FieldPlan plan = FieldPlan.of("lucene", "ACME-INDEX", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("UserId", "keyword",
                        "EventSource/User/Id")));
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("build", IndexingWorld.BUILD);
        arguments.put("backend", "lucene");
        arguments.put("name", "ACME-INDEX");
        arguments.put("time_field", "EventTime");
        arguments.put("plan", plan.toJson());
        final Map<String, Object> made = call(arguments, world.forms(question -> Map.of("name", "ACME-INDEX")));
        final LuceneIndexDoc doc = (LuceneIndexDoc) world.world.docs.get(String.valueOf(made.get("uuid")));
        assertThat(doc.getVolumeGroupName()).isEqualTo("Default Volume Group");
        assertThat(doc.getPartitionBy()).isEqualTo(LuceneIndexDoc.PartitionBy.MONTH);
        assertThat(((Map<?, ?>) made.get("fields")).get("added")).isEqualTo(List.of("StreamId", "UserId"));
        final ArgumentCaptor<AddField> added = ArgumentCaptor.forClass(AddField.class);
        Mockito.verify(world.indexResource, Mockito.times(2)).addField(added.capture());
        assertThat(added.getAllValues().getLast().getIndexField().getAnalyzerType().name()).isEqualTo("KEYWORD");
    }

    @Test
    void theBuildsLuceneIndexDocGetsAPlansNewFieldsWhenNamedAgain() {
        // Seen in eval cases 02 and 13: a field wanted after indexing couldn't be added to the build's index, and the
        // agent asked the user to add it in Stroom, or made a second index.
        final Map<String, Object> first = lucene(FieldPlan.of("lucene", "ACME-INDEX", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"))));
        final String uuid = (String) call(first, world.forms(question -> Map.of("name", "ACME-INDEX"))).get("uuid");
        // The field the first call added, as Stroom's index service now lists it.
        Mockito.when(world.indexResource.findFields(any())).thenReturn(ResultPage.createUnboundedList(List.of(
                IndexFieldImpl.builder().fldName("StreamId").fldType(FieldType.ID).build())));
        final List<String> asked = new ArrayList<>();

        final Map<String, Object> again = call(lucene(FieldPlan.of("lucene", "ACME-INDEX", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("Severity", "long",
                        "EventDetail/Alert/Severity")))), world.forms(question -> {
                            asked.add(question);
                            return Map.of("value", true);
                        }));

        assertThat(again.get("uuid")).isEqualTo(uuid);
        assertThat(((Map<?, ?>) again.get("fields")).get("added")).isEqualTo(List.of("Severity"));
        assertThat(((Map<?, ?>) again.get("fields")).get("already_present")).isEqualTo(List.of("StreamId"));
        assertThat(String.valueOf(again.get("hint"))).startsWith("Events indexed before have only the fields");
        assertThat(asked).singleElement().asString().startsWith("Add the plan's fields to Lucene index doc "
                                                                 + "'ACME-INDEX'");
        // Named again with no plan: nothing to add, said so.
        final Map<String, Object> noPlan = lucene(null);
        assertThat(refused(noPlan)).contains("The build already has Lucene index doc 'ACME-INDEX'")
                .contains("give plan=");
    }

    private static Map<String, Object> lucene(final FieldPlan plan) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("build", IndexingWorld.BUILD);
        arguments.put("backend", "lucene");
        arguments.put("name", "ACME-INDEX");
        arguments.put("time_field", "EventTime");
        if (plan != null) {
            arguments.put("plan", plan.toJson());
        }
        return arguments;
    }

    private void pipelineWriting(final String indexName) {
        world.processing.pipeline(new DocRef(PipelineDoc.TYPE, "ix", "ACME-Indexing"), true, "");
        world.processing.layers("ix", world.processing.layer("ix", "xsltFilter", "XSLTFilter",
                        "elasticIndexingFilter", "ElasticIndexingFilter")
                .property("elasticIndexingFilter", "indexName", indexName));
    }

    private static Map<String, Object> elastic(final String indexName) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("build", IndexingWorld.BUILD);
        arguments.put("backend", "elasticsearch");
        arguments.put("name", "ACME-ES");
        arguments.put("index_name", indexName);
        arguments.put("cluster_uuid", "dev");
        arguments.put("time_field", "@timestamp");
        return arguments;
    }

    private Map<String, Object> call(final Map<String, Object> arguments, final ToolCallContext context) {
        final ToolResult result = world.createTool().execute(BuildToolCalls.call(CreateIndexDocTool.NAME,
                ToolJson.dumps(arguments)), context);
        assertThat(result.isError()).as(result.text()).isFalse();
        return BuildToolCalls.json(result.text());
    }

    private String refused(final Map<String, Object> arguments) {
        final ToolResult result = world.createTool().execute(BuildToolCalls.call(CreateIndexDocTool.NAME,
                ToolJson.dumps(arguments)), IndexingWorld.noForms());
        assertThat(result.isError()).as(result.text()).isTrue();
        return result.text();
    }
}
