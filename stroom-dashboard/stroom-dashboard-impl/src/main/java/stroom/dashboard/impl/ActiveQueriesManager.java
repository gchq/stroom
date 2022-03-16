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

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.query.api.v2.QueryKey;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Clearable;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class ActiveQueriesManager implements Clearable {

    private static final String CACHE_NAME = "Active Queries";

    private final SecurityContext securityContext;
    private final ICache<QueryKey, ActiveQuery> cache;

    @Inject
    ActiveQueriesManager(final CacheManager cacheManager,
                         final SecurityContext securityContext,
                         final DashboardConfig dashboardConfig) {
        this.securityContext = securityContext;
        cache = cacheManager
                .create(CACHE_NAME, dashboardConfig::getActiveQueriesCache, null, this::destroy);
    }

    private void destroy(final QueryKey key, final ActiveQuery value) {
        securityContext.asProcessingUser(value::destroy);
    }

    public void put(final QueryKey key, final ActiveQuery activeQuery) {
        cache.put(key, activeQuery);
    }

    public Optional<ActiveQuery> getOptional(final QueryKey key) {
        return cache.getOptional(key);
    }

    public void remove(final QueryKey key) {
        cache.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
