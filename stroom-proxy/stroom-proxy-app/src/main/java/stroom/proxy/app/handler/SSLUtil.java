package stroom.proxy.app.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LogUtil;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

public class SSLUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLUtil.class);
    private static final HostnameVerifier PERMISSIVE_HOSTNAME_VERIFIER = (s, sslSession) -> true;

    private SSLUtil() {
    }

    public static void applySSLConfiguration(final HttpURLConnection connection,
                                             final SSLSocketFactory sslSocketFactory,
                                             final SSLConfig sslConfig) {
        if (connection instanceof HttpsURLConnection) {
            LOGGER.debug("Connection for {} is HTTPS", connection.getURL());

            final HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;

            if (sslSocketFactory == null) {
                throw new RuntimeException(LogUtil.message(
                        "Missing SSLSocketFactory for forward url {}. Is the SSL config missing?",
                        connection.getURL()));
            }

            LOGGER.debug("Setting custom ssl socket factory");
            httpsConnection.setSSLSocketFactory(sslSocketFactory);

            if (sslConfig != null &&
                    !sslConfig.isHostnameVerificationEnabled()) {
                LOGGER.debug("Disabling hostname verification");
                httpsConnection.setHostnameVerifier(PERMISSIVE_HOSTNAME_VERIFIER);
            }
        }
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
                LOGGER.debug("Sending health check OPTIONS request to {}", urlStr);
                connection.connect();

                final int responseCode = connection.getResponseCode();
                final String responseMessage = connection.getResponseMessage();

                LOGGER.debug("Received response {}", responseMessage);
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
