package stroom.servicediscovery;

import com.google.common.base.Preconditions;
import org.apache.curator.x.discovery.ProviderStrategy;
import org.apache.curator.x.discovery.strategies.RandomStrategy;
import org.apache.curator.x.discovery.strategies.StickyStrategy;
import stroom.util.config.StroomProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The canonical list of external services.
 * <p>
 * Service names, used for service discovery lookup, are obtained from configuration.
 */
public enum ExternalService {
    //stroom index involves multiple calls to fetch the data iteratively so must be sticky
    INDEX("index", new StickyStrategy<>(new RandomStrategy<>())),
    //stroom stats returns all results in one go so is stateless and can use a random strategy
    STROOM_STATS("stroomStats", new RandomStrategy<>()),
    //sql statistics returns all results in one go so is stateless and can use a random strategy
    SQL_STATISTICS("sqlStatistics", new RandomStrategy<>()),
    //stateless so random strategy
    AUTHENTICATION("authentication", new RandomStrategy<>()),
    //stateless so random strategy
    AUTHORISATION("authorisation", new RandomStrategy<>());

    private static final String PROP_KEY_PREFIX = "stroom.services.";
    private static final String NAME_SUFFIX = ".name";
    private static final String VERSION_SUFFIX = ".version";
    private static final String DOC_REF_TYPE_SUFFIX = ".docRefType";

    private final String serviceKey;
    private final ProviderStrategy<String> providerStrategy;

    /**
     * This maps doc ref types to services. I.e. if someone has the doc ref type they can get an ExternalService.
     */
    private static Map<String, ExternalService> docRefTypeToServiceMap = new HashMap<>();

    ExternalService(final String serviceKey, final ProviderStrategy<String> providerStrategy) {
        this.serviceKey = serviceKey;
        this.providerStrategy = providerStrategy;
    }

    static {
        Stream.of(ExternalService.values())
                .forEach(externalService -> {
                    String docRefType = StroomProperties.getProperty(
                            PROP_KEY_PREFIX + externalService.getServiceKey() + DOC_REF_TYPE_SUFFIX);
                    if (docRefType != null && !docRefType.isEmpty()) {
                        docRefTypeToServiceMap.put(docRefType, externalService);
                    }
                });
    }

    public static Optional<ExternalService> getExternalService(final String docRefType) {
        Preconditions.checkNotNull(docRefType);
        return Optional.ofNullable(docRefTypeToServiceMap.get(docRefType));
    }

    /**
     * This is the name of the service, as obtained from configuration.
     */
    public String getBaseServiceName() {
        String propKey = PROP_KEY_PREFIX + serviceKey + NAME_SUFFIX;
        return Preconditions.checkNotNull(StroomProperties.getProperty(propKey), "Property %s does not have a value but should", propKey);
    }

    public int getVersion() {
        String propKey = PROP_KEY_PREFIX + serviceKey + VERSION_SUFFIX;
        return StroomProperties.getIntProperty(propKey, 1);
    }

    public String getVersionedServiceName() {
        String baseServiceName = getBaseServiceName();
        return getBaseServiceName() + "-v" + getVersion();
    }

    public ProviderStrategy<String> getProviderStrategy() {
        return providerStrategy;
    }

    /**
     * This is the value in the configuration, i.e. stroom.services.<serviceKey>.name.
     */
    public String getServiceKey() {
        return serviceKey;
    }

}