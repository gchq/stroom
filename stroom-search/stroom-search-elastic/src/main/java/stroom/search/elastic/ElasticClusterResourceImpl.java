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
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticClusterResource;
import stroom.search.elastic.shared.ElasticClusterTestResponse;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.PermissionException;
import stroom.util.shared.http.HttpClientConfig;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class ElasticClusterResourceImpl implements ElasticClusterResource, FetchWithUuid<ElasticClusterDoc> {

    private final Provider<ElasticClusterStore> elasticClusterStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<ElasticConfig> elasticConfigProvider;
    private final Provider<ElasticConnectionResolver> elasticConnectionResolverProvider;
    private final Provider<ElasticClientFactory> elasticClientFactoryProvider;
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    ElasticClusterResourceImpl(
            final Provider<ElasticClusterStore> elasticClusterStoreProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider,
            final Provider<ElasticConfig> elasticConfigProvider,
            final Provider<ElasticConnectionResolver> elasticConnectionResolverProvider,
            final Provider<ElasticClientFactory> elasticClientFactoryProvider,
            final Provider<SecurityContext> securityContextProvider
    ) {
        this.elasticClusterStoreProvider = elasticClusterStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.elasticConfigProvider = elasticConfigProvider;
        this.elasticConnectionResolverProvider = elasticConnectionResolverProvider;
        this.elasticClientFactoryProvider = elasticClientFactoryProvider;
        this.securityContextProvider = securityContextProvider;
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
        // The test uses the (possibly unsaved) URLs from the request together with the stored credentials
        // named in it. Only allow this for users who could save those URLs anyway, otherwise a user could
        // send a credential they cannot read to a server of their choosing.
        checkEditPermission(cluster);

        try {
            final ResolvedElasticConnection connection = elasticConnectionResolverProvider.get()
                    .resolve(cluster.getConnection());
            return testConnection(connection);
        } catch (final RuntimeException e) {
            return new ElasticClusterTestResponse(false, e.getMessage());
        }
    }

    private ElasticClusterTestResponse testConnection(final ResolvedElasticConnection connection) {
        return ElasticClusterConnectionTest.test(connection, elasticClientFactoryProvider.get(),
                elasticConfigProvider.get().getClientConfig());
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public HttpClientConfig getDefaultHttpClientConfig() {
        return elasticConnectionResolverProvider.get().getDefaultHttpClientConfig();
    }

    private void checkEditPermission(final ElasticClusterDoc cluster) {
        if (cluster == null || cluster.getUuid() == null) {
            throw new EntityServiceException("No Elasticsearch cluster supplied");
        }
        final SecurityContext securityContext = securityContextProvider.get();
        if (!securityContext.hasDocumentPermission(getDocRef(cluster.getUuid()), DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to test this Elasticsearch cluster");
        }
    }
}
