package stroom.proxy.guice;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.client.JerseyClientConfiguration;
import stroom.content.ContentSyncConfig;
import stroom.proxy.handler.FeedStatusConfig;
import stroom.proxy.handler.ForwardStreamConfig;
import stroom.proxy.handler.LogStreamConfig;
import stroom.proxy.handler.ProxyRequestConfig;
import stroom.proxy.repo.ProxyRepositoryConfigImpl;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;

import javax.validation.Valid;

public class ProxyConfig {
    private String proxyContentDir;
    private ProxyRequestConfig proxyRequestConfig = new ProxyRequestConfig();
    private ForwardStreamConfig forwardStreamConfig = new ForwardStreamConfig();
    private ProxyRepositoryConfigImpl proxyRepositoryConfig = new ProxyRepositoryConfigImpl();
    private ProxyRepositoryReaderConfig proxyRepositoryReaderConfig = new ProxyRepositoryReaderConfig();
    private LogStreamConfig logStreamConfig = new LogStreamConfig();
    private ContentSyncConfig contentSyncConfig = new ContentSyncConfig();
    private FeedStatusConfig feedStatusConfig = new FeedStatusConfig();

    @Valid
    private JerseyClientConfiguration jerseyClientConfig = new JerseyClientConfiguration();

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
    public ProxyRepositoryConfigImpl getProxyRepositoryConfig() {
        return proxyRepositoryConfig;
    }

    @JsonProperty
    public void setProxyRepositoryConfig(final ProxyRepositoryConfigImpl proxyRepositoryConfig) {
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

    @JsonProperty("jerseyClient")
    public JerseyClientConfiguration getJerseyClientConfiguration() {
        return jerseyClientConfig;
    }

    @JsonProperty("jerseyClient")
    public void setJerseyClientConfiguration(final JerseyClientConfiguration jerseyClientConfig) {
        this.jerseyClientConfig = jerseyClientConfig;
    }
}
