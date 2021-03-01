package stroom.proxy.app;

import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ProxyRequestConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.dropwizard.client.JerseyClientConfiguration;

public class ProxyConfig {

    private String proxyContentDir;
    private boolean useDefaultOpenIdCredentials = true;

    private ProxyRequestConfig proxyRequestConfig = new ProxyRequestConfig();
    private ProxyRepoConfig proxyRepoConfig = new ProxyRepoConfig();
    private ProxyRepoFileScannerConfig proxyRepoFileScannerConfig = new ProxyRepoFileScannerConfig();
    private AggregatorConfig aggregatorConfig = new AggregatorConfig();
    private ForwarderConfig forwarderConfig = new ForwarderConfig();
    private LogStreamConfig logStreamConfig = new LogStreamConfig();
    private ContentSyncConfig contentSyncConfig = new ContentSyncConfig();
    private FeedStatusConfig feedStatusConfig = new FeedStatusConfig();
    private JerseyClientConfiguration jerseyClientConfig = new JerseyClientConfiguration();


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

    @JsonProperty("request")
    public ProxyRequestConfig getProxyRequestConfig() {
        return proxyRequestConfig;
    }

    @JsonProperty
    public void setProxyRequestConfig(final ProxyRequestConfig proxyRequestConfig) {
        this.proxyRequestConfig = proxyRequestConfig;
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
    public ForwarderConfig getForwardStreamConfig() {
        return forwarderConfig;
    }

    @JsonProperty
    public void setForwardStreamConfig(final ForwarderConfig forwarderConfig) {
        this.forwarderConfig = forwarderConfig;
    }

    @JsonProperty
    public LogStreamConfig getLogStreamConfig() {
        return logStreamConfig;
    }

    @JsonProperty
    public void setLogStreamConfig(final LogStreamConfig logStreamConfig) {
        this.logStreamConfig = logStreamConfig;
    }

    @JsonProperty
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

    @JsonProperty
    public String getProxyContentDir() {
        return proxyContentDir;
    }

    @JsonProperty
    public void setProxyContentDir(final String proxyContentDir) {
        this.proxyContentDir = proxyContentDir;
    }

    @JsonProperty("jerseyClient")
    public JerseyClientConfiguration getJerseyClientConfiguration() {
        return jerseyClientConfig;
    }

    @JsonProperty("jerseyClient")
    public void setJerseyClientConfiguration(final JerseyClientConfiguration jerseyClientConfig) {
        this.jerseyClientConfig = jerseyClientConfig;
    }
}
