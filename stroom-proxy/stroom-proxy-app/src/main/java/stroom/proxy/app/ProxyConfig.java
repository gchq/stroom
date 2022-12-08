package stroom.proxy.app;

import stroom.proxy.app.event.EventStoreConfig;
import stroom.proxy.app.forwarder.ForwardConfig;
import stroom.proxy.app.forwarder.ThreadConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.FileScannerConfig;
import stroom.proxy.repo.ForwardRetryConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.AssertTrue;

@JsonPropertyOrder(alphabetic = true)
public class ProxyConfig extends AbstractConfig implements IsProxyConfig {

    public static final PropertyPath ROOT_PROPERTY_PATH = PropertyPath.fromParts("proxyConfig");

    public static final String PROP_NAME_USE_DEFAULT_OPENID_CREDENTIALS = "useDefaultOpenIdCredentials";
    public static final String PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE = "haltBootOnConfigValidationFailure";
    public static final String PROP_NAME_PROXY_ID = "proxyId";
    public static final String PROP_NAME_CONTENT_DIR = "contentDir";
    public static final String PROP_NAME_PATH = "path";
    public static final String PROP_NAME_DB = "db";
    public static final String PROP_NAME_RECEIVE_DATA_CONFIG = "receiveDataConfig";
    public static final String PROP_NAME_REPOSITORY = "repository";
    public static final String PROP_NAME_EVENT_STORE = "eventStore";
    public static final String PROP_NAME_FILE_SCANNERS = "fileScanners";
    public static final String PROP_NAME_AGGREGATOR = "aggregator";
    public static final String PROP_NAME_FORWARD_DESTINATIONS = "forwardDestinations";
    public static final String PROP_NAME_LOG_STREAM = "logStream";
    public static final String PROP_NAME_CONTENT_SYNC = "contentSync";
    public static final String PROP_NAME_FEED_STATUS = "feedStatus";
    public static final String PROP_NAME_REST_CLIENT = "restClient";
    public static final String PROP_NAME_THREADS = "threads";
    public static final String PROP_NAME_FORWARD_RETRY = "forwardRetry";
    public static final String PROP_NAME_SQS_CONNECTORS = "sqsConnectors";

    protected static final boolean DEFAULT_USE_DEFAULT_OPEN_ID_CREDENTIALS = false;
    protected static final boolean DEFAULT_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE = true;
    protected static final String DEFAULT_CONTENT_DIR = "content";

    private final boolean useDefaultOpenIdCredentials;
    private final boolean haltBootOnConfigValidationFailure;
    private final String proxyId;
    private final String contentDir;

    private final ProxyPathConfig pathConfig;
    private final ProxyDbConfig proxyDbConfig;
    private final ReceiveDataConfig receiveDataConfig;
    private final ProxyRepoConfig proxyRepoConfig;
    private final EventStoreConfig eventStoreConfig;
    private final List<FileScannerConfig> fileScanners;
    private final AggregatorConfig aggregatorConfig;
    private final List<ForwardConfig> forwardDestinations;
    private final LogStreamConfig logStreamConfig;
    private final ContentSyncConfig contentSyncConfig;
    private final FeedStatusConfig feedStatusConfig;
    private final RestClientConfig restClientConfig;
    private final ThreadConfig threadConfig;
    private final ForwardRetryConfig forwardRetry;
    private final List<SqsConnectorConfig> sqsConnectors;

