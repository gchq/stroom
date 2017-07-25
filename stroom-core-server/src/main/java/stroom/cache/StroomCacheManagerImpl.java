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

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.FindCacheInfoCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.PageRequest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class StroomCacheManagerImpl implements StroomCacheManager, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomCacheManagerImpl.class);

    @Resource
    private CacheManager cacheManager;

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
        final int cacheCount = cacheManager.getCacheNames().length;
        final boolean more = pageRequest.getOffset() + list.size() < cacheCount;
        return new BaseResultList<>(list, pageRequest.getOffset(), Long.valueOf(cacheCount), more);
    }

    private List<CacheInfo> findCaches(final FindCacheInfoCriteria criteria) {
        final String[] cacheNames = cacheManager.getCacheNames();
        Arrays.sort(cacheNames);

        final List<CacheInfo> list = new ArrayList<>(cacheNames.length);
        for (final String cacheName : cacheNames) {
            boolean include = true;
            if (criteria != null && criteria.getName() != null) {
                include = criteria.getName().isMatch(cacheName);
            }

            if (include) {
                final Ehcache cache = cacheManager.getEhcache(cacheName);
                final CacheInfo info = CacheUtil.getInfo(cache);
                if (info != null) {
                    list.add(info);
                }
            }
        }
        return list;
    }

    /**
     * Removes all items from all caches.
     */
    @Override
    public void clear() {
        final String[] cacheNames = cacheManager.getCacheNames();
        for (final String cacheName : cacheNames) {
            final Ehcache cache = cacheManager.getEhcache(cacheName);
            if (cache != null) {
                CacheUtil.clear(cache);
            }
        }
    }

    /**
     * Clears all items from named cache.
     */
    @Override
    public Long findClear(final FindCacheInfoCriteria criteria) {
        final List<CacheInfo> caches = findCaches(criteria);
        for (final CacheInfo cacheInfo : caches) {
            final String cacheName = cacheInfo.getName();
            final Ehcache cache = cacheManager.getEhcache(cacheName);
            if (cache != null) {
                CacheUtil.clear(cache);
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
