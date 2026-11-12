/*
 * Copyright 2026 Crown Copyright
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

package stroom.dataconnection.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/// Builds the Hikari connection pools that JDBC Data Connections use.
final class JdbcDataSourceFactory {

    private static final String POOL_NAME_PREFIX = "DataConnection-";
    private static final long CONNECTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);

    private static final AtomicLong POOL_COUNTER = new AtomicLong();

    private JdbcDataSourceFactory() {
        // Static methods only.
    }

    /// Creates a connection pool. Every connection it hands out is read-only, with auto-commit off.
    ///
    /// Creating the pool opens a first connection, so a wrong URL, host or password is reported here.
    ///
    /// @param connection  The resolved connection to pool.
    /// @param maxPoolSize The most connections the pool may hold.
    /// @return The pool, which the caller must close.
    static HikariDataSource create(final ResolvedJdbcConnection connection, final int maxPoolSize) {
        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(POOL_NAME_PREFIX + POOL_COUNTER.incrementAndGet());
        hikariConfig.setJdbcUrl(connection.url());
        hikariConfig.setUsername(connection.username());
        hikariConfig.setPassword(connection.password());
        connection.connectionProperties().forEach(hikariConfig::addDataSourceProperty);
        hikariConfig.setReadOnly(true);
        // Without auto-commit each query runs in a transaction, which matters for two reasons.
        // PostgreSQL's driver only makes a connection read-only by starting read-only transactions,
        // so with auto-commit on, setReadOnly(true) has no effect. And PostgreSQL only fetches rows
        // in batches inside a transaction; with auto-commit on it reads the whole result into memory.
        // Hikari rolls back the transaction when the connection goes back to the pool.
        hikariConfig.setAutoCommit(false);
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        // Idle connections are closed rather than kept warm, as most data sources are queried rarely.
        hikariConfig.setMinimumIdle(0);
        hikariConfig.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        return new HikariDataSource(hikariConfig);
    }
}