    public ProxyConfig() {
        useDefaultOpenIdCredentials = DEFAULT_USE_DEFAULT_OPEN_ID_CREDENTIALS;
        haltBootOnConfigValidationFailure = DEFAULT_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE;
        proxyId = null;
        contentDir = DEFAULT_CONTENT_DIR;

        pathConfig = new ProxyPathConfig();
        proxyDbConfig = new ProxyDbConfig();
        receiveDataConfig = new ReceiveDataConfig();
        proxyRepoConfig = new ProxyRepoConfig();
        eventStoreConfig = new EventStoreConfig();
        fileScanners = Collections.emptyList();
        aggregatorConfig = new AggregatorConfig();
        forwardDestinations = Collections.emptyList();
        logStreamConfig = new LogStreamConfig();
        contentSyncConfig = new ContentSyncConfig();
        feedStatusConfig = new FeedStatusConfig();
        restClientConfig = new RestClientConfig();
        threadConfig = new ThreadConfig();
        forwardRetry = new ForwardRetryConfig();
        sqsConnectors = new ArrayList<>();
    }


    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public ProxyConfig(
            @JsonProperty(PROP_NAME_USE_DEFAULT_OPENID_CREDENTIALS) final boolean useDefaultOpenIdCredentials,
            @JsonProperty(PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE) final boolean haltBootOnConfigValidationFailure,
            @JsonProperty(PROP_NAME_PROXY_ID) final String proxyId,
            @JsonProperty(PROP_NAME_CONTENT_DIR) final String contentDir,
            @JsonProperty(PROP_NAME_PATH) final ProxyPathConfig pathConfig,
            @JsonProperty(PROP_NAME_DB) final ProxyDbConfig proxyDbConfig,
            @JsonProperty(PROP_NAME_RECEIVE_DATA_CONFIG) final ReceiveDataConfig receiveDataConfig,
            @JsonProperty(PROP_NAME_REPOSITORY) final ProxyRepoConfig proxyRepoConfig,
            @JsonProperty(PROP_NAME_EVENT_STORE) final EventStoreConfig eventStoreConfig,
            @JsonProperty(PROP_NAME_FILE_SCANNERS) final List<FileScannerConfig> fileScanners,
            @JsonProperty(PROP_NAME_AGGREGATOR) final AggregatorConfig aggregatorConfig,
            @JsonProperty(PROP_NAME_FORWARD_DESTINATIONS) final List<ForwardConfig> forwardDestinations,
            @JsonProperty(PROP_NAME_LOG_STREAM) final LogStreamConfig logStreamConfig,
            @JsonProperty(PROP_NAME_CONTENT_SYNC) final ContentSyncConfig contentSyncConfig,
            @JsonProperty(PROP_NAME_FEED_STATUS) final FeedStatusConfig feedStatusConfig,
            @JsonProperty(PROP_NAME_REST_CLIENT) final RestClientConfig restClientConfig,
            @JsonProperty(PROP_NAME_THREADS) final ThreadConfig threadConfig,
            @JsonProperty(PROP_NAME_FORWARD_RETRY) final ForwardRetryConfig forwardRetry,
            @JsonProperty(PROP_NAME_SQS_CONNECTORS) final List<SqsConnectorConfig> sqsConnectors) {

        this.useDefaultOpenIdCredentials = useDefaultOpenIdCredentials;
        this.haltBootOnConfigValidationFailure = haltBootOnConfigValidationFailure;
        this.proxyId = proxyId;
        this.contentDir = contentDir;
        this.pathConfig = pathConfig;
        this.proxyDbConfig = proxyDbConfig;
        this.receiveDataConfig = receiveDataConfig;
        this.proxyRepoConfig = proxyRepoConfig;
        this.eventStoreConfig = eventStoreConfig;
        this.fileScanners = fileScanners;
        this.aggregatorConfig = aggregatorConfig;
        this.forwardDestinations = forwardDestinations;
        this.logStreamConfig = logStreamConfig;
        this.contentSyncConfig = contentSyncConfig;
        this.feedStatusConfig = feedStatusConfig;
        this.restClientConfig = restClientConfig;
        this.threadConfig = threadConfig;
        this.forwardRetry = forwardRetry;
        this.sqsConnectors = sqsConnectors;
    }

    @AssertTrue(
            message = "proxyConfig." + PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE + " is set to false. " +
                    "If there is invalid configuration the system may behave in unexpected ways. This setting is " +
                    "not advised.",
            payload = ValidationSeverity.Warning.class)
    @JsonProperty(PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE)
    @JsonPropertyDescription("If true, Stroom-Proxy will halt on start up if any errors are found in the YAML " +
            "configuration file. If false, the errors will simply be logged. Setting this to false is not advised.")

    public boolean isHaltBootOnConfigValidationFailure() {
        return haltBootOnConfigValidationFailure;
    }

    @JsonProperty(PROP_NAME_USE_DEFAULT_OPENID_CREDENTIALS)
    @JsonPropertyDescription("If true, stroom will use a set of default authentication credentials to allow" +
            "API calls from stroom-proxy. For test or demonstration purposes only, set to false for production. " +
            "If API keys are set elsewhere in config then they will override this setting.")
    public boolean isUseDefaultOpenIdCredentials() {
        return useDefaultOpenIdCredentials;
    }

    @JsonProperty
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

    @JsonProperty(PROP_NAME_DB)
    public ProxyDbConfig getProxyDbConfig() {
        return proxyDbConfig;
    }

    @JsonProperty(PROP_NAME_RECEIVE_DATA_CONFIG)
    public ReceiveDataConfig getReceiveDataConfig() {
        return receiveDataConfig;
    }

    @JsonProperty(PROP_NAME_REPOSITORY)
    public ProxyRepoConfig getProxyRepositoryConfig() {
        return proxyRepoConfig;
    }

    @JsonProperty(PROP_NAME_EVENT_STORE)
    public EventStoreConfig getEventStoreConfig() {
        return eventStoreConfig;
    }

    @JsonProperty(PROP_NAME_FILE_SCANNERS)
    public List<FileScannerConfig> getFileScanners() {
        return fileScanners;
    }

