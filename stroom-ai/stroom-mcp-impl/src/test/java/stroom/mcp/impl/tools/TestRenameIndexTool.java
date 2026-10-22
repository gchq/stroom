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
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// rename_index, as the Stroom MCP server's tests/test_index_rename.py tests it: one index name for the build's
/// Elastic Index doc and its indexing pipelines, changed only in a build.
class TestRenameIndexTool {

    private final IndexingWorld world = new IndexingWorld();

    @Test
    @SuppressWarnings("unchecked")
    void aNewIndexNameGoesToTheDocAndEveryPipelineWritingToIt() {
        final String agreed = KeptMapping.withAgreedTemplate(null, Map.of("name", "acme-v1", "index", "acme-v1",
                "cluster", "ES", "dev_tools", ""));
        setUp("acme-v1", "acme-v1", agreed);
        world.processing.filter("ix", 3, true, ExpressionOperator.builder().build(), null);
        final Map<String, Object> result = rename("acme-prod-v1", world.forms(question -> Map.of("value", true)));
        assertThat(((ElasticIndexDoc) world.world.docs.get("i")).getIndexName()).isEqualTo("acme-prod-v1");
        assertThat(PipelineDocs.addedProperties(world.processing.pipelineDoc("ix")).getLast().getValue().getString())
                .isEqualTo("acme-prod-v1");
        assertThat(result.get("pipelines")).isEqualTo(List.of("ACME-Indexing"));
        // What the old name left on the cluster, for the admin: its template, and the index this build wrote to.
        final List<String> removals = (List<String>) result.get("for_the_cluster_admin");
        assertThat(removals.getFirst()).isEqualTo("DELETE _index_template/acme-v1");
        assertThat(removals.get(1)).startsWith("DELETE ");
        assertThat((List<Map<String, Object>>) result.get("user_actions"))
                .anyMatch(action -> "DELETE _index_template/acme-v1".equals(action.get("request")));
        assertThat(String.valueOf(result.get("hint"))).contains("propose_index_template again");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theUserConfirmsWhatChangesWithTheName() {
        setUp("acme-v1", "acme-v1", "");
        final Map<String, Object> asked = rename("acme-prod-v1", IndexingWorld.noForms());
        assertThat(asked.get("status")).isEqualTo("needs_confirmation");
        assertThat(((Map<String, Object>) asked.get("details")).get("indexing pipelines changed with it"))
                .isEqualTo(List.of("ACME-Indexing"));
        assertThat(((ElasticIndexDoc) world.world.docs.get("i")).getIndexName()).isEqualTo("acme-v1");
    }

    @Test
    void renamingIsRefusedOutsideABuildAndForNamesBuiltFromValues() {
        setUp("acme-2026", "acme-{_year}", "");
        assertThat(refused("acme-prod")).contains("builds its index name from values");
        assertThat(refused("Acme Prod")).contains("not an Elasticsearch index name");
        world.world.move("i", world.world.folder("System/Elsewhere").uuid);
        assertThat(refused("acme-prod")).contains("is not in a build folder");
        assertThat(((ElasticIndexDoc) world.world.docs.get("i")).getIndexName()).isEqualTo("acme-2026");
    }

    @Test
    void theSameNameChangesNothing() {
        setUp("acme-v1", "acme-v1", "");
        final Map<String, Object> result = rename("acme-v1", IndexingWorld.noForms());
        assertThat(result.get("hint")).isEqualTo("The doc already names 'acme-v1': nothing changed.");
    }

    @Test
    void aDocThatIsNotAnElasticIndexIsRefused() {
        assertThat(refused("acme-v1", "nope")).isEqualTo("nope is not an Elastic Index doc: give the build's "
                                                         + "(find_documents type ElasticIndex). A Lucene index is "
                                                         + "named by its doc: nothing to rename.");
    }

    private void setUp(final String docIndex, final String pipelineIndex, final String description) {
        world.elasticIndex("i", "ACME-ES", docIndex, new DocRef("ElasticCluster", "c", "ES"), true);
        world.processing.pipeline(new DocRef(PipelineDoc.TYPE, "ix", "ACME-Indexing"), true, description);
        world.processing.layers("ix", world.processing.layer("ix", "xsltFilter", "XSLTFilter",
                        "elasticIndexingFilter", "ElasticIndexingFilter")
                .property("elasticIndexingFilter", "indexName", pipelineIndex));
    }

    private Map<String, Object> rename(final String indexName,
                                       final ToolCallContext context) {
        final ToolResult result = world.renameTool().execute(PortedToolCalls.call(RenameIndexTool.NAME,
                PyJson.dumps(Map.of("index_uuid", "i", "index_name", indexName))), context);
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }

    private String refused(final String indexName) {
        return refused(indexName, "i");
    }

    private String refused(final String indexName, final String uuid) {
        final ToolResult result = world.renameTool().execute(PortedToolCalls.call(RenameIndexTool.NAME,
                PyJson.dumps(Map.of("index_uuid", uuid, "index_name", indexName))), IndexingWorld.noForms());
        assertThat(result.isError()).as(result.text()).isTrue();
        return result.text();
    }
}
