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
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `find_elastic_clusters` - the Elastic Cluster documents the user can view, with their connection URLs (never
/// credentials), the Elastic Index documents that use each, and those documents' settings; optionally with
/// Stroom's connection test of each. It never creates or changes a cluster document.
class FindElasticClustersTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FindElasticClustersTool.class);

    static final String NAME = "find_elastic_clusters";
    static final String TEST_ARG = "test";
    /// The most cluster documents listed.
    static final int MAX_CLUSTERS = 50;
    /// The most index documents read.
    static final int MAX_INDEXES = 500;

    private final PipelineIndex pipelineIndex;
    private final DocumentAccess documentAccess;
    private final Provider<ElasticClusterTests> elasticClusterTestsProvider;

    @Inject
    FindElasticClustersTool(final PipelineIndex pipelineIndex,
                            final DocumentAccess documentAccess,
                            final Provider<ElasticClusterTests> elasticClusterTestsProvider) {
        this.pipelineIndex = pipelineIndex;
        this.documentAccess = documentAccess;
        this.elasticClusterTestsProvider = elasticClusterTestsProvider;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.INDEXING;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .note("With test, each cluster is reached with its document's own connection settings and "
                      + "credentials, as its Test Connection button does.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Elasticsearch: the Elastic Cluster docs in Stroom with their connection URLs (never "
                             + "credentials), the Elastic Index docs that use each, and those docs' settings. Pick "
                             + "the cluster that sibling sources use and confirm it with the user; this tool never "
                             + "creates or changes cluster docs.")
                .parameters(ObjectSchema.builder()
                        .addBooleanProperty(TEST_ARG, "Also run Stroom's connection test on each.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final boolean test = Boolean.parseBoolean(stringArg(call, TEST_ARG));
        final List<String> unread = new ArrayList<>();
        final Map<String, List<Map<String, Object>>> byCluster = indexDocsByCluster(unread);

        final List<Map<String, Object>> clusters = new ArrayList<>();
        final List<DocRef> clusterRefs = pipelineIndex.list(ElasticClusterDoc.TYPE);
        for (final DocRef ref : clusterRefs.subList(0, Math.min(MAX_CLUSTERS, clusterRefs.size()))) {
            final Optional<ElasticClusterDoc> doc = documentAccess.read(ref, ElasticClusterDoc.class);
            if (doc.isEmpty()) {
                unread.add("Elastic Cluster '" + ref.getName() + "': the document cannot be read (deleted, or no "
                           + "permission)");
                continue;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", doc.get().getName());
            entry.put("uuid", doc.get().getUuid());
            entry.put("urls", NullSafe.get(doc.get().getConnection(), ElasticConnectionConfig::getConnectionUrls));
            entry.put("index_docs", byCluster.getOrDefault(ref.getUuid(), List.of()));
            if (test) {
                // Each cluster's own result: one that can't be reached is that cluster's answer, not the whole
                // call's.
                entry.put("test", test(doc.get()));
            }
            clusters.add(entry);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("clusters", clusters);
        if (!unread.isEmpty()) {
            result.put("unreadable", unread);
        }
        return JsonResults.of(result);
    }

    // Every Elastic Index doc the user can view, by the cluster it uses: one that can't be read (deleted since it
    // was listed) is said, the others still listed.
    private Map<String, List<Map<String, Object>>> indexDocsByCluster(final List<String> unread) {
        final Map<String, List<Map<String, Object>>> byCluster = new LinkedHashMap<>();
        final List<DocRef> indexRefs = pipelineIndex.list(ElasticIndexDoc.TYPE);
        for (final DocRef ref : indexRefs.subList(0, Math.min(MAX_INDEXES, indexRefs.size()))) {
            final Optional<ElasticIndexDoc> doc = documentAccess.read(ref, ElasticIndexDoc.class);
            if (doc.isEmpty()) {
                unread.add("Elastic Index '" + ref.getName() + "': the document cannot be read (deleted, or no "
                           + "permission)");
                continue;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", doc.get().getName());
            entry.put("uuid", doc.get().getUuid());
            entry.put("index_name", doc.get().getIndexName());
            entry.put("time_field", doc.get().getTimeField());
            entry.put("path", PipelineIndex.path(documentAccess, ref));
            byCluster.computeIfAbsent(NullSafe.get(doc.get().getClusterRef(), DocRef::getUuid),
                    k -> new ArrayList<>()).add(entry);
        }
        return byCluster;
    }

    private Map<String, Object> test(final ElasticClusterDoc cluster) {
        final Map<String, Object> outcome = new LinkedHashMap<>();
        try {
            final TestResult result = elasticClusterTestsProvider.get().test(cluster);
            outcome.put("ok", result.ok());
            outcome.put("message", result.message());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to test cluster " + cluster.getUuid(), e);
            outcome.put("ok", false);
            outcome.put("message", PipelineShapes.reason(e));
        }
        return outcome;
    }
}
