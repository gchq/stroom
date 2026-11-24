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

import com.codahale.metrics.MetricRegistry;
import com.sun.net.httpserver.HttpServer;
import io.dropwizard.client.HttpClientBuilder;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAddressCheckingDnsResolver {

    private HttpServer server;
    private String serverUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        // Redirects to the same server by its address.
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", serverUrl + "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",
            "127.1.2.3",
            "::1",
            "0.0.0.0",
            "0.1.2.3",
            "::",
            "169.254.169.254",
            "169.254.1.1",
            "fe80::1",
            "100.100.100.200",
            "fd00:ec2::254",
            "::ffff:127.0.0.1",
            "::127.0.0.1",
            "::169.254.169.254",
            "64:ff9b::7f00:1",
            "64:ff9b::a9fe:a9fe"})
    void testRefused(final String address) throws UnknownHostException {
        assertThat(AddressCheckingDnsResolver.isAllowed(InetAddress.getByName(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10.0.0.1",
            "172.16.0.1",
            "192.168.1.1",
            "100.100.100.201",
            "8.8.8.8",
            "2001:db8::1",
            "fd12:3456::1",
            "64:ff9b::808:808"})
    void testAllowed(final String address) throws UnknownHostException {
        assertThat(AddressCheckingDnsResolver.isAllowed(InetAddress.getByName(address))).isTrue();
    }

    @Test
    void testInterfaceAddressesRefused() throws SocketException {
        // E.g. the host's own address on its network, which reaches services listening on all interfaces.
        final List<InetAddress> addresses = NetworkInterface.networkInterfaces()
                .flatMap(NetworkInterface::inetAddresses)
                .filter(address -> !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                .toList();
        Assumptions.assumeThat(addresses).isNotEmpty();
        assertThat(addresses).noneMatch(AddressCheckingDnsResolver::isAllowed);
    }

    @Test
    void testResolve() throws UnknownHostException {
        assertThat(AddressCheckingDnsResolver.INSTANCE.resolve("10.1.2.3"))
                .containsExactly(InetAddress.getByName("10.1.2.3"));
        assertThatThrownBy(() -> AddressCheckingDnsResolver.INSTANCE.resolve("localhost"))
                .isInstanceOf(UnknownHostException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> AddressCheckingDnsResolver.INSTANCE.resolve("127.0.0.1"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void testClientRefusesAddress() throws IOException {
        try (final CloseableHttpClient client = newClient(AddressCheckingDnsResolver.INSTANCE)) {
            assertThatThrownBy(() -> client.execute(new HttpGet(serverUrl + "/"), response -> response.getCode()))
                    .isInstanceOf(UnknownHostException.class);
        }
    }

    @Test
    void testClientWithoutResolverConnects() throws IOException {
        // Shows the test above would connect without the resolver.
        try (final CloseableHttpClient client = newClient(null)) {
            final int code = client.execute(new HttpGet(serverUrl + "/"), response -> response.getCode());
            assertThat(code).isEqualTo(200);
        }
    }

    @Test
    void testClientChecksRedirects() throws IOException {
        // The first host is let through so that the redirect it sends is checked.
        final DnsResolver resolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(final String host) throws UnknownHostException {
                return "start.test".equals(host)
                        ? new InetAddress[]{InetAddress.getLoopbackAddress()}
                        : AddressCheckingDnsResolver.INSTANCE.resolve(host);
            }

            @Override
            public String resolveCanonicalHostname(final String host) {
                return host;
            }
        };
        final String startUrl = "http://start.test:" + server.getAddress().getPort();
        try (final CloseableHttpClient client = newClient(resolver)) {
            final int code = client.execute(new HttpGet(startUrl + "/"), response -> response.getCode());
            assertThat(code).isEqualTo(200);
            assertThatThrownBy(() -> client.execute(new HttpGet(startUrl + "/redirect"),
                    response -> response.getCode()))
                    .isInstanceOf(UnknownHostException.class);
        }
    }

    // Built as the HTTP client factories do, which need a Dropwizard environment.
    private CloseableHttpClient newClient(final DnsResolver dnsResolver) {
        final HttpClientBuilder builder = new HttpClientBuilder(new MetricRegistry())
                .using(new io.dropwizard.client.HttpClientConfiguration())
                .using(new ConfiguredRedirectStrategy(true));
        if (dnsResolver != null) {
            builder.using(dnsResolver);
        }
        return builder.build("test-" + UUID.randomUUID());
    }
}
