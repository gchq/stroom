package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ReceiptPolicyConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ProxyConfig implements IsProxyConfig {

    private ProxyPathConfig pathConfig = new ProxyPathConfig();
    private ReceiptPolicyConfig receiptPolicyConfig = new ReceiptPolicyConfig();
    private ProxyRepoConfig proxyRepoConfig = new ProxyRepoConfig();
    private ProxyRepoFileScannerConfig proxyRepoFileScannerConfig = new ProxyRepoFileScannerConfig();
    private AggregatorConfig aggregatorConfig = new AggregatorConfig();
    private ForwarderConfig forwarderConfig = new ForwarderConfig();
    private LogStreamConfig logStreamConfig = new LogStreamConfig();
    private String contentDir;
    private ContentSyncConfig contentSyncConfig = new ContentSyncConfig();
    private boolean useDefaultOpenIdCredentials = true;
    private FeedStatusConfig feedStatusConfig = new FeedStatusConfig();
    private RestClientConfig restClientConfig = new RestClientConfig();

    @JsonProperty("path")
    public ProxyPathConfig getProxyPathConfig() {
        return pathConfig;
    }

    @SuppressWarnings("unused")
    public void setProxyPathConfig(final ProxyPathConfig pathConfig) {
        this.pathConfig = pathConfig;
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

    @JsonProperty
    public String getContentDir() {
        return contentDir;
    }

    @JsonProperty
    public void setContentDir(final String contentDir) {
        this.contentDir = contentDir;
    }

    @JsonProperty("contentSync")
    public ContentSyncConfig getContentSyncConfig() {
        return contentSyncConfig;
    }

    @JsonProperty
    public void setContentSyncConfig(final ContentSyncConfig contentSyncConfig) {
        this.contentSyncConfig = contentSyncConfig;
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
}
