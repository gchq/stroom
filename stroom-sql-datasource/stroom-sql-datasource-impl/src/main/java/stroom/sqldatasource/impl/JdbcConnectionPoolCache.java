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

package stroom.sqldatasource.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/// Holds one connection pool per distinct JDBC connection and lends out connections from them.
///
/// The cache is keyed on the [ResolvedJdbcConnection], so a change to a document's settings or to
/// its credential's secret leads to a new pool. The credential is resolved on every use, as that is
/// where the current user's permission to use it is checked. A pool dropped from the cache while in
/// use is closed only once its last borrower has finished.
///
/// Rebuilding the cache after its settings change replaces it without removing its entries, so the
/// old pools are not passed to the removal listener. Every pool made is therefore remembered, and
/// one that is no longer in the cache or in use is closed when a pool is next made or returned.
@Singleton
public class JdbcConnectionPoolCache implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JdbcConnectionPoolCache.class);

    private static final String CACHE_NAME = "SQL Data Source Connection Pool Cache";

    private final LoadingStroomCache<ResolvedJdbcConnection, HikariDataSource> cache;
    private final Map<HikariDataSource, UseState> useStates = new IdentityHashMap<>();
    // Every open pool made, and whether it is still being loaded into the cache, so not yet in it.
    private final Map<HikariDataSource, Boolean> openPools = new IdentityHashMap<>();
    private final JdbcConnectionResolver connectionResolver;
    private final JdbcDriverLoader driverLoader;

    /// Creates the cache. Guice supplies every argument.
    @Inject
    JdbcConnectionPoolCache(final CacheManager cacheManager,
                            final Provider<SqlDataSourceConfig> configProvider,
                            final JdbcConnectionResolver connectionResolver,
                            final JdbcDriverLoader driverLoader) {
        this.connectionResolver = connectionResolver;
        this.driverLoader = driverLoader;
        this.cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> configProvider.get().getConnectionPoolCache(),
                this::createPool,
                this::onRemoval);
    }

    /// Runs a function with a pooled, read-only connection, returning the connection to its pool after.
    ///
    /// @param settings The JDBC settings of a SQL Data Source.
    /// @param function What to do with the connection. It must not keep the connection.
    /// @param <R>      The type of the function's result.
    /// @return The function's result.
    /// @throws SQLException            If a connection cannot be had, or the function fails.
    /// @throws IllegalArgumentException If the settings break the administrator's controls.
    /// @throws RuntimeException         If the current user may not use the credential.
    public <R> R useConnection(final JdbcConnectionSettings settings,
                               final ConnectionFunction<R> function) throws SQLException {
        final ResolvedJdbcConnection resolvedConnection = connectionResolver.resolve(settings);
        final HikariDataSource pool = borrowPool(resolvedConnection);
        try (final Connection connection = pool.getConnection()) {
            return function.apply(connection);
        } finally {
            returnPool(pool);
        }
    }

    private HikariDataSource createPool(final ResolvedJdbcConnection resolvedConnection) {
        driverLoader.ensureLoaded();
        LOGGER.debug("Creating connection pool for {}", resolvedConnection);
        final HikariDataSource pool = JdbcDataSourceFactory.create(resolvedConnection,
                resolvedConnection.maxPoolSize());
        synchronized (this) {
            closeAbandonedPools();
            openPools.put(pool, Boolean.TRUE);
        }
        return pool;
    }

    private HikariDataSource borrowPool(final ResolvedJdbcConnection resolvedConnection) {
        // A pool can be dropped from the cache and closed between the cache handing it out and its use
        // being counted. If that happens, ask again, which builds a new pool.
        for (int attempt = 0; attempt < 2; attempt++) {
            final HikariDataSource pool = cache.get(resolvedConnection);
            synchronized (this) {
                // The cache has handed the pool out, so it has finished loading.
                openPools.replace(pool, Boolean.FALSE);
                if (!pool.isClosed()) {
                    useStates.computeIfAbsent(pool, ignored -> new UseState()).useCount++;
                    return pool;
                }
            }
        }
        throw new IllegalStateException("Unable to get an open connection pool for " + resolvedConnection.url());
    }

    private void returnPool(final HikariDataSource pool) {
        synchronized (this) {
            final UseState useState = useStates.get(pool);
            useState.useCount--;
            if (useState.useCount == 0) {
                useStates.remove(pool);
                if (useState.dropped) {
                    close(pool);
                }
                closeAbandonedPools();
            }
        }
    }

    private void onRemoval(final ResolvedJdbcConnection resolvedConnection, final HikariDataSource pool) {
        synchronized (this) {
            final UseState useState = useStates.get(pool);
            if (useState == null) {
                close(pool);
            } else {
                // Closed by the last borrower in returnPool.
                useState.dropped = true;
            }
        }
    }

    // Closes pools the cache no longer holds, which no one is using and which are not still loading.
    // Called while holding this object's lock.
    private void closeAbandonedPools() {
        final Set<HikariDataSource> cachedPools = Collections.newSetFromMap(new IdentityHashMap<>());
        cachedPools.addAll(cache.asMap().values());
        final Iterator<Entry<HikariDataSource, Boolean>> iterator = openPools.entrySet().iterator();
        while (iterator.hasNext()) {
            final Entry<HikariDataSource, Boolean> entry = iterator.next();
            final HikariDataSource pool = entry.getKey();
            final boolean loading = entry.getValue();
            if (!loading && !cachedPools.contains(pool) && !useStates.containsKey(pool)) {
                LOGGER.debug(() -> LogUtil.message(
                        "Closing connection pool {}, which is no longer in the cache", pool.getPoolName()));
                iterator.remove();
                closeQuietly(pool);
            }
        }
    }

    // Called while holding this object's lock.
    private void close(final HikariDataSource pool) {
        openPools.remove(pool);
        closeQuietly(pool);
    }

    private static void closeQuietly(final HikariDataSource pool) {
        try {
            pool.close();
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error closing connection pool {}: {}",
                    pool.getPoolName(), e.getMessage()), e);
        }
    }

    /// Closes every pool that is not in use, and marks the rest to close when their use ends.
    @Override
    public void clear() {
        cache.clear();
    }

    /// Work done with a borrowed connection.
    ///
    /// @param <R> The type of the result.
    @FunctionalInterface
    public interface ConnectionFunction<R> {

        /// Does the work.
        ///
        /// @param connection A read-only connection, valid only for the duration of the call.
        /// @return The result.
        /// @throws SQLException If the work fails.
        R apply(Connection connection) throws SQLException;
    }

    private static final class UseState {

        private int useCount;
        private boolean dropped;
    }
}
