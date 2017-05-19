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

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.query.api.DocRef;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Scope(StroomScope.PROTOTYPE)
public class DataSourceProviderRegistry {
    private final Map<String, DataSourceProvider> providers = new HashMap<>();

    public static final String TYPE_LUCENE = "lucene";
    public static final String TYPE_SQL_STATISTICS = "sqlstatistics";
    public static final String TYPE_STROOM_STATS = "stroom-stats";

    private final SecurityContext securityContext;

    @Inject
    public DataSourceProviderRegistry(final SecurityContext securityContext) {
        this.securityContext = securityContext;

        //TODO initialise the service discovery
        providers.put(TYPE_LUCENE, new RemoteDataSourceProvider(
                securityContext,
                TYPE_LUCENE,
                "http://127.0.0.1:8080/api/lucene"));

        providers.put(TYPE_SQL_STATISTICS, new RemoteDataSourceProvider(
                securityContext,
                TYPE_SQL_STATISTICS,
                "http://127.0.0.1:8080/api/sqlstatistics"));

        providers.put(TYPE_STROOM_STATS, new RemoteDataSourceProvider(
                securityContext,
                TYPE_STROOM_STATS,
                "http://127.0.0.1:8081/"));

    }

    public Optional<DataSourceProvider> getDataSourceProvider(final String type) {
        if (type != null) {
            return Optional.ofNullable(providers.get(type));
        } else {
            return Optional.empty();
        }

    }
    public Optional<DataSourceProvider> getDataSourceProvider(final DocRef dataSourceRef) {
        if (dataSourceRef != null) {
            return getDataSourceProvider(dataSourceRef.getType());
        } else {
            return Optional.empty();
        }
    }
}
