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

import stroom.ai.api.ElasticClusterTests;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticClusterTestResponse;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

/// Stroom AI's way to test an Elastic Cluster document's connection ([ElasticClusterTests]), with the same test as
/// the REST endpoint's ([ElasticClusterConnectionTest]).
public class AiElasticClusterTests implements ElasticClusterTests {

    private final Provider<ElasticConfig> elasticConfigProvider;
    private final Provider<ElasticConnectionResolver> connectionResolverProvider;
    private final Provider<ElasticClientFactory> clientFactoryProvider;

    /// @param elasticConfigProvider      How Stroom's Elasticsearch clients are set up.
    /// @param connectionResolverProvider Resolves the credentials a cluster document names, as the user.
    /// @param clientFactoryProvider      Makes the client.
    @Inject
    public AiElasticClusterTests(final Provider<ElasticConfig> elasticConfigProvider,
                                 final Provider<ElasticConnectionResolver> connectionResolverProvider,
                                 final Provider<ElasticClientFactory> clientFactoryProvider) {
        this.elasticConfigProvider = elasticConfigProvider;
        this.connectionResolverProvider = connectionResolverProvider;
        this.clientFactoryProvider = clientFactoryProvider;
    }

    @Override
    public TestResult test(final ElasticClusterDoc cluster) {
        Objects.requireNonNull(cluster, "cluster");
        // The saved document's URLs, with its credentials resolved as the user (who may not view them); the REST
        // endpoint's Edit check guards a test of unsaved URLs, which this never is.
        final ResolvedElasticConnection connection;
        try {
            connection = connectionResolverProvider.get().resolve(cluster.getConnection());
        } catch (final RuntimeException e) {
            return new TestResult(false, e.getMessage());
        }
        final ElasticClusterTestResponse response = ElasticClusterConnectionTest.test(connection,
                clientFactoryProvider.get(), elasticConfigProvider.get().getClientConfig());
        return new TestResult(response.isOk(), response.getMessage());
    }
}
