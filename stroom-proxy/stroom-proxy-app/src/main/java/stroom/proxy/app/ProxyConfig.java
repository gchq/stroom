package stroom.proxy.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.proxy.app.ContentSyncConfig;
import stroom.proxy.app.handler.ForwardStreamConfig;
import stroom.proxy.app.handler.LogStreamConfig;
import stroom.proxy.app.handler.ProxyRequestConfig;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProxyConfig {
    private String proxyContentDir;
    private ProxyRequestConfig proxyRequestConfig;
    private ForwardStreamConfig forwardStreamConfig;
    private ProxyRepositoryConfig proxyRepositoryConfig;
    private ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;
    private LogStreamConfig logStreamConfig;
    private ContentSyncConfig contentSyncConfig;

    @Inject
    public ProxyConfig(final String proxyContentDir,
                       final ProxyRequestConfig proxyRequestConfig,
                       final ForwardStreamConfig forwardStreamConfig,
                       final ProxyRepositoryConfig proxyRepositoryConfig,
                       final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig,
                       final LogStreamConfig logStreamConfig,
                       final ContentSyncConfig contentSyncConfig) {
        this.proxyContentDir = proxyContentDir;
        this.proxyRequestConfig = proxyRequestConfig;
        this.forwardStreamConfig = forwardStreamConfig;
        this.proxyRepositoryConfig = proxyRepositoryConfig;
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
        this.logStreamConfig = logStreamConfig;
        this.contentSyncConfig = contentSyncConfig;
    }

    public ProxyConfig() {
        this.proxyRequestConfig = new ProxyRequestConfig();
        this.forwardStreamConfig = new ForwardStreamConfig();
        this.proxyRepositoryConfig = new ProxyRepositoryConfig();
        this.proxyRepositoryReaderConfig = new ProxyRepositoryReaderConfig();
        this.logStreamConfig = new LogStreamConfig();
        this.contentSyncConfig = new ContentSyncConfig();
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

    @JsonProperty
    public ContentSyncConfig getContentSyncConfig() {
        return contentSyncConfig;
    }

    @JsonProperty
    public void setContentSyncConfig(final ContentSyncConfig contentSyncConfig) {
        this.contentSyncConfig = contentSyncConfig;
    }

    @JsonProperty
    public String getProxyContentDir() {
        return proxyContentDir;
    }

    @JsonProperty
    public void setProxyContentDir(final String proxyContentDir) {
        this.proxyContentDir = proxyContentDir;
    }
}
