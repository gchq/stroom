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

package stroom.search.solr;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.solr.client.solrj.SolrClient;
import stroom.cache.api.CacheManager;
import stroom.cache.api.CacheUtil;
import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;


@Singleton
public class SolrIndexClientCacheImpl implements SolrIndexClientCache, Clearable {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrIndexClientCacheImpl.class);

    private static final int MAX_CACHE_ENTRIES = 100;

    private final LoadingCache<SolrConnectionConfig, SolrClient> cache;

    private final IdentityHashMap<SolrClient, State> useMap = new IdentityHashMap<>();

    @Inject
    @SuppressWarnings("unchecked")
    SolrIndexClientCacheImpl(final CacheManager cacheManager) {
        final CacheLoader<SolrConnectionConfig, SolrClient> cacheLoader = CacheLoader.from(k -> {
            if (k == null) {
                throw new NullPointerException("Null key supplied");
            }
            return new SolrClientFactory().create(k);
        });

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .removalListener(entry -> {
                    synchronized (this) {
                        final SolrClient client = (SolrClient) entry.getValue();
                        final State state = useMap.get(client);
                        state.stale = true;
                        if (state.useCount == 0) {
                            close(client);
                            useMap.remove(client);
                        }
                    }
                });
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Solr Client Cache", cacheBuilder, cache);
    }

    @Override
    public void context(final SolrConnectionConfig key, final Consumer<SolrClient> consumer) {
        final SolrClient client = borrowClient(key);
        try {
            consumer.accept(client);
        } finally {
            returnClient(client);
        }
    }

    @Override
    public <R> R contextResult(final SolrConnectionConfig key, final Function<SolrClient, R> function) {
        final SolrClient client = borrowClient(key);
        try {
            return function.apply(client);
        } finally {
            returnClient(client);
        }
    }

    private SolrClient borrowClient(final SolrConnectionConfig key) {
        final SolrClient solrClient = cache.getUnchecked(key);
        synchronized (this) {
            useMap.computeIfAbsent(solrClient, k -> new State()).increment();
        }
        return solrClient;
    }

    private void returnClient(final SolrClient client) {
        synchronized (this) {
            final State state = useMap.get(client);
            state.decrement();
            if (state.stale && state.useCount == 0) {
                close(client);
                useMap.remove(client);
            }
        }
    }

    private void close(final SolrClient client) {
        try {
            client.close();
        } catch (final RuntimeException | IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
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
