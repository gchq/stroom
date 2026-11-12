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

package stroom.query.common.v2;

import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/// Creates the join tables of searches with joins, and holds them by the search's query key from when
/// they are built until they are removed.
///
/// The tables are created in the search result store's LMDB directory, which is emptied when Stroom
/// starts, and hold at most the rows and bytes set in [SearchResultStoreConfig].
///
/// The coprocessors that apply a search's joins use its tables (see [#acquire(QueryKey)]), and the
/// tables are removed once the last use ends. On the node that ran the search they are built from its
/// joined sources' searches and removed by that search if no coprocessors ever use them. On any other
/// node that searches its first source they are a copy fetched from that node, held by the fetch
/// until that node's coprocessors use them (see [#registerAndAcquire(QueryKey, Query, List, UserRef)]).
@Singleton
public class JoinTables {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JoinTables.class);

    private final LmdbEnvDirFactory lmdbEnvDirFactory;
    private final Provider<SearchResultStoreConfig> resultStoreConfigProvider;
    private final Map<QueryKey, Registration> registrations = new ConcurrentHashMap<>();

    @Inject
    public JoinTables(final LmdbEnvDirFactory lmdbEnvDirFactory,
                      final Provider<SearchResultStoreConfig> resultStoreConfigProvider) {
        this.lmdbEnvDirFactory = lmdbEnvDirFactory;
        this.resultStoreConfigProvider = resultStoreConfigProvider;
    }

    /// Creates an empty join table for one join line of a search.
    ///
    /// @param queryKey The key of the search.
    /// @param alias    The join line's alias.
    /// @param fields   The joined source's fields the table holds, in the order of each row's values.
    /// @return The table, which the caller must close unless it is registered.
    public JoinTable create(final QueryKey queryKey, final String alias, final List<String> fields) {
        final SearchResultStoreConfig config = resultStoreConfigProvider.get();
        final LmdbEnvDir lmdbEnvDir = lmdbEnvDirFactory
                .builder()
                .config(config.getLmdbConfig())
                .subDir("join_" + queryKey + "_" + UUID.randomUUID())
                .build();
        final LmdbEnv.Builder lmdbEnvBuilder = LmdbEnv
                .builder()
                .config(config.getLmdbConfig())
                .lmdbEnvDir(lmdbEnvDir);
        final JoinLimits limits = new JoinLimits(config.getJoinMaxRows(), config.getJoinMaxSize().getBytes());
        return new JoinTable(alias, fields, lmdbEnvBuilder, limits);
    }

    /// Holds the query and complete join tables of a search, one table for each join line in order.
    /// From then on, coprocessors created for the search's key apply the joins (see
    /// [CoprocessorsFactory]).
    ///
    /// @param queryKey The key of the search.
    /// @param query    The search's query.
    /// @param tables   The tables, which may be empty for a search with no join lines.
    /// @param owner    The user who ran the search, the only user other than the processing user who
    ///                 may read its tables from another node.
    public void register(final QueryKey queryKey,
                         final Query query,
                         final List<JoinTable> tables,
                         final UserRef owner) {
        final Registration previous = registrations.put(
                queryKey, new Registration(query, List.copyOf(tables), owner, new AtomicInteger()));
        if (previous != null) {
            close(queryKey, previous.tables());
        }
    }

    /// Holds the join tables of a search, unless some are already held for it, and uses them, in one
    /// step. Two nodes' searches of the same search can then never close each other's tables.
    ///
    /// @param queryKey The key of the search.
    /// @param query    The search's query.
    /// @param tables   The tables to hold if none are held already. If some are, these are closed.
    /// @param owner    The user who ran the search.
    /// @return The registration now held and used, which the caller must release.
    public Registration registerAndAcquire(final QueryKey queryKey,
                                           final Query query,
                                           final List<JoinTable> tables,
                                           final UserRef owner) {
        final AtomicBoolean alreadyHeld = new AtomicBoolean();
        final Registration registration = registrations.compute(queryKey, (key, current) -> {
            final Registration held;
            if (current == null) {
                held = new Registration(query, List.copyOf(tables), owner, new AtomicInteger());
            } else {
                alreadyHeld.set(true);
                held = current;
            }
            held.users().incrementAndGet();
            return held;
        });
        if (alreadyHeld.get()) {
            close(queryKey, tables);
        }
        return registration;
    }

    /// Gets the query and join tables of a search, without using them.
    ///
    /// @param queryKey The key of the search.
    /// @return The search's registration, or empty if none is held for the search.
    public Optional<Registration> get(final QueryKey queryKey) {
        return Optional.ofNullable(registrations.get(queryKey));
    }

    /// Gets the query and join tables of a search for coprocessors that apply its joins. The tables
    /// are kept until every caller has released them (see [#release(QueryKey, Registration)]).
    ///
    /// @param queryKey The key of the search.
    /// @return The search's registration, or empty if none is held for the search.
    public Optional<Registration> acquire(final QueryKey queryKey) {
        final Registration registration = registrations.computeIfPresent(queryKey, (key, current) -> {
            current.users().incrementAndGet();
            return current;
        });
        return Optional.ofNullable(registration);
    }

    /// Ends a use of a search's tables. Once the last use ends, the tables are closed and deleted.
    ///
    /// @param queryKey     The key of the search.
    /// @param registration The registration [#acquire(QueryKey)] gave.
    public void release(final QueryKey queryKey, final Registration registration) {
        // The count and the removal change together, so an acquire can't take tables about to close.
        final AtomicBoolean removed = new AtomicBoolean();
        registrations.compute(queryKey, (key, current) -> {
            final int users = registration.users().decrementAndGet();
            if (current == registration && users <= 0) {
                removed.set(true);
                return null;
            }
            return current;
        });
        if (removed.get()) {
            close(queryKey, registration.tables());
        }
    }

    /// Closes and deletes the join tables of a search, if any are held, whether or not they are in use.
    ///
    /// @param queryKey The key of the search.
    public void remove(final QueryKey queryKey) {
        final Registration registration = registrations.remove(queryKey);
        if (registration != null) {
            close(queryKey, registration.tables());
        }
    }

    private static void close(final QueryKey queryKey, final List<JoinTable> tables) {
        for (final JoinTable table : tables) {
            try {
                table.close();
            } catch (final RuntimeException e) {
                LOGGER.error(() -> "Error closing a join table of search " + queryKey + ": " + e.getMessage(), e);
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// The query and join tables of a search with joins.
    ///
    /// @param query  The search's query.
    /// @param tables The join tables, one for each join line in order.
    /// @param owner  The user who ran the search.
    /// @param users  The number of coprocessors using the tables.
    public record Registration(Query query, List<JoinTable> tables, UserRef owner, AtomicInteger users) {

    }
}
