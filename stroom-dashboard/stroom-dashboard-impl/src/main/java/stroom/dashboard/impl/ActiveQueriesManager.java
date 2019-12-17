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
import stroom.dashboard.impl.datasource.DataSourceProviderRegistry;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class ActiveQueriesManager implements Clearable {
    private static final String CACHE_NAME = "Active Queries";

    private final DataSourceProviderRegistry dataSourceProviderRegistry;
    private final SecurityContext securityContext;
    private final ICache<String, ActiveQueries> cache;

    @Inject
    ActiveQueriesManager(final CacheManager cacheManager,
                         final DataSourceProviderRegistry dataSourceProviderRegistry,
                         final SecurityContext securityContext,
                         final DashboardConfig dashboardConfig) {
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.securityContext = securityContext;
        cache = cacheManager.create(CACHE_NAME, dashboardConfig::getActiveQueriesCache, this::create, this::destroy);
    }

    private ActiveQueries create(final String key) {
        return new ActiveQueries(dataSourceProviderRegistry, securityContext);
    }

    private void destroy(final String key, final ActiveQueries value) {
        value.destroy();
    }

    public ActiveQueries get(final String key) {
        return cache.get(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
