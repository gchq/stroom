package stroom.proxy.guice;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.proxy.handler.ForwardRequestConfig;
import stroom.proxy.handler.LogRequestConfig;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;

public class ProxyConfig {
    private ForwardRequestConfig forwardRequestConfig;
    private ProxyRepositoryConfig proxyRepositoryConfig;
    private ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;
    private LogRequestConfig logRequestConfig;

    @JsonProperty
    public ForwardRequestConfig getForwardRequestConfig() {
        return forwardRequestConfig;
    }

    @JsonProperty
    public void setForwardRequestConfig(final ForwardRequestConfig forwardRequestConfig) {
        this.forwardRequestConfig = forwardRequestConfig;
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
    public LogRequestConfig getLogRequestConfig() {
        return logRequestConfig;
    }

    @JsonProperty
    public void setLogRequestConfig(final LogRequestConfig logRequestConfig) {
        this.logRequestConfig = logRequestConfig;
    }
}
