package stroom.proxy.app;

import stroom.proxy.app.event.EventStoreConfig;
import stroom.proxy.app.forwarder.ForwardConfig;
import stroom.proxy.app.forwarder.ThreadConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.FileScannerConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyDbConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.validation.ValidationSeverity;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.AssertTrue;

@JsonPropertyOrder(alphabetic = true)
public class ProxyConfig extends AbstractConfig implements IsProxyConfig {

    public static final PropertyPath ROOT_PROPERTY_PATH = PropertyPath.fromParts("proxyConfig");

    public static final String PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE = "haltBootOnConfigValidationFailure";
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
    private final StroomDuration retryFrequency;

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
        retryFrequency = StroomDuration.ofMinutes(1);
    }

    @JsonCreator
    public ProxyConfig(
            @JsonProperty("useDefaultOpenIdCredentials") final boolean useDefaultOpenIdCredentials,
            @JsonProperty("haltBootOnConfigValidationFailure") final boolean haltBootOnConfigValidationFailure,
            @JsonProperty("proxyId") final String proxyId,
            @JsonProperty("contentDir") final String contentDir,
            @JsonProperty("path") final ProxyPathConfig pathConfig,
            @JsonProperty("db") final ProxyDbConfig proxyDbConfig,
            @JsonProperty("receiveDataConfig") final ReceiveDataConfig receiveDataConfig,
            @JsonProperty("repository") final ProxyRepoConfig proxyRepoConfig,
            @JsonProperty("eventStore") final EventStoreConfig eventStoreConfig,
            @JsonProperty("fileScanners") final List<FileScannerConfig> fileScanners,
            @JsonProperty("aggregator") final AggregatorConfig aggregatorConfig,
            @JsonProperty("forwardDestinations") final List<ForwardConfig> forwardDestinations,
            @JsonProperty("logStream") final LogStreamConfig logStreamConfig,
            @JsonProperty("contentSync") final ContentSyncConfig contentSyncConfig,
            @JsonProperty("feedStatus") final FeedStatusConfig feedStatusConfig,
            @JsonProperty("restClient") final RestClientConfig restClientConfig,
            @JsonProperty("threads") final ThreadConfig threadConfig,
            @JsonProperty("retryFrequency") final StroomDuration retryFrequency) {

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
        this.retryFrequency = retryFrequency;
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

    @JsonProperty()
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

    @JsonProperty
    public String getContentDir() {
        return contentDir;
    }

    @JsonProperty("path")
    public ProxyPathConfig getPathConfig() {
        return pathConfig;
    }

    @JsonProperty("db")
    public ProxyDbConfig getProxyDbConfig() {
        return proxyDbConfig;
    }

    @JsonProperty("receiveDataConfig")
    public ReceiveDataConfig getReceiveDataConfig() {
        return receiveDataConfig;
    }

    @JsonProperty("repository")
    public ProxyRepoConfig getProxyRepositoryConfig() {
        return proxyRepoConfig;
    }

    @JsonProperty("eventStore")
    public EventStoreConfig getEventStoreConfig() {
        return eventStoreConfig;
    }

    @JsonProperty("fileScanners")
    public List<FileScannerConfig> getFileScanners() {
        return fileScanners;
    }

    @JsonProperty("aggregator")
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    @JsonProperty("forwardDestinations")
    public List<ForwardConfig> getForwardDestinations() {
        return forwardDestinations;
    }

    @JsonProperty("logStream")
    public LogStreamConfig getLogStreamConfig() {
        return logStreamConfig;
    }

    @JsonProperty("contentSync")
    public ContentSyncConfig getContentSyncConfig() {
        return contentSyncConfig;
    }

    @JsonProperty("feedStatus")
    public FeedStatusConfig getFeedStatusConfig() {
        return feedStatusConfig;
    }

    @JsonProperty("restClient")
    public RestClientConfig getRestClientConfig() {
        return restClientConfig;
    }

    @JsonProperty("threads")
    public ThreadConfig getThreadConfig() {
        return threadConfig;
    }

    @JsonPropertyDescription("How often do we want to retry forwarding data that fails to forward?")
    @JsonProperty
    public StroomDuration getRetryFrequency() {
        return retryFrequency;
    }

    public static Builder builder() {
        return new Builder();
    }

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
        private StroomDuration retryFrequency = StroomDuration.ofMinutes(1);

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

        public Builder retryFrequency(final StroomDuration retryFrequency) {
            this.retryFrequency = retryFrequency;
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
                    retryFrequency);
        }
    }
}
