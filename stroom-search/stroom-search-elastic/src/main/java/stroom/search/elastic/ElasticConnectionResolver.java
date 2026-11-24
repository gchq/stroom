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
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.http.HttpClientUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

/// Resolves the credential names held in an [ElasticConnectionConfig] into the secrets they refer to.
///
/// Resolution happens on the server at the point of use and is subject to the current user having
/// permission to view the named credentials.
@Singleton
public class ElasticConnectionResolver {

    // Bounds each wait for a response rather than the whole request, so large bulk requests are not affected
    private static final SimpleDuration DEFAULT_TIMEOUT = SimpleDuration
            .builder()
            .time(5)
            .timeUnit(TimeUnit.MINUTES)
            .build();

    // Kept short so that the client quickly fails over to another node if one is unreachable
    private static final SimpleDuration DEFAULT_CONNECTION_TIMEOUT = SimpleDuration
            .builder()
            .time(3)
            .timeUnit(TimeUnit.SECONDS)
            .build();

    private final Provider<StoredSecrets> storedSecretsProvider;
    private final HttpConfigResolver httpConfigResolver;

    private volatile HttpClientConfig defaultHttpClientConfig;

    @Inject
    public ElasticConnectionResolver(final Provider<StoredSecrets> storedSecretsProvider,
                                     final HttpConfigResolver httpConfigResolver) {
        this.storedSecretsProvider = storedSecretsProvider;
        this.httpConfigResolver = httpConfigResolver;
    }

    /// Resolves the API key and the key/trust stores named in the connection configuration.
    ///
    /// @param connectionConfig The connection configuration from an Elastic Cluster document.
    /// @return The connection with all secrets resolved.
    /// @throws IllegalArgumentException If the named API key credential does not exist or is not an access
    ///                                  token.
    /// @throws RuntimeException         If a named credential cannot be resolved, including when the
    ///                                  current user does not have permission to view it.
    public ResolvedElasticConnection resolve(final ElasticConnectionConfig connectionConfig) {
        Objects.requireNonNull(connectionConfig, "Elasticsearch connection config not provided");

        final HttpClientConfig httpClientConfig = Objects.requireNonNullElseGet(
                connectionConfig.getHttpClientConfiguration(),
                this::getDefaultHttpClientConfig);

        return new ResolvedElasticConnection(
                NullSafe.list(connectionConfig.getConnectionUrls()),
                resolveApiKey(connectionConfig.getApiKeyName()),
                httpConfigResolver.resolve(httpClientConfig));
    }

    /// @return The HTTP client configuration to use when a cluster has none of its own.
    public HttpClientConfig getDefaultHttpClientConfig() {
        // Reading the JVM's enabled ciphers and protocols means opening a socket, so hold on to the result.
        // It cannot change while the process is running.
        HttpClientConfig config = defaultHttpClientConfig;
        if (config == null) {
            config = HttpClientUtil.createDefaultHttpClientConfig(DEFAULT_TIMEOUT)
                    .copy()
                    .connectionTimeout(DEFAULT_CONNECTION_TIMEOUT)
                    .build();
            defaultHttpClientConfig = config;
        }
        return config;
    }

    private String resolveApiKey(final String apiKeyName) {
        if (NullSafe.isBlankString(apiKeyName)) {
            return null;
        }

        final StoredSecret storedSecret = storedSecretsProvider.get().get(apiKeyName);
        if (storedSecret == null) {
            throw new IllegalArgumentException("Unable to find API key credential '" + apiKeyName + "'");
        }
        if (storedSecret.secret() instanceof final AccessTokenSecret accessTokenSecret
            && NullSafe.isNonBlankString(accessTokenSecret.getAccessToken())) {
            return accessTokenSecret.getAccessToken().trim();
        }
        throw new IllegalArgumentException("Credential '" + apiKeyName + "' is not an access token");
    }
}
