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

import stroom.config.common.UriFactory;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.ws.rs.client.Client;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class SimpleDataSourceProviderRegistry implements DataSourceProviderRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleDataSourceProviderRegistry.class);

    private final Map<String, Supplier<String>> urlMap;

    private final SecurityContext securityContext;
    private final Provider<Client> clientProvider;
    private final UriFactory uriFactory;

    SimpleDataSourceProviderRegistry(final SecurityContext securityContext,
                                     final UriFactory uriFactory,
                                     final DataSourceUrlConfig dataSourceUrlConfig,
                                     final Provider<Client> clientProvider) {
        this.securityContext = securityContext;
        this.clientProvider = clientProvider;
        this.uriFactory = uriFactory;

        urlMap = new HashMap<>();
        urlMap.put("Index", getUriSupplier(dataSourceUrlConfig::getIndex));
        urlMap.put("Searchable", getUriSupplier(dataSourceUrlConfig::getSearchable));
        urlMap.put("SolrIndex", getUriSupplier(dataSourceUrlConfig::getSolrIndex));
        urlMap.put("StatisticStore", getUriSupplier(dataSourceUrlConfig::getStatisticStore));

        //strooom-stats is not available as a local service as if you have stroom-stats you have zookeeper so
        //you can run service discovery

        LOGGER.info("Using the following local URLs for services:\n" +
                urlMap.entrySet().stream()
                        .map(entry -> "    " + entry.getKey() + " - " + entry.getValue().get())
                        .sorted()
                        .collect(Collectors.joining("\n"))
        );
        LOGGER.info("Stroom-stats is not available when service discovery is disabled");
    }

    /**
     * Convert the supplier of something like /api/stroom-index/v2
     * into a supplier of something like http://localhost:8080/api/stroom-index/v2
     */
    private Supplier<String> getUriSupplier(final Supplier<String> pathSupplier) {
        // We need to go via the local URI as dropwiz has no client ssl certs to
        // be able to go via nginx. A node calling itself is fine as we know the
        // node is available.
        return () ->
                uriFactory.nodeUri(pathSupplier.get())
                        .toString();
    }

    /**
     * Gets a valid instance of a {@link DataSourceProvider} by querying service discovery
     *
     * @param docRefType The docRef type to get a data source provider for
     * @return A remote data source provider that can handle docRefs of the passed type. Will return
     * an empty optional for two reasons:
     * There may be no services that can handle the passed docRefType.
     * The service has no instances that are up and enabled.
     * <p>
     * The returned {@link DataSourceProvider} should be used and then thrown away, not cached or held.
     */
    private Optional<DataSourceProvider> getDataSourceProvider(final String docRefType) {
        return Optional.ofNullable(urlMap.get(docRefType))
                .map(urlProvider ->
                        new RemoteDataSourceProvider(securityContext, urlProvider.get(), clientProvider));
    }

    /**
     * Gets a valid instance of a {@link RemoteDataSourceProvider} by querying service discovery
     *
     * @param dataSourceRef The docRef to get a data source provider for
     * @return A remote data source provider that can handle the passed docRef. Will return
     * an empty optional for two reasons:
     * There may be no services that can handle the passed docRefType.
     * The service has no instances that are up and enabled.
     */
    @Override
    public Optional<DataSourceProvider> getDataSourceProvider(final DocRef dataSourceRef) {
        return Optional.ofNullable(dataSourceRef)
                .map(DocRef::getType)
                .flatMap(this::getDataSourceProvider);
    }
}