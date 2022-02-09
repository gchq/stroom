package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.app.forwarder.ThreadConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoDbConfig;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
    private final String contentDir;

    private final ProxyPathConfig pathConfig;
    private final ProxyRepoDbConfig proxyDbConfig;
    private final ReceiveDataConfig receiveDataConfig;
    private final ProxyRepoConfig proxyRepoConfig;
    private final ProxyRepoFileScannerConfig proxyRepoFileScannerConfig;
    private final AggregatorConfig aggregatorConfig;
    private final ForwarderConfig forwarderConfig;
    private final LogStreamConfig logStreamConfig;
    private final ContentSyncConfig contentSyncConfig;
    private final FeedStatusConfig feedStatusConfig;
    private final RestClientConfig restClientConfig;
    private final ThreadConfig threadConfig;

    public ProxyConfig() {
        useDefaultOpenIdCredentials = DEFAULT_USE_DEFAULT_OPEN_ID_CREDENTIALS;
        haltBootOnConfigValidationFailure = DEFAULT_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE;
        contentDir = DEFAULT_CONTENT_DIR;

        pathConfig = new ProxyPathConfig();
        proxyDbConfig = new ProxyRepoDbConfig();
        receiveDataConfig = new ReceiveDataConfig();
        proxyRepoConfig = new ProxyRepoConfig();
        proxyRepoFileScannerConfig = new ProxyRepoFileScannerConfig();
        aggregatorConfig = new AggregatorConfig();
        forwarderConfig = new ForwarderConfig();
        logStreamConfig = new LogStreamConfig();
        contentSyncConfig = new ContentSyncConfig();
        feedStatusConfig = new FeedStatusConfig();
        restClientConfig = new RestClientConfig();
        threadConfig = new ThreadConfig();
    }

    @JsonCreator
    public ProxyConfig(
            @JsonProperty("useDefaultOpenIdCredentials") final boolean useDefaultOpenIdCredentials,
            @JsonProperty("haltBootOnConfigValidationFailure") final boolean haltBootOnConfigValidationFailure,
            @JsonProperty("contentDir") final String contentDir,
            @JsonProperty("path") final ProxyPathConfig pathConfig,
            @JsonProperty("db") final ProxyRepoDbConfig proxyDbConfig,
            @JsonProperty("receiveDataConfig") final ReceiveDataConfig receiveDataConfig,
            @JsonProperty("repository") final ProxyRepoConfig proxyRepoConfig,
            @JsonProperty("scanner") final ProxyRepoFileScannerConfig proxyRepoFileScannerConfig,
            @JsonProperty("aggregator") final AggregatorConfig aggregatorConfig,
            @JsonProperty("forwarder") final ForwarderConfig forwarderConfig,
            @JsonProperty("logStream") final LogStreamConfig logStreamConfig,
            @JsonProperty("contentSync") final ContentSyncConfig contentSyncConfig,
            @JsonProperty("feedStatus") final FeedStatusConfig feedStatusConfig,
            @JsonProperty("restClient") final RestClientConfig restClientConfig,
            @JsonProperty("threads") final ThreadConfig threadConfig) {

        this.useDefaultOpenIdCredentials = useDefaultOpenIdCredentials;
        this.haltBootOnConfigValidationFailure = haltBootOnConfigValidationFailure;
        this.contentDir = contentDir;
        this.pathConfig = pathConfig;
        this.proxyDbConfig = proxyDbConfig;
        this.receiveDataConfig = receiveDataConfig;
        this.proxyRepoConfig = proxyRepoConfig;
        this.proxyRepoFileScannerConfig = proxyRepoFileScannerConfig;
        this.aggregatorConfig = aggregatorConfig;
        this.forwarderConfig = forwarderConfig;
        this.logStreamConfig = logStreamConfig;
        this.contentSyncConfig = contentSyncConfig;
        this.feedStatusConfig = feedStatusConfig;
        this.restClientConfig = restClientConfig;
        this.threadConfig = threadConfig;
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
    public String getContentDir() {
        return contentDir;
    }

    @JsonProperty("path")
    public ProxyPathConfig getPathConfig() {
        return pathConfig;
    }

    @JsonProperty("db")
    public ProxyRepoDbConfig getProxyDbConfig() {
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

    @JsonProperty("scanner")
    public ProxyRepoFileScannerConfig getProxyRepoFileScannerConfig() {
        return proxyRepoFileScannerConfig;
    }

    @JsonProperty("aggregator")
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    @JsonProperty("forwarder")
    public ForwarderConfig getForwarderConfig() {
        return forwarderConfig;
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Boolean useDefaultOpenIdCredentials = DEFAULT_USE_DEFAULT_OPEN_ID_CREDENTIALS;
        private Boolean haltBootOnConfigValidationFailure = DEFAULT_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE;
        private String contentDir = DEFAULT_CONTENT_DIR;

        private ProxyPathConfig pathConfig = new ProxyPathConfig();
        private ProxyRepoDbConfig proxyDbConfig = new ProxyRepoDbConfig();
        private ReceiveDataConfig receiveDataConfig = new ReceiveDataConfig();
        private ProxyRepoConfig proxyRepoConfig = new ProxyRepoConfig();
        private ProxyRepoFileScannerConfig proxyRepoFileScannerConfig = new ProxyRepoFileScannerConfig();
        private AggregatorConfig aggregatorConfig = new AggregatorConfig();
        private ForwarderConfig forwarderConfig = new ForwarderConfig();
        private LogStreamConfig logStreamConfig = new LogStreamConfig();
        private ContentSyncConfig contentSyncConfig = new ContentSyncConfig();
        private FeedStatusConfig feedStatusConfig = new FeedStatusConfig();
        private RestClientConfig restClientConfig = new RestClientConfig();
        private ThreadConfig threadConfig = new ThreadConfig();

        private Builder() {

        }

        public Builder withUseDefaultOpenIdCredentials(final Boolean useDefaultOpenIdCredentials) {
            this.useDefaultOpenIdCredentials = useDefaultOpenIdCredentials;
            return this;
        }

        public Builder withHaltBootOnConfigValidationFailure(final Boolean haltBootOnConfigValidationFailure) {
            this.haltBootOnConfigValidationFailure = haltBootOnConfigValidationFailure;
            return this;
        }

        public Builder withContentDir(final String contentDir) {
            this.contentDir = contentDir;
            return this;
        }

        public Builder withPathConfig(final ProxyPathConfig pathConfig) {
            this.pathConfig = pathConfig;
            return this;
        }

        public Builder withProxyDbConfig(final ProxyRepoDbConfig proxyDbConfig) {
            this.proxyDbConfig = proxyDbConfig;
            return this;
        }

        public Builder withReceiveDataConfig(final ReceiveDataConfig receiveDataConfig) {
            this.receiveDataConfig = receiveDataConfig;
            return this;
        }

        public Builder withProxyRepoConfig(final ProxyRepoConfig proxyRepoConfig) {
            this.proxyRepoConfig = proxyRepoConfig;
            return this;
        }

        public Builder withProxyRepoFileScannerConfig(final ProxyRepoFileScannerConfig proxyRepoFileScannerConfig) {
            this.proxyRepoFileScannerConfig = proxyRepoFileScannerConfig;
            return this;
        }

        public Builder withAggregatorConfig(final AggregatorConfig aggregatorConfig) {
            this.aggregatorConfig = aggregatorConfig;
            return this;
        }

        public Builder withForwarderConfig(final ForwarderConfig forwarderConfig) {
            this.forwarderConfig = forwarderConfig;
            return this;
        }

        public Builder withLogStreamConfig(final LogStreamConfig logStreamConfig) {
            this.logStreamConfig = logStreamConfig;
            return this;
        }

        public Builder withContentSyncConfig(final ContentSyncConfig contentSyncConfig) {
            this.contentSyncConfig = contentSyncConfig;
            return this;
        }

        public Builder withFeedStatusConfig(final FeedStatusConfig feedStatusConfig) {
            this.feedStatusConfig = feedStatusConfig;
            return this;
        }

        public Builder withRestClientConfig(final RestClientConfig restClientConfig) {
            this.restClientConfig = restClientConfig;
            return this;
        }

        public Builder withThreadConfig(final ThreadConfig threadConfig) {
            this.threadConfig = threadConfig;
            return this;
        }

        public ProxyConfig build() {
            return new ProxyConfig(
                    useDefaultOpenIdCredentials,
                    haltBootOnConfigValidationFailure,
                    contentDir,
                    pathConfig,
                    proxyDbConfig,
                    receiveDataConfig,
                    proxyRepoConfig,
                    proxyRepoFileScannerConfig,
                    aggregatorConfig,
                    forwarderConfig,
                    logStreamConfig,
                    contentSyncConfig,
                    feedStatusConfig,
                    restClientConfig,
                    threadConfig);
        }
    }
}
