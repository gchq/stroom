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

package stroom.dashboard.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.QueryKey;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveQueries {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveQueries.class);

    private final ConcurrentHashMap<DashboardQueryKey, ActiveQuery> activeQueries = new ConcurrentHashMap<>();

    private final DataSourceProviderRegistry dataSourceProviderRegistry;

    public ActiveQueries(final DataSourceProviderRegistry dataSourceProviderRegistry) {
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
    }

    public void destroyUnusedQueries(final Set<DashboardQueryKey> keys) {
        // Kill off any searches that are no longer required by the UI.
        Iterator<Entry<DashboardQueryKey, ActiveQuery>> iterator = activeQueries.entrySet().iterator();
        while (iterator.hasNext()) {
            final Entry<DashboardQueryKey, ActiveQuery> entry = iterator.next();
            final DashboardQueryKey queryKey = entry.getKey();
            final ActiveQuery activeQuery = entry.getValue();
            if (keys == null || !keys.contains(queryKey)) {
                // Terminate the associated search task.
                Boolean success = dataSourceProviderRegistry.getDataSourceProvider(activeQuery.getDocRef())
                        .map(provider -> provider.destroy(new QueryKey(queryKey.getUuid())))
                        .orElseGet(() -> {
                            LOGGER.warn("Unable to destroy query with key {} as provider {} cannot be found",
                                    queryKey.getUuid(),
                                    activeQuery.getDocRef().getType());
                            return Boolean.TRUE;
                        });

                if (Boolean.TRUE.equals(success)) {
                    // Remove the collector from the available searches as it is no longer required by the UI.
                    iterator.remove();
                }
            }
        }
    }

    public ActiveQuery getExistingQuery(final DashboardQueryKey queryKey) {
        return activeQueries.get(queryKey);
    }

    public ActiveQuery addNewQuery(final DashboardQueryKey queryKey, final DocRef docRef) {
        final ActiveQuery activeQuery = new ActiveQuery(docRef);
        final ActiveQuery existing = activeQueries.put(queryKey, activeQuery);
        if (existing != null) {
            throw new RuntimeException(
                    "Existing active query found in active query map for '" + queryKey.toString() + "'");
        }
        return activeQuery;
    }

    public void destroy() {
        destroyUnusedQueries(null);
    }
}
