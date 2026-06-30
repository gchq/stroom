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

package stroom.meta.impl.db;

import stroom.cache.api.LoadingStroomCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.PatternUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WildCardHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WildCardHelper.class);

    private WildCardHelper() {
    }

    /**
     * For a list of wild-carded names, return 0-* IDs for each one.
     * If no wildCardedNames are provided, returns an empty map.
     *
     * @param wildCardedNames e.g. 'TEST_*' or 'TEST_FEED'
     * @param cache           The cache to use for getting/putting items. The cache's own load function
     *                        will not be used.
     * @param fetchFunc       Function to call to provide values if the cache get is a miss.
     */
    static Map<String, Integer> find(
            final List<String> wildCardedNames,
            final LoadingStroomCache<String, Integer> cache,
            final Function<List<String>, Map<String, Integer>> fetchFunc) {

        if (NullSafe.isEmptyCollection(wildCardedNames)) {
            return Collections.emptyMap();
        } else {
            final Map<String, Integer> nameToIdMap = new HashMap<>(wildCardedNames.size());

            final List<String> namesNotInCache = new ArrayList<>(wildCardedNames.size());

            for (final String name : wildCardedNames) {
                if (!NullSafe.isBlankString(name)) {
                    // We can't cache wildcard names as we don't know what they will match in the DB.
                    if (PatternUtil.containsWildCards(name)) {
                        namesNotInCache.add(name);
                    } else {
                        final Optional<Integer> optId = cache.getIfPresent(name);

                        optId.ifPresentOrElse(
                                id -> {
                                    // The cache load func creates items in the db, so add manually
                                    cache.put(name, id);
                                    nameToIdMap.put(name, id);
                                },
                                () -> {
                                    // Not in cache so try the db
                                    namesNotInCache.add(name);
                                });
                    }
                }
            }

            if (NullSafe.hasItems(namesNotInCache)) {
                final Map<String, Integer> dbFeedToIdMap = fetchFunc.apply(namesNotInCache);
                nameToIdMap.putAll(dbFeedToIdMap);
            }

            LOGGER.debug(() -> LogUtil.message("find called for wildCardedNames: '{}', returning: '{}'",
                    wildCardedNames, nameToIdMap.entrySet()
                            .stream()
                            .map(entry -> entry.getKey() + ":" + entry.getValue())
                            .collect(Collectors.joining(", "))));

            return nameToIdMap;
        }
    }
}
