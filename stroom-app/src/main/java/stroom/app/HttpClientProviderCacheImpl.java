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
import stroom.util.jersey.HttpClientProvider;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.io.CloseMode;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class HttpClientProviderCacheImpl implements HttpClientProviderCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpClientProviderCacheImpl.class);

    private static final String CACHE_NAME = "Http Client Cache";

    private final LoadingStroomCache<HttpClientConfiguration, HttpClientProviderImpl> cache;
    private final HttpClientFactory httpClientFactory;

    @Inject
    public HttpClientProviderCacheImpl(final CacheManager cacheManager,
                                       final Provider<PipelineConfig> pipelineConfigProvider,
                                       final HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> pipelineConfigProvider.get().getHttpClientCache(),
                this::create,
                this::destroy);
    }

    public HttpClientProvider get(final HttpClientConfiguration httpClientConfiguration) {
        // Get an item from the cache.
        HttpClientProviderImpl httpClientProvider = cache.get(httpClientConfiguration);
        // Try to acquire, if we fail then keep trying to get an item from the cache.
        while (!httpClientProvider.acquire()) {
            // Make sure the cache releases the currently cached item if it hasn't already.
            cache.invalidate(httpClientConfiguration);
            // Get a new item from the cache.
            httpClientProvider = cache.get(httpClientConfiguration);
        }
        return httpClientProvider;
    }

    private HttpClientProviderImpl create(final HttpClientConfiguration httpClientConfiguration) {
        LOGGER.debug("Creating client");
        final CloseableHttpClient httpClient = httpClientFactory.get(
                "HttpClientCache-" + UUID.randomUUID(),
                httpClientConfiguration);
        LOGGER.debug("Creating client provider");
        return new HttpClientProviderImpl(httpClient);
    }

    private void destroy(final HttpClientConfiguration key, final HttpClientProviderImpl value) {
        LOGGER.debug("Destroying client provider");
        try {
            value.release();
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public static class HttpClientProviderImpl implements HttpClientProvider {

        private final CloseableHttpClient httpClient;
        private final AtomicInteger openCount = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();

        public HttpClientProviderImpl(final CloseableHttpClient httpClient) {
            this.httpClient = httpClient;
            // Begin life acquired by the cache.
            openCount.set(1);
        }

        @Override
        public HttpClient get() {
            return httpClient;
        }

        @Override
        public void close() {
            release();
        }

        boolean acquire() {
            // Increment if greater than 0.
            return openCount.updateAndGet(count -> count > 0
                    ? count + 1
                    : count) > 0;
        }

        void release() {
            // Decrement but don't go lower than 0.
            if (openCount.updateAndGet(count -> count > 0
                    ? count - 1
                    : count) == 0) {
                // Make sure we only ever try to close once.
                if (closed.compareAndSet(false, true)) {
                    LOGGER.debug("Closing client");
                    httpClient.close(CloseMode.GRACEFUL);
                }
            }
        }
    }
}
