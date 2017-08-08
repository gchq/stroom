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

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Scope(StroomScope.PROTOTYPE)
public class SimpleDataSourceProviderRegistry implements DataSourceProviderRegistry {
    private final Map<String, String> urlMap = new HashMap<>();

    private final SecurityContext securityContext;

    @Inject
    public SimpleDataSourceProviderRegistry(final SecurityContext securityContext) {
        this.securityContext = securityContext;

        final String basePath = "http://127.0.0.1:8080";

        urlMap.put("Index", basePath + "/api/stroom-index/v1");
        urlMap.put("StatisticStore", basePath + "/api/sqlstatistics/v1");
        urlMap.put("StroomStatsStore", basePath + "/api/stroom-stats/v1");
        urlMap.put("authentication", basePath + "/api/authentication/v1");
        urlMap.put("authorisation", basePath + "/api/authorisation/v1");
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
        final String url = urlMap.get(docRefType);
        if (url == null) {
            return Optional.empty();
        }

        return Optional.of(new RemoteDataSourceProvider(securityContext, url));
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