/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.impl;

import stroom.util.http.HttpClientConfiguration;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;
import java.util.Map;

/// A langchain4j [HttpClientBuilder] that creates [ApacheHttpClient] instances from the HTTP client
/// configuration held on an OpenAI Model document.
///
/// The document's configuration is the only source of the connection and read timeouts. langchain4j's
/// model classes always push their own timeouts into the builder (e.g. `OpenAiChatModel` pushes 15s/60s
/// when no timeout is set on the model builder), which would otherwise silently replace the timeouts
/// the user configured on the document. Those calls are therefore ignored.
public class ApacheHttpClientBuilder implements HttpClientBuilder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApacheHttpClientBuilder.class);

    private final HttpClientProviderCache httpClientProviderCache;
    private final HttpClientConfiguration httpClientConfiguration;
    private final Map<String, String> headers;

    /// @param httpClientProviderCache The cache to obtain the underlying Apache HTTP clients from.
    /// @param httpClientConfiguration The HTTP client configuration resolved from the OpenAI Model
    ///                                document. Its timeouts are used as-is for every client built.
    public ApacheHttpClientBuilder(final HttpClientProviderCache httpClientProviderCache,
                                   final HttpClientConfiguration httpClientConfiguration) {
        this(httpClientProviderCache, httpClientConfiguration, Map.of());
    }

    /// @param headers Headers to send with every request, unless the request sets them itself.
    public ApacheHttpClientBuilder(final HttpClientProviderCache httpClientProviderCache,
                                   final HttpClientConfiguration httpClientConfiguration,
                                   final Map<String, String> headers) {
        this.httpClientProviderCache = httpClientProviderCache;
        this.httpClientConfiguration = httpClientConfiguration;
        this.headers = headers;
    }

    /// @return The connection timeout from the document's HTTP client configuration.
    @Override
    public Duration connectTimeout() {
        return NullSafe.getOrElse(httpClientConfiguration,
                        HttpClientConfiguration::getConnectionTimeout,
                        HttpClientConfiguration.DEFAULT_CONNECTION_TIMEOUT)
                .getDuration();
    }

    /// Ignored, as the connection timeout is controlled by the document's HTTP client configuration.
    ///
    /// @param timeout The timeout langchain4j would like to use, which is logged and discarded.
    /// @return This builder.
    @Override
    public HttpClientBuilder connectTimeout(final Duration timeout) {
        LOGGER.debug(() -> LogUtil.message(
                "connectTimeout() - Ignoring requested connect timeout {}, using configured {}",
                timeout,
                connectTimeout()));
        return this;
    }

    /// @return The read timeout from the document's HTTP client configuration.
    @Override
    public Duration readTimeout() {
        return NullSafe.getOrElse(httpClientConfiguration,
                        HttpClientConfiguration::getTimeout,
                        HttpClientConfiguration.DEFAULT_TIMEOUT)
                .getDuration();
    }

    /// Ignored, as the read timeout is controlled by the document's HTTP client configuration.
    ///
    /// @param timeout The timeout langchain4j would like to use, which is logged and discarded.
    /// @return This builder.
    @Override
    public HttpClientBuilder readTimeout(final Duration timeout) {
        LOGGER.debug(() -> LogUtil.message(
                "readTimeout() - Ignoring requested read timeout {}, using configured {}",
                timeout,
                readTimeout()));
        return this;
    }

    /// @return A new [ApacheHttpClient] using the document's HTTP client configuration.
    @Override
    public HttpClient build() {
        return new ApacheHttpClient(httpClientProviderCache, httpClientConfiguration, headers);
    }
}
