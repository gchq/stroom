/*
 * Copyright 2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.http;

import stroom.util.cert.SSLConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.util.shared.time.SimpleDuration;

import io.dropwizard.client.ssl.TlsConfiguration;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class HttpClientUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpClientUtil.class);

    private HttpClientUtil() {
        // Ignore
    }

    /**
     * Builds the configuration a screen should start from when a document has none of its own.
     * <p>
     * The supported ciphers and protocols are read from this JVM rather than hard coded, so what the user is
     * offered is what will actually work here.
     * </p>
     *
     * @param timeout The default to use for the three timeouts. Callers differ widely - a model may think
     *                for minutes where a Git fetch should not - so there is no single sensible value.
     */
    public static HttpClientConfig createDefaultHttpClientConfig(final SimpleDuration timeout) {
        HttpTlsConfig httpTlsConfig = null;
        try (final SSLServerSocket sslServerSocket = ((SSLServerSocket) SSLServerSocketFactory.getDefault()
                .createServerSocket())) {
            final List<String> supportedCiphers = Arrays.stream(sslServerSocket.getEnabledCipherSuites()).toList();
            final List<String> supportedProtocols = Arrays.stream(sslServerSocket.getEnabledProtocols()).toList();
            httpTlsConfig = HttpTlsConfig
                    .builder()
                    .supportedCiphers(supportedCiphers)
                    .supportedProtocols(supportedProtocols)
                    .build();
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }

        return HttpClientConfig
                .builder()
                .timeout(timeout)
                .connectionTimeout(timeout)
                .connectionRequestTimeout(timeout)
                .tlsConfiguration(httpTlsConfig)
                .build();
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

    /**
     * @return True if response is 200 - OK.
     */
    public static boolean isOK(final Response response) {
        Objects.requireNonNull(response);
        return response.getStatus() == Status.OK.getStatusCode();
    }

    /**
     * @return True if response is 404 - Not found.
     */
    public static boolean isNotFound(final Response response) {
        Objects.requireNonNull(response);
        return response.getStatus() == Status.NOT_FOUND.getStatusCode();
    }

    /**
     * @return True if response is 204 - No content.
     */
    public static boolean isNoContent(final Response response) {
        Objects.requireNonNull(response);
        return response.getStatus() == Status.NO_CONTENT.getStatusCode();
    }
}
