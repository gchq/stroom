/*
 * Copyright 2021 Crown Copyright
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
import stroom.search.elastic.search.ElasticFieldStatsService;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticFieldStats;
import stroom.search.elastic.shared.ElasticFieldStatsRequest;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.search.elastic.shared.ElasticIndexTestResponse;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.PermissionException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@AutoLogged
class ElasticIndexResourceImpl implements ElasticIndexResource, FetchWithUuid<ElasticIndexDoc> {

    private final Provider<ElasticClusterStore> elasticClusterStoreProvider;
    private final Provider<ElasticIndexStore> elasticIndexStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<ElasticConfig> elasticConfigProvider;
    private final Provider<ElasticConnectionResolver> elasticConnectionResolverProvider;
    private final Provider<ElasticClientFactory> elasticClientFactoryProvider;
    private final Provider<ElasticIndexService> elasticIndexServiceProvider;
    private final Provider<ElasticFieldStatsService> elasticFieldStatsServiceProvider;
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    ElasticIndexResourceImpl(
            final Provider<ElasticClusterStore> elasticClusterStoreProvider,
            final Provider<ElasticIndexStore> elasticIndexStoreProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider,
            final Provider<ElasticConfig> elasticConfigProvider,
            final Provider<ElasticConnectionResolver> elasticConnectionResolverProvider,
            final Provider<ElasticClientFactory> elasticClientFactoryProvider,
            final Provider<ElasticIndexService> elasticIndexServiceProvider,
            final Provider<ElasticFieldStatsService> elasticFieldStatsServiceProvider,
            final Provider<SecurityContext> securityContextProvider
    ) {
        this.elasticClusterStoreProvider = elasticClusterStoreProvider;
        this.elasticIndexStoreProvider = elasticIndexStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.elasticConfigProvider = elasticConfigProvider;
        this.elasticConnectionResolverProvider = elasticConnectionResolverProvider;
        this.elasticClientFactoryProvider = elasticClientFactoryProvider;
        this.elasticIndexServiceProvider = elasticIndexServiceProvider;
        this.elasticFieldStatsServiceProvider = elasticFieldStatsServiceProvider;
        this.securityContextProvider = securityContextProvider;
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

            final ResolvedElasticConnection connection = elasticConnectionResolverProvider.get()
                    .resolve(elasticCluster.getConnection());
            final ElasticClientConfig elasticClientConfig = elasticConfigProvider.get().getClientConfig();
            try (final ElasticsearchClient elasticClient = elasticClientFactoryProvider.get()
                    .create(connection, elasticClientConfig)) {

                // Check whether the specified index exists
                final String indexName = index.getIndexName();
                final GetIndexResponse getIndexResponse = elasticClient.indices()
                        .get(r -> r.index(indexName));

                if (getIndexResponse.indices().isEmpty()) {
                    throw new NotFoundException("Index '" + indexName + "' was not found");
                }
            }

            return new ElasticIndexTestResponse(true, "Elasticsearch index '" + index.getIndexName() + "' was found");

        } catch (final Exception e) {
            return new ElasticIndexTestResponse(false, e.getMessage());
        }
    }

    /// The fields are read using the cluster and index name as given, which may not be saved, so only users who
    /// may edit the doc can read them.
    @Override
    @AutoLogged(OperationType.VIEW)
    public List<ElasticIndexField> fetchFields(final ElasticIndexDoc elasticIndexDoc) {
        final DocRef docRef = elasticIndexDoc.asDocRef();
        final SecurityContext securityContext = securityContextProvider.get();
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to edit {}", docRef));
        }
        return elasticIndexServiceProvider.get().getFields(elasticIndexDoc);
    }

    @Override
    @AutoLogged(value = OperationType.VIEW, verb = "Fetching Elasticsearch index field statistics")
    public ElasticFieldStats fetchFieldStats(final ElasticFieldStatsRequest request) {
        return elasticFieldStatsServiceProvider.get().getFieldStats(request);
    }
}
