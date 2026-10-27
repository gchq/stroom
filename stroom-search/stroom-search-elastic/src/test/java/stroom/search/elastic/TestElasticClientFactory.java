/*
 * Copyright 2021 Crown Copyright
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

package stroom.search.elastic;

import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpProxyConfiguration;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.time.StroomDuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests the client built by [ElasticClientFactory] against a WireMock server standing in for an
/// Elasticsearch cluster, so that the TLS, authentication, proxy and timeout settings are exercised over a
/// real connection.
class TestElasticClientFactory {

    private static final String PASSWORD = "changeit";
    private static final String API_KEY = "ZW5jb2RlZEtleQ==";

    // The minimum the Elasticsearch Java client needs to parse an info response
    private static final String INFO_RESPONSE = """
            {
              "name" : "node-1",
              "cluster_name" : "test-cluster",
              "cluster_uuid" : "abc123",
              "version" : {
                "number" : "9.4.2",
                "build_flavor" : "default",
                "build_type" : "docker",
                "build_hash" : "abc",
                "build_date" : "2026-01-01T00:00:00.000Z",
                "build_snapshot" : false,
                "lucene_version" : "10.0.0",
                "minimum_wire_compatibility_version" : "8.18.0",
                "minimum_index_compatibility_version" : "8.0.0"
              },
              "tagline" : "You Know, for Search"
            }""";

    @TempDir
    static Path tempDir;

    private static Path serverKeyStore;
    private static Path trustStore;
    private static WireMockServer httpsServer;
    private static Path caTrustStore;
    private static WireMockServer caSignedServer;

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        // A self-signed certificate valid for "localhost" only, so connecting to 127.0.0.1 is a hostname
        // mismatch
        serverKeyStore = tempDir.resolve("server.p12");
        final Path certificate = tempDir.resolve("server.crt");
        trustStore = tempDir.resolve("trust.p12");
        keytool("-genkeypair",
                "-alias", "server",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost",
                "-validity", "1",
                "-storetype", "PKCS12",
                "-keystore", serverKeyStore.toString(),
                "-storepass", PASSWORD);
        keytool("-exportcert",
                "-alias", "server",
                "-keystore", serverKeyStore.toString(),
                "-storepass", PASSWORD,
                "-file", certificate.toString());
        keytool("-importcert",
                "-noprompt",
                "-alias", "server",
                "-file", certificate.toString(),
                "-storetype", "PKCS12",
                "-keystore", trustStore.toString(),
                "-storepass", PASSWORD);

        httpsServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .httpDisabled(true)
                .dynamicHttpsPort()
                // Elasticsearch only speaks HTTP/1.1, so do not let the client negotiate HTTP/2
                .http2TlsDisabled(true)
                .keystorePath(serverKeyStore.toString())
                .keystoreType("PKCS12")
                .keystorePassword(PASSWORD)
                .keyManagerPassword(PASSWORD));
        httpsServer.start();

        caSignedServer = createCaSignedServer();
        caSignedServer.start();
    }

    /// Creates a server whose certificate, for "localhost" only, is signed by a test CA, as Elasticsearch's are
    /// when its security is configured automatically. The server presents the chain of its certificate and
    /// the CA's, and [#caTrustStore] holds only the CA's.
    private static WireMockServer createCaSignedServer() throws IOException, InterruptedException {
        final Path caKeyStore = tempDir.resolve("ca.p12");
        final Path caCertificate = tempDir.resolve("ca.crt");
        final Path caSignedKeyStore = tempDir.resolve("ca-signed-server.p12");
        final Path certificateRequest = tempDir.resolve("ca-signed-server.csr");
        final Path signedCertificate = tempDir.resolve("ca-signed-server.crt");
        caTrustStore = tempDir.resolve("ca-trust.p12");

        keytool("-genkeypair",
                "-alias", "ca",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-dname", "CN=Test CA",
                "-ext", "bc:c",
                "-validity", "1",
                "-storetype", "PKCS12",
                "-keystore", caKeyStore.toString(),
                "-storepass", PASSWORD);
        keytool("-exportcert",
                "-alias", "ca",
                "-keystore", caKeyStore.toString(),
                "-storepass", PASSWORD,
                "-file", caCertificate.toString());
        keytool("-genkeypair",
                "-alias", "server",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-dname", "CN=localhost",
                "-validity", "1",
                "-storetype", "PKCS12",
                "-keystore", caSignedKeyStore.toString(),
                "-storepass", PASSWORD);
        keytool("-certreq",
                "-alias", "server",
                "-keystore", caSignedKeyStore.toString(),
                "-storepass", PASSWORD,
                "-file", certificateRequest.toString());
        keytool("-gencert",
                "-alias", "ca",
                "-keystore", caKeyStore.toString(),
                "-storepass", PASSWORD,
                "-infile", certificateRequest.toString(),
                "-outfile", signedCertificate.toString(),
                "-ext", "SAN=dns:localhost",
                "-ext", "eku=serverAuth",
                "-validity", "1");
        // The CA must be in the key store for the signed certificate to replace the self-signed one as a chain
        keytool("-importcert",
                "-noprompt",
                "-alias", "ca",
                "-file", caCertificate.toString(),
                "-keystore", caSignedKeyStore.toString(),
                "-storepass", PASSWORD);
        keytool("-importcert",
                "-alias", "server",
                "-file", signedCertificate.toString(),
                "-keystore", caSignedKeyStore.toString(),
                "-storepass", PASSWORD);
        keytool("-importcert",
                "-noprompt",
                "-alias", "ca",
                "-file", caCertificate.toString(),
                "-storetype", "PKCS12",
                "-keystore", caTrustStore.toString(),
                "-storepass", PASSWORD);

        return new WireMockServer(WireMockConfiguration.wireMockConfig()
                .httpDisabled(true)
                .dynamicHttpsPort()
                .http2TlsDisabled(true)
                .keystorePath(caSignedKeyStore.toString())
                .keystoreType("PKCS12")
                .keystorePassword(PASSWORD)
                .keyManagerPassword(PASSWORD));
    }

    @AfterAll
    static void afterAll() {
        if (httpsServer != null) {
            httpsServer.stop();
        }
        if (caSignedServer != null) {
            caSignedServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        httpsServer.resetAll();
        httpsServer.stubFor(infoRequest());
        caSignedServer.resetAll();
        caSignedServer.stubFor(infoRequest());
    }

    @Test
    void testHostFromUrl() {
        // Scheme and hostname
        final String hostName = "elastic.example.com.au";
        String url = "https://" + hostName;
        HttpHost host = ElasticClientFactory.hostFromUrl(url);
        assertThat(host)
                .as("Valid host is returned")
                .isNotNull();
        assertThat(host.getSchemeName())
                .isEqualTo("https");
        assertThat(host.getHostName())
                .isEqualTo(hostName);

        // Scheme, hostname and port
        final int port = 9200;
        //noinspection HttpUrlsUsage
        url = "http://" + hostName + ":9200";
        host = ElasticClientFactory.hostFromUrl(url);
        assertThat(host)
                .as("Valid host is returned")
                .isNotNull();
        assertThat(host.getHostName())
                .isEqualTo(hostName);
        assertThat(host.getPort())
                .isEqualTo(port);

        // Invalid URL
        url = hostName;
        host = ElasticClientFactory.hostFromUrl(url);
        assertThat(host)
                .as("No host is returned for an invalid URL")
                .isNull();
    }

    @Test
    void testTrustStoreAndApiKey() throws IOException {
        final InfoResponse response = info(new ResolvedElasticConnection(
                List.of(localhostUrl()),
                API_KEY,
                httpClientConfiguration(trustStoreTls().build())));

        assertThat(response.clusterName())
                .isEqualTo("test-cluster");
        httpsServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/"))
                .withHeader("Authorization", WireMock.equalTo("ApiKey " + API_KEY)));
    }

    @Test
    void testNoApiKey() throws IOException {
        info(new ResolvedElasticConnection(
                List.of(localhostUrl()),
                null,
                httpClientConfiguration(trustStoreTls().build())));

        httpsServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/"))
                .withoutHeader("Authorization"));
    }

    @Test
    void testUntrustedCertificate() {
        // No trust store, so only the JVM's default CAs are trusted
        final ResolvedElasticConnection connection = new ResolvedElasticConnection(
                List.of(localhostUrl()),
                API_KEY,
                httpClientConfiguration(HttpTlsConfiguration.builder().build()));

        assertThatThrownBy(() -> info(connection))
                .isInstanceOf(SSLException.class);
        // The API key must not have been sent to a server we do not trust
        assertThat(httpsServer.getAllServeEvents())
                .isEmpty();
    }

    @Test
    void testTrustSelfSignedCertificates() throws IOException {
        final InfoResponse response = info(new ResolvedElasticConnection(
                List.of(localhostUrl()),
                null,
                httpClientConfiguration(HttpTlsConfiguration.builder()
                        .trustSelfSignedCertificates(true)
                        .build())));

        assertThat(response.clusterName())
                .isEqualTo("test-cluster");
    }

    @Test
    void testHostnameMismatch() {
        // The certificate is for "localhost", not "127.0.0.1"
        final ResolvedElasticConnection connection = new ResolvedElasticConnection(
                List.of(loopbackIpUrl()),
                API_KEY,
                httpClientConfiguration(trustStoreTls().build()));

        assertThatThrownBy(() -> info(connection))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("doesn't match any of the subject alternative names");
        assertThat(httpsServer.getAllServeEvents())
                .isEmpty();
    }

    @Test
    void testHostnameVerificationDisabled() throws IOException {
        final InfoResponse response = info(new ResolvedElasticConnection(
                List.of(loopbackIpUrl()),
                null,
                httpClientConfiguration(trustStoreTls()
                        .verifyHostname(false)
                        .build())));

        assertThat(response.clusterName())
                .isEqualTo("test-cluster");
    }

    @Test
    void testVerificationDisabledForSelfSignedCertificate() throws IOException {
        // Trusting self-signed certificates and not verifying hostnames accepts a self-signed certificate for
        // another host, without a trust store
        final InfoResponse response = info(new ResolvedElasticConnection(
                List.of(loopbackIpUrl()),
                API_KEY,
                httpClientConfiguration(HttpTlsConfiguration.builder()
                        .trustSelfSignedCertificates(true)
                        .verifyHostname(false)
                        .build())));

        assertThat(response.clusterName())
                .isEqualTo("test-cluster");
    }

    @Test
    void testCaSignedCertificateTrustedByCa() throws IOException {
        final InfoResponse response = info(new ResolvedElasticConnection(
                List.of(caSignedUrl("localhost")),
                API_KEY,
                httpClientConfiguration(caTrustStoreTls().build())));

        assertThat(response.clusterName())
                .isEqualTo("test-cluster");
        caSignedServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/"))
                .withHeader("Authorization", WireMock.equalTo("ApiKey " + API_KEY)));
    }

    @Test
    void testCaSignedCertificateUntrusted() {
        // No trust store, so only the JVM's default CAs are trusted, which don't include the test CA
        final ResolvedElasticConnection connection = new ResolvedElasticConnection(
                List.of(caSignedUrl("localhost")),
                API_KEY,
                httpClientConfiguration(HttpTlsConfiguration.builder().build()));

        assertThatThrownBy(() -> info(connection))
                .isInstanceOf(SSLException.class);
        assertThat(caSignedServer.getAllServeEvents())
                .isEmpty();
    }

    @Test
    void testCaSignedCertificateHostnameMismatch() {
        // The CA is trusted, but the certificate is for "localhost", not "127.0.0.1"
        final ResolvedElasticConnection connection = new ResolvedElasticConnection(
                List.of(caSignedUrl("127.0.0.1")),
                API_KEY,
                httpClientConfiguration(caTrustStoreTls().build()));

        assertThatThrownBy(() -> info(connection))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("doesn't match any of the subject alternative names");
        assertThat(caSignedServer.getAllServeEvents())
                .isEmpty();
    }

    @Test
    void testCaSignedCertificateHostnameVerificationDisabled() throws IOException {
        final InfoResponse response = info(new ResolvedElasticConnection(
                List.of(caSignedUrl("127.0.0.1")),
                null,
                httpClientConfiguration(caTrustStoreTls()
                        .verifyHostname(false)
                        .build())));

        assertThat(response.clusterName())
                .isEqualTo("test-cluster");
    }

    @Test
    void testCaSignedCertificateNotTrustedAsSelfSigned() {
        // Trusting self-signed certificates only trusts a certificate presented on its own, not one signed by
        // an unknown CA, even with hostname verification disabled. There is no setting that turns off
        // certificate verification, so the CA must be given as the trust store.
        final ResolvedElasticConnection connection = new ResolvedElasticConnection(
                List.of(caSignedUrl("localhost")),
                API_KEY,
                httpClientConfiguration(HttpTlsConfiguration.builder()
                        .trustSelfSignedCertificates(true)
                        .verifyHostname(false)
                        .build()));

        assertThatThrownBy(() -> info(connection))
                .isInstanceOf(SSLException.class);
        assertThat(caSignedServer.getAllServeEvents())
                .isEmpty();
    }

    @Test
    void testWrongTrustStorePassword() {
        final ResolvedElasticConnection connection = new ResolvedElasticConnection(
                List.of(localhostUrl()),
                null,
                httpClientConfiguration(trustStoreTls()
                        .trustStorePassword("wrong")
                        .build()));

        assertThatThrownBy(() -> new ElasticClientFactory().create(connection, new ElasticClientConfig()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Unable to initialise SSL context");
    }

    @Test
    void testResponseTimeout() {
        httpsServer.stubFor(infoRequest().willReturn(infoResponse().withFixedDelay(5_000)));

        final ResolvedElasticConnection connection = new ResolvedElasticConnection(
                List.of(localhostUrl()),
                null,
                HttpClientConfiguration.builder()
                        .timeout(StroomDuration.ofMillis(500))
                        .tlsConfiguration(trustStoreTls().build())
                        .build());

        final long startTime = System.nanoTime();
        assertThatThrownBy(() -> info(connection))
                .isInstanceOf(IOException.class);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime))
                .as("The response timeout should have been applied")
                .isLessThan(4_000);
    }

    @Test
    void testServiceUnavailableNotRetriedByClient() {
        // Callers apply their own retry logic, so the HTTP client must not retry a 503 (or 429) itself
        httpsServer.stubFor(WireMock.get(WireMock.urlEqualTo("/"))
                .willReturn(ElasticWireMock.elasticResponse(503, """
                        {"error":{"type":"unavailable_shards_exception","reason":"unavailable"},"status":503}""")));

        final ResolvedElasticConnection connection = new ResolvedElasticConnection(
                List.of(localhostUrl()),
                null,
                httpClientConfiguration(trustStoreTls().build()));

        assertThatThrownBy(() -> info(connection))
                .isInstanceOf(Exception.class);
        httpsServer.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo("/")));
    }

    @Test
    void testProxy() throws IOException {
        // Plain HTTP so that the proxy sees the request rather than an opaque CONNECT tunnel
        final WireMockServer elasticServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .http2PlainDisabled(true));
        final WireMockServer proxyServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .http2PlainDisabled(true)
                .enableBrowserProxying(true));
        elasticServer.start();
        proxyServer.start();
        try {
            elasticServer.stubFor(infoRequest());

            //noinspection HttpUrlsUsage
            final InfoResponse response = info(new ResolvedElasticConnection(
                    List.of("http://localhost:" + elasticServer.port()),
                    API_KEY,
                    HttpClientConfiguration.builder()
                            .proxyConfiguration(HttpProxyConfiguration.builder()
                                    .host("localhost")
                                    .port(proxyServer.port())
                                    .scheme("http")
                                    .build())
                            .build()));

            assertThat(response.clusterName())
                    .isEqualTo("test-cluster");
            assertThat(proxyServer.getAllServeEvents())
                    .as("The request should have gone via the proxy")
                    .hasSize(1);
            elasticServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/"))
                    .withHeader("Authorization", WireMock.equalTo("ApiKey " + API_KEY)));
        } finally {
            proxyServer.stop();
            elasticServer.stop();
        }
    }

    private static InfoResponse info(final ResolvedElasticConnection connection) throws IOException {
        try (final ElasticsearchClient client = new ElasticClientFactory()
                .create(connection, new ElasticClientConfig())) {
            return client.info();
        }
    }

    private static MappingBuilder infoRequest() {
        return WireMock.get(WireMock.urlEqualTo("/")).willReturn(infoResponse());
    }

    private static ResponseDefinitionBuilder infoResponse() {
        return ElasticWireMock.elasticResponse(200, INFO_RESPONSE);
    }

    private static HttpTlsConfiguration.Builder trustStoreTls() {
        return HttpTlsConfiguration.builder()
                .trustStorePath(trustStore.toString())
                .trustStorePassword(PASSWORD)
                .trustStoreType("PKCS12");
    }

    private static HttpTlsConfiguration.Builder caTrustStoreTls() {
        return HttpTlsConfiguration.builder()
                .trustStorePath(caTrustStore.toString())
                .trustStorePassword(PASSWORD)
                .trustStoreType("PKCS12");
    }

    private static String caSignedUrl(final String host) {
        return "https://" + host + ":" + caSignedServer.httpsPort();
    }

    private static HttpClientConfiguration httpClientConfiguration(final HttpTlsConfiguration tlsConfiguration) {
        return HttpClientConfiguration.builder()
                .tlsConfiguration(tlsConfiguration)
                .build();
    }

    private static String localhostUrl() {
        return "https://localhost:" + httpsServer.httpsPort();
    }

    private static String loopbackIpUrl() {
        return "https://127.0.0.1:" + httpsServer.httpsPort();
    }

    private static void keytool(final String... args) throws IOException, InterruptedException {
        final List<String> command = new java.util.ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(args));
        final Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor())
                .as("keytool %s failed: %s", args[0], output)
                .isZero();
    }
}
