/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticClusterResource;
import stroom.search.elastic.shared.ElasticClusterTestResponse;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class ElasticClusterResourceImpl implements ElasticClusterResource, FetchWithUuid<ElasticClusterDoc> {

    private final Provider<ElasticClusterStore> elasticClusterStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<ElasticConfig> elasticConfigProvider;

    @Inject
    ElasticClusterResourceImpl(
            final Provider<ElasticClusterStore> elasticClusterStoreProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider,
            final Provider<ElasticConfig> elasticConfigProvider
    ) {
        this.elasticClusterStoreProvider = elasticClusterStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.elasticConfigProvider = elasticConfigProvider;
    }

    @Override
    public ElasticClusterDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(elasticClusterStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public ElasticClusterDoc update(final String uuid, final ElasticClusterDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(elasticClusterStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(ElasticClusterDoc.TYPE)
                .build();
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Testing Elasticsearch cluster")
    public ElasticClusterTestResponse testCluster(final ElasticClusterDoc cluster) {
        try {
            final ElasticConnectionConfig connectionConfig = cluster.getConnection();
            final ElasticClientConfig elasticClientConfig = elasticConfigProvider.get().getClientConfig();
            final ElasticsearchClient elasticClient = new ElasticClientFactory()
                    .create(connectionConfig, elasticClientConfig);

            final InfoResponse response = elasticClient.info();

            final StringBuilder sb = new StringBuilder()
                    .append("Cluster URLs: ")
                    .append(connectionConfig.getConnectionUrls())
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
