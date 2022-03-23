/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.impl;

import stroom.query.api.v2.QueryKey;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class ActiveQueriesManager implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActiveQueriesManager.class);

    private final Map<QueryKey, ActiveQuery> cache = new ConcurrentHashMap<>();

    @Inject
    public ActiveQueriesManager() {
        // Keep active queries alive.
        Executors.newScheduledThreadPool(1).schedule(() ->
                cache.values().forEach(ActiveQuery::keepAlive), 1, TimeUnit.MINUTES);
    }

    public void put(final QueryKey key, final ActiveQuery activeQuery) {
        LOGGER.trace(() -> "put() " + key);
        cache.put(key, activeQuery);
        LOGGER.debug(() -> "ActiveQuery count = " + cache.size());
    }

    public Optional<ActiveQuery> getOptional(final QueryKey key) {
        return Optional.ofNullable(cache.get(key));
    }

    public void destroy(final QueryKey key) {
        LOGGER.trace(() -> "destroy() " + key);
        final ActiveQuery activeQuery = cache.remove(key);
        if (activeQuery != null) {
            activeQuery.destroy();
        } else {
            LOGGER.error("No active query");
        }
        LOGGER.debug(() -> "ActiveQuery count = " + cache.size());
    }

    @Override
    public void clear() {
        cache.forEach((k, v) -> {
            v.destroy();
            cache.remove(k);
        });
    }
}
