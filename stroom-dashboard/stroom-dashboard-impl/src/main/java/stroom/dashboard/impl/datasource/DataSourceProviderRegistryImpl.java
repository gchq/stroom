package stroom.dashboard.impl.datasource;

import stroom.config.common.UriFactory;
import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.servicediscovery.api.ServiceDiscoverer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;

@Singleton
@SuppressWarnings("unused")
public class DataSourceProviderRegistryImpl implements DataSourceProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceProviderRegistryImpl.class);

    private final DataSourceProviderRegistry delegateDataSourceProviderRegistry;
    private final SecurityContext securityContext;

    @SuppressWarnings("unused")
    @Inject
    DataSourceProviderRegistryImpl(final SecurityContext securityContext,
                                   final Provider<ServiceDiscoverer> serviceDiscovererProvider,
                                   final UriFactory uriFactory,
                                   final DataSourceUrlConfig dataSourceUrlConfig,
                                   final Provider<Client> clientProvider) {
        this.securityContext = securityContext;
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

    @Override
    public List<AbstractField> getFieldsForDataSource(final DocRef dataSourceRef) {
        // Elevate the users permissions for the duration of this task so they can read the index if
        // they have 'use' permission.
        return securityContext.useAsReadResult(
                () -> getDataSourceProvider(dataSourceRef)
                        .map(provider -> provider.getDataSource(dataSourceRef).getFields())
                        .orElse(null));
    }


}
