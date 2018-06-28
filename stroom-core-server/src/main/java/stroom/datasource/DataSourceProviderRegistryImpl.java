package stroom.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.properties.StroomPropertyService;
import stroom.docref.DocRef;
import stroom.security.SecurityContext;
import stroom.servicediscovery.ServiceDiscoverer;
import stroom.servlet.HttpServletRequestHolder;
import stroom.guice.StroomBeanStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
@SuppressWarnings("unused")
public class DataSourceProviderRegistryImpl implements DataSourceProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceProviderRegistryImpl.class);

    public static final String PROP_KEY_SERVICE_DISCOVERY_ENABLED = "stroom.serviceDiscovery.enabled";

    private final DataSourceProviderRegistry delegateDataSourceProviderRegistry;

    @SuppressWarnings("unused")
    @Inject
    DataSourceProviderRegistryImpl(final SecurityContext securityContext,
                                          final StroomPropertyService stroomPropertyService,
                                          final StroomBeanStore stroomBeanStore,
                                          final HttpServletRequestHolder httpServletRequestHolder) {
        boolean isServiceDiscoveryEnabled = stroomPropertyService.getBooleanProperty(
                PROP_KEY_SERVICE_DISCOVERY_ENABLED,
                false);

        if (isServiceDiscoveryEnabled) {
            ServiceDiscoverer serviceDiscoverer = stroomBeanStore.getInstance(ServiceDiscoverer.class);
            LOGGER.debug("Using service discovery for service lookup");
            delegateDataSourceProviderRegistry = new ServiceDiscoveryDataSourceProviderRegistry(
                    securityContext,
                    serviceDiscoverer,
                    httpServletRequestHolder);
        } else {
            LOGGER.debug("Using local services");
            delegateDataSourceProviderRegistry = new SimpleDataSourceProviderRegistry(
                    securityContext,
                    stroomPropertyService,
                    httpServletRequestHolder);
        }
    }

    @Override
    public Optional<DataSourceProvider> getDataSourceProvider(final DocRef dataSourceRef) {
        return delegateDataSourceProviderRegistry.getDataSourceProvider(dataSourceRef);
    }
}
