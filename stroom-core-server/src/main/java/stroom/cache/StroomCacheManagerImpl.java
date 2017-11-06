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

package stroom.cache;

import org.ehcache.Cache;
import org.ehcache.config.ResourcePool;
import org.ehcache.config.ResourcePools;
import org.ehcache.config.SizedResourcePool;
import org.ehcache.core.HumanReadable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.FindCacheInfoCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.PageRequest;
import stroom.pool.CacheUtil;
import stroom.util.cache.CentralCacheManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StroomCacheManagerImpl implements StroomCacheManager, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomCacheManagerImpl.class);

    private final CentralCacheManager cacheManager;

    @Inject
    public StroomCacheManagerImpl(final CentralCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public BaseResultList<CacheInfo> find(final FindCacheInfoCriteria criteria) throws RuntimeException {
        final PageRequest pageRequest = criteria.obtainPageRequest();

        List<CacheInfo> list = findCaches(criteria);

        // Trim the list to the specified range.
        if (pageRequest.getLength() != null && pageRequest.getLength() < list.size()) {
            int from = 0;
            int to = 0;
            if (pageRequest.getOffset() != null) {
                from = pageRequest.getOffset().intValue();
            }

            to = from + pageRequest.getLength();

            final List<CacheInfo> shortList = new ArrayList<>(pageRequest.getLength());
            for (int i = from; i < to; i++) {
                shortList.add(list.get(i));
            }
            list = shortList;
        }

        // Return a base result list.
        final int cacheCount = cacheManager.getCaches().size();
        final boolean more = pageRequest.getOffset() + list.size() < cacheCount;
        return new BaseResultList<>(list, pageRequest.getOffset(), Long.valueOf(cacheCount), more);
    }

    private List<CacheInfo> findCaches(final FindCacheInfoCriteria criteria) {
        final List<String> cacheNames = cacheManager.getCaches()
                .keySet()
                .stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        final List<CacheInfo> list = new ArrayList<>(cacheNames.size());
        for (final String cacheName : cacheNames) {
            boolean include = true;
            if (criteria != null && criteria.getName() != null) {
                include = criteria.getName().isMatch(cacheName);
            }

            if (include) {
                final Cache cache = cacheManager.getCaches().get(cacheName);


                if (cache != null) {
                    final Map<String, Map<String, String>> resourcePoolDetails = new HashMap<>();
                    final ResourcePools resourcePools = cache.getRuntimeConfiguration().getResourcePools();
                    resourcePools.getResourceTypeSet().forEach(resourceType -> {
                        final Map<String, String> details = resourcePoolDetails.computeIfAbsent("Tier " + resourceType.getTierHeight(), k -> new HashMap<>());

                        final ResourcePool resourcePool = resourcePools.getPoolForResource(resourceType);
                        details.put("Name", resourceType.getResourcePoolClass().getSimpleName());
                        details.put("Persistable", String.valueOf(resourceType.isPersistable()));
                        details.put("Required Serialisation", String.valueOf(resourceType.requiresSerialization()));

                        if (resourcePool instanceof SizedResourcePool) {
                            final SizedResourcePool sizedResourcePool = (SizedResourcePool) resourcePool;
                            details.put("Size", String.valueOf(sizedResourcePool.getSize()) + " " + sizedResourcePool.getUnit().toString());
                        }
                    });

                    resourcePoolDetails.computeIfAbsent("Expiry", k -> new HashMap<>())
                    .put("Expiry", cache.getRuntimeConfiguration().getExpiry().toString());

                    final CacheInfo info = new CacheInfo(cacheName, resourcePoolDetails);
                    list.add(info);
                }
            }
        }
        return list;
    }

//    @Override
//    @StroomFrequencySchedule("1m")
//    public void evictExpiredElements() {
//        cacheManager.getCaches().forEach((k, v) -> {
//            LOGGER.debug("Evicting cache entries for " + k);
//            try {
//                v.evictExpiredElements();
//            } catch (final Exception e) {
//                LOGGER.error(e.getMessage(), e);
//            }
//        });
//    }

    /**
     * Removes all items from all caches.
     */
    @Override
    public void clear() {
        cacheManager.getCaches().forEach((k, v) -> {
            LOGGER.debug("Clearing cache entries for " + k);
            try {
                CacheUtil.removeAll(v);
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        });

//        final String[] cacheNames = cacheManager.getCacheNames();
//        for (final String cacheName : cacheNames) {
//            final Ehcache cache = cacheManager.getEhcache(cacheName);
//            if (cache != null) {
//                CacheUtil.clear(cache);
//            }
//        }
    }

    /**
     * Clears all items from named cache.
     */
    @Override
    public Long findClear(final FindCacheInfoCriteria criteria) {
        final List<CacheInfo> caches = findCaches(criteria);
        for (final CacheInfo cacheInfo : caches) {
            final String cacheName = cacheInfo.getName();
            final Cache cache = cacheManager.getCaches().get(cacheName);
            if (cache != null) {
                CacheUtil.removeAll(cache);
            } else {
                LOGGER.error("Unable to find cache with name '" + cacheName + "'");
            }
        }
        return null;
    }

    @Override
    public FindCacheInfoCriteria createCriteria() {
        return new FindCacheInfoCriteria();
    }
}
