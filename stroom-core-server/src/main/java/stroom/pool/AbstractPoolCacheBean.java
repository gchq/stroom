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
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

import stroom.cache.AbstractCacheBean;
import net.sf.ehcache.CacheManager;

public abstract class AbstractPoolCacheBean<K, V> extends AbstractCacheBean<K, ObjectPool<PoolItem<K, V>>>
        implements PoolBean<K, V> {
    private static class ObjectFactory<K, V> extends BasePooledObjectFactory<PoolItem<K, V>> {
        private final AbstractPoolCacheBean<K, V> parent;
        private final K key;

        public ObjectFactory(final AbstractPoolCacheBean<K, V> parent, final K key) {
            this.parent = parent;
            this.key = key;
        }

        @Override
        public PoolItem<K, V> create() throws Exception {
            return new PoolItem<>(key, parent.createValue(key));
        }

        @Override
        public PooledObject<PoolItem<K, V>> wrap(final PoolItem<K, V> obj) {
            return new DefaultPooledObject<>(obj);
        }
    }

    private static final int MAX_CACHE_ENTRIES = 1000000;

    public AbstractPoolCacheBean(final CacheManager cacheManager, final String name) {
        super(cacheManager, name, MAX_CACHE_ENTRIES);
    }

    @Override
    protected ObjectPool<PoolItem<K, V>> create(final K key) {
        final GenericObjectPool<PoolItem<K, V>> pool = new GenericObjectPool<>(new ObjectFactory<>(this, key));
        return pool;
    }

    protected abstract V createValue(K key);

    @Override
    public PoolItem<K, V> borrowObject(final K key) {
        try {
            final ObjectPool<PoolItem<K, V>> pool = get(key);
            return pool.borrowObject();
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void returnObject(final PoolItem<K, V> poolItem) {
        try {
            final K key = poolItem.getKey();
            final ObjectPool<PoolItem<K, V>> pool = getQuiet(key);
            if (pool != null) {
                pool.returnObject(poolItem);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
