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

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;

class ActiveQuery {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ActiveQuery.class);

    private final QueryKey queryKey;
    private final String userId;
    private final long creationTime;

    private volatile boolean destroy;
    private volatile DocRef docRef;
    private volatile DataSourceProvider dataSourceProvider;

    ActiveQuery(final QueryKey queryKey,
                final String userId) {
        LOGGER.trace(() -> "New ActiveQuery " + queryKey);
        this.queryKey = queryKey;
        this.userId = userId;
        this.creationTime = System.currentTimeMillis();
    }

    public String getUserId() {
        return userId;
    }

    public boolean started() {
        return dataSourceProvider != null && queryKey != null;
    }

    public synchronized void startNewSearch(final DocRef docRef, final DataSourceProvider dataSourceProvider) {
        if (destroy) {
            throw new RuntimeException("Destroyed");
        }
        this.docRef = docRef;
        this.dataSourceProvider = dataSourceProvider;
    }

    public synchronized SearchResponse search(final SearchRequest request) {
        if (!started()) {
            throw new EntityServiceException("The requested search has not started.");
        }
        if (destroy) {
            throw new EntityServiceException("The requested search has been destroyed.");
        }
        return dataSourceProvider.search(request);
    }

    public boolean keepAlive() {
        if (!destroy && started()) {
            LOGGER.trace(() -> "keepAlive: " + queryKey);
            return dataSourceProvider.keepAlive(queryKey);
        }
        return false;
    }

    public synchronized boolean destroy() {
        this.destroy = true;
        if (dataSourceProvider != null && queryKey != null) {
            return dataSourceProvider.destroy(queryKey);
        }
        return false;
    }

    public boolean isDestroy() {
        return destroy;
    }

    @Override
    public String toString() {
        return "ActiveQuery{" +
                "queryKey=" + queryKey +
                ", docRef=" + docRef +
                ", creationTime=" + creationTime +
                '}';
    }
}
