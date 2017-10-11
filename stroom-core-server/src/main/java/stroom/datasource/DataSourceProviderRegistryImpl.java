package stroom.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.apiclients.AuthenticationServiceClient;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.servicediscovery.ServiceDiscoverer;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Optional;

@Component
@Scope(StroomScope.SINGLETON)
@SuppressWarnings("unused")
public class DataSourceProviderRegistryImpl implements DataSourceProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceProviderRegistryImpl.class);

    public static final String PROP_KEY_SERVICE_DISCOVERY_ENABLED = "stroom.serviceDiscovery.enabled";

    private final SecurityContext securityContext;
    private final StroomPropertyService stroomPropertyService;
    private final DataSourceProviderRegistry delegateDataSourceProviderRegistry;
    private final AuthenticationServiceClient authenticationServiceClient;

    @SuppressWarnings("unused")
    @Inject
    public DataSourceProviderRegistryImpl (final SecurityContext securityContext,
                                           final StroomPropertyService stroomPropertyService,
                                           final StroomBeanStore stroomBeanStore,
                                           final AuthenticationServiceClient authenticationServiceClient) {
        this.securityContext = securityContext;
        this.stroomPropertyService = stroomPropertyService;
        this.authenticationServiceClient = authenticationServiceClient;

        boolean isServiceDiscoveryEnabled = stroomPropertyService.getBooleanProperty(
                PROP_KEY_SERVICE_DISCOVERY_ENABLED,
                false);

        if (isServiceDiscoveryEnabled) {
            ServiceDiscoverer serviceDiscoverer = stroomBeanStore.getBean(ServiceDiscoverer.class);
            LOGGER.debug("Using service discovery for service lookup");
            delegateDataSourceProviderRegistry = new ServiceDiscoveryDataSourceProviderRegistry(
                    securityContext,
                    serviceDiscoverer,
                    authenticationServiceClient);
        } else {
            LOGGER.debug("Using local services");
            delegateDataSourceProviderRegistry = new SimpleDataSourceProviderRegistry(
                securityContext,
                stroomPropertyService,
                    authenticationServiceClient);
        }
    }

    @Override
    public Optional<DataSourceProvider> getDataSourceProvider(final DocRef dataSourceRef) {
        return delegateDataSourceProviderRegistry.getDataSourceProvider(dataSourceRef);
    }
}
