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

package stroom.cache.service.impl;

import stroom.cache.api.StroomCache;
import stroom.cache.impl.CacheManagerImpl;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.TaskContextFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.cache.CacheIdentity;
import stroom.util.shared.cache.CacheInfo;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CacheManagerServiceImpl implements CacheManagerService, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheManagerServiceImpl.class);

    private final CacheManagerImpl cacheManager;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public CacheManagerServiceImpl(final CacheManagerImpl cacheManager,
                                   final SecurityContext securityContext,
                                   final TaskContextFactory taskContextFactory) {
        this.cacheManager = cacheManager;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    public List<String> getCacheNames() {
        return securityContext.secureResult(AppPermission.MANAGE_CACHE_PERMISSION, () ->
                cacheManager.getCacheNames()
                        .stream()
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList()));
    }

    @Override
    public List<CacheIdentity> getCacheIdentities() {
        return securityContext.secureResult(AppPermission.MANAGE_CACHE_PERMISSION, () ->
                cacheManager.getCacheIdentities()
                        .stream()
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList()));
    }

    @Override
    public List<CacheInfo> find(final FindCacheInfoCriteria criteria) {
        return securityContext.secureResult(AppPermission.MANAGE_CACHE_PERMISSION, () -> {
            final List<String> cacheNames = cacheManager.getCacheNames()
                    .stream()
                    .sorted(Comparator.naturalOrder())
                    .toList();

            final List<CacheInfo> list = new ArrayList<>(cacheNames.size());
            for (final String cacheName : cacheNames) {
                boolean include = true;
                if (criteria != null && criteria.getName() != null) {
                    include = criteria.getName().isMatch(cacheName);
                }

                if (include) {
                    final StroomCache<?, ?> cache = cacheManager.getCaches().get(cacheName);
                    final CacheInfo cacheInfo = cache.getCacheInfo();
                    list.add(cacheInfo);
                }
            }
            return list;
        });
    }

    @Override
    public void evictExpiredElements() {
        taskContextFactory.current().info(() -> "Evicting expired elements");
        cacheManager.getCaches().forEach((name, cache) -> {
            LOGGER.debug("Evicting cache entries for " + name);
            try {
                cache.evictExpiredElements();
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    /**
     * Removes all items from all caches and rebuilds it from config
     */
    @Override
    public void clear() {
        cacheManager.getCaches().forEach((name, cache) -> {
            LOGGER.debug("Clearing cache entries for " + name);
            try {
                cache.rebuild();
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    /**
     * Clears all items from named cache and rebuilds the cache from config
     */
    @Override
    public Long clear(final FindCacheInfoCriteria criteria) {
        return doCacheAction(criteria, StroomCache::rebuild);
    }

    @Override
    public Long evictExpiredElements(final FindCacheInfoCriteria criteria) {
        return doCacheAction(criteria, StroomCache::evictExpiredElements);
    }

    private Long doCacheAction(final FindCacheInfoCriteria criteria,
                               final Consumer<StroomCache<?, ?>> cacheAction) {
        final List<CacheInfo> caches = find(criteria);
        for (final CacheInfo cacheInfo : caches) {
            final String cacheName = cacheInfo.getName();
            final StroomCache<?, ?> cache = cacheManager.getCaches().get(cacheName);
            if (cache != null) {
                cacheAction.accept(cache);
            } else {
                LOGGER.error("Unable to find cache with name '" + cacheName + "'");
            }
        }
        return (long) caches.size();
    }
}
