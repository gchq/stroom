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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.filter.XsltConfig;
import stroom.util.config.OkHttpClientConfig;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientUtil;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.jersey.HttpClientProvider;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import jakarta.inject.Provider;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CommonHttpClient {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CommonHttpClient.class);

    // Properties that only the deprecated OkHttp client config has.
    private static final List<String> OK_HTTP_ONLY_PROPERTIES = List.of(
            "sslConfig",
            "httpProtocols",
            "callTimeout",
            "readTimeout",
            "writeTimeout",
            "followSslRedirects",
            "retryOnConnectionFailure");

    // Properties that only the Apache client config has.
    private static final List<String> APACHE_ONLY_PROPERTIES = List.of(
            "timeout",
            "connectionRequestTimeout",
            "timeToLive",
            "cookiesEnabled",
            "maxConnections",
            "maxConnectionsPerRoute",
            "keepAlive",
            "retries",
            "userAgent",
            "proxy",
            "validateAfterInactivityPeriod",
            HttpClientConfiguration.PROP_NAME_TLS);

    private static final StroomDuration OK_HTTP_DEFAULT_TIMEOUT = StroomDuration.ofMillis(500);

    private final HttpClientProviderCache httpClientProviderCache;
    private final Provider<XsltConfig> xsltConfigProvider;
    private final Map<String, HttpClientConfiguration> configCache = new HashMap<>();

    CommonHttpClient(final HttpClientProviderCache httpClientProviderCache,
                     final Provider<XsltConfig> xsltConfigProvider) {
        this.httpClientProviderCache = httpClientProviderCache;
        this.xsltConfigProvider = xsltConfigProvider;
    }

    private boolean isSecureMode() {
        return xsltConfigProvider.get().isSecureMode();
    }

    /// Gets a client provider for the supplied client config.
    ///
    /// The config may be in the Apache format ([HttpClientConfiguration]) or the deprecated
    /// OkHttp format ([OkHttpClientConfig]). The format is chosen from the property names:
    /// a config with any OkHttp-only property is read as OkHttp, anything else as Apache.
    ///
    /// @param clientConfigStr The JSON client config, may be blank to use the defaults.
    /// @return A client provider for the config.
    /// @throws ProcessException If the config is not a valid JSON object, cannot be read in its
    ///                          format, or mixes OkHttp-only and Apache-only properties.
    public HttpClientProvider createClientProvider(final String clientConfigStr) {
        final HttpClientConfiguration configuration = configCache.computeIfAbsent(
                clientConfigStr,
                CommonHttpClient::parseClientConfiguration);

        return createClientProvider(configuration);
    }

    /// Gets a client for a parsed configuration. In secure mode it only connects to allowed addresses.
    ///
    /// @param configuration The client configuration.
    /// @return The client provider, to be closed after use.
    HttpClientProvider createClientProvider(final HttpClientConfiguration configuration) {
        LOGGER.debug("Creating client");
        if (!isSecureMode()) {
            return httpClientProviderCache.get(configuration);
        }
        // The client checks the address of every host it connects to, which for a proxy is the proxy itself.
        if (configuration.getProxyConfiguration() != null) {
            throw ProcessException.create(
                    "A proxy can't be set in the HTTP client configuration while secure mode is on");
        }
        return httpClientProviderCache.getAddressChecked(configuration);
    }

    /// Reads a client config string, in either the Apache or the deprecated OkHttp format.
    ///
    /// @param clientConfigStr The JSON client config, may be blank to use the defaults.
    /// @return The client config in the Apache format.
    /// @throws ProcessException If the config is not a valid JSON object, cannot be read in its
    ///                          format, or mixes OkHttp-only and Apache-only properties.
    static HttpClientConfiguration parseClientConfiguration(final String clientConfigStr) {
        // Error messages never include the config itself, as it may hold key or trust store passwords.
        if (NullSafe.isBlankString(clientConfigStr)) {
            return HttpClientConfiguration.builder().build();
        }

        final JsonNode jsonNode = readTree(clientConfigStr);
        final List<String> okHttpProperties = getPresentProperties(jsonNode, OK_HTTP_ONLY_PROPERTIES);
        final List<String> apacheProperties = getPresentProperties(jsonNode, APACHE_ONLY_PROPERTIES);

        if (!okHttpProperties.isEmpty() && !apacheProperties.isEmpty()) {
            throw ProcessException.create(LogUtil.message(
                    "HTTP client configuration mixes OkHttp-only properties {} with Apache-only " +
                    "properties {}. Use only the Apache format.",
                    okHttpProperties, apacheProperties));
        } else if (!okHttpProperties.isEmpty()) {
            LOGGER.debug("Reading HTTP client configuration in the deprecated OkHttp format");
            return getOkHttpClientConfiguration(clientConfigStr);
        } else {
            LOGGER.debug("Reading HTTP client configuration in the Apache format");
            return getHttpClientConfiguration(clientConfigStr);
        }
    }

    private static JsonNode readTree(final String clientConfigStr) {
        final JsonNode jsonNode;
        try {
            jsonNode = JsonUtil.getMapper().readTree(clientConfigStr);
        } catch (final RuntimeException e) {
            throw ProcessException.create(LogUtil.message(
                    "Error parsing HTTP client configuration. {}", e.getMessage()), e);
        }
        if (jsonNode == null || !jsonNode.isObject()) {
            throw ProcessException.create("HTTP client configuration is not a JSON object.");
        }
        return jsonNode;
    }

    private static List<String> getPresentProperties(final JsonNode jsonNode, final List<String> propertyNames) {
        return propertyNames.stream()
                .filter(jsonNode::hasNonNull)
                .toList();
    }

    @Deprecated // Moving to Apache HttpClientConfiguration
    private static HttpClientConfiguration getOkHttpClientConfiguration(final String clientConfigStr) {
        try {
            final OkHttpClientConfig clientConfig = JsonUtil.readValue(clientConfigStr, OkHttpClientConfig.class);
            final HttpTlsConfiguration httpTlsConfiguration = HttpClientUtil
                    .getHttpTlsConfiguration(clientConfig.getSslConfig());
            final HttpClientConfiguration.Builder builder = HttpClientConfiguration
                    .builder()
                    .connectionRequestTimeout(Objects
                            .requireNonNullElse(clientConfig.getCallTimeout(), OK_HTTP_DEFAULT_TIMEOUT))
                    .connectionTimeout(Objects
                            .requireNonNullElse(clientConfig.getConnectionTimeout(), OK_HTTP_DEFAULT_TIMEOUT))
                    .tlsConfiguration(httpTlsConfiguration);
            final Boolean followRedirects = clientConfig.isFollowRedirects();
            if (followRedirects != null) {
                builder.followRedirects(followRedirects);
            }
            return builder.build();
        } catch (final RuntimeException e) {
            throw ProcessException.create(LogUtil.message(
                    "Error parsing OkHttp client configuration. {}", e.getMessage()), e);
        }
    }

    private static HttpClientConfiguration getHttpClientConfiguration(final String clientConfigStr) {
        try {
            return JsonUtil.readValue(clientConfigStr, HttpClientConfiguration.class);
        } catch (final RuntimeException e) {
            throw ProcessException.create(LogUtil.message(
                    "Error parsing Apache HTTP client configuration. {}", e.getMessage()), e);
        }
    }
}
