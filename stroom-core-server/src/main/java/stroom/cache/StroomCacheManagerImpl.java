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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.FindCacheInfoCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.PageRequest;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheManager.CacheHolder;
import stroom.util.cache.CacheUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomFrequencySchedule;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StroomCacheManagerImpl implements StroomCacheManager, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomCacheManagerImpl.class);

    private final CacheManager cacheManager;

    @Inject
    public StroomCacheManagerImpl(final CacheManager cacheManager) {
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
        final long cacheCount = cacheManager.getCaches().size();
        return new BaseResultList<>(list, pageRequest.getOffset(), cacheCount, pageRequest.getOffset() + list.size() == cacheCount);
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
                final CacheHolder cacheHolder = cacheManager.getCaches().get(cacheName);


                if (cacheHolder != null) {
                    final Map<String, String> map = new HashMap<>();
                    map.put("Entries", String.valueOf(cacheHolder.getCache().size()));
                    addEntries(map, cacheHolder.getCacheBuilder().toString());
                    addEntries(map, cacheHolder.getCache().stats().toString());

                    map.forEach((k, v) -> {
                        if (k.startsWith("Expire")) {
                            try {
                                final long nanos = Long.valueOf(v);
                                map.put(k, ModelStringUtil.formatDurationString(nanos / 1000000, true));

                            } catch (final Exception e) {
                                // Ignore.
                            }
                        }
                    });

                    final CacheInfo info = new CacheInfo(cacheName, map);
                    list.add(info);
                }
            }
        }
        return list;
    }

    private void addEntries(final Map<String, String> map, String string) {
        if (string != null && !string.isEmpty()) {

            string = string.replaceAll("^[^{]*\\{", "");
            string = string.replaceAll("}.*", "");

            Arrays.stream(string.split(",")).forEach(pair -> {
                final String[] kv = pair.split("=");
                if (kv.length == 2) {
                    String key = kv[0].replaceAll("\\W", "");
                    String value = kv[1].replaceAll("\\D", "");
                    if (key.length() > 0) {
                        final char[] chars = key.toCharArray();
                        chars[0] = Character.toUpperCase(chars[0]);
                        key = new String(chars, 0, chars.length);
                    }

                    map.put(key, value);
                }
            });
        }
    }

    @Override
    @StroomFrequencySchedule("1m")
    public void evictExpiredElements() {
        cacheManager.getCaches().forEach((k, v) -> {
            LOGGER.debug("Evicting cache entries for " + k);
            try {
                v.getCache().cleanUp();
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    /**
     * Removes all items from all caches.
     */
    @Override
    public void clear() {
        cacheManager.getCaches().forEach((k, v) -> {
            LOGGER.debug("Clearing cache entries for " + k);
            try {
                CacheUtil.clear(v.getCache());
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    /**
     * Clears all items from named cache.
     */
    @Override
    public Long findClear(final FindCacheInfoCriteria criteria) {
        final List<CacheInfo> caches = findCaches(criteria);
        for (final CacheInfo cacheInfo : caches) {
            final String cacheName = cacheInfo.getName();
            final CacheHolder cacheHolder = cacheManager.getCaches().get(cacheName);
            if (cacheHolder != null) {
                CacheUtil.clear(cacheHolder.getCache());
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
