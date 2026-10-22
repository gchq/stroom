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

import stroom.ai.api.ElasticClusterTests;
import stroom.ai.api.ElasticClusterTests.TestResult;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class TestFindElasticClustersTool {

    private static final DocRef PROD = new DocRef(ElasticClusterDoc.TYPE, "prod", "ES_PROD");
    private static final DocRef DEV = new DocRef(ElasticClusterDoc.TYPE, "dev", "ES_DEV");
    private static final DocRef VPN = new DocRef(ElasticIndexDoc.TYPE, "vpn", "ecs-vpn-v1");
    private static final DocRef GONE = new DocRef(ElasticIndexDoc.TYPE, "gone", "Gone");

    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final ElasticClusterTests clusterTests = Mockito.mock(ElasticClusterTests.class);
    private FindElasticClustersTool tool;

    @BeforeEach
    void setUp() {
        Mockito.when(pipelineIndex.list(ElasticClusterDoc.TYPE)).thenReturn(List.of(DEV, PROD));
        Mockito.when(pipelineIndex.list(ElasticIndexDoc.TYPE)).thenReturn(List.of(GONE, VPN));
        Mockito.when(documentAccess.path(any())).thenReturn("/Indexes");
        Mockito.when(documentAccess.read(any(), any())).thenReturn(Optional.empty());
        Mockito.when(documentAccess.read(PROD, ElasticClusterDoc.class)).thenReturn(Optional.of(cluster(PROD,
                "https://es-prod:9200")));
        Mockito.when(documentAccess.read(DEV, ElasticClusterDoc.class)).thenReturn(Optional.of(cluster(DEV,
                "https://es-dev:9200")));
        Mockito.when(documentAccess.read(VPN, ElasticIndexDoc.class)).thenReturn(Optional.of(ElasticIndexDoc
                .builder().uuid("vpn").name("ecs-vpn-v1").indexName("ecs-vpn-v1").timeField("@timestamp")
                .clusterRef(PROD).build()));
        tool = new FindElasticClustersTool(pipelineIndex, documentAccess, () -> clusterTests);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEachClusterIsListedWithTheIndexDocsThatUseIt() {
        final Map<String, Object> out = call("{}");
        final List<Map<String, Object>> clusters = (List<Map<String, Object>>) out.get("clusters");
        assertThat(clusters).extracting(c -> c.get("name")).containsExactly("ES_DEV", "ES_PROD");
        assertThat(clusters.get(0).get("index_docs")).isEqualTo(List.of());
        final Map<String, Object> index = ((List<Map<String, Object>>) clusters.get(1).get("index_docs")).getFirst();
        assertThat(index).containsEntry("index_name", "ecs-vpn-v1")
                .containsEntry("time_field", "@timestamp")
                .containsEntry("path", "System/Indexes");
        assertThat(clusters.get(1).get("urls")).isEqualTo(List.of("https://es-prod:9200"));
        assertThat(clusters.get(1)).doesNotContainKey("test");
        // One doc Stroom can't give is said; the others still listed.
        assertThat((List<String>) out.get("unreadable")).containsExactly(
                "Elastic Index 'Gone': the document cannot be read (deleted, or no permission)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEachClustersTestIsItsOwnAnswer() {
        Mockito.when(clusterTests.test(any())).thenAnswer(invocation -> {
            final ElasticClusterDoc doc = invocation.getArgument(0);
            if ("dev".equals(doc.getUuid())) {
                throw new RuntimeException("Connection refused");
            }
            return new TestResult(true, "Cluster name: prod");
        });
        final List<Map<String, Object>> clusters = (List<Map<String, Object>>) call("{\"test\": true}")
                .get("clusters");
        assertThat(clusters.get(0).get("test")).isEqualTo(Map.of("ok", false, "message", "Connection refused"));
        assertThat(clusters.get(1).get("test")).isEqualTo(Map.of("ok", true, "message", "Cluster name: prod"));
    }

    @Test
    void testTheToolIsAnIndexingOne() {
        assertThat(tool.category()).isEqualTo(AiToolCategory.INDEXING);
        assertThat(tool.isReadOnly()).isTrue();
    }

    private Map<String, Object> call(final String arguments) {
        final ToolResult result = tool.execute(new ToolCall("1", FindElasticClustersTool.NAME, arguments),
                ToolCallContext.builder(ToolSession.chat(1)).build());
        assertThat(result.isError()).isFalse();
        return result.structuredContent();
    }

    private static ElasticClusterDoc cluster(final DocRef ref, final String url) {
        final ElasticConnectionConfig connection = new ElasticConnectionConfig();
        connection.setConnectionUrls(List.of(url));
        return ElasticClusterDoc.builder().uuid(ref.getUuid()).name(ref.getName()).connection(connection).build();
    }
}
