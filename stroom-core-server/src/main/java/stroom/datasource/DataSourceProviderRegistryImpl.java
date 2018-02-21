package stroom.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.servicediscovery.ServiceDiscoverer;
import stroom.servlet.HttpServletRequestHolder;
import stroom.util.spring.StroomBeanStore;

import javax.inject.Inject;
import java.util.Optional;

@SuppressWarnings("unused")
public class DataSourceProviderRegistryImpl implements DataSourceProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceProviderRegistryImpl.class);

    public static final String PROP_KEY_SERVICE_DISCOVERY_ENABLED = "stroom.serviceDiscovery.enabled";

    private final SecurityContext securityContext;
    private final StroomPropertyService stroomPropertyService;
    private final DataSourceProviderRegistry delegateDataSourceProviderRegistry;

    @SuppressWarnings("unused")
    @Inject
    public DataSourceProviderRegistryImpl(final SecurityContext securityContext,
                                          final StroomPropertyService stroomPropertyService,
                                          final StroomBeanStore stroomBeanStore,
                                          final HttpServletRequestHolder httpServletRequestHolder) {
        this.securityContext = securityContext;
        this.stroomPropertyService = stroomPropertyService;

        boolean isServiceDiscoveryEnabled = stroomPropertyService.getBooleanProperty(
                PROP_KEY_SERVICE_DISCOVERY_ENABLED,
                false);

        if (isServiceDiscoveryEnabled) {
            ServiceDiscoverer serviceDiscoverer = stroomBeanStore.getBean(ServiceDiscoverer.class);
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
