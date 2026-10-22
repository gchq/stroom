/*
 * Copyright 2026 Crown Copyright
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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import javax.net.ssl.SSLContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestHttpAsyncClientUtil {

    private static final String PASSWORD = "changeit";

    @TempDir
    Path tempDir;

    @Test
    void testCreateSslContext_defaults() {
        final SSLContext sslContext = HttpAsyncClientUtil.createSslContext(HttpTlsConfiguration.builder().build());

        assertThat(sslContext.getProtocol())
                .isEqualTo("TLSv1.2");
    }

    @Test
    void testCreateSslContext_withKeyAndTrustStores() throws Exception {
        final Path keyStore = createEmptyPkcs12("key.p12");
        final Path trustStore = createEmptyPkcs12("trust.p12");

        final HttpTlsConfiguration tlsConfiguration = HttpTlsConfiguration.builder()
                .protocol("TLSv1.3")
                .keyStorePath(keyStore.toString())
                .keyStorePassword(PASSWORD)
                .keyStoreType("PKCS12")
                .trustStorePath(trustStore.toString())
                .trustStorePassword(PASSWORD)
                .trustStoreType("PKCS12")
                .trustSelfSignedCertificates(true)
                .build();

        final SSLContext sslContext = HttpAsyncClientUtil.createSslContext(tlsConfiguration);

        assertThat(sslContext.getProtocol())
                .isEqualTo("TLSv1.3");
    }

    @Test
    void testCreateSslContext_wrongTrustStorePassword() throws Exception {
        final Path trustStore = createEmptyPkcs12("trust.p12");

        final HttpTlsConfiguration tlsConfiguration = HttpTlsConfiguration.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword("wrong")
                .trustStoreType("PKCS12")
                .build();

        assertThatThrownBy(() -> HttpAsyncClientUtil.createSslContext(tlsConfiguration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Unable to initialise SSL context");
    }

    @Test
    void testCreateSslContext_missingKeyStore() {
        final HttpTlsConfiguration tlsConfiguration = HttpTlsConfiguration.builder()
                .keyStorePath(tempDir.resolve("missing.p12").toString())
                .keyStorePassword(PASSWORD)
                .keyStoreType("PKCS12")
                .build();

        assertThatThrownBy(() -> HttpAsyncClientUtil.createSslContext(tlsConfiguration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing.p12");
    }

    @Test
    void testCreateSslContext_unknownKeyStoreType() throws Exception {
        final Path keyStore = createEmptyPkcs12("key.p12");

        final HttpTlsConfiguration tlsConfiguration = HttpTlsConfiguration.builder()
                .keyStorePath(keyStore.toString())
                .keyStorePassword(PASSWORD)
                .keyStoreType("NOT_A_TYPE")
                .build();

        assertThatThrownBy(() -> HttpAsyncClientUtil.createSslContext(tlsConfiguration))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testCreateSslContext_null() {
        assertThatThrownBy(() -> HttpAsyncClientUtil.createSslContext(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCreateTlsStrategy() {
        final HttpTlsConfiguration tlsConfiguration = HttpTlsConfiguration.builder()
                .verifyHostname(false)
                .supportedProtocols(List.of("TLSv1.2", "TLSv1.3"))
                .supportedCiphers(List.of("TLS_AES_128_GCM_SHA256"))
                .build();

        assertThat(HttpAsyncClientUtil.createTlsStrategy(tlsConfiguration))
                .isNotNull();
    }

    @Test
    void testCreateDefaultTlsStrategy() {
        assertThat(HttpAsyncClientUtil.createDefaultTlsStrategy())
                .isNotNull();
    }

    @Test
    void testCreateRoutePlanner_noProxy() {
        assertThat(HttpAsyncClientUtil.createRoutePlanner(null))
                .isNull();
        assertThat(HttpAsyncClientUtil.createRoutePlanner(HttpProxyConfiguration.builder().host(" ").build()))
                .isNull();
    }

    @Test
    void testCreateRoutePlanner_withProxy() throws HttpException {
        final HttpProxyConfiguration proxyConfiguration = HttpProxyConfiguration.builder()
                .host("proxy.example.com")
                .port(8080)
                .scheme("http")
                .nonProxyHosts(List.of("direct.example.com"))
                .build();

        final HttpRoutePlanner routePlanner = HttpAsyncClientUtil.createRoutePlanner(proxyConfiguration);

        final HttpRoute proxiedRoute = routePlanner.determineRoute(
                new HttpHost("https", "elastic.example.com", 9200),
                HttpClientContext.create());
        assertThat(proxiedRoute.getProxyHost())
                .isEqualTo(new HttpHost("http", "proxy.example.com", 8080));

        final HttpRoute directRoute = routePlanner.determineRoute(
                new HttpHost("https", "direct.example.com", 9200),
                HttpClientContext.create());
        assertThat(directRoute.getProxyHost())
                .isNull();
    }

    @Test
    void testCreateProxyCredentialsProvider_noAuth() {
        final HttpProxyConfiguration proxyConfiguration = HttpProxyConfiguration.builder()
                .host("proxy.example.com")
                .port(8080)
                .build();

        assertThat(HttpAsyncClientUtil.createProxyCredentialsProvider(null))
                .isNull();
        assertThat(HttpAsyncClientUtil.createProxyCredentialsProvider(proxyConfiguration))
                .isNull();
    }

    @Test
    void testCreateProxyCredentialsProvider_usernamePassword() {
        final HttpProxyConfiguration proxyConfiguration = HttpProxyConfiguration.builder()
                .host("proxy.example.com")
                .port(8080)
                .auth(HttpAuthConfiguration.builder()
                        .username("user")
                        .password("pass")
                        .build())
                .build();

        final Credentials credentials = getProxyCredentials(proxyConfiguration);

        assertThat(credentials)
                .isInstanceOf(UsernamePasswordCredentials.class);
        final UsernamePasswordCredentials usernamePasswordCredentials = (UsernamePasswordCredentials) credentials;
        assertThat(usernamePasswordCredentials.getUserName())
                .isEqualTo("user");
        assertThat(usernamePasswordCredentials.getUserPassword())
                .isEqualTo("pass".toCharArray());
    }

    @Test
    void testCreateProxyCredentialsProvider_nt() {
        final HttpProxyConfiguration proxyConfiguration = HttpProxyConfiguration.builder()
                .host("proxy.example.com")
                .port(8080)
                .auth(HttpAuthConfiguration.builder()
                        .username("user")
                        .password("pass")
                        .domain("DOMAIN")
                        .credentialType(HttpAuthConfiguration.NT_CREDS)
                        .build())
                .build();

        assertThat(getProxyCredentials(proxyConfiguration))
                .isInstanceOf(NTCredentials.class);
    }

    private Credentials getProxyCredentials(final HttpProxyConfiguration proxyConfiguration) {
        final CredentialsProvider credentialsProvider =
                HttpAsyncClientUtil.createProxyCredentialsProvider(proxyConfiguration);
        assertThat(credentialsProvider)
                .isNotNull();
        return credentialsProvider.getCredentials(
                new AuthScope(new HttpHost("http", "proxy.example.com", 8080)),
                HttpClientContext.create());
    }

    private Path createEmptyPkcs12(final String fileName) throws GeneralSecurityException, IOException {
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, PASSWORD.toCharArray());
        final Path path = tempDir.resolve(fileName);
        try (final OutputStream outputStream = Files.newOutputStream(path)) {
            keyStore.store(outputStream, PASSWORD.toCharArray());
        }
        return path;
    }
}
