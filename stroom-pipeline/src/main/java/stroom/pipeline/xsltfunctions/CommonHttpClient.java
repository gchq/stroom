/*
 * Copyright 2016-2025 Crown Copyright
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CommonHttpClient {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CommonHttpClient.class);

    private final HttpClientProviderCache httpClientProviderCache;
    private final Map<String, HttpClientConfiguration> configCache = new HashMap<>();

    CommonHttpClient(final HttpClientProviderCache httpClientProviderCache) {
        this.httpClientProviderCache = httpClientProviderCache;
    }

    public HttpClientProvider createClientProvider(final String clientConfigStr) {
        final HttpClientConfiguration configuration = configCache.computeIfAbsent(clientConfigStr, k -> {
            RuntimeException exception = null;
            HttpClientConfiguration httpClientConfiguration = null;
            try {
                // Try deprecated OKHttp config.
                httpClientConfiguration = getOKHttpClientConfiguration(clientConfigStr);
            } catch (final RuntimeException e) {
                exception = e;
            }

            if (httpClientConfiguration == null) {
                try {
                    httpClientConfiguration = getHttpClientConfiguration(clientConfigStr);
                } catch (final RuntimeException e) {
                    if (exception != null) {
                        exception = e;
                    }
                }
            }

            if (exception != null) {
                throw exception;
            }

            if (httpClientConfiguration == null) {
                httpClientConfiguration = HttpClientConfiguration.builder().build();
            }

            return httpClientConfiguration;
        });

        LOGGER.debug(() -> "Creating client");
        return httpClientProviderCache.get(configuration);
    }

    @Deprecated // Moving to Apache HttpClientConfiguration
    private HttpClientConfiguration getOKHttpClientConfiguration(final String string) {
        if (NullSafe.isBlankString(string)) {
            return null;
        }
        try {
            // Try deprecated OKHttp config.
            final OkHttpClientConfig clientConfig = JsonUtil.readValue(string, OkHttpClientConfig.class);
            final HttpTlsConfiguration httpTlsConfiguration = HttpClientUtil
                    .getHttpTlsConfiguration(clientConfig.getSslConfig());
            return HttpClientConfiguration
                    .builder()
                    .connectionRequestTimeout(Objects
                            .requireNonNullElse(clientConfig.getCallTimeout(), StroomDuration.ofMillis(500)))
                    .connectionTimeout(Objects
                            .requireNonNullElse(clientConfig.getConnectionTimeout(), StroomDuration.ofMillis(500)))
                    .tlsConfiguration(httpTlsConfiguration)
                    .build();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "Error parsing OkHTTP client configuration \"{}\". {}", string, e.getMessage()));
            throw ProcessException.create(LogUtil.message(
                    "Error parsing OkHTTP client configuration \"{}\". {}", string, e.getMessage()), e);
        }
    }

    private HttpClientConfiguration getHttpClientConfiguration(final String string) {
        if (NullSafe.isBlankString(string)) {
            return null;
        }
        try {
            return JsonUtil.readValue(string, HttpClientConfiguration.class);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "Error parsing Apache HTTP client configuration \"{}\". {}", string, e.getMessage()));
            throw ProcessException.create(LogUtil.message(
                    "Error parsing Apache HTTP client configuration \"{}\". {}", string, e.getMessage()), e);
        }
    }
}
