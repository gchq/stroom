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

package stroom.entity.server;

import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.Entity;
import stroom.entity.shared.SummaryDataRow;
import stroom.util.cache.CentralCacheManager;

import javax.inject.Inject;
import javax.persistence.FlushModeType;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class CachingEntityManager implements StroomEntityManager, Clearable {
    private final StroomEntityManager stroomEntityManager;

    private Cache<Object, Object> cache;

    @Inject
    public CachingEntityManager(final StroomEntityManager stroomEntityManager, final CentralCacheManager cacheManager) {
        this.stroomEntityManager = stroomEntityManager;


//        ResourcePoolsBuilder.newResourcePoolsBuilder()
//                .heap(10, EntryUnit.ENTRIES)
//                .offheap(1, MemoryUnit.MB)
//                .disk(20, MemoryUnit.MB, true)
//            )


        //	<cache name="serviceCache" maxElementsInMemory="1000" eternal="false"
//    overflowToDisk="false" timeToIdleSeconds="60" timeToLiveSeconds="60" />

        final CacheConfiguration<Object, Object> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(Object.class, Object.class,
                ResourcePoolsBuilder.heap(1000))
                .withExpiry(Expirations.timeToLiveExpiration(Duration.of(1, TimeUnit.MINUTES)))
                .build();

        cache = cacheManager.createCache("serviceCache", cacheConfiguration);
    }

    @Override
    public void flush() {
        stroomEntityManager.flush();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Entity> T loadEntity(final Class<?> clazz, final T entity) {
        T result;
        if (entity != null && entity.isPersistent() && entity.getPrimaryKey() != null) {
            final List<Object> key = Arrays.asList("loadEntity", clazz, entity.getPrimaryKey());

            // Try and get a cached method result from the cache.
            final Object cached = cache.get(key);
            if (cached == null) {
                // We didn't find a cached result so get one and put it in the
                // cache.
                result = stroomEntityManager.loadEntity(clazz, entity);
                cache.put(key, result);
            } else {
                result = (T) cached;
            }

        } else {
            result = stroomEntityManager.loadEntity(clazz, entity);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Entity> T loadEntityById(final Class<?> clazz, final long id) {
        T result;
        final List<Object> key = Arrays.asList("loadEntityById", clazz, id);

        // Try and get a cached method result from the cache.
        final Object cached = cache.get(key);
        if (cached == null) {
            // We didn't find a cached result so get one and put it in the
            // cache.
            result = stroomEntityManager.loadEntityById(clazz, id);
            cache.put(key, result);
        } else {
            result = (T) cached;
        }

        return result;
    }

    @Override
    public <T extends Entity> T saveEntity(final T entity) {
        return stroomEntityManager.saveEntity(entity);
    }

    @Override
    public <T extends Entity> Boolean deleteEntity(final T entity) {
        return stroomEntityManager.deleteEntity(entity);
    }

    @Override
    public Long executeNativeUpdate(final SqlBuilder sql) {
        return stroomEntityManager.executeNativeUpdate(sql);
    }

    @Override
    public long executeNativeQueryLongResult(final SqlBuilder sql) {
        return stroomEntityManager.executeNativeQueryLongResult(sql);
    }

    @Override
    public BaseResultList<SummaryDataRow> executeNativeQuerySummaryDataResult(final SqlBuilder sql, final int numberKeys) {
        return stroomEntityManager.executeNativeQuerySummaryDataResult(sql, numberKeys);
    }

    @Override
    public List executeNativeQueryResultList(final SqlBuilder sql) {
        return stroomEntityManager.executeNativeQueryResultList(sql);
    }

    @Override
    public <T> List<T> executeNativeQueryResultList(final SqlBuilder sql, final Class<?> clazz) {
        return stroomEntityManager.executeNativeQueryResultList(sql, clazz);
    }

    @Override
    public List executeQueryResultList(final HqlBuilder sql) {
        return executeQueryResultList(sql, null, false);
    }

    @Override
    public List executeQueryResultList(final HqlBuilder sql, final BaseCriteria criteria) {
        return executeQueryResultList(sql, criteria, false);
    }

    @Override
    public List executeQueryResultList(final HqlBuilder sql, final BaseCriteria criteria, final boolean allowCaching) {
        if (!allowCaching) {
            return stroomEntityManager.executeQueryResultList(sql, criteria, allowCaching);
        }

        List result;
        final List<Object> key = Arrays.asList("executeCachedQueryResultList", sql.toString(), sql.getArgs(), criteria);

        // Try and get a cached method result from the cache.
        final Object cached = cache.get(key);
        if (cached == null) {
            // We didn't find a cached result so get one and put it in the
            // cache.
            result = stroomEntityManager.executeQueryResultList(sql, criteria, allowCaching);
            cache.put(key, result);
        } else {
            result = (List) cached;
        }

        return result;
    }

    @Override
    public long executeQueryLongResult(final HqlBuilder sql) {
        return stroomEntityManager.executeQueryLongResult(sql);

//        List result;
//        final List<Object> key = Arrays.asList("executeQueryResultList", sql.toString(), sql.getArgs(), criteria);
//
//        // Try and get a cached method result from the cache.
//        final Element element = cache.get(key);
//        if (element == null) {
//            // We didn't find a cached result so get one and put it in the
//            // cache.
//            result = stroomEntityManager.executeQueryResultList(sql, criteria);
//            cache.put(new Element(key, result));
//        } else {
//            result = (List) element.getObjectValue();
//        }
//
//        return result;
    }

    @Override
    public String runSubSelectQuery(final HqlBuilder sql, final boolean handleNull) {
        return stroomEntityManager.runSubSelectQuery(sql, handleNull);
    }

    @Override
    public boolean hasNativeColumn(final String nativeTable, final String nativeColumn) {
        return stroomEntityManager.hasNativeColumn(nativeTable, nativeColumn);
    }

    @Override
    public void shutdown() {
        stroomEntityManager.shutdown();
    }

    @Override
    public void setFlushMode(final FlushModeType mode) {
        stroomEntityManager.setFlushMode(mode);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
