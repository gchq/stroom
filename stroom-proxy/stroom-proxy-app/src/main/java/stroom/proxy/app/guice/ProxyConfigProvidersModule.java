package stroom.proxy.app.guice;

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

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.DownstreamHostConfig getDownstreamHostConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.DownstreamHostConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.DirScannerConfig getDirScannerConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.DirScannerConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.ProxyAuthenticationConfig getProxyAuthenticationConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxyAuthenticationConfig.class);
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
    stroom.proxy.app.ProxyOpenIdConfig getProxyOpenIdConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxyOpenIdConfig.class);
    }

    // Binding ProxyOpenIdConfig to additional interface AbstractOpenIdConfig
    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.security.openid.api.AbstractOpenIdConfig getAbstractOpenIdConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxyOpenIdConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.ProxyPathConfig getProxyPathConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxyPathConfig.class);
    }

    // Binding ProxyPathConfig to additional interface PathConfig
    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.io.PathConfig getPathConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxyPathConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.ProxyReceiptPolicyConfig getProxyReceiptPolicyConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxyReceiptPolicyConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.ProxySecurityConfig getProxySecurityConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.ProxySecurityConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.event.EventStoreConfig getEventStoreConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.event.EventStoreConfig.class);
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
    stroom.proxy.app.handler.ThreadConfig getThreadConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.handler.ThreadConfig.class);
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
    stroom.receive.common.ReceiveDataConfig getReceiveDataConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.receive.common.ReceiveDataConfig.class);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.handler.ForwardFileConfig getForwardFileConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.handler.ForwardFileConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.handler.ForwardHttpPostConfig getForwardHttpPostConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.handler.ForwardHttpPostConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.handler.ForwardQueueConfig getForwardQueueConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.handler.ForwardQueueConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.handler.PathTemplateConfig getPathTemplateConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.handler.PathTemplateConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.cache.CacheConfig getCacheConfigButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.util.cache.CacheConfig cannot be injected directly. "
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

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.http.HttpAuthConfiguration getHttpAuthConfigurationButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.util.http.HttpAuthConfiguration cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.http.HttpClientConfiguration getHttpClientConfigurationButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.util.http.HttpClientConfiguration cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.http.HttpProxyConfiguration getHttpProxyConfigurationButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.util.http.HttpProxyConfiguration cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.http.HttpTlsConfiguration getHttpTlsConfigurationButThrow(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.util.http.HttpTlsConfiguration cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

}
