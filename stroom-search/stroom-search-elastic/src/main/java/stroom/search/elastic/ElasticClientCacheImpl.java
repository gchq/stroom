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

package stroom.search.elastic;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public class ElasticClientCacheImpl implements ElasticClientCache, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticClientCacheImpl.class);
    private static final String CACHE_NAME = "Elastic Client Cache";

    private final LoadingStroomCache<ElasticConnectionConfig, ElasticsearchClient> cache;
    private final IdentityHashMap<ElasticsearchClient, State> useMap = new IdentityHashMap<>();

    private final Provider<ElasticConfig> elasticConfigProvider;

    @Inject
    ElasticClientCacheImpl(final CacheManager cacheManager,
                           final Provider<ElasticConfig> elasticConfigProvider) {
        this.elasticConfigProvider = elasticConfigProvider;
        this.cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> elasticConfigProvider.get().getIndexClientCache(),
                this::create,
                this::destroy);
    }

    private ElasticsearchClient create(final ElasticConnectionConfig elasticConnectionConfig) {
        if (elasticConnectionConfig == null) {
            throw new NullPointerException("Elasticsearch connection config not provided");
        }

        // Create a new instance of `RestHighLevelClient`
        return new ElasticClientFactory().create(elasticConnectionConfig,
                elasticConfigProvider.get().getClientConfig());
    }

    private void destroy(final ElasticConnectionConfig key, final ElasticsearchClient value) {
        synchronized (this) {
            final State state = useMap.get(value);
            state.stale = true;
            if (state.useCount == 0) {
                close(value);
                useMap.remove(value);
            }
        }
    }

    @Override
    public void context(final ElasticConnectionConfig key, final Consumer<ElasticsearchClient> consumer) {
        final ElasticsearchClient client = borrowClient(key);
        try {
            consumer.accept(client);
        } finally {
            returnClient(client);
        }
    }

    @Override
    public <R> R contextResult(final ElasticConnectionConfig key, final Function<ElasticsearchClient, R> function) {
        final ElasticsearchClient client = borrowClient(key);
        try {
            return function.apply(client);
        } finally {
            returnClient(client);
        }
    }

    private ElasticsearchClient borrowClient(final ElasticConnectionConfig key) {
        final ElasticsearchClient client = cache.get(key);
        synchronized (this) {
            useMap.computeIfAbsent(client, k -> new State()).increment();
        }
        return client;
    }

    private void returnClient(final ElasticsearchClient client) {
        synchronized (this) {
            final State state = useMap.get(client);
            state.decrement();
            if (state.stale && state.useCount == 0) {
                close(client);
                useMap.remove(client);
            }
        }
    }

    private void close(final ElasticsearchClient client) {
        try {
            client._transport().close();
        } catch (final RuntimeException | IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private static class State {

        private int useCount;
        private boolean stale;

        void increment() {
            useCount++;
        }

        void decrement() {
            useCount--;
        }
    }
}
