package stroom.dashboard.impl.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.UriFactory;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.servicediscovery.api.ServiceDiscoverer;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import java.util.Optional;

@Singleton
@SuppressWarnings("unused")
public class DataSourceProviderRegistryImpl implements DataSourceProviderRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceProviderRegistryImpl.class);

    private final DataSourceProviderRegistry delegateDataSourceProviderRegistry;

    @SuppressWarnings("unused")
    @Inject
    DataSourceProviderRegistryImpl(final SecurityContext securityContext,
                                   final Provider<ServiceDiscoverer> serviceDiscovererProvider,
                                   final UriFactory uriFactory,
                                   final DataSourceUrlConfig dataSourceUrlConfig,
                                   final Provider<Client> clientProvider) {
        final ServiceDiscoverer serviceDiscoverer = serviceDiscovererProvider.get();
        if (serviceDiscoverer.isEnabled()) {
            LOGGER.debug("Using service discovery for service lookup");
            delegateDataSourceProviderRegistry = new ServiceDiscoveryDataSourceProviderRegistry(
                    securityContext,
                    serviceDiscoverer,
                    clientProvider);
        } else {
            LOGGER.debug("Using local services");
            delegateDataSourceProviderRegistry = new SimpleDataSourceProviderRegistry(
                    securityContext,
                    uriFactory,
                    dataSourceUrlConfig,
                    clientProvider);
        }
    }

    @Override
    public Optional<DataSourceProvider> getDataSourceProvider(final DocRef dataSourceRef) {
        return delegateDataSourceProviderRegistry.getDataSourceProvider(dataSourceRef);
    }
}
