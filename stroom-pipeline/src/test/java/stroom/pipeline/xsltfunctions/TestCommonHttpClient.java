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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.filter.XsltConfig;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpProxyConfiguration;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class TestCommonHttpClient {

    private final HttpClientProviderCache cache = Mockito.mock(HttpClientProviderCache.class);

    @Test
    void testBlankUsesDefaults() {
        assertThat(CommonHttpClient.parseClientConfiguration(""))
                .isEqualTo(HttpClientConfiguration.builder().build());
        assertThat(CommonHttpClient.parseClientConfiguration(null))
                .isEqualTo(HttpClientConfiguration.builder().build());
    }

    @Test
    void testApacheProxyIsKept() {
        final HttpClientConfiguration config = CommonHttpClient.parseClientConfiguration("""
                {
                  "proxy": { "host": "proxy.example.com", "port": 3128 }
                }""");

        final HttpProxyConfiguration proxyConfiguration = config.getProxyConfiguration();
        assertThat(proxyConfiguration)
                .isNotNull();
        assertThat(proxyConfiguration.getHost())
                .isEqualTo("proxy.example.com");
        assertThat(proxyConfiguration.getPort())
                .isEqualTo(3128);
    }

    @Test
    void testApacheFollowRedirectsIsKept() {
        // followRedirects is in both formats, so on its own it is read as Apache.
        final HttpClientConfiguration config = CommonHttpClient.parseClientConfiguration("""
                { "followRedirects": false }""");

        assertThat(config.isFollowRedirects())
                .isFalse();
    }

    @Test
    void testApacheTlsAndTimeoutsAreKept() {
        final HttpClientConfiguration config = CommonHttpClient.parseClientConfiguration("""
                {
                  "timeout": "PT10S",
                  "connectionTimeout": "PT5S",
                  "userAgent": "my-agent",
                  "tls": { "keyStorePath": "/keys/client.jks", "verifyHostname": false }
                }""");

        assertThat(config.getTimeout())
                .isEqualTo(StroomDuration.ofSeconds(10));
        assertThat(config.getConnectionTimeout())
                .isEqualTo(StroomDuration.ofSeconds(5));
        assertThat(config.getUserAgent())
                .isEqualTo("my-agent");
        assertThat(config.getTlsConfiguration())
                .isNotNull();
        assertThat(config.getTlsConfiguration().getKeyStorePath())
                .isEqualTo("/keys/client.jks");
    }

    @Test
    void testSharedPropertiesOnlyUseApacheDefaults() {
        final HttpClientConfiguration config = CommonHttpClient.parseClientConfiguration("""
                { "connectionTimeout": "PT5S" }""");

        assertThat(config.getConnectionTimeout())
                .isEqualTo(StroomDuration.ofSeconds(5));
        assertThat(config.getConnectionRequestTimeout())
                .isEqualTo(HttpClientConfiguration.DEFAULT_CONNECTION_REQUEST_TIMEOUT);
    }

    @Test
    void testOkHttpIsConverted() {
        final HttpClientConfiguration config = CommonHttpClient.parseClientConfiguration("""
                {
                  "callTimeout": "PT2S",
                  "connectionTimeout": "PT3S",
                  "followRedirects": false,
                  "sslConfig": { "keyStorePath": "/keys/client.jks", "sslProtocol": "TLSv1.3" }
                }""");

        assertThat(config.getConnectionRequestTimeout())
                .isEqualTo(StroomDuration.ofSeconds(2));
        assertThat(config.getConnectionTimeout())
                .isEqualTo(StroomDuration.ofSeconds(3));
        assertThat(config.isFollowRedirects())
                .isFalse();
        assertThat(config.getTlsConfiguration())
                .isNotNull();
        assertThat(config.getTlsConfiguration().getKeyStorePath())
                .isEqualTo("/keys/client.jks");
        assertThat(config.getTlsConfiguration().getProtocol())
                .isEqualTo("TLSv1.3");
    }

    @Test
    void testOkHttpDefaults() {
        final HttpClientConfiguration config = CommonHttpClient.parseClientConfiguration("""
                { "readTimeout": "PT30S" }""");

        assertThat(config.getConnectionRequestTimeout())
                .isEqualTo(StroomDuration.ofMillis(500));
        assertThat(config.getConnectionTimeout())
                .isEqualTo(StroomDuration.ofMillis(500));
        assertThat(config.isFollowRedirects())
                .isEqualTo(HttpClientConfiguration.builder().build().isFollowRedirects());
        assertThat(config.getTlsConfiguration())
                .isNull();
    }

    @Test
    void testMixedFormatsAreRejected() {
        assertThatThrownBy(() -> CommonHttpClient.parseClientConfiguration("""
                {
                  "sslConfig": { "keyStorePath": "/keys/client.jks" },
                  "proxy": { "host": "proxy.example.com", "port": 3128 }
                }"""))
                .isInstanceOf(ProcessException.class)
                .hasMessageContaining("sslConfig")
                .hasMessageContaining("proxy");
    }

    @Test
    void testNullPropertiesDoNotChooseFormat() {
        // A null OkHttp-only property must not make an Apache config look mixed.
        final HttpClientConfiguration config = CommonHttpClient.parseClientConfiguration("""
                {
                  "sslConfig": null,
                  "proxy": { "host": "proxy.example.com", "port": 3128 }
                }""");

        assertThat(config.getProxyConfiguration())
                .isNotNull();
        assertThat(config.getProxyConfiguration().getHost())
                .isEqualTo("proxy.example.com");
    }

    @Test
    void testErrorMessageExcludesPasswords() {
        assertThatThrownBy(() -> CommonHttpClient.parseClientConfiguration("""
                {
                  "sslConfig": { "keyStorePassword": "s3cret" },
                  "proxy": { "host": "proxy.example.com", "port": 3128 }
                }"""))
                .isInstanceOf(ProcessException.class)
                .message()
                .doesNotContain("s3cret");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{ \"proxy\": ",
            "[]",
            "\"a string\"",
            "{ \"timeout\": \"not a duration\" }",
            "{ \"callTimeout\": \"not a duration\" }"})
    void testInvalidConfigIsRejected(final String clientConfigStr) {
        assertThatThrownBy(() -> CommonHttpClient.parseClientConfiguration(clientConfigStr))
                .isInstanceOf(ProcessException.class);
    }

    @Test
    void testCreateClientProviderPassesParsedConfig() {
        final String clientConfigStr = """
                { "proxy": { "host": "proxy.example.com", "port": 3128 } }""";

        newClient(false).createClientProvider(clientConfigStr);

        final ArgumentCaptor<HttpClientConfiguration> captor =
                ArgumentCaptor.forClass(HttpClientConfiguration.class);
        Mockito.verify(cache).get(captor.capture());
        assertThat(captor.getValue().getProxyConfiguration())
                .isNotNull();
        assertThat(captor.getValue().getProxyConfiguration().getHost())
                .isEqualTo("proxy.example.com");
    }

    @Test
    void testJsonProxyRefusedInSecureMode() {
        // Before #6082 a proxy in the JSON config was dropped, so secure mode never saw it.
        final CommonHttpClient client = newClient(true);

        assertThatThrownBy(() -> client.createClientProvider("""
                { "proxy": { "host": "proxy.example.com", "port": 3128 } }"""))
                .isInstanceOf(ProcessException.class)
                .hasMessageContaining("proxy");
        Mockito.verifyNoInteractions(cache);
    }

    @Test
    void testSecureModeUsesAddressCheckedClient() {
        newClient(true).createClientProvider("");

        Mockito.verify(cache).getAddressChecked(any(HttpClientConfiguration.class));
        Mockito.verify(cache, Mockito.never()).get(any());
    }

    @Test
    void testSecureModeOffUsesOrdinaryClient() {
        newClient(false).createClientProvider("");

        Mockito.verify(cache).get(any(HttpClientConfiguration.class));
        Mockito.verify(cache, Mockito.never()).getAddressChecked(any());
    }

    @Test
    void testProxyRefusedInSecureMode() {
        final CommonHttpClient client = newClient(true);
        final HttpClientConfiguration configuration = HttpClientConfiguration.builder()
                .proxyConfiguration(new HttpProxyConfiguration("localhost", 3128, null, null, null))
                .build();

        assertThatThrownBy(() -> client.createClientProvider(configuration))
                .hasMessageContaining("proxy");
        Mockito.verifyNoInteractions(cache);
    }

    @Test
    void testProxyAllowedWithSecureModeOff() {
        final HttpClientConfiguration configuration = HttpClientConfiguration.builder()
                .proxyConfiguration(new HttpProxyConfiguration("localhost", 3128, null, null, null))
                .build();

        newClient(false).createClientProvider(configuration);

        Mockito.verify(cache).get(configuration);
    }

    private CommonHttpClient newClient(final boolean secureMode) {
        final XsltConfig xsltConfig = new XsltConfig(null, null, secureMode, List.of(), null, null);
        return new CommonHttpClient(cache, () -> xsltConfig);
    }
}
