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

package stroom.dashboard.impl.datasource;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.security.api.ClientSecurityUtil;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;

import io.dropwizard.jersey.errors.ErrorMessage;

import javax.inject.Provider;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class RemoteDataSourceProvider implements DataSourceProvider {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteDataSourceProvider.class);

    private static final String DATA_SOURCE_ENDPOINT = "/dataSource";
    private static final String SEARCH_ENDPOINT = "/search";
    private static final String DESTROY_ENDPOINT = "/destroy";

    private final SecurityContext securityContext;
    private final String url;
    private final Provider<Client> clientProvider;

    RemoteDataSourceProvider(final SecurityContext securityContext,
                             final String url,
                             final Provider<Client> clientProvider) {
        this.securityContext = securityContext;
        this.url = url;
        this.clientProvider = clientProvider;
        LOGGER.trace("Creating RemoteDataSourceProvider for url {}", url);
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        LOGGER.trace("getDataSource() called for docRef {} on url {}", docRef, url);
        //TODO this needs to be backed by a short life cache to avoid repeated trips over the net
        return post(docRef, docRef, DATA_SOURCE_ENDPOINT, DataSource.class);
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        LOGGER.trace("search() called for request {} on url {}", request, url);
        return post(request.getQuery().getDataSource(), request, SEARCH_ENDPOINT, SearchResponse.class);
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        try {
            LOGGER.trace("destroy() called for queryKey {} on url {}", queryKey, url);
            return post(null, queryKey, DESTROY_ENDPOINT, Boolean.class);
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to destroy active query for queryKey {} on url {}", queryKey, url, e);
        }
        return Boolean.FALSE;
    }

    private <T> T post(final DocRef docRef,
                       final Object request,
                       String path,
                       final Class<T> responseClass) {
        try {
            LOGGER.trace("Sending request {} to {}/{}", request, url, path);
            Client client = clientProvider.get();
            WebTarget webTarget = client.target(url).path(path);

            final Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
            ClientSecurityUtil.addAuthorisationHeader(invocationBuilder, securityContext);
            final Response response = invocationBuilder.post(Entity.entity(request, MediaType.APPLICATION_JSON));

            switch (response.getStatus()) {
                case (HttpServletResponse.SC_OK):
                    return response.readEntity(responseClass);
                case HttpServletResponse.SC_UNAUTHORIZED:
                case HttpServletResponse.SC_FORBIDDEN:
                    final StringBuilder msg = new StringBuilder("The user is not authorized to make this request! ");
                    msg.append(path);
                    if (docRef != null) {
                        msg.append(", ");
                        msg.append(docRef);
                    }
                    throw new PermissionException(securityContext.getUserId(), msg.toString());
                default:
                    ErrorMessage errorMsg;
                    try {
                        errorMsg = response.readEntity(ErrorMessage.class);
                    } catch (Exception e) {
                        errorMsg = null;
                        LOGGER.debug("Error reading entity", e);
                    }
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Error {} sending request {} to {}: {}, {}",
                                response.getStatus(),
                                request,
                                webTarget.getUri(),
                                response.getStatusInfo().getReasonPhrase(),
                                errorMsg);
                    }

                    if (errorMsg != null) {
                        throw new WebApplicationException(errorMsg.getMessage(), errorMsg.getCode());
                    } else {
                        throw new RuntimeException(LogUtil.message("Error {} sending request {} to {}: {}",
                                response.getStatus(),
                                request,
                                webTarget.getUri(),
                                response.getStatusInfo().getReasonPhrase()));
                    }
            }

        } catch (final RuntimeException e) {
            LOGGER.debug("Error sending request {} to {}{}", request, url, path, e);
            throw e;
        }
    }

    @Override
    public String getType() {
        return "remote";
    }

    @Override
    public String toString() {
        return "RemoteDataSourceProvider{" +
                "url='" + url + '\'' +
                '}';
    }

}
