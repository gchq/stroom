package stroom.proxy.app.guice;

import stroom.proxy.repo.ProxyDbConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.annotation.processing.Generated;

/**
 * IMPORTANT - This whole file is generated using
 * {@link stroom.proxy.app.guice.GenerateProxyConfigProvidersModule}
 * DO NOT edit it directly
 */
@Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
public class ProxyConfigProvidersModule extends AbstractModule {

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.io.PathConfig getPathConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxyPathConfig.class);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.ContentSyncConfig getContentSyncConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ContentSyncConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.ProxyConfig getProxyConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxyConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.ProxyPathConfig getProxyPathConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxyPathConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.ReceiveDataConfig getReceiveDataConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ReceiveDataConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.RestClientConfig getRestClientConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.RestClientConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.forwarder.ThreadConfig getThreadConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.forwarder.ThreadConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.handler.FeedStatusConfig getFeedStatusConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.handler.FeedStatusConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.AggregatorConfig getAggregatorConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.repo.AggregatorConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.LogStreamConfig getLogStreamConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.repo.LogStreamConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.ProxyRepoConfig getProxyRepoConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.repo.ProxyRepoConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    ProxyDbConfig getRepoDbConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                ProxyDbConfig.class);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.forwarder.ForwardHttpPostConfig getForwardHttpPostConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.forwarder.ForwardHttpPostConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.forwarder.ForwardFileConfig getForwardFileConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.forwarder.ForwardFileConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.HttpClientConfig getHttpClientConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.HttpClientConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.HttpClientTlsConfig getHttpClientTlsConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.HttpClientTlsConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.cert.SSLConfig getSSLConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.util.cert.SSLConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

}
