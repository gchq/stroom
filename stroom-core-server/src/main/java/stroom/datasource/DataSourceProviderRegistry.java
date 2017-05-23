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
import stroom.ExternalService;
import stroom.ServiceDiscoverer;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Scope(StroomScope.PROTOTYPE)
public class DataSourceProviderRegistry {
    private final Map<ExternalService, DataSourceProvider> providers = new HashMap<>();
    private final SecurityContext securityContext;
    private final ServiceDiscoverer serviceDiscoverer;

    @Inject
    public DataSourceProviderRegistry(final SecurityContext securityContext,
                                      final ServiceDiscoverer serviceDiscoverer) {
        this.securityContext = securityContext;
        this.serviceDiscoverer = serviceDiscoverer;

        //TODO this means we register services explicitly on boot so if the service happens
        //to be down at that point then we will never have access to it.
        //Think whenever anybody needs a data source provider we use the service discoverer to
        //find the required datasourceprovider.  Not sure if curator caches the KVs locally or whether
        //it means a round trip to ZK.  If not we may need to hold a short term cache.
        //I think we can do away with the providers map above, instead treating ServiceDiscoverer as the map

        register(ExternalService.INDEX);
        register(ExternalService.STROOM_STATS);
        register(ExternalService.SQL_STATISTICS);
    }

    public Optional<DataSourceProvider> getDataSourceProvider(final String docRefType) {

            ExternalService dataSourceService = ExternalService.docRefTypeToServiceMap.get(docRefType);
            return Optional.ofNullable(providers.get(dataSourceService));
    }

    public Optional<DataSourceProvider> getDataSourceProvider(final DocRef dataSourceRef) {

        return Optional.ofNullable(dataSourceRef)
                .map(DocRef::getType)
                .flatMap(this::getDataSourceProvider);
    }

    private void register(ExternalService externalService) {
        Optional<String> indexAddress = serviceDiscoverer.getAddress(externalService);

        indexAddress.ifPresent(url ->
                providers.put( externalService, new RemoteDataSourceProvider( securityContext, url)
        ));
    }
}
