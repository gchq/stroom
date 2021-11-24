package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.app.forwarder.ThreadConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ReceiptPolicyConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.proxy.repo.RepoDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.validation.ValidationSeverity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;
import javax.validation.constraints.AssertTrue;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ProxyConfig extends AbstractConfig implements IsProxyConfig {

    public static final PropertyPath ROOT_PROPERTY_PATH = PropertyPath.fromParts("proxyConfig");

    public static final String PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE = "haltBootOnConfigValidationFailure";

    private boolean useDefaultOpenIdCredentials = true;
    private boolean haltBootOnConfigValidationFailure = true;
    private String contentDir;

    private ProxyPathConfig pathConfig = new ProxyPathConfig();
    private RepoDbConfig proxyDbConfig = new RepoDbConfig();
    private ReceiptPolicyConfig receiptPolicyConfig = new ReceiptPolicyConfig();
    private ProxyRepoConfig proxyRepoConfig = new ProxyRepoConfig();
    private ProxyRepoFileScannerConfig proxyRepoFileScannerConfig = new ProxyRepoFileScannerConfig();
    private AggregatorConfig aggregatorConfig = new AggregatorConfig();
    private ForwarderConfig forwarderConfig = new ForwarderConfig();
    private LogStreamConfig logStreamConfig = new LogStreamConfig();
    private ContentSyncConfig contentSyncConfig = new ContentSyncConfig();
    private FeedStatusConfig feedStatusConfig = new FeedStatusConfig();
    private RestClientConfig restClientConfig = new RestClientConfig();
    private ThreadConfig threadConfig = new ThreadConfig();

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

    @SuppressWarnings("unused")
    public void setHaltBootOnConfigValidationFailure(final boolean haltBootOnConfigValidationFailure) {
        this.haltBootOnConfigValidationFailure = haltBootOnConfigValidationFailure;
    }

    @JsonProperty()
    @JsonPropertyDescription("If true, stroom will use a set of default authentication credentials to allow" +
            "API calls from stroom-proxy. For test or demonstration purposes only, set to false for production. " +
            "If API keys are set elsewhere in config then they will override this setting.")
    public boolean isUseDefaultOpenIdCredentials() {
        return useDefaultOpenIdCredentials;
    }

    public void setUseDefaultOpenIdCredentials(final boolean useDefaultOpenIdCredentials) {
        this.useDefaultOpenIdCredentials = useDefaultOpenIdCredentials;
    }

    @JsonProperty
    public String getContentDir() {
        return contentDir;
    }

    @JsonProperty
    public void setContentDir(final String contentDir) {
        this.contentDir = contentDir;
    }

    @JsonProperty("path")
    public ProxyPathConfig getPathConfig() {
        return pathConfig;
    }

    public void setPathConfig(final ProxyPathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    @JsonProperty("db")
    public RepoDbConfig getProxyDbConfig() {
        return proxyDbConfig;
    }

    public void setProxyDbConfig(final RepoDbConfig proxyDbConfig) {
        this.proxyDbConfig = proxyDbConfig;
    }

    @JsonProperty("receiptPolicy")
    public ReceiptPolicyConfig getReceiptPolicyConfig() {
        return receiptPolicyConfig;
    }

    public void setReceiptPolicyConfig(final ReceiptPolicyConfig receiptPolicyConfig) {
        this.receiptPolicyConfig = receiptPolicyConfig;
    }

    @JsonProperty("repository")
    public ProxyRepoConfig getProxyRepositoryConfig() {
        return proxyRepoConfig;
    }

    @JsonProperty
    public void setProxyRepositoryConfig(final ProxyRepoConfig proxyRepoConfig) {
        this.proxyRepoConfig = proxyRepoConfig;
    }

    @JsonProperty("scanner")
    public ProxyRepoFileScannerConfig getProxyRepoFileScannerConfig() {
        return proxyRepoFileScannerConfig;
    }

    public void setProxyRepoFileScannerConfig(final ProxyRepoFileScannerConfig proxyRepoFileScannerConfig) {
        this.proxyRepoFileScannerConfig = proxyRepoFileScannerConfig;
    }

    @JsonProperty("aggregator")
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    public void setAggregatorConfig(final AggregatorConfig aggregatorConfig) {
        this.aggregatorConfig = aggregatorConfig;
    }

    @JsonProperty("forwarder")
    public ForwarderConfig getForwarderConfig() {
        return forwarderConfig;
    }

    @JsonProperty
    public void setForwarderConfig(final ForwarderConfig forwarderConfig) {
        this.forwarderConfig = forwarderConfig;
    }

    @JsonProperty("logStream")
    public LogStreamConfig getLogStreamConfig() {
        return logStreamConfig;
    }

    @JsonProperty
    public void setLogStreamConfig(final LogStreamConfig logStreamConfig) {
        this.logStreamConfig = logStreamConfig;
    }

    @JsonProperty("contentSync")
    public ContentSyncConfig getContentSyncConfig() {
        return contentSyncConfig;
    }

    @JsonProperty
    public void setContentSyncConfig(final ContentSyncConfig contentSyncConfig) {
        this.contentSyncConfig = contentSyncConfig;
    }

    @JsonProperty("feedStatus")
    public FeedStatusConfig getFeedStatusConfig() {
        return feedStatusConfig;
    }

    @JsonProperty("feedStatus")
    public void setFeedStatusConfig(final FeedStatusConfig feedStatusConfig) {
        this.feedStatusConfig = feedStatusConfig;
    }

    @JsonProperty("restClient")
    public RestClientConfig getRestClientConfig() {
        return restClientConfig;
    }

    @JsonProperty("restClient")
    public void setRestClientConfig(final RestClientConfig restClientConfig) {
        this.restClientConfig = restClientConfig;
    }

    @JsonProperty("threads")
    public ThreadConfig getThreadConfig() {
        return threadConfig;
    }

    @JsonProperty("threads")
    public void setThreadConfig(final ThreadConfig threadConfig) {
        this.threadConfig = threadConfig;
    }
}
