package stroom.proxy.app.guice;

import stroom.proxy.app.ContentSyncConfig;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.LogStreamConfig;
import stroom.proxy.app.handler.ProxyRequestConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;

import com.google.inject.AbstractModule;
import io.dropwizard.client.JerseyClientConfiguration;

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
        bind(ProxyRepoConfig.class).toInstance(proxyConfig.getProxyRepositoryConfig());
        bind(ProxyRepoFileScannerConfig.class).toInstance(proxyConfig.getProxyRepoFileScannerConfig());
        bind(AggregatorConfig.class).toInstance(proxyConfig.getAggregatorConfig());
        bind(ForwarderConfig.class).toInstance(proxyConfig.getForwardStreamConfig());
        bind(LogStreamConfig.class).toInstance(proxyConfig.getLogStreamConfig());
        bind(ContentSyncConfig.class).toInstance(proxyConfig.getContentSyncConfig());
        bind(FeedStatusConfig.class).toInstance(proxyConfig.getFeedStatusConfig());
        bind(JerseyClientConfiguration.class).toInstance(proxyConfig.getJerseyClientConfiguration());
    }
}
