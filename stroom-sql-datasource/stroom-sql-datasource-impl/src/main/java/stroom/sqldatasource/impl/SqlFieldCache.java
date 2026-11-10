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
import stroom.cache.api.StroomCache;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/// Remembers the columns returned by the SQL of each query, by document version and SQL.
///
/// It is filled when the columns are described and whenever a query runs, so the query editors can
/// list a query's fields without asking the database each time. A changed document has a new
/// version, so its old entries are never used. Callers check the user's permission on the document
/// before asking.
@Singleton
public class SqlFieldCache implements Clearable {

    private static final String CACHE_NAME = "SQL Data Source Field Cache";

    private final StroomCache<Key, List<SqlColumn>> cache;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    SqlFieldCache(final CacheManager cacheManager,
                  final Provider<SqlDataSourceConfig> configProvider) {
        this.cache = cacheManager.create(CACHE_NAME, () -> configProvider.get().getFieldCache());
    }

    /// Gets the columns for a query, describing them if they are not already known.
    ///
    /// @param doc       The SQL Data Source.
    /// @param sql       The query's SQL as written.
    /// @param describer Finds the columns if they are not known.
    /// @return The columns.
    public List<SqlColumn> get(final SqlDataSourceDoc doc,
                               final String sql,
                               final Function<String, List<SqlColumn>> describer) {
        return cache.get(Key.of(doc, sql), key -> List.copyOf(describer.apply(sql)));
    }

    /// Gets the columns for a query if they are known.
    ///
    /// @param doc The SQL Data Source.
    /// @param sql The query's SQL as written.
    /// @return The columns, or empty if they are not known.
    public Optional<List<SqlColumn>> getIfPresent(final SqlDataSourceDoc doc, final String sql) {
        return cache.getIfPresent(Key.of(doc, sql));
    }

    /// Records the columns a query returned.
    ///
    /// @param doc     The SQL Data Source.
    /// @param sql     The query's SQL as written.
    /// @param columns The columns.
    public void put(final SqlDataSourceDoc doc, final String sql, final List<SqlColumn> columns) {
        cache.put(Key.of(doc, sql), List.copyOf(columns));
    }

    @Override
    public void clear() {
        cache.clear();
    }


    // --------------------------------------------------------------------------------


    private record Key(String uuid, String version, String sql) {

        private static Key of(final SqlDataSourceDoc doc, final String sql) {
            return new Key(doc.getUuid(), doc.getVersion(), sql);
        }
    }
}
