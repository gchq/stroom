/*
 * Copyright 2018 Crown Copyright
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

package stroom.properties.api;

import java.util.Objects;

public class ConnectionPoolConfig {
    private static final String PROP_CACHE_PREPARED_STATEMENTS = "cachePrepStmts";
    private static final String PROP_PREPARED_STATEMENT_CACHE_SIZE = "prepStmtCacheSize";
    private static final String PROP_PREPARED_STATEMENT_CACHE_SQL_LIMIT = "prepStmtCacheSqlLimit";

    private final boolean cachePrepStmts;
    private final int prepStmtCacheSize;
    private final int prepStmtCacheSqlLimit;

    public ConnectionPoolConfig(final String prefix, final PropertyService propertyService) {
        this.cachePrepStmts = propertyService.getBooleanProperty(prefix + PROP_CACHE_PREPARED_STATEMENTS, true);
        this.prepStmtCacheSize = propertyService.getIntProperty(prefix + PROP_PREPARED_STATEMENT_CACHE_SIZE + "|trace", 250);
        this.prepStmtCacheSqlLimit = propertyService.getIntProperty(prefix + PROP_PREPARED_STATEMENT_CACHE_SQL_LIMIT, 2048);
    }

    public boolean isCachePrepStmts() {
        return cachePrepStmts;
    }

    public int getPrepStmtCacheSize() {
        return prepStmtCacheSize;
    }

    public int getPrepStmtCacheSqlLimit() {
        return prepStmtCacheSqlLimit;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ConnectionPoolConfig that = (ConnectionPoolConfig) o;
        return cachePrepStmts == that.cachePrepStmts &&
                prepStmtCacheSize == that.prepStmtCacheSize &&
                prepStmtCacheSqlLimit == that.prepStmtCacheSqlLimit;
    }

    @Override
    public int hashCode() {

        return Objects.hash(cachePrepStmts, prepStmtCacheSize, prepStmtCacheSqlLimit);
    }
}
