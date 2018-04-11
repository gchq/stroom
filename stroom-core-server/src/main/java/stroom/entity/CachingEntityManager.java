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

package stroom.entity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.Entity;
import stroom.entity.shared.SummaryDataRow;
import stroom.entity.util.HqlBuilder;
import stroom.entity.util.SqlBuilder;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.FlushModeType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class CachingEntityManager implements StroomEntityManager, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingEntityManager.class);

    private final StroomEntityManager stroomEntityManager;

    private Cache<Object, Optional<Object>> cache;

    @Inject
    @SuppressWarnings("unchecked")
    CachingEntityManager(final StroomEntityManager stroomEntityManager,
                         final CacheManager cacheManager) {
        this.stroomEntityManager = stroomEntityManager;

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.MINUTES);
        cache = cacheBuilder.build();
        cacheManager.registerCache("Entity Cache", cacheBuilder, cache);
    }

    @Override
    public void flush() {
        stroomEntityManager.flush();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Entity> T loadEntity(final Class<?> clazz, final T entity) {
        T result = null;
        if (entity != null && entity.isPersistent() && entity.getPrimaryKey() != null) {
            final List<Object> key = Arrays.asList("loadEntity", clazz, entity.getPrimaryKey());

            // Try and get a cached method result from the cache.
            try {
                final Optional<Object> cached = cache.get(key, () -> Optional.ofNullable(stroomEntityManager.loadEntity(clazz, entity)));
                if (cached.isPresent()) {
                    result = (T) cached.get();
                }
            } catch (final ExecutionException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        } else {
            result = stroomEntityManager.loadEntity(clazz, entity);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Entity> T loadEntityById(final Class<?> clazz, final long id) {
        final List<Object> key = Arrays.asList("loadEntityById", clazz, id);

        // Try and get a cached method result from the cache.
        try {
            final Optional<Object> optional = cache.get(key, () -> Optional.ofNullable(stroomEntityManager.loadEntityById(clazz, id)));
            return (T) optional.orElse(null);
        } catch (final ExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
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
    public <T extends Entity> void detach(final T entity) {
        stroomEntityManager.detach(entity);
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

        final List<Object> key = Arrays.asList("executeCachedQueryResultList", sql.toString(), sql.getArgs(), criteria);

        // Try and get a cached method result from the cache.
        try {
            final Optional<Object> optional = cache.get(key, () -> Optional.ofNullable(stroomEntityManager.executeQueryResultList(sql, criteria, allowCaching)));
            return (List) optional.orElse(null);
        } catch (final ExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
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

//    @Override
//    public void setFlushMode(final FlushModeType mode) {
//        stroomEntityManager.setFlushMode(mode);
//    }

    @Override
    public void clearContext() {
        clear();
        stroomEntityManager.clearContext();
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }
}
