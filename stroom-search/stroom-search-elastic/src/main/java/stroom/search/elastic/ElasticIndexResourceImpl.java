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
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.search.elastic.shared.ElasticIndexTestResponse;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;

@AutoLogged
class ElasticIndexResourceImpl implements ElasticIndexResource, FetchWithUuid<ElasticIndexDoc> {

    private final Provider<ElasticClusterStore> elasticClusterStoreProvider;
    private final Provider<ElasticIndexStore> elasticIndexStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<ElasticConfig> elasticConfigProvider;

    @Inject
    ElasticIndexResourceImpl(
            final Provider<ElasticClusterStore> elasticClusterStoreProvider,
            final Provider<ElasticIndexStore> elasticIndexStoreProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider,
            final Provider<ElasticConfig> elasticConfigProvider
    ) {
        this.elasticClusterStoreProvider = elasticClusterStoreProvider;
        this.elasticIndexStoreProvider = elasticIndexStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.elasticConfigProvider = elasticConfigProvider;
    }

    @Override
    public ElasticIndexDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(elasticIndexStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public ElasticIndexDoc update(final String uuid, final ElasticIndexDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(elasticIndexStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(ElasticIndexDoc.TYPE)
                .build();
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Testing Elasticsearch index")
    public ElasticIndexTestResponse testIndex(final ElasticIndexDoc index) {
        try {
            if (index.getClusterRef() == null) {
                throw new IllegalArgumentException("Elasticsearch cluster configuration not specified");
            }

            final ElasticClusterDoc elasticCluster = documentResourceHelperProvider.get().read(
                    elasticClusterStoreProvider.get(), index.getClusterRef());

            final ElasticConnectionConfig connectionConfig = elasticCluster.getConnection();
            final ElasticClientConfig elasticClientConfig = elasticConfigProvider.get().getClientConfig();
            final ElasticsearchClient elasticClient = new ElasticClientFactory()
                    .create(connectionConfig, elasticClientConfig);

            // Check whether the specified index exists
            final String indexName = index.getIndexName();
            final GetIndexResponse getIndexResponse = elasticClient.indices()
                    .get(r -> r.index(indexName));

            if (getIndexResponse.indices().isEmpty()) {
                throw new NotFoundException("Index '" + indexName + "' was not found");
            }

            return new ElasticIndexTestResponse(true, "Elasticsearch index '" + index.getIndexName() + "' was found");

        } catch (final Exception e) {
            return new ElasticIndexTestResponse(false, e.getMessage());
        }
    }
}
