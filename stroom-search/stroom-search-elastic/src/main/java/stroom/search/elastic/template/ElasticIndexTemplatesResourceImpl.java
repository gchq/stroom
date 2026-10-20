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
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
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
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/// Reads an Elastic Index doc's templates from its cluster, with the doc's cluster connection as a search
/// uses it, so the caller needs READ on the index doc but nothing on the cluster doc.
@AutoLogged
public class ElasticIndexTemplatesResourceImpl implements ElasticIndexTemplatesResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            ElasticIndexTemplatesResourceImpl.class);

    private final Provider<ElasticIndexStore> elasticIndexStoreProvider;
    private final Provider<ElasticClusterStore> elasticClusterStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<ElasticClientCache> elasticClientCacheProvider;
    private final Provider<ElasticIndexTemplateService> elasticIndexTemplateServiceProvider;

    @Inject
    public ElasticIndexTemplatesResourceImpl(
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

    @Override
    @AutoLogged(value = OperationType.VIEW, verb = "Reading Elasticsearch index templates")
    public ElasticIndexTemplatesResponse fetchTemplates(final ElasticIndexTemplatesRequest request) {
        final ElasticIndexDoc index = readIndexDoc(getIndexDocRef(request));

        final String indexName = index.getIndexName();
        if (NullSafe.isBlankString(indexName)) {
            throw error(Status.BAD_REQUEST, "Elasticsearch index name not specified");
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
            throw error(Status.SERVICE_UNAVAILABLE, e.getCause().getMessage());
        }
    }

    private DocRef getIndexDocRef(final ElasticIndexTemplatesRequest request) {
        // A DocRef always has a type and UUID, else it would not have been deserialised
        final DocRef docRef = NullSafe.get(request, ElasticIndexTemplatesRequest::indexDocRef);
        if (docRef == null) {
            throw error(Status.BAD_REQUEST, "indexDocRef is required");
        }
        if (!ElasticIndexDoc.TYPE.equals(docRef.getType())) {
            throw error(Status.BAD_REQUEST, LogUtil.message(
                    "indexDocRef must be of type {}, not {}", ElasticIndexDoc.TYPE, docRef.getType()));
        }
        return docRef;
    }

    // Checks the caller has READ on the doc, throwing a PermissionException (403) if not
    private ElasticIndexDoc readIndexDoc(final DocRef docRef) {
        final ElasticIndexDoc index;
        try {
            index = documentResourceHelperProvider.get().read(elasticIndexStoreProvider.get(), docRef);
        } catch (final DocumentNotFoundException e) {
            throw error(Status.NOT_FOUND, e.getMessage());
        }
        if (index == null) {
            throw error(Status.NOT_FOUND, LogUtil.message("Elastic Index {} not found", docRef.getUuid()));
        }
        return index;
    }

    // Read as the processing user, as a search reads it, since READ on the index doc is what grants access
    private ElasticClusterDoc readClusterDoc(final ElasticIndexDoc index) {
        final DocRef clusterRef = index.getClusterRef();
        if (clusterRef == null) {
            throw error(Status.BAD_REQUEST, "Elasticsearch cluster configuration not specified");
        }
        final ElasticClusterDoc cluster;
        try {
            cluster = securityContextProvider.get().asProcessingUserResult(() ->
                    elasticClusterStoreProvider.get().readDocument(clusterRef));
        } catch (final DocumentNotFoundException e) {
            throw error(Status.SERVICE_UNAVAILABLE, e.getMessage());
        }
        if (cluster == null || cluster.getConnection() == null) {
            throw error(Status.SERVICE_UNAVAILABLE, LogUtil.message(
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

    // A response with a body like the one the exception mappers give, so the caller sees the reason
    private static WebApplicationException error(final Status status, final String message) {
        return new WebApplicationException(message, jakarta.ws.rs.core.Response
                .status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of(
                        "code", status.getStatusCode(),
                        "message", Objects.requireNonNullElse(message, status.getReasonPhrase())))
                .build());
    }
}
