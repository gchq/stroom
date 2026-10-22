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

package stroom.search.elastic;

import stroom.credentials.api.HttpConfigResolver;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.shared.PermissionException;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestElasticConnectionResolver {

    private static final String API_KEY_NAME = "elastic-api-key";
    private static final List<String> URLS = List.of("https://elastic1:9200", "https://elastic2:9200");

    @Mock
    private StoredSecrets storedSecrets;

    private ElasticConnectionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ElasticConnectionResolver(
                () -> storedSecrets,
                new HttpConfigResolver(() -> storedSecrets));
    }

    @Test
    void testResolve_noApiKeyUsesDefaultHttpClientConfig() {
        final ResolvedElasticConnection connection = resolver.resolve(
                new ElasticConnectionConfig(URLS, null, null));

        assertThat(connection.connectionUrls())
                .isEqualTo(URLS);
        assertThat(connection.apiKey())
                .isNull();
        assertThat(connection.httpClientConfiguration().getConnectionTimeout())
                .isEqualTo(StroomDuration.ofSeconds(3));
        assertThat(connection.httpClientConfiguration().getTimeout())
                .isEqualTo(StroomDuration.ofMinutes(5));
    }

    @Test
    void testResolve_explicitHttpClientConfig() {
        final HttpClientConfig httpClientConfig = HttpClientConfig.builder()
                .timeout(SimpleDuration.builder().time(10).timeUnit(TimeUnit.SECONDS).build())
                .build();

        final ResolvedElasticConnection connection = resolver.resolve(
                new ElasticConnectionConfig(URLS, null, httpClientConfig));

        assertThat(connection.httpClientConfiguration().getTimeout())
                .isEqualTo(StroomDuration.ofSeconds(10));
    }

    @Test
    void testResolve_accessToken() {
        when(storedSecrets.get(API_KEY_NAME))
                .thenReturn(new StoredSecret(null, new AccessTokenSecret(" encodedKey== "), null));

        final ResolvedElasticConnection connection = resolver.resolve(
                new ElasticConnectionConfig(URLS, API_KEY_NAME, null));

        assertThat(connection.apiKey())
                .isEqualTo("encodedKey==");
        assertThat(connection.toString())
                .doesNotContain("encodedKey");
    }

    @Test
    void testResolve_missingCredential() {
        when(storedSecrets.get(API_KEY_NAME))
                .thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve(new ElasticConnectionConfig(URLS, API_KEY_NAME, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to find")
                .hasMessageContaining(API_KEY_NAME);
    }

    @Test
    void testResolve_wrongCredentialType() {
        when(storedSecrets.get(API_KEY_NAME))
                .thenReturn(new StoredSecret(null, new UsernamePasswordSecret("user", "pass"), null));

        assertThatThrownBy(() -> resolver.resolve(new ElasticConnectionConfig(URLS, API_KEY_NAME, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an access token");
    }

    @Test
    void testResolve_blankAccessToken() {
        when(storedSecrets.get(API_KEY_NAME))
                .thenReturn(new StoredSecret(null, new AccessTokenSecret("  "), null));

        assertThatThrownBy(() -> resolver.resolve(new ElasticConnectionConfig(URLS, API_KEY_NAME, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testResolve_noPermissionToCredential() {
        when(storedSecrets.get(API_KEY_NAME))
                .thenThrow(new PermissionException(null, "You do not have permission to view these credentials"));

        assertThatThrownBy(() -> resolver.resolve(new ElasticConnectionConfig(URLS, API_KEY_NAME, null)))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void testResolve_nullConfig() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testResolve_sameConfigGivesEqualConnections() {
        // The client cache relies on this
        final ElasticConnectionConfig config = new ElasticConnectionConfig(URLS, null, null);

        assertThat(resolver.resolve(config))
                .isEqualTo(resolver.resolve(config));
    }
}
