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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.impl.datasource.DataSourceProviderRegistry;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.security.api.Security;
import stroom.security.api.SecurityContext;
import stroom.security.shared.UserToken;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class ActiveQueries {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveQueries.class);

    private final ConcurrentHashMap<DashboardQueryKey, ActiveQuery> activeQueries = new ConcurrentHashMap<>();

    private final DataSourceProviderRegistry dataSourceProviderRegistry;
    private final SecurityContext securityContext;
    private final Security security;

    ActiveQueries(final DataSourceProviderRegistry dataSourceProviderRegistry,
                  final SecurityContext securityContext,
                  final Security security) {
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.securityContext = securityContext;
        this.security = security;
    }

    void destroyUnusedQueries(final Set<DashboardQueryKey> keys) {
        // Kill off any searches that are no longer required by the UI.
        Iterator<Entry<DashboardQueryKey, ActiveQuery>> iterator = activeQueries.entrySet().iterator();
        while (iterator.hasNext()) {
            final Entry<DashboardQueryKey, ActiveQuery> entry = iterator.next();
            final DashboardQueryKey queryKey = entry.getKey();
            final ActiveQuery activeQuery = entry.getValue();
            if (keys == null || !keys.contains(queryKey)) {
                final Boolean success = security.asUserResult(activeQuery.getUserToken(), () -> dataSourceProviderRegistry.getDataSourceProvider(activeQuery.getDocRef())
                        .map(provider -> provider.destroy(new QueryKey(queryKey.getUuid())))
                        .orElseGet(() -> {
                            LOGGER.warn("Unable to destroy query with key {} as provider {} cannot be found",
                                    queryKey.getUuid(),
                                    activeQuery.getDocRef().getType());
                            return Boolean.TRUE;
                        }));

                if (Boolean.TRUE.equals(success)) {
                    // Remove the collector from the available searches as it is no longer required by the UI.
                    iterator.remove();
                }
            }
        }
    }

    ActiveQuery getExistingQuery(final DashboardQueryKey queryKey) {
        return activeQueries.get(queryKey);
    }

    ActiveQuery addNewQuery(final DashboardQueryKey queryKey, final DocRef docRef) {
        final UserToken userToken = securityContext.getUserToken();
        if (userToken == null) {
            throw new RuntimeException("No user is currently logged in");
        }
        final ActiveQuery activeQuery = new ActiveQuery(docRef, userToken);
        final ActiveQuery existing = activeQueries.put(queryKey, activeQuery);
        if (existing != null) {
            throw new RuntimeException(
                    "Existing active query found in active query map for '" + queryKey.toString() + "'");
        }
        return activeQuery;
    }

    void destroy() {
        destroyUnusedQueries(null);
    }
}