    @JsonProperty(PROP_NAME_AGGREGATOR)
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    @RequiresProxyRestart
    @JsonProperty(PROP_NAME_FORWARD_DESTINATIONS)
    public List<ForwardConfig> getForwardDestinations() {
        return forwardDestinations;
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

    @JsonProperty(PROP_NAME_REST_CLIENT)
    public RestClientConfig getRestClientConfig() {
        return restClientConfig;
    }

    @JsonProperty(PROP_NAME_THREADS)
    public ThreadConfig getThreadConfig() {
        return threadConfig;
    }

    @JsonPropertyDescription(PROP_NAME_FORWARD_RETRY)
    @JsonProperty
    public ForwardRetryConfig getForwardRetry() {
        return forwardRetry;
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "If repository.storingEnabled is not true, then forwardDestinations must contain at " +
            "least one destination")
    public boolean isRepoConfigValid() {
        return (proxyRepoConfig != null && proxyRepoConfig.isStoringEnabled())
                || (forwardDestinations != null && !forwardDestinations.isEmpty());
    }

    @JsonPropertyDescription("Configurations for AWS SQS connectors")
    @JsonProperty
    public List<SqsConnectorConfig> getSqsConnectors() {
        return sqsConnectors;
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

        private Boolean useDefaultOpenIdCredentials = DEFAULT_USE_DEFAULT_OPEN_ID_CREDENTIALS;
        private Boolean haltBootOnConfigValidationFailure = DEFAULT_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE;
        private String proxyId;
        private String contentDir = DEFAULT_CONTENT_DIR;

        private ProxyPathConfig pathConfig = new ProxyPathConfig();
        private ProxyDbConfig proxyDbConfig = new ProxyDbConfig();
        private ReceiveDataConfig receiveDataConfig = new ReceiveDataConfig();
        private ProxyRepoConfig proxyRepoConfig = new ProxyRepoConfig();
        private EventStoreConfig eventStoreConfig = new EventStoreConfig();
        private List<FileScannerConfig> fileScanners = new ArrayList<>();
        private AggregatorConfig aggregatorConfig = new AggregatorConfig();
        private List<ForwardConfig> forwardDestinations = new ArrayList<>();
        private LogStreamConfig logStreamConfig = new LogStreamConfig();
        private ContentSyncConfig contentSyncConfig = new ContentSyncConfig();
        private FeedStatusConfig feedStatusConfig = new FeedStatusConfig();
        private RestClientConfig restClientConfig = new RestClientConfig();
        private ThreadConfig threadConfig = new ThreadConfig();
        private ForwardRetryConfig forwardRetry = new ForwardRetryConfig();
        private List<SqsConnectorConfig> sqsConnectors = new ArrayList<>();

        private Builder() {

        }

        public Builder useDefaultOpenIdCredentials(final Boolean useDefaultOpenIdCredentials) {
            this.useDefaultOpenIdCredentials = useDefaultOpenIdCredentials;
            return this;
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

        public Builder proxyDbConfig(final ProxyDbConfig proxyDbConfig) {
            this.proxyDbConfig = proxyDbConfig;
            return this;
        }

        public Builder receiveDataConfig(final ReceiveDataConfig receiveDataConfig) {
            this.receiveDataConfig = receiveDataConfig;
            return this;
        }

        public Builder proxyRepoConfig(final ProxyRepoConfig proxyRepoConfig) {
            this.proxyRepoConfig = proxyRepoConfig;
            return this;
        }

        public Builder eventStoreConfig(final EventStoreConfig eventStoreConfig) {
            this.eventStoreConfig = eventStoreConfig;
            return this;
        }

        public Builder addFileScanner(final FileScannerConfig fileScannerConfig) {
            this.fileScanners.add(fileScannerConfig);
            return this;
        }

        public Builder aggregatorConfig(final AggregatorConfig aggregatorConfig) {
            this.aggregatorConfig = aggregatorConfig;
            return this;
        }

        public Builder addForwardDestination(final ForwardConfig forwarderConfig) {
            this.forwardDestinations.add(forwarderConfig);
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

        public Builder restClientConfig(final RestClientConfig restClientConfig) {
            this.restClientConfig = restClientConfig;
            return this;
        }

        public Builder threadConfig(final ThreadConfig threadConfig) {
            this.threadConfig = threadConfig;
            return this;
        }

        public Builder forwardRetry(final ForwardRetryConfig forwardRetry) {
            this.forwardRetry = forwardRetry;
            return this;
        }

        public Builder addSqsConnector(final SqsConnectorConfig sqsConnector) {
            this.sqsConnectors.add(sqsConnector);
            return this;
        }

        public ProxyConfig build() {
            return new ProxyConfig(
                    useDefaultOpenIdCredentials,
                    haltBootOnConfigValidationFailure,
                    proxyId,
                    contentDir,
                    pathConfig,
                    proxyDbConfig,
                    receiveDataConfig,
                    proxyRepoConfig,
                    eventStoreConfig,
                    fileScanners,
                    aggregatorConfig,
                    forwardDestinations,
                    logStreamConfig,
                    contentSyncConfig,
                    feedStatusConfig,
                    restClientConfig,
                    threadConfig,
                    forwardRetry,
                    sqsConnectors);
        }
    }
}
