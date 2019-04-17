package stroom.proxy.app.guice;

import com.google.inject.AbstractModule;
import stroom.proxy.app.ContentSyncConfig;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
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
        // Bind the application config.        
        bind(ProxyConfig.class).toInstance(proxyConfig);

        // AppConfig will instantiate all of its child config objects so
        // bind each of these instances so we can inject these objects on their own
        bind(ProxyRequestConfig.class).toInstance(proxyConfig.getProxyRequestConfig());
        bind(LogStreamConfig.class).toInstance(proxyConfig.getLogStreamConfig());
        bind(ForwardStreamConfig.class).toInstance(proxyConfig.getForwardStreamConfig());
        bind(ProxyRepositoryConfig.class).toInstance(proxyConfig.getProxyRepositoryConfig());
        bind(ProxyRepositoryReaderConfig.class).toInstance(proxyConfig.getProxyRepositoryReaderConfig());
        bind(ContentSyncConfig.class).toInstance(proxyConfig.getContentSyncConfig());
        bind(FeedStatusConfig.class).toInstance(proxyConfig.getFeedStatusConfig());
    }
}
