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

package stroom.datasource;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.security.SecurityContext;
import stroom.servlet.HttpServletRequestHolder;
import stroom.servlet.HttpSessionUtil;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class RemoteDataSourceProvider implements DataSourceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDataSourceProvider.class);

    private static final String DATA_SOURCE_ENDPOINT = "/dataSource";
    private static final String SEARCH_ENDPOINT = "/search";
    private static final String DESTROY_ENDPOINT = "/destroy";

    private final SecurityContext securityContext;
    private final String url;
    private HttpServletRequestHolder httpServletRequestHolder;

    RemoteDataSourceProvider(final SecurityContext securityContext,
                             final String url,
                             final HttpServletRequestHolder httpServletRequestHolder) {
        this.securityContext = securityContext;
        this.url = url;
        this.httpServletRequestHolder = httpServletRequestHolder;
        LOGGER.trace("Creating RemoteDataSourceProvider for url {}", url);
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        LOGGER.trace("getDataSource() called for docRef {} on url {}", docRef, url);
        //TODO this needs to be backed by a short life cache to avoid repeated trips over the net
        return post(docRef, DATA_SOURCE_ENDPOINT, DataSource.class);
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        LOGGER.trace("search() called for request {} on url {}", request, url);
        return post(request, SEARCH_ENDPOINT, SearchResponse.class);
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        LOGGER.trace("destroy() called for queryKey {} on url {}", queryKey, url);
        return post(queryKey, DESTROY_ENDPOINT, Boolean.class);
    }

    private <T> T post(final Object request, String path, final Class<T> responseClass) {
        try {
            LOGGER.trace("Sending request {} to {}", request, path);
            Client client = ClientBuilder.newClient(new ClientConfig().register(LoggingFeature.class));
            WebTarget webTarget = client.target(url).path(path);

            Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);

            // We'll look for the user's API key in the session, but if they're not logged in we'll try and get
            // one from the security context.
            String usersApiKey = HttpSessionUtil.getUserApiKey(httpServletRequestHolder.get().getSession(true));
            if (usersApiKey == null || usersApiKey.isEmpty()) {
                usersApiKey = securityContext.getApiToken();
            }

            invocationBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + usersApiKey);
            Response response = invocationBuilder.post(Entity.entity(request, MediaType.APPLICATION_JSON));

            if (HttpServletResponse.SC_OK == response.getStatus()) {
                return response.readEntity(responseClass);
            } else if (HttpServletResponse.SC_UNAUTHORIZED == response.getStatus()) {
                throw new RuntimeException("The user is not authorized to make this request! The user was " +
                        securityContext.getUserId());
            } else {
                throw new RuntimeException(String.format("Error %s sending request %s to %s: %s",
                        response.getStatus(), request, webTarget.getUri(), response.getStatusInfo().getReasonPhrase()));
            }

        } catch (final RuntimeException e) {
            throw new RuntimeException(String.format("Error sending request %s to %s", request, path), e);
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
