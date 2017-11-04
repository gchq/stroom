/*
 * Copyright 2016 Crown Copyright
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

package stroom.pool;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.Loader;
import stroom.util.cache.CentralCacheManager;

public abstract class AbstractPoolCache<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPoolCache.class);

    private static final int MAX_CACHE_ENTRIES = 1000;

    private final Cache<Object, ObjectPool> cache;

    public AbstractPoolCache(final CentralCacheManager cacheManager, final String name) {
        final Loader<Object, ObjectPool> loader = new Loader<Object, ObjectPool>() {
            @Override
            public ObjectPool load(final Object key) throws Exception {
                final GenericObjectPoolConfig config = new GenericObjectPoolConfig();
                config.setMaxTotal(1000);
                config.setMaxIdle(1000);
                config.setBlockWhenExhausted(false);

                final GenericObjectPool<PoolItem<V>> pool = new GenericObjectPool<>(new ObjectFactory<>(AbstractPoolCache.this, key), config);
                pool.setAbandonedConfig(new AbandonedConfig());

                return pool;
            }
        };

        final CacheConfiguration<Object, ObjectPool> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(Object.class, ObjectPool.class,
                ResourcePoolsBuilder.heap(MAX_CACHE_ENTRIES))
//                .withExpiry(Expirations.timeToIdleExpiration(Duration.of(1, TimeUnit.MINUTES)))
//                .withExpiry(Expirations.timeToLiveExpiration(Duration.of(24, TimeUnit.HOURS)))
                .withLoaderWriter(loader)
                .build();

        cache = cacheManager.createCache(name, cacheConfiguration);
    }

    protected abstract V internalCreateValue(Object key);

    @SuppressWarnings("unchecked")
    protected PoolItem<V> internalBorrowObject(final K key, final boolean usePool) {
        try {
            if (!usePool) {
                return new PoolItem<>(key, internalCreateValue(key));
            }

            final ObjectPool<PoolItem<V>> pool = cache.get(key);
            return pool.borrowObject();
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    protected void internalReturnObject(final PoolItem<V> poolItem, final boolean usePool) {
        if (usePool) {
            try {
                final Object key = poolItem.getKey();
                final ObjectPool pool = cache.get(key);
                if (pool != null) {
                    pool.returnObject(poolItem);
                }
            } catch (final Exception e) {
                LOGGER.debug(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    private static class ObjectFactory<K, V> extends BasePooledObjectFactory<PoolItem<V>> {
        private final AbstractPoolCache<K, V> parent;
        private final Object key;

        ObjectFactory(final AbstractPoolCache<K, V> parent, final Object key) {
            this.parent = parent;
            this.key = key;
        }

        @Override
        public PoolItem<V> create() throws Exception {
            return new PoolItem<>(key, parent.internalCreateValue(key));
        }

        @Override
        public PooledObject<PoolItem<V>> wrap(final PoolItem<V> obj) {
            return new DefaultPooledObject<>(obj);
        }
    }

    protected void clear() {
        cache.clear();
    }
}
