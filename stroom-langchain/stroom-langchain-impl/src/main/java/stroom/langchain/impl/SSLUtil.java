package stroom.langchain.impl;

import stroom.util.cert.SSLConfig;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SSLUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SSLUtil.class);
    public static final HostnameVerifier PERMISSIVE_HOSTNAME_VERIFIER = (s, sslSession) -> true;

    private SSLUtil() {
    }

    public static SSLSocketFactory createSslSocketFactory(
            final SSLConfig sslConfig,
            final PathCreator pathCreator) {
        Objects.requireNonNull(sslConfig);

        final KeyManager[] keyManagers = createKeyManagers(sslConfig, pathCreator);
        final TrustManager[] trustManagers = createTrustManagers(sslConfig, pathCreator);

        final SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance(sslConfig.getSslProtocol());
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Error initialising ssl context", e);
        }

        LOGGER.info(() -> "Creating ssl socket factory");
        return sslContext.getSocketFactory();
    }

    public static KeyManager[] createKeyManagers(
            final SSLConfig sslConfig,
            final PathCreator pathCreator) {

        Objects.requireNonNull(sslConfig);

        KeyStore keyStore = null;
        final KeyManagerFactory keyManagerFactory;

        // Load the keystore
        if (sslConfig.getKeyStorePath() != null) {
            final Path keyStorePath = pathCreator.toAppPath(sslConfig.getKeyStorePath());

            try (final InputStream inputStream = new BufferedInputStream(
                    new FileInputStream(keyStorePath.toFile()))) {

                keyStore = KeyStore.getInstance(sslConfig.getKeyStoreType());
                LOGGER.info(() ->
                        "Loading keystore " + sslConfig.getKeyStorePath() + " of type " + sslConfig.getKeyStoreType());
                keyStore.load(inputStream, sslConfig.getKeyStorePassword().toCharArray());
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
                throw new RuntimeException(LogUtil.message("Error locating and loading keystore {} with type {}: {}",
                        sslConfig.getKeyStorePath(), sslConfig.getKeyStoreType(), e.getMessage()), e);
            }
        }

        try {
            keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            if (keyStore != null) {
                keyManagerFactory.init(keyStore, sslConfig.getKeyStorePassword().toCharArray());
            }
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e) {
            throw new RuntimeException(LogUtil.message("Error initialising KeyManagerFactory for keystore {}: {}",
                    sslConfig.getKeyStorePath(), e.getMessage()), e);
        }

        return keyManagerFactory.getKeyManagers();
    }

    public static TrustManager[] createTrustManagers(
            final SSLConfig sslConfig,
            final PathCreator pathCreator) {
        Objects.requireNonNull(sslConfig);

        KeyStore trustStore = null;
        final TrustManagerFactory trustManagerFactory;

        // Load the truststore
        if (sslConfig.getTrustStorePath() != null) {
            final Path trustStorePath = pathCreator.toAppPath(sslConfig.getTrustStorePath());

            try (final InputStream inputStream = new BufferedInputStream(
                    new FileInputStream(trustStorePath.toFile()))) {
                trustStore = KeyStore.getInstance(sslConfig.getTrustStoreType());
                LOGGER.info(() ->
                        "Loading truststore " + sslConfig.getTrustStorePath() +
                        " of type " + sslConfig.getTrustStoreType());
                trustStore.load(inputStream, sslConfig.getTrustStorePassword().toCharArray());
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
                throw new RuntimeException(LogUtil.message("Error locating and loading truststore {} with type {}: {}",
                        sslConfig.getTrustStorePath(), sslConfig.getTrustStoreType(), e.getMessage()), e);
            }
        }

        try {
            trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            if (trustStore != null) {
                trustManagerFactory.init(trustStore);
            }
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException(LogUtil.message("Error initialising TrustManagerFactory for truststore {}: {}",
                    sslConfig.getTrustStorePath(), e.getMessage()), e);
        }

        return trustManagerFactory.getTrustManagers();
    }

    public static void applySSLConfiguration(final HttpURLConnection connection,
                                             final SSLSocketFactory sslSocketFactory,
                                             final SSLConfig sslConfig) {
        if (connection instanceof final HttpsURLConnection httpsConnection) {
            LOGGER.debug(() -> "Connection for " + connection.getURL() + " is HTTPS");

            if (sslSocketFactory == null) {
                throw new RuntimeException(LogUtil.message(
                        "Missing SSLSocketFactory for forward url {}. Is the SSL config missing?",
                        connection.getURL()));
            }

            LOGGER.debug(() -> "Setting custom ssl socket factory");
            httpsConnection.setSSLSocketFactory(sslSocketFactory);

            if (sslConfig != null &&
                !sslConfig.isHostnameVerificationEnabled()) {
                disableHostnameVerification(httpsConnection);
            }
        }
    }

    public static void disableHostnameVerification(final HttpsURLConnection httpsConnection) {
        LOGGER.debug(() -> "Disabling hostname verification");
        httpsConnection.setHostnameVerifier(PERMISSIVE_HOSTNAME_VERIFIER);
    }

    public static Optional<String> checkUrlHealth(final String urlStr,
                                                  final SSLSocketFactory sslSocketFactory,
                                                  final SSLConfig sslConfig,
                                                  final String expectedAllowedMethod) {
        try {
            // send an HTTP OPTIONS request just to check the url can be reached
            final URL url = new URL(urlStr);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("OPTIONS");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5_000);

            SSLUtil.applySSLConfiguration(connection, sslSocketFactory, sslConfig);

            try {
                LOGGER.debug(() -> "Sending health check OPTIONS request to " + urlStr);
                connection.connect();

                final int responseCode = connection.getResponseCode();
                final String responseMessage = connection.getResponseMessage();

                LOGGER.debug(() -> "Received response " + responseMessage);
                if (responseCode != 200) {
                    return Optional.of(Integer.toString(responseCode));
                } else {
                    final String allowedMethods = connection.getHeaderField("Allow");
                    if (!allowedMethods.contains(expectedAllowedMethod)) {
                        return Optional.of("Unhealthy: POST method not supported");
                    }
                    return Optional.empty();
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            return Optional.of("Unhealthy: Error sending request - " + e.getMessage());
        }
    }
}
