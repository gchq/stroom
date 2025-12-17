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

package stroom.search.solr;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.search.solr.shared.SolrConnectionConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.solr.client.solrj.SolrClient;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public class SolrIndexClientCacheImpl implements SolrIndexClientCache, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrIndexClientCacheImpl.class);
    private static final String CACHE_NAME = "Solr Client Cache";

    private final LoadingStroomCache<SolrConnectionConfig, SolrClient> cache;
    private final IdentityHashMap<SolrClient, State> useMap = new IdentityHashMap<>();

    @Inject
    SolrIndexClientCacheImpl(final CacheManager cacheManager,
                             final Provider<SolrConfig> solrConfigProvider) {
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> solrConfigProvider.get().getIndexClientCache(),
                this::create,
                this::destroy);
    }

    private SolrClient create(final SolrConnectionConfig solrConnectionConfig) {
        return new SolrClientFactory().create(solrConnectionConfig);
    }

    private void destroy(final SolrConnectionConfig key, final SolrClient value) {
        synchronized (this) {
            final State state = useMap.get(value);
            if (state != null) {
                state.stale = true;
                if (state.useCount == 0) {
                    close(value);
                    useMap.remove(value);
                }
            }
        }
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
        Objects.requireNonNull(key, "Null key supplied");
        final SolrClient solrClient = cache.get(key);
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
