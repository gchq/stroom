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

package stroom.mcp.impl.e2e;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.mcp.impl.e2e.InsiderDataset.Act;
import stroom.mcp.impl.e2e.InsiderDataset.Options;
import stroom.mcp.impl.e2e.InsiderDataset.Source;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// An e2e test on the insider-risk dataset ([InsiderDataset]): [#load] generates it (by default the reduced form,
/// [Options#reduced]) and puts each source's events in an Elasticsearch index of its own on the test node (ECS
/// fields, the person id as `user.id`, the account normalised as `user.name`; [InsiderRecords#document]) and in an
/// Events stream in Stroom (each event's XML, its EventId its place in the stream, as the index's StreamId and EventId
/// say), with an Elastic Index doc per source named `E2E insider <source> <index>`. The indexes are removed after
/// each test.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`.
abstract class AbstractInsiderE2eTest extends AbstractE2eTest {

    private static final int BULK = 5_000;

    @Inject
    private ElasticIndexStore elasticIndexStore;

    private ElasticTestNode node;
    private final List<String> indexNames = new ArrayList<>();

    /// Loads the dataset into the test node and Stroom.
    ///
    /// @param options What to generate.
    /// @return The dataset and where each source went.
    Loaded load(final Options options) {
        node = ElasticTestNode.getOrSkip();
        final InsiderDataset dataset = InsiderDataset.generate(options);
        final DocRef cluster = liveElasticCluster(node, "E2E insider cluster " + System.nanoTime());
        final Map<Source, DocRef> indexes = new EnumMap<>(Source.class);
        final Map<Source, Long> streams = new EnumMap<>(Source.class);
        for (final Source source : Source.values()) {
            final List<Act> acts = dataset.acts(source);
            if (acts.isEmpty()) {
                continue;
            }
            final String indexName = ElasticTestNode.createIndexName("insider-" + source.key());
            indexNames.add(indexName);
            final long stream = storeEvents("INSIDER-" + source.key().toUpperCase(Locale.ROOT) + "-"
                                            + indexName.toUpperCase(Locale.ROOT), acts.stream()
                    .map(InsiderRecords::eventXml).toList());
            streams.put(source, stream);
            node.createIndex(indexName, InsiderRecords.indexBody(source));
            for (int from = 0; from < acts.size(); from += BULK) {
                final ArrayNode documents = JsonUtil.getMapper().createArrayNode();
                for (final Act act : acts.subList(from, Math.min(acts.size(), from + BULK))) {
                    final ObjectNode document = documents.addObject();
                    InsiderRecords.document(act, stream).forEach((field, value) -> {
                        if (value instanceof final Number number) {
                            document.put(field, number.longValue());
                        } else {
                            document.put(field, String.valueOf(value));
                        }
                    });
                }
                node.bulkIndex(indexName, documents);
            }
            final ExplorerNode created = create(ElasticIndexDoc.TYPE, "E2E insider " + source.key() + " "
                                                                      + indexName, templateFolder());
            asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                    .copy()
                    .clusterRef(cluster)
                    .indexName(indexName)
                    .timeField("@timestamp")
                    .build()));
            indexes.put(source, created.getDocRef());
        }
        return new Loaded(dataset, indexes, streams);
    }

    /// @return The test node, once [#load] has run.
    ElasticTestNode node() {
        return node;
    }

    @AfterEach
    void removeTheInsiderIndexes() {
        if (node != null) {
            indexNames.forEach(node::deleteIndices);
        }
        indexNames.clear();
    }


    // --------------------------------------------------------------------------------


    /// The dataset loaded.
    ///
    /// @param dataset The dataset, with its known answer.
    /// @param indexes Each source's Elastic Index doc.
    /// @param streams Each source's Events stream.
    record Loaded(InsiderDataset dataset, Map<Source, DocRef> indexes, Map<Source, Long> streams) {

        /// @param source A source.
        /// @return Its Elastic Index doc's uuid.
        String index(final Source source) {
            return indexes.get(source).getUuid();
        }
    }
}
