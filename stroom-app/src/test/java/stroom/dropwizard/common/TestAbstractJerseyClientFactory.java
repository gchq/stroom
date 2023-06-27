package stroom.dropwizard.common;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.proxy.ProxyConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.util.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAbstractJerseyClientFactory {

    @Test
    void mergeInStroomDefaults() {

        final Duration timeout = Duration.seconds(23);
        final JerseyClientConfiguration vanillaConfig = new JerseyClientConfiguration();
        final JerseyClientConfiguration actualConfig = new JerseyClientConfiguration();
        actualConfig.setTimeout(timeout);
        final JerseyClientConfiguration defaultConfig = AbstractJerseyClientFactory.buildStroomClientDefaultConfig();

        final JerseyClientConfiguration mergedConfig = AbstractJerseyClientFactory.mergeInStroomDefaults(
                vanillaConfig,
                defaultConfig,
                actualConfig);

        assertThat(mergedConfig.getTimeout())
                .isEqualTo(timeout);

        assertThat(vanillaConfig.getConnectionTimeout())
                .isNotEqualTo(defaultConfig.getConnectionTimeout());
        assertThat(mergedConfig.getConnectionTimeout())
                .isEqualTo(defaultConfig.getConnectionTimeout());
        assertThat(mergedConfig.getTlsConfiguration())
                .isNull();
        assertThat(mergedConfig.getProxyConfiguration())
                .isNull();
    }

    @Test
    void mergeInStroomDefaults_tls() {

        final String certAlias = "foo";
        final JerseyClientConfiguration vanillaConfig = new JerseyClientConfiguration();
        final JerseyClientConfiguration actualConfig = new JerseyClientConfiguration();
        final TlsConfiguration tlsConfiguration = new TlsConfiguration();
        tlsConfiguration.setCertAlias(certAlias);
        actualConfig.setTlsConfiguration(tlsConfiguration);
        final JerseyClientConfiguration defaultConfig = AbstractJerseyClientFactory.buildStroomClientDefaultConfig();

        final JerseyClientConfiguration mergedConfig = AbstractJerseyClientFactory.mergeInStroomDefaults(
                vanillaConfig,
                defaultConfig,
                actualConfig);

        assertThat(mergedConfig.getTlsConfiguration())
                .isNotNull()
                .extracting(TlsConfiguration::getCertAlias)
                .isEqualTo(certAlias);
        assertThat(mergedConfig.getTlsConfiguration().getTrustStoreType())
                .isEqualTo(new TlsConfiguration().getTrustStoreType());
    }

    @Test
    void mergeInStroomDefaults_proxy() {
        final String host = "some.host";
        final JerseyClientConfiguration vanillaConfig = new JerseyClientConfiguration();
        final JerseyClientConfiguration actualConfig = new JerseyClientConfiguration();
        final ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
        proxyConfiguration.setHost(host);
        actualConfig.setProxyConfiguration(proxyConfiguration);

        final JerseyClientConfiguration defaultConfig = AbstractJerseyClientFactory.buildStroomClientDefaultConfig();

        final JerseyClientConfiguration mergedConfig = AbstractJerseyClientFactory.mergeInStroomDefaults(
                vanillaConfig,
                defaultConfig,
                actualConfig);

        assertThat(mergedConfig.getProxyConfiguration())
                .isNotNull()
                .extracting(ProxyConfiguration::getHost)
                .isEqualTo(host);
        assertThat(mergedConfig.getProxyConfiguration().getScheme())
                .isEqualTo(new ProxyConfiguration().getScheme());
    }
}
