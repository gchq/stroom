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

package stroom.search.elastic.template;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.ResponseException;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/// Reads an Elastic Index doc's templates from its cluster, with the doc's cluster connection as a search uses
/// it, so the caller needs READ on the index doc but nothing on the cluster doc. It is what both the REST
/// endpoint ([ElasticIndexTemplatesResourceImpl]) and Stroom AI ([AiElasticIndexTemplates]) read the templates
/// with; each turns its failures into its own form.
public class ElasticIndexTemplateReader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexTemplateReader.class);

    private final Provider<ElasticIndexStore> elasticIndexStoreProvider;
    private final Provider<ElasticClusterStore> elasticClusterStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<ElasticClientCache> elasticClientCacheProvider;
    private final Provider<ElasticIndexTemplateService> elasticIndexTemplateServiceProvider;

    /// @param elasticIndexStoreProvider           Reads the Elastic Index doc.
    /// @param elasticClusterStoreProvider         Reads its Elastic Cluster doc.
    /// @param documentResourceHelperProvider      Reads the index doc with the caller's READ checked.
    /// @param securityContextProvider             Reads the cluster doc as the processing user.
    /// @param elasticClientCacheProvider          Gives a client for the cluster's connection.
    /// @param elasticIndexTemplateServiceProvider Finds the templates with that client.
    @Inject
    public ElasticIndexTemplateReader(
            final Provider<ElasticIndexStore> elasticIndexStoreProvider,
            final Provider<ElasticClusterStore> elasticClusterStoreProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider,
            final Provider<SecurityContext> securityContextProvider,
            final Provider<ElasticClientCache> elasticClientCacheProvider,
            final Provider<ElasticIndexTemplateService> elasticIndexTemplateServiceProvider) {
        this.elasticIndexStoreProvider = elasticIndexStoreProvider;
        this.elasticClusterStoreProvider = elasticClusterStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.securityContextProvider = securityContextProvider;
        this.elasticClientCacheProvider = elasticClientCacheProvider;
        this.elasticIndexTemplateServiceProvider = elasticIndexTemplateServiceProvider;
    }

    /// Reads the templates that apply to an Elastic Index doc's index, as the caller.
    ///
    /// @param docRef The Elastic Index doc, of type [ElasticIndexDoc#TYPE].
    /// @return The templates, each part with its own outcome.
    /// @throws stroom.util.shared.PermissionException if the caller may not read the index doc.
    /// @throws ReadFailure                            if nothing could be read, with why.
    public ElasticIndexTemplatesResponse read(final DocRef docRef) {
        final ElasticIndexDoc index = readIndexDoc(docRef);

        final String indexName = index.getIndexName();
        if (NullSafe.isBlankString(indexName)) {
            throw new ReadFailure(Failure.NO_INDEX_NAME, "Elasticsearch index name not specified");
        }
        final ElasticClusterDoc cluster = readClusterDoc(index);

        try {
            return elasticClientCacheProvider.get().contextResult(cluster.getConnection(), client -> {
                final Rest5Client restClient = getRestClient(client);
                try {
                    return elasticIndexTemplateServiceProvider.get()
                            .read(indexName, endpoint -> get(restClient, endpoint));
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final UncheckedIOException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to reach Elasticsearch cluster '{}': {}",
                    cluster.getName(), e.getCause().getMessage()), e);
            throw new ReadFailure(Failure.CLUSTER_UNREACHABLE, e.getCause().getMessage());
        }
    }

    // Checks the caller has READ on the doc, throwing a PermissionException if not
    private ElasticIndexDoc readIndexDoc(final DocRef docRef) {
        final ElasticIndexDoc index;
        try {
            index = documentResourceHelperProvider.get().read(elasticIndexStoreProvider.get(), docRef);
        } catch (final DocumentNotFoundException e) {
            throw new ReadFailure(Failure.INDEX_NOT_FOUND, e.getMessage());
        }
        if (index == null) {
            throw new ReadFailure(Failure.INDEX_NOT_FOUND,
                    LogUtil.message("Elastic Index {} not found", docRef.getUuid()));
        }
        return index;
    }

    // Read as the processing user, as a search reads it, since READ on the index doc is what grants access
    private ElasticClusterDoc readClusterDoc(final ElasticIndexDoc index) {
        final DocRef clusterRef = index.getClusterRef();
        if (clusterRef == null) {
            throw new ReadFailure(Failure.NO_CLUSTER, "Elasticsearch cluster configuration not specified");
        }
        final ElasticClusterDoc cluster;
        try {
            cluster = securityContextProvider.get().asProcessingUserResult(() ->
                    elasticClusterStoreProvider.get().readDocument(clusterRef));
        } catch (final DocumentNotFoundException e) {
            throw new ReadFailure(Failure.CLUSTER_NOT_FOUND, e.getMessage());
        }
        if (cluster == null || cluster.getConnection() == null) {
            throw new ReadFailure(Failure.CLUSTER_NOT_FOUND, LogUtil.message(
                    "Elasticsearch cluster {} not found", clusterRef.getUuid()));
        }
        return cluster;
    }

    private static Rest5Client getRestClient(final ElasticsearchClient client) {
        if (client._transport() instanceof final Rest5ClientTransport transport) {
            return transport.restClient();
        }
        throw new IllegalStateException(LogUtil.message("Unexpected Elasticsearch transport {}",
                client._transport().getClass().getName()));
    }

    // Any HTTP status comes back as a response; only a cluster that cannot be reached throws
    private static ElasticGetResponse get(final Rest5Client restClient, final String endpoint) throws IOException {
        try {
            return toElasticGetResponse(restClient.performRequest(new Request("GET", endpoint)));
        } catch (final ResponseException e) {
            return toElasticGetResponse(e.getResponse());
        }
    }

    private static ElasticGetResponse toElasticGetResponse(final Response response) throws IOException {
        final HttpEntity entity = response.getEntity();
        try {
            final String body = entity != null
                    ? EntityUtils.toString(entity, StandardCharsets.UTF_8)
                    : "";
            return new ElasticGetResponse(response.getStatusCode(), body);
        } catch (final ParseException e) {
            throw new IOException("Unable to read Elasticsearch response: " + e.getMessage(), e);
        }
    }


    // --------------------------------------------------------------------------------


    /// Why nothing could be read.
    public enum Failure {
        /// The index doc doesn't exist.
        INDEX_NOT_FOUND,
        /// The index doc has no index name.
        NO_INDEX_NAME,
        /// The index doc has no cluster.
        NO_CLUSTER,
        /// The index doc's cluster doc doesn't exist, or has no connection.
        CLUSTER_NOT_FOUND,
        /// The cluster can't be reached.
        CLUSTER_UNREACHABLE
    }


    // --------------------------------------------------------------------------------


    /// Thrown when nothing could be read, with why.
    public static class ReadFailure extends RuntimeException {

        private final Failure failure;

        /// @param failure Why nothing could be read.
        /// @param message What went wrong, which may be null.
        public ReadFailure(final Failure failure, final String message) {
            super(message);
            this.failure = Objects.requireNonNull(failure, "failure");
        }

        /// @return Why nothing could be read.
        public Failure getFailure() {
            return failure;
        }
    }
}
