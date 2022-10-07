/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.search.elastic;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class ElasticClientCacheImpl implements ElasticClientCache, Clearable {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticClientCacheImpl.class);
    private static final String CACHE_NAME = "Elastic Client Cache";

    private final LoadingStroomCache<ElasticConnectionConfig, RestHighLevelClient> cache;
    private final IdentityHashMap<RestHighLevelClient, State> useMap = new IdentityHashMap<>();

    private final ElasticConfig elasticConfig;

    @Inject
    ElasticClientCacheImpl(final CacheManager cacheManager,
                           final Provider<ElasticConfig> elasticConfigProvider) {
        this.elasticConfig = elasticConfigProvider.get();
        this.cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> elasticConfigProvider.get().getIndexClientCache(),
                this::create,
                this::destroy);
    }

    private RestHighLevelClient create(ElasticConnectionConfig elasticConnectionConfig) {
        if (elasticConnectionConfig == null) {
            throw new NullPointerException("Elasticsearch connection config not provided");
        }

        // Create a new instance of `RestHighLevelClient`
        return new ElasticClientFactory().create(elasticConnectionConfig, elasticConfig.getElasticClientConfig());
    }

    private void destroy(final ElasticConnectionConfig key, final RestHighLevelClient value) {
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
    public void context(final ElasticConnectionConfig key, final Consumer<RestHighLevelClient> consumer) {
        final RestHighLevelClient client = borrowClient(key);
        try {
            consumer.accept(client);
        } finally {
            returnClient(client);
        }
    }

    @Override
    public <R> R contextResult(final ElasticConnectionConfig key, final Function<RestHighLevelClient, R> function) {
        final RestHighLevelClient client = borrowClient(key);
        try {
            return function.apply(client);
        } finally {
            returnClient(client);
        }
    }

    private RestHighLevelClient borrowClient(final ElasticConnectionConfig key) {
        final RestHighLevelClient client = cache.get(key);
        synchronized (this) {
            useMap.computeIfAbsent(client, k -> new State()).increment();
        }
        return client;
    }

    private void returnClient(final RestHighLevelClient client) {
        synchronized (this) {
            final State state = useMap.get(client);
            state.decrement();
            if (state.stale && state.useCount == 0) {
                close(client);
                useMap.remove(client);
            }
        }
    }

    private void close(final RestHighLevelClient client) {
        try {
            client.close();
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
