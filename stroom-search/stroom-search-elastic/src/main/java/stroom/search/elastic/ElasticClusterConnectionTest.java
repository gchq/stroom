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

package stroom.search.elastic;

import stroom.search.elastic.shared.ElasticClusterTestResponse;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;

/// Stroom's connection test of an Elastic Cluster document, as its Test Connection button and the AI tools run it:
/// it connects with the document's settings and asks the cluster for its name and version.
final class ElasticClusterConnectionTest {

    private ElasticClusterConnectionTest() {
    }

    /// @param connection          The cluster document's connection, its credentials resolved
    ///                            ([ElasticConnectionResolver]).
    /// @param clientFactory       Makes the client.
    /// @param elasticClientConfig How Stroom's Elasticsearch clients are set up.
    /// @return Whether the cluster answered, and what it said, or why it couldn't be reached.
    static ElasticClusterTestResponse test(final ResolvedElasticConnection connection,
                                           final ElasticClientFactory clientFactory,
                                           final ElasticClientConfig elasticClientConfig) {
        try (final ElasticsearchClient elasticClient = clientFactory.create(connection, elasticClientConfig)) {
            final InfoResponse response = elasticClient.info();

            final StringBuilder sb = new StringBuilder()
                    .append("Cluster URLs: ")
                    .append(connection.connectionUrls())
                    .append("\nCluster name: ")
                    .append(response.clusterName())
                    .append("\nCluster UUID: ")
                    .append(response.clusterUuid())
                    .append("\nNode name: ")
                    .append(response.name())
                    .append("\nVersion: ")
                    .append(response.version().number());

            return new ElasticClusterTestResponse(true, sb.toString());

        } catch (final Exception e) {
            return new ElasticClusterTestResponse(false, e.getMessage());
        }
    }
}
