package stroom.proxy.guice;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.content.ContentSyncConfig;
import stroom.proxy.handler.ForwardStreamConfig;
import stroom.proxy.handler.LogStreamConfig;
import stroom.proxy.handler.ProxyRequestConfig;
import stroom.proxy.repo.ProxyRepositoryConfigImpl;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;

public class ProxyConfig {
    private ProxyRequestConfig proxyRequestConfig;
    private ForwardStreamConfig forwardStreamConfig;
    private ProxyRepositoryConfigImpl proxyRepositoryConfig;
    private ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;
    private LogStreamConfig logStreamConfig;
    private ContentSyncConfig rulesetContentSyncConfig;
    private ContentSyncConfig dictionaryContentSyncConfig;

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
    public ContentSyncConfig getRulesetContentSyncConfig() {
        return rulesetContentSyncConfig;
    }

    @JsonProperty
    public void setRulesetContentSyncConfig(final ContentSyncConfig rulesetContentSyncConfig) {
        this.rulesetContentSyncConfig = rulesetContentSyncConfig;
    }

    @JsonProperty
    public ContentSyncConfig getDictionaryContentSyncConfig() {
        return dictionaryContentSyncConfig;
    }

    @JsonProperty
    public void setDictionaryContentSyncConfig(final ContentSyncConfig dictionaryContentSyncConfig) {
        this.dictionaryContentSyncConfig = dictionaryContentSyncConfig;
    }
}
