package stroom.proxy.app;

import stroom.proxy.app.event.EventStoreConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.handler.ForwarderConfig;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.app.handler.ThreadConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.receive.common.AuthenticationType;
import stroom.receive.common.ReceiveDataConfig;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@JsonPropertyOrder(alphabetic = true)
public class ProxyConfig extends AbstractConfig implements IsProxyConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyConfig.class);

    public static final PropertyPath ROOT_PROPERTY_PATH = PropertyPath.fromParts("proxyConfig");

    public static final String PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE = "haltBootOnConfigValidationFailure";
    public static final String PROP_NAME_PROXY_ID = "proxyId";
    public static final String PROP_NAME_CONTENT_DIR = "contentDir";
    public static final String PROP_NAME_PATH = "path";
    public static final String PROP_NAME_RECEIVE = "receive";
    public static final String PROP_NAME_EVENT_STORE = "eventStore";
    public static final String PROP_NAME_AGGREGATOR = "aggregator";
    public static final String PROP_NAME_DIR_SCANNER = "dirScanner";
    public static final String PROP_NAME_FORWARD_FILE_DESTINATIONS = "forwardFileDestinations";
    public static final String PROP_NAME_FORWARD_HTTP_DESTINATIONS = "forwardHttpDestinations";
    public static final String PROP_NAME_LOG_STREAM = "logStream";
    public static final String PROP_NAME_CONTENT_SYNC = "contentSync";
    public static final String PROP_NAME_FEED_STATUS = "feedStatus";
    public static final String PROP_NAME_THREADS = "threads";
    public static final String PROP_NAME_SECURITY = "security";
    public static final String PROP_NAME_SQS_CONNECTORS = "sqsConnectors";

    protected static final boolean DEFAULT_USE_DEFAULT_OPEN_ID_CREDENTIALS = false;
    protected static final boolean DEFAULT_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE = true;
    protected static final String DEFAULT_CONTENT_DIR = "content";

    private final boolean haltBootOnConfigValidationFailure;
    private final String proxyId;
    private final String contentDir;

    private final ProxyPathConfig pathConfig;
    private final ReceiveDataConfig receiveDataConfig;
    private final EventStoreConfig eventStoreConfig;
    private final AggregatorConfig aggregatorConfig;
    private final DirScannerConfig dirScannerConfig;
    private final List<ForwardFileConfig> forwardFileDestinations;
    private final List<ForwardHttpPostConfig> forwardHttpDestinations;
    private final LogStreamConfig logStreamConfig;
    private final ContentSyncConfig contentSyncConfig;
    private final FeedStatusConfig feedStatusConfig;
    private final ThreadConfig threadConfig;
    private final ProxySecurityConfig proxySecurityConfig;
    private final List<SqsConnectorConfig> sqsConnectors;

    public ProxyConfig() {
        this(DEFAULT_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE,
                null,
                DEFAULT_CONTENT_DIR,
                new ProxyPathConfig(),
                new ReceiveDataConfig(),
                new EventStoreConfig(),
                new AggregatorConfig(),
                new DirScannerConfig(),
                new ArrayList<>(),
                new ArrayList<>(),
                new LogStreamConfig(),
                new ContentSyncConfig(),
                new FeedStatusConfig(),
                new ThreadConfig(),
                new ProxySecurityConfig(),
                new ArrayList<>());
    }

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public ProxyConfig(
            @JsonProperty(PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE) final boolean haltBootOnConfigValidationFailure,
            @JsonProperty(PROP_NAME_PROXY_ID) final String proxyId,
            @JsonProperty(PROP_NAME_CONTENT_DIR) final String contentDir,
            @JsonProperty(PROP_NAME_PATH) final ProxyPathConfig pathConfig,
            @JsonProperty(PROP_NAME_RECEIVE) final ReceiveDataConfig receiveDataConfig,
            @JsonProperty(PROP_NAME_EVENT_STORE) final EventStoreConfig eventStoreConfig,
            @JsonProperty(PROP_NAME_AGGREGATOR) final AggregatorConfig aggregatorConfig,
            @JsonProperty(PROP_NAME_DIR_SCANNER) final DirScannerConfig dirScannerConfig,
            @JsonProperty(PROP_NAME_FORWARD_FILE_DESTINATIONS) final List<ForwardFileConfig> forwardFileDestinations,
            @JsonProperty(PROP_NAME_FORWARD_HTTP_DESTINATIONS) final List<ForwardHttpPostConfig> forwardHttpDestinations,
            @JsonProperty(PROP_NAME_LOG_STREAM) final LogStreamConfig logStreamConfig,
            @JsonProperty(PROP_NAME_CONTENT_SYNC) final ContentSyncConfig contentSyncConfig,
            @JsonProperty(PROP_NAME_FEED_STATUS) final FeedStatusConfig feedStatusConfig,
            @JsonProperty(PROP_NAME_THREADS) final ThreadConfig threadConfig,
            @JsonProperty(PROP_NAME_SECURITY) final ProxySecurityConfig proxySecurityConfig,
            @JsonProperty(PROP_NAME_SQS_CONNECTORS) final List<SqsConnectorConfig> sqsConnectors) {

        this.haltBootOnConfigValidationFailure = haltBootOnConfigValidationFailure;
        this.proxyId = proxyId;
        this.contentDir = contentDir;
        this.pathConfig = pathConfig;
        this.receiveDataConfig = receiveDataConfig;
        this.eventStoreConfig = eventStoreConfig;
        this.aggregatorConfig = aggregatorConfig;
        this.dirScannerConfig = dirScannerConfig;
        this.forwardFileDestinations = forwardFileDestinations;
        this.forwardHttpDestinations = forwardHttpDestinations;
        this.logStreamConfig = logStreamConfig;
        this.contentSyncConfig = contentSyncConfig;
        this.feedStatusConfig = feedStatusConfig;
        this.threadConfig = threadConfig;
        this.proxySecurityConfig = proxySecurityConfig;
        this.sqsConnectors = sqsConnectors;
    }

    @AssertTrue(
            message = "proxyConfig." + PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE + " is set to false. " +
                      "If there is invalid configuration the system may behave in unexpected ways. This setting is " +
                      "not advised.",
            payload = ValidationSeverity.Warning.class)
    @JsonProperty(PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE)
    @JsonPropertyDescription("If true, Stroom-Proxy will halt on start up if any errors are found in the YAML " +
                             "configuration file. If false, the errors will simply be logged. Setting this to " +
                             "false is not advised.")
    public boolean isHaltBootOnConfigValidationFailure() {
        return haltBootOnConfigValidationFailure;
    }

    @Pattern(regexp = ProxyId.PROXY_ID_REGEX)
    @JsonProperty
    @JsonPropertyDescription("The unique id for this proxy instance. Must match the pattern '^[A-Za-z0-9-]$' and " +
                             "be unique within the whole chain of proxies. It is used in the receipt ID that " +
                             "is generated for each received stream. If not set a Proxy ID will be generated and " +
                             "and stored in the file 'proxy-id.txt'.")
    public String getProxyId() {
        return proxyId;
    }

    @RequiresProxyRestart
    @JsonProperty
    public String getContentDir() {
        return contentDir;
    }

    @JsonProperty(PROP_NAME_PATH)
    public ProxyPathConfig getPathConfig() {
        return pathConfig;
    }

    @JsonProperty(PROP_NAME_RECEIVE)
    public ReceiveDataConfig getReceiveDataConfig() {
        return receiveDataConfig;
    }

    @JsonProperty(PROP_NAME_EVENT_STORE)
    public EventStoreConfig getEventStoreConfig() {
        return eventStoreConfig;
    }

    @JsonProperty(PROP_NAME_AGGREGATOR)
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    @JsonProperty(PROP_NAME_DIR_SCANNER)
    public DirScannerConfig getDirScannerConfig() {
        return dirScannerConfig;
    }

    @RequiresProxyRestart
    @JsonProperty(PROP_NAME_FORWARD_FILE_DESTINATIONS)
    public List<ForwardFileConfig> getForwardFileDestinations() {
        return forwardFileDestinations;
    }

    @RequiresProxyRestart
    @JsonProperty(PROP_NAME_FORWARD_HTTP_DESTINATIONS)
    public List<ForwardHttpPostConfig> getForwardHttpDestinations() {
        return forwardHttpDestinations;
    }

    @JsonProperty(PROP_NAME_LOG_STREAM)
    public LogStreamConfig getLogStreamConfig() {
        return logStreamConfig;
    }

    @JsonProperty(PROP_NAME_CONTENT_SYNC)
    public ContentSyncConfig getContentSyncConfig() {
        return contentSyncConfig;
    }

    @JsonProperty(PROP_NAME_FEED_STATUS)
    public FeedStatusConfig getFeedStatusConfig() {
        return feedStatusConfig;
    }

    @JsonProperty(PROP_NAME_THREADS)
    public ThreadConfig getThreadConfig() {
        return threadConfig;
    }

    @JsonProperty(PROP_NAME_SECURITY)
    public ProxySecurityConfig getProxySecurityConfig() {
        return proxySecurityConfig;
    }

    @JsonPropertyDescription("Configurations for AWS SQS connectors")
    @JsonProperty
    public List<SqsConnectorConfig> getSqsConnectors() {
        return sqsConnectors;
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "identityProviderType must be set to EXTERNAL_IDP if enabledAuthenticationTypes " +
                                "contains 'TOKEN'")
    public boolean isTokenAuthenticationEnabledValid() {
        LOGGER.info("enabledAuthenticationTypes: {}, idpType: {}",
                NullSafe.get(receiveDataConfig, ReceiveDataConfig::getEnabledAuthenticationTypes),
                NullSafe.get(proxySecurityConfig,
                        ProxySecurityConfig::getAuthenticationConfig,
                        ProxyAuthenticationConfig::getOpenIdConfig,
                        AbstractOpenIdConfig::getIdentityProviderType));

        if (NullSafe.test(
                receiveDataConfig,
                config -> config.isAuthenticationTypeEnabled(AuthenticationType.TOKEN))) {

            return NullSafe.test(
                    proxySecurityConfig,
                    ProxySecurityConfig::getAuthenticationConfig,
                    ProxyAuthenticationConfig::getOpenIdConfig,
                    AbstractOpenIdConfig::getIdentityProviderType,
                    idpType -> idpType == IdpType.EXTERNAL_IDP);
        } else {
            return true;
        }
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "All forwarders must have unique names, ignoring case.")
    public boolean isForwardNamesValid() {
        final List<String> allNames = streamAllForwarders()
                .map(ForwarderConfig::getName)
                .filter(Objects::nonNull) // Null names get picked up elsewhere
                .map(String::toLowerCase)
                .sorted()
                .toList();
        final long distinctCount = allNames.stream()
                .distinct()
                .count();
        return distinctCount == allNames.size();
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "Only one forwarder (regardless of type) is permitted if any " +
                                "forwarder has instant=true. If you want to forward to multiple " +
                                "destinations you cannot use instant forwarding.")
    public boolean isInstantForwardingValid() {
        final long enabledInstantForwardersCount = streamAllForwarders()
                .filter(ForwarderConfig::isEnabled)
                .filter(ForwarderConfig::isInstant)
                .count();
        if (enabledInstantForwardersCount > 1) {
            // There can be only one!
            return false;
        } else if (enabledInstantForwardersCount == 1) {
            final long allEnabledForwardersCount = streamAllForwarders()
                    .filter(ForwarderConfig::isEnabled)
                    .count();
            // It must be the only enabled forwarder
            return allEnabledForwardersCount == 1;
        } else {
            // No instant forwarders so we don't care about the number of forwarders (here at least)
            return true;
        }
    }

