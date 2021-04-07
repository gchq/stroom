/*
 * Copyright 2017 Crown Copyright
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

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class ElasticIndexResourceImpl implements ElasticIndexResource, FetchWithUuid<ElasticIndexDoc> {

    private final Provider<ElasticClusterStore> elasticClusterStoreProvider;
    private final Provider<ElasticIndexStore> elasticIndexStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    ElasticIndexResourceImpl(
            final Provider<ElasticClusterStore> elasticClusterStoreProvider,
            final Provider<ElasticIndexStore> elasticIndexStoreProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider
    ) {
        this.elasticClusterStoreProvider = elasticClusterStoreProvider;
        this.elasticIndexStoreProvider = elasticIndexStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
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
                .type(ElasticIndexDoc.DOCUMENT_TYPE)
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
            final RestHighLevelClient elasticClient = new ElasticClientFactory().create(connectionConfig);

            // Check whether the specified index exists
            final String indexName = index.getIndexName();
            final GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
            final GetIndexResponse getIndexResponse = elasticClient.indices()
                    .get(getIndexRequest, RequestOptions.DEFAULT);

            if (getIndexResponse.getIndices().length < 1) {
                throw new ResourceNotFoundException("Index '" + indexName + "' was not found");
            }

            return new ElasticIndexTestResponse(true, "Elasticsearch index '" + index.getIndexName() + "' was found");

        } catch (final Exception e) {
            return new ElasticIndexTestResponse(false, e.getMessage());
        }
    }
}
