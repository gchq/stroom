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

package stroom.app;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.pipeline.PipelineConfig;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientFactory;
import stroom.util.jersey.HttpClientCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.io.CloseMode;

import java.util.UUID;

@Singleton
public class HttpClientCacheImpl implements HttpClientCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpClientCacheImpl.class);

    private static final String CACHE_NAME = "Http Client Cache";

    private final LoadingStroomCache<HttpClientConfiguration, CloseableHttpClient> cache;
    private final HttpClientFactory httpClientFactory;

    @Inject
    public HttpClientCacheImpl(final CacheManager cacheManager,
                               final Provider<PipelineConfig> pipelineConfigProvider,
                               final HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> pipelineConfigProvider.get().getHttpClientCache(),
                this::create,
                this::destroy);
    }

    public HttpClient get(final HttpClientConfiguration httpClientConfiguration) {
        return cache.get(httpClientConfiguration);
    }

    private CloseableHttpClient create(final HttpClientConfiguration httpClientConfiguration) {
        LOGGER.debug(() -> "Creating client");
        return httpClientFactory.get("HttpClientCache-" + UUID.randomUUID(), httpClientConfiguration);
    }

    private void destroy(final HttpClientConfiguration key, final CloseableHttpClient value) {
        LOGGER.debug(() -> "Closing client");
        try {
            value.close(CloseMode.GRACEFUL);
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }
}
