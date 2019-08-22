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

package stroom.config.common;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.IsConfig;

import java.util.Objects;

public class ConnectionPoolConfig implements IsConfig {
    private boolean cachePrepStmts = true;
    private int prepStmtCacheSize = 250;
    private int prepStmtCacheSqlLimit = 2048;

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    public boolean isCachePrepStmts() {
        return cachePrepStmts;
    }

    public void setCachePrepStmts(final boolean cachePrepStmts) {
        this.cachePrepStmts = cachePrepStmts;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    public int getPrepStmtCacheSize() {
        return prepStmtCacheSize;
    }

    public void setPrepStmtCacheSize(final int prepStmtCacheSize) {
        this.prepStmtCacheSize = prepStmtCacheSize;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    public int getPrepStmtCacheSqlLimit() {
        return prepStmtCacheSqlLimit;
    }

    public void setPrepStmtCacheSqlLimit(final int prepStmtCacheSqlLimit) {
        this.prepStmtCacheSqlLimit = prepStmtCacheSqlLimit;
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

    @Override
    public String toString() {
        return "ConnectionPoolConfig{" +
                "cachePrepStmts=" + cachePrepStmts +
                ", prepStmtCacheSize=" + prepStmtCacheSize +
                ", prepStmtCacheSqlLimit=" + prepStmtCacheSqlLimit +
                '}';
    }
    public static class Builder {
        private final ConnectionPoolConfig instance;

        public Builder() {
            this(new ConnectionPoolConfig());
        }

        public Builder(ConnectionPoolConfig instance) {
            this.instance = instance;
        }

        public Builder withCachePrepStmts(final boolean value) {
            this.instance.setCachePrepStmts(value);
            return this;
        }
        public Builder withPrepStmtCacheSize(final int value) {
            this.instance.setPrepStmtCacheSize(value);
            return this;
        }
        public Builder withPrepStmtCacheSqlLimit(final int value) {
            this.instance.setPrepStmtCacheSqlLimit(value);
            return this;
        }

        public ConnectionPoolConfig build() {
            return instance;
        }
    }
}
