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

import org.apache.curator.x.discovery.ServiceInstance;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.ExternalService;
import stroom.ServiceDiscoverer;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Optional;

@Component
@Scope(StroomScope.PROTOTYPE)
public class DataSourceProviderRegistry {

    private final SecurityContext securityContext;
    private final ServiceDiscoverer serviceDiscoverer;

    @Inject
    public DataSourceProviderRegistry(final SecurityContext securityContext,
                                      final ServiceDiscoverer serviceDiscoverer) {
        this.securityContext = securityContext;
        this.serviceDiscoverer = serviceDiscoverer;
    }

    /**
     * Gets a valid instance of a {@link DataSourceProvider} by querying service discovery
     * @param docRefType The docRef type to get a data source provider for
     * @return A remote data source provider that can handle docRefs of the passed type. Will return
     * an empty optional for two reasons:
     * There may be no services that can handle the passed docRefType.
     * The service has no instances that are up and enabled.
     *
     * The returned {@link DataSourceProvider} should be used and then thrown away, not cached or held.
     */
    public Optional<DataSourceProvider> getDataSourceProvider(final String docRefType) {

        return ExternalService.getExternalService(docRefType)
                .flatMap(serviceDiscoverer::getServiceInstance)
                .filter(ServiceInstance::isEnabled)
                .flatMap(serviceInstance -> {
                    String address = serviceInstance.buildUriSpec();
                    return Optional.of(new RemoteDataSourceProvider(securityContext, address));
                });
    }

    /**
     * Gets a valid instance of a {@link RemoteDataSourceProvider} by querying service discovery
     * @param dataSourceRef The docRef to get a data source provider for
     * @return A remote data source provider that can handle the passed docRef. Will return
     * an empty optional for two reasons:
     * There may be no services that can handle the passed docRefType.
     * The service has no instances that are up and enabled.
     */
    public Optional<DataSourceProvider> getDataSourceProvider(final DocRef dataSourceRef) {
        return Optional.ofNullable(dataSourceRef)
                .map(DocRef::getType)
                .flatMap(this::getDataSourceProvider);
    }
}
