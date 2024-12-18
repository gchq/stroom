package stroom.util.http;

import stroom.util.cert.SSLConfig;

import io.dropwizard.client.ssl.TlsConfiguration;

import java.nio.file.Paths;

public class HttpClientUtil {

    private HttpClientUtil() {
        // Ignore
    }

    public static TlsConfiguration getTlsConfiguration(final SSLConfig sslConfig) {
        if (sslConfig == null) {
            return null;
        }

        final TlsConfiguration tlsConfiguration = new TlsConfiguration();
        tlsConfiguration.setProtocol(sslConfig.getSslProtocol());
//            tlsConfiguration.setProvider(sslConfig.getSslProtocol());
        tlsConfiguration.setKeyStorePath(Paths.get(sslConfig.getKeyStorePath()).toFile());
        tlsConfiguration.setKeyStorePassword(sslConfig.getKeyStorePassword());
        tlsConfiguration.setKeyStoreType(sslConfig.getKeyStoreType());
//            tlsConfiguration.setKeyStoreProvider(sslConfig.getSslProtocol());
        tlsConfiguration.setTrustStorePath(Paths.get(sslConfig.getTrustStorePath()).toFile());
        tlsConfiguration.setTrustStorePassword(sslConfig.getTrustStorePassword());
        tlsConfiguration.setTrustStoreType(sslConfig.getTrustStoreType());
//            tlsConfiguration.setTrustStoreProvider(sslConfig.getSslProtocol());
//            tlsConfiguration.setTrustSelfSignedCertificates(sslConfig.getSslProtocol());
        tlsConfiguration.setVerifyHostname(sslConfig.isHostnameVerificationEnabled());
//            tlsConfiguration.setSupportedProtocols();Protocol(sslConfig.getSslProtocol());
//            tlsConfiguration.setSupportedCiphers();Protocol(sslConfig.getSslProtocol());
//            tlsConfiguration.setCertAlias(sslConfig.get);SupportedCiphers();Protocol(sslConfig.getSslProtocol());

        return tlsConfiguration;
    }

    public static HttpTlsConfiguration getHttpTlsConfiguration(final SSLConfig sslConfig) {
        if (sslConfig == null) {
            return null;
        }

        return HttpTlsConfiguration
                .builder()
                .protocol(sslConfig.getSslProtocol())
                .keyStorePath(sslConfig.getKeyStorePath())
                .keyStorePassword(sslConfig.getKeyStorePassword())
                .keyStoreType(sslConfig.getKeyStoreType())
                .trustStorePath(sslConfig.getTrustStorePath())
                .trustStorePassword(sslConfig.getTrustStorePassword())
                .trustStoreType(sslConfig.getTrustStoreType())
                .verifyHostname(sslConfig.isHostnameVerificationEnabled())
                .build();
    }
}
