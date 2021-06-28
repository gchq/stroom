package stroom.proxy.app;

import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.handler.ForwardStreamConfig;
import stroom.proxy.app.handler.LogStreamConfig;
import stroom.proxy.app.handler.ProxyRequestConfig;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ProxyConfig implements IsProxyConfig {

    private String proxyContentDir = "content";
    private boolean useDefaultOpenIdCredentials = true;

    private ContentSyncConfig contentSyncConfig = new ContentSyncConfig();
    private FeedStatusConfig feedStatusConfig = new FeedStatusConfig();
    private ForwardStreamConfig forwardStreamConfig = new ForwardStreamConfig();
    private RestClientConfig restClientConfig = new RestClientConfig();
    private LogStreamConfig logStreamConfig = new LogStreamConfig();
    private ProxyPathConfig pathConfig = new ProxyPathConfig();
    private ProxyRepositoryConfig proxyRepositoryConfig = new ProxyRepositoryConfig();
    private ProxyRepositoryReaderConfig proxyRepositoryReaderConfig = new ProxyRepositoryReaderConfig();
    private ProxyRequestConfig proxyRequestConfig = new ProxyRequestConfig();

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
    public ProxyRequestConfig getProxyRequestConfig() {
        return proxyRequestConfig;
    }

    @JsonProperty
    public void setProxyRequestConfig(final ProxyRequestConfig proxyRequestConfig) {
        this.proxyRequestConfig = proxyRequestConfig;
    }

    @JsonProperty
    public ForwardStreamConfig getForwardStreamConfig() {
        return forwardStreamConfig;
    }

    @JsonProperty
    public void setForwardStreamConfig(final ForwardStreamConfig forwardStreamConfig) {
        this.forwardStreamConfig = forwardStreamConfig;
    }

    @JsonProperty
    public ProxyRepositoryConfig getProxyRepositoryConfig() {
        return proxyRepositoryConfig;
    }

    @JsonProperty
    public void setProxyRepositoryConfig(final ProxyRepositoryConfig proxyRepositoryConfig) {
        this.proxyRepositoryConfig = proxyRepositoryConfig;
    }

    @JsonProperty
    public ProxyRepositoryReaderConfig getProxyRepositoryReaderConfig() {
        return proxyRepositoryReaderConfig;
    }

    @JsonProperty
    public void setProxyRepositoryReaderConfig(final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig) {
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
    }

    @JsonProperty
    public LogStreamConfig getLogStreamConfig() {
        return logStreamConfig;
    }

    @JsonProperty
    public void setLogStreamConfig(final LogStreamConfig logStreamConfig) {
        this.logStreamConfig = logStreamConfig;
    }

    @JsonProperty("path")
    public ProxyPathConfig getProxyPathConfig() {
        return pathConfig;
    }

    @SuppressWarnings("unused")
    public void setProxyPathConfig(final ProxyPathConfig pathConfig) {
        this.pathConfig = pathConfig;
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

    @JsonProperty("restClient")
    public RestClientConfig getRestClientConfig() {
        return restClientConfig;
    }

    @JsonProperty("restClient")
    public void setRestClientConfig(final RestClientConfig restClientConfig) {
        this.restClientConfig = restClientConfig;
    }
}
