package stroom.proxy.app.guice;

import com.google.inject.AbstractModule;
import stroom.proxy.app.handler.ForwardStreamConfig;
import stroom.proxy.app.handler.LogStreamConfig;
import stroom.proxy.app.handler.ProxyRequestConfig;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;

public class ProxyConfigModule extends AbstractModule {
    private final ProxyConfig proxyConfig;

    public ProxyConfigModule(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    protected void configure() {
        bind(ProxyRequestConfig.class).toProvider(proxyConfig::getProxyRequestConfig);
        bind(LogStreamConfig.class).toProvider(proxyConfig::getLogStreamConfig);
        bind(ForwardStreamConfig.class).toProvider(proxyConfig::getForwardStreamConfig);
        bind(ProxyRepositoryConfig.class).toProvider(proxyConfig::getProxyRepositoryConfig);
        bind(ProxyRepositoryReaderConfig.class).toProvider(proxyConfig::getProxyRepositoryReaderConfig);
        bind(ContentSyncConfig.class).toProvider(proxyConfig::getContentSyncConfig);
    }
}
