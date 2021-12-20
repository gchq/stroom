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
    stroom.proxy.repo.RepoConfig getRepoConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.repo.ProxyRepoConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.RepoDbConfig getRepoDbConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.repo.ProxyRepoDbConfig.class);
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
    stroom.proxy.app.RestClientConfig getRestClientConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.RestClientConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.app.forwarder.ForwarderConfig getForwarderConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.forwarder.ForwarderConfig.class);
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
    stroom.proxy.app.handler.ReceiptPolicyConfig getReceiptPolicyConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.app.handler.ReceiptPolicyConfig.class);
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
    stroom.proxy.repo.ProxyRepoDbConfig getProxyRepoDbConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.repo.ProxyRepoDbConfig.class);
    }

    @Generated("stroom.proxy.app.guice.GenerateProxyConfigProvidersModule")
    @Provides
    @SuppressWarnings("unused")
    stroom.proxy.repo.ProxyRepoFileScannerConfig getProxyRepoFileScannerConfig(
            final ProxyConfigProvider proxyConfigProvider) {
        return proxyConfigProvider.getConfigObject(
                stroom.proxy.repo.ProxyRepoFileScannerConfig.class);
    }

}