//    @JsonIgnore
//    @SuppressWarnings("unused")
//    @ValidationMethod(message = "Only one forwarder is permitted if any forwarder has instant=true.")
//    public boolean isForwarderCountValid() {
//        final long allEnabledForwardersCount = streamAllForwarders()
//                .filter(ForwarderConfig::isEnabled)
//                .count();
//        return allEnabledForwardersCount >= 1;
//    }

    /**
     * @return A {@link Stream} of all forward destination config objects regardless of enabled
     * state.
     */
    public Stream<ForwarderConfig> streamAllForwarders() {
        return Stream.concat(
                NullSafe.stream(getForwardFileDestinations())
                        .filter(Objects::nonNull)
                        .map(config -> (ForwarderConfig) config),
                NullSafe.stream(getForwardHttpDestinations())
                        .filter(Objects::nonNull)
                        .map(config -> (ForwarderConfig) config));
    }

    /**
     * @return A {@link Stream} of all enabed forward destination config objects.
     */
    public Stream<ForwarderConfig> streamAllEnabledForwarders() {
        return streamAllForwarders()
                .filter(ForwarderConfig::isEnabled);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a {@link PropertyPath} by merging parts onto the root {@link PropertyPath}
     */
    public static PropertyPath buildPath(final String... parts) {
        return ROOT_PROPERTY_PATH.merge(parts);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class Builder {

        private Boolean haltBootOnConfigValidationFailure = DEFAULT_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE;
        private String proxyId;
        private String contentDir = DEFAULT_CONTENT_DIR;

        private ProxyPathConfig pathConfig = new ProxyPathConfig();
        private ReceiveDataConfig receiveDataConfig = new ReceiveDataConfig();
        private EventStoreConfig eventStoreConfig = new EventStoreConfig();
        private AggregatorConfig aggregatorConfig = new AggregatorConfig();
        private DirScannerConfig dirScannerConfig = new DirScannerConfig();
        private final List<ForwardFileConfig> forwardFileDestinations = new ArrayList<>();
        private final List<ForwardHttpPostConfig> forwardHttpDestinations = new ArrayList<>();
        private LogStreamConfig logStreamConfig = new LogStreamConfig();
        private ContentSyncConfig contentSyncConfig = new ContentSyncConfig();
        private FeedStatusConfig feedStatusConfig = new FeedStatusConfig();
        private ThreadConfig threadConfig = new ThreadConfig();
        private ProxySecurityConfig proxySecurityConfig = new ProxySecurityConfig();
        private final List<SqsConnectorConfig> sqsConnectors = new ArrayList<>();

        private Builder() {

        }

        public Builder haltBootOnConfigValidationFailure(final Boolean haltBootOnConfigValidationFailure) {
            this.haltBootOnConfigValidationFailure = haltBootOnConfigValidationFailure;
            return this;
        }

        public Builder proxyId(final String proxyId) {
            this.proxyId = proxyId;
            return this;
        }

        public Builder contentDir(final String contentDir) {
            this.contentDir = contentDir;
            return this;
        }

        public Builder pathConfig(final ProxyPathConfig pathConfig) {
            this.pathConfig = pathConfig;
            return this;
        }

        public Builder receiveDataConfig(final ReceiveDataConfig receiveDataConfig) {
            this.receiveDataConfig = receiveDataConfig;
            return this;
        }

        public Builder eventStoreConfig(final EventStoreConfig eventStoreConfig) {
            this.eventStoreConfig = eventStoreConfig;
            return this;
        }

        public Builder aggregatorConfig(final AggregatorConfig aggregatorConfig) {
            this.aggregatorConfig = aggregatorConfig;
            return this;
        }

        public Builder dirScannerConfig(final DirScannerConfig dirScannerConfig) {
            this.dirScannerConfig = dirScannerConfig;
            return this;
        }

        public Builder addForwardFileDestination(final ForwardFileConfig forwarderFileConfig) {
            this.forwardFileDestinations.add(forwarderFileConfig);
            return this;
        }

        public Builder forwardFileDestinations(final Collection<ForwardFileConfig> forwarderFileConfigs) {
            this.forwardFileDestinations.clear();
            if (forwarderFileConfigs != null) {
                this.forwardFileDestinations.addAll(forwarderFileConfigs);
            }
            return this;
        }

        public Builder addForwardHttpDestination(final ForwardHttpPostConfig forwarderHttpConfig) {
            this.forwardHttpDestinations.add(forwarderHttpConfig);
            return this;
        }

        public Builder forwardHttpDestinations(final Collection<ForwardHttpPostConfig> forwarderHttpConfigs) {
            this.forwardHttpDestinations.clear();
            if (forwarderHttpConfigs != null) {
                this.forwardHttpDestinations.addAll(forwarderHttpConfigs);
            }
            return this;
        }

        public Builder logStreamConfig(final LogStreamConfig logStreamConfig) {
            this.logStreamConfig = logStreamConfig;
            return this;
        }

        public Builder contentSyncConfig(final ContentSyncConfig contentSyncConfig) {
            this.contentSyncConfig = contentSyncConfig;
            return this;
        }

        public Builder feedStatusConfig(final FeedStatusConfig feedStatusConfig) {
            this.feedStatusConfig = feedStatusConfig;
            return this;
        }

        public Builder threadConfig(final ThreadConfig threadConfig) {
            this.threadConfig = threadConfig;
            return this;
        }

        public Builder securityConfig(final ProxySecurityConfig proxySecurityConfig) {
            this.proxySecurityConfig = proxySecurityConfig;
            return this;
        }

        public Builder addSqsConnector(final SqsConnectorConfig sqsConnector) {
            this.sqsConnectors.add(sqsConnector);
            return this;
        }

        public ProxyConfig build() {
            return new ProxyConfig(
                    haltBootOnConfigValidationFailure,
                    proxyId,
                    contentDir,
                    pathConfig,
                    receiveDataConfig,
                    eventStoreConfig,
                    aggregatorConfig,
                    dirScannerConfig,
                    forwardFileDestinations,
                    forwardHttpDestinations,
                    logStreamConfig,
                    contentSyncConfig,
                    feedStatusConfig,
                    threadConfig,
                    proxySecurityConfig,
                    sqsConnectors);
        }
    }
}
