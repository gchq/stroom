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

import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.cache.CacheManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;


@Component
public class ElasticClientCacheImpl implements ElasticClientCache {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticClientCacheImpl.class);

    private static final int MAX_CACHE_ENTRIES = 100;

    private final LoadingCache<ElasticConnectionConfig, RestHighLevelClient> cache;

    private final IdentityHashMap<RestHighLevelClient, State> useMap = new IdentityHashMap<>();

    @Inject
    @SuppressWarnings("unchecked")
    ElasticClientCacheImpl(
        final CacheManager cacheManager,
        @Value("#{propertyConfigurer.getProperty('stroom.pki.caCert')}") final String caCertPath
    ) {
        final CacheLoader<ElasticConnectionConfig, RestHighLevelClient> cacheLoader = CacheLoader.from(k -> {
            if (k == null) {
                throw new NullPointerException("Elasticsearch connection config not provided");
            }

            // Create a new instance of `RestHighLevelClient`
            return new ElasticClientFactory().create(k);
        });

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .removalListener(entry -> {
                    synchronized (this) {
                        final RestHighLevelClient client = (RestHighLevelClient) entry.getValue();
                        final State state = useMap.get(client);
                        state.stale = true;
                        if (state.useCount == 0) {
                            close(client);
                            useMap.remove(client);
                        }
                    }
                });
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Elasticsearch Client Cache", cacheBuilder, cache);
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
        final RestHighLevelClient client = cache.getUnchecked(key);
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
