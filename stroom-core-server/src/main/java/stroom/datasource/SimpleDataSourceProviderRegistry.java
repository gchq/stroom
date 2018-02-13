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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.servlet.HttpServletRequestHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SimpleDataSourceProviderRegistry implements DataSourceProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleDataSourceProviderRegistry.class);

    private static final String PROP_KEY_BASE_PATH = "stroom.serviceDiscovery.simpleLookup.basePath";
    private static final String PROP_KEY_ANNOTATIONS_PATH = "stroom.url.annotations-query";
    private static final String PROP_KEY_ELASTIC_PATH = "stroom.url.elastic-query";

    private final Map<String, String> urlMap;

    private final SecurityContext securityContext;
    private HttpServletRequestHolder httpServletRequestHolder;

    SimpleDataSourceProviderRegistry(final SecurityContext securityContext,
                                     final StroomPropertyService stroomPropertyService,
                                     final HttpServletRequestHolder httpServletRequestHolder) {
        this.securityContext = securityContext;
        this.httpServletRequestHolder = httpServletRequestHolder;

        final String basePath = stroomPropertyService.getProperty(PROP_KEY_BASE_PATH);
        final String annotationsPath = stroomPropertyService.getProperty(PROP_KEY_ANNOTATIONS_PATH);
        final String elasticPath = stroomPropertyService.getProperty(PROP_KEY_ELASTIC_PATH);

        if (!Strings.isNullOrEmpty(basePath)) {
            //TODO the path strings are defined in ResourcePaths but this is not accessible from here
            //if this code is kept long term then ResourcePaths needs to be mode so that is accessible to all
            urlMap = new HashMap<>();
            urlMap.put("Index", basePath + "/api/stroom-index/v2");
            urlMap.put("StatisticStore", basePath + "/api/sqlstatistics/v2");
            urlMap.put("AnnotationsIndex", annotationsPath);
            urlMap.put("ElasticIndex", elasticPath);
            //strooom-stats is not available as a local service as if you have stroom-stats you have zookeeper so
            //you can run service discovery

            //No idea why these two are here, neither are data source providers
//            urlMap.put("authentication", basePath + "/api/authentication/v1");
//            urlMap.put("authorisation", basePath + "/api/authorisation/v1");

            LOGGER.info("Using the following local URLs for services:\n" +
                    urlMap.entrySet().stream()
                            .map(entry -> "    " + entry.getKey() + " - " + entry.getValue())
                            .sorted()
                            .collect(Collectors.joining("\n"))
            );
            LOGGER.info("Stroom-stats is not available when servcie discovery is disabled");
        } else {
            LOGGER.error("Property value for {} is null or empty, local service lookup will not function",
                    PROP_KEY_BASE_PATH);
            urlMap = ImmutableMap.of();
        }
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
                .map(url -> new RemoteDataSourceProvider(securityContext, url, httpServletRequestHolder));
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