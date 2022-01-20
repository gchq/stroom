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

import stroom.dashboard.shared.DashboardQueryKey;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

class ActiveQuery {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActiveQuery.class);

    private final DashboardQueryKey dashboardQueryKey;
    private final QueryKey queryKey;
    private final DocRef docRef;
    private final DataSourceProvider dataSourceProvider;
    private final long creationTime;

    ActiveQuery(final DashboardQueryKey dashboardQueryKey,
                final QueryKey queryKey,
                final DocRef docRef,
                final DataSourceProvider dataSourceProvider) {
        LOGGER.trace(() -> "New ActiveQuery " + queryKey);
        this.dashboardQueryKey = dashboardQueryKey;
        this.queryKey = queryKey;
        this.docRef = docRef;
        this.dataSourceProvider = dataSourceProvider;
        this.creationTime = System.currentTimeMillis();
    }

    public SearchResponse search(final SearchRequest request) {
        return dataSourceProvider.search(request);
    }

    public boolean keepAlive() {
        return dataSourceProvider.keepAlive(queryKey);
    }

    public boolean destroy() {
        return dataSourceProvider.destroy(queryKey);
    }

    @Override
    public String toString() {
        return "ActiveQuery{" +
                "dashboardQueryKey=" + dashboardQueryKey +
                ", queryKey=" + queryKey +
                ", docRef=" + docRef +
                ", creationTime=" + creationTime +
                '}';
    }
}
