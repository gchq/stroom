/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import stroom.datasource.api.v1.DataSource;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.QueryKey;
import stroom.query.api.v1.SearchRequest;
import stroom.query.api.v1.SearchResponse;
import stroom.security.SecurityContext;

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
    private final SecurityContext securityContext;
    private final String url;

    public RemoteDataSourceProvider(final SecurityContext securityContext,
                                    final String url) {
        this.securityContext = securityContext;
        this.url = url;
        LOGGER.trace("Creating RemoteDataSourceProvider for url {}", url);
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        LOGGER.trace("getDataSource() called for docRef {} on url {}", docRef, url);
        //TODO this needs to be backed by a short life cache to avoid repeated trips over the net
        return post(docRef, "dataSource", DataSource.class);
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        LOGGER.trace("search() called for request {} on url {}", request, url);
        return post(request, "search", SearchResponse.class);
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        LOGGER.trace("destroy() called for queryKey {} on url {}", queryKey, url);
        return post(queryKey, "destroy", Boolean.class);
    }

    private <T> T post(final Object request, String path, final Class<T> responseClass) {
        Client client = ClientBuilder.newClient(new ClientConfig().register(LoggingFeature.class));
        WebTarget webTarget = client.target(url).path(path);

        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        invocationBuilder.header(HttpHeaders.AUTHORIZATION, securityContext.getToken());
        Response response = invocationBuilder.post(Entity.entity(request, MediaType.APPLICATION_JSON));

        if (HttpServletResponse.SC_OK == response.getStatus()) {
            return response.readEntity(responseClass);
        }

        throw new RuntimeException("Error " + response.getStatus() + ": " + response.getStatusInfo().getReasonPhrase());
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
