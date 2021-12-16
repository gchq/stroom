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

    // Special case to allow ProxyPathConfig to be injected as itself or as
    // PathConfig
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
    stroom.proxy.app.RestClientConfig getRestClientConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.RestClientConfig.class);
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
    stroom.proxy.app.handler.ForwardStreamConfig getForwardStreamConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.handler.ForwardStreamConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.handler.LogStreamConfig getLogStreamConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.handler.LogStreamConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.handler.ProxyRequestConfig getProxyRequestConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.handler.ProxyRequestConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.ProxyRepositoryConfig getProxyRepositoryConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.repo.ProxyRepositoryConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.ProxyRepositoryReaderConfig getProxyRepositoryReaderConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.repo.ProxyRepositoryReaderConfig.class);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.handler.ForwardDestinationConfig getForwardDestinationConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.handler.ForwardDestinationConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.HttpClientConfig getHttpClientConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.HttpClientConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.HttpClientTlsConfig getHttpClientTlsConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.proxy.app.HttpClientTlsConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.util.cert.SSLConfig getSSLConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        throw new UnsupportedOperationException(
                "stroom.util.cert.SSLConfig cannot be injected directly. "
                        + "Inject a config class that uses it or one of its sub-class instead.");
    }

}
