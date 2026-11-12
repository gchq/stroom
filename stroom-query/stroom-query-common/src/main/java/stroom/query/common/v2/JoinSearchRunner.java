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

import stroom.query.api.Column;
import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.ParamUtil;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
import stroom.query.api.TableSettings;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/// Runs searches whose queries have joins, an alias or a bracketed query in `from`.
///
/// Such a search starts with a placeholder result store, which polls see as an incomplete search with
/// no rows. A background task then searches each joined source, as a child search of its own that is
/// held by this search rather than by the [ResultStoreManager], and builds its join table from the
/// child's rows. Once every table is built, the first source is searched as its query would be with
/// no joins, but with its rows going through the joins (see [CoprocessorsFactory]), and the result
/// store of that search takes the placeholder's place. When the first source is a bracketed query,
/// that query is searched as a child too, and its rows are given to a result store of the query's own
/// through the joins.
///
/// Each child search runs as the user who ran the query, so a join never shows the user rows they
/// can't see. Terminating or destroying the search terminates or destroys its children, and
/// destroying the result store that takes the placeholder's place deletes the join tables.
@Singleton
public class JoinSearchRunner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JoinSearchRunner.class);

    private static final String CHILD_COMPONENT_ID = "join";
    private static final long AWAIT_INTERVAL_SECONDS = 1;

    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final JoinTables joinTables;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;
    private final Provider<SearchResultStoreConfig> resultStoreConfigProvider;
    private final SecurityContext securityContext;
    private final JoinPushdown joinPushdown;

    @Inject
    public JoinSearchRunner(final CoprocessorsFactory coprocessorsFactory,
                            final ResultStoreFactory resultStoreFactory,
                            final JoinTables joinTables,
                            final TaskContextFactory taskContextFactory,
                            final Executor executor,
                            final Provider<SearchResultStoreConfig> resultStoreConfigProvider,
                            final SecurityContext securityContext,
                            final JoinPushdown joinPushdown) {
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.joinTables = joinTables;
        this.taskContextFactory = taskContextFactory;
        this.executor = executor;
        this.resultStoreConfigProvider = resultStoreConfigProvider;
        this.securityContext = securityContext;
        this.joinPushdown = joinPushdown;
    }

    /// Creates the placeholder result store of a search with joins. Put it where polls find it, then
    /// call [#start(SearchRequest, ResultStore, SearchStarter, Consumer)].
    ///
    /// @param searchRequest The search's request, with its query key.
    /// @return The placeholder result store.
    public ResultStore createPlaceholder(final SearchRequest searchRequest) {
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                searchRequest,
                DataStoreSettings.createBasicSearchResultStoreSettings());
        return resultStoreFactory.create(searchRequest.getSearchRequestSource(), coprocessors);
    }

    /// Starts a search with joins in the background.
    ///
    /// @param searchRequest The search's request, with its query key.
    /// @param placeholder   The search's placeholder result store.
    /// @param starter       Starts a search of one data source: a child search, or the search of the
    ///                      first source.
    /// @param onSearchStore Takes the result store that replaces the placeholder once the first source
    ///                      is searched. If the placeholder is gone by then, it must destroy the store.
    ///                      The placeholder is cleared once the store has taken its place.
    public void start(final SearchRequest searchRequest,
                      final ResultStore placeholder,
                      final SearchStarter starter,
                      final Consumer<ResultStore> onSearchStore) {
        final JoinSearch joinSearch = new JoinSearch(searchRequest, placeholder, starter, onSearchStore);
        placeholder.setSearchProcess(new SearchProcess() {
            @Override
            public SearchTaskProgress getSearchTaskProgress() {
                return joinSearch.getSearchTaskProgress();
            }

            @Override
            public void onTerminate() {
                joinSearch.terminatePlaceholder();
            }
        });
        executor.execute(taskContextFactory.context("StroomQL join search", taskContext -> joinSearch.run()));
    }


    // --------------------------------------------------------------------------------


    /// Starts the search of one data source.
    @FunctionalInterface
    public interface SearchStarter {

        /// Starts a search.
        ///
        /// @param searchRequest The request, with no query key for a child search, or the search's own
        ///                      key for the search of the first source.
        /// @return The result store of the search.
        ResultStore start(SearchRequest searchRequest);
    }


    // --------------------------------------------------------------------------------


    // One search with joins, from its placeholder until the first source's search takes over.
    private final class JoinSearch implements SearchProcess {

        private final SearchRequest searchRequest;
        private final Query query;
        private final ResultStore placeholder;
        private final SearchStarter starter;
        private final Consumer<ResultStore> onSearchStore;
        private final long submitTimeMs = System.currentTimeMillis();
        private final List<ResultStore> children = new ArrayList<>();
        private volatile boolean terminated;
        private volatile String info = "Starting";
        // The store that took the placeholder's place, once one has, and whether the placeholder has
        // been cleared since. Both are guarded by this object.
        private ResultStore handedOverTo;
        private boolean placeholderRetired;

        private JoinSearch(final SearchRequest searchRequest,
                           final ResultStore placeholder,
                           final SearchStarter starter,
                           final Consumer<ResultStore> onSearchStore) {
            this.searchRequest = searchRequest;
            this.query = searchRequest.getQuery();
            this.placeholder = placeholder;
            this.starter = starter;
            this.onSearchStore = onSearchStore;
        }

        private void run() {
            final List<JoinTable> tables = new ArrayList<>();
            final List<JoinPushdown.Keys> keys = new ArrayList<>();
            boolean tablesRegistered = false;
            try {
                checkNoNestedJoins();
                final List<JoinPlan> plans = JoinPlan.createAll(
                        query,
                        // The placeholder's field index holds the fields the query names, as the
                        // first source's search will.
                        placeholder.getCoprocessors().getFieldIndex(),
                        placeholder.getCoprocessors().getExpressionContext(),
                        ParamUtil.createParamMap(query.getParams()));
                for (final JoinPlan plan : plans) {
                    final JoinPushdown.Keys tableKeys = JoinPushdown.Keys.forJoin(plan.getJoin());
                    tables.add(buildTable(plan, tableKeys));
                    keys.add(tableKeys);
                }

                joinTables.register(searchRequest.getKey(), query, tables, placeholder.getUserRef());
                tablesRegistered = true;
                checkNotTerminated();
                if (query.getSourceTableSettings() == null) {
                    searchFirstSource(keys);
                } else {
                    searchBracketedFirstSource();
                }
            } catch (final JoinSearchTerminatedException e) {
                LOGGER.debug("Join search {} terminated", searchRequest.getKey());
                placeholder.signalComplete();
                cleanUp(tables, tablesRegistered);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Join search " + searchRequest.getKey() + " failed: " + e.getMessage(), e);
                placeholder.addError(e);
                placeholder.signalComplete();
                cleanUp(tables, tablesRegistered);
            } finally {
                destroyChildren();
            }
        }

        // Ending a search stops its tasks, which needs the Manage Tasks permission, so the result store
        // manager ends searches as the processing user, as these do for this search's own searches.
        private void terminate(final ResultStore store) {
            securityContext.asProcessingUser(store::terminate);
        }

        private void destroy(final ResultStore store) {
            securityContext.asProcessingUser(store::destroy);
        }

        // Puts the store in the placeholder's place, then clears the placeholder. A terminate that came
        // while the store was being started, when it went to this search, terminates the store.
        private void handOver(final ResultStore searchStore) {
            synchronized (this) {
                handedOverTo = searchStore;
                if (terminated) {
                    terminate(searchStore);
                }
            }
            onSearchStore.accept(searchStore);
            synchronized (this) {
                placeholderRetired = true;
            }
            // Cleared rather than destroyed, as destroying it would terminate it, and the placeholder's
            // terminate now goes to the store that took its place.
            ((CoprocessorsImpl) placeholder.getCoprocessors()).clear();
        }

        // A terminate of the placeholder: before the hand-over it stops this search, then, until the
        // placeholder is cleared, it terminates the store that took its place.
        private synchronized void terminatePlaceholder() {
            if (handedOverTo == null) {
                onTerminate();
            } else if (!placeholderRetired) {
                terminate(handedOverTo);
            }
        }

        // A bracketed query in a search with joins can't have joins of its own yet.
        private void checkNoNestedJoins() {
            final boolean nested = NullSafe.list(query.getJoins())
                    .stream()
                    .anyMatch(join -> join.getQuery().needsJoinSupport());
            if (nested) {
                throw new IllegalArgumentException("A bracketed query in a join line can't have joins, an alias or "
                                                   + "a bracketed query of its own yet");
            }
        }

        // Searches a joined source as a child search and builds its join table from the child's rows,
        // collecting its keys for pushing the join down.
        private JoinTable buildTable(final JoinPlan plan, final JoinPushdown.Keys keys) {
            final QueryJoin join = plan.getJoin();
            final String alias = join.getAlias();
            info = "Searching '" + alias + "' to join";
            final JoinTable table = joinTables.create(searchRequest.getKey(), alias, plan.getStoredFields());
            try {
                final ResultStore child = searchChild(join.getQuery(), getJoinedTableSettings(join, plan), alias);
                final List<String> storedFields = plan.getStoredFields();
                readRows(child, alias, storedFields, row -> {
                    final String key = plan.getRightKey(row);
                    table.add(key, row);
                    keys.add(key);
                });
                table.complete();
                info = "Joining " + ModelStringUtil.formatCsv(table.getRowCount()) + " rows of '" + alias + "'";
                destroyChild(child);
                return table;
            } catch (final RuntimeException e) {
                table.close();
                throw e;
            }
        }

        // The table settings of a joined source's child search: its bracketed query's, or a column for
        // each field its join table holds. Either way it is limited to one row more than a join table
        // may hold, so a joined source with too many rows fills the table rather than being cut short.
        private TableSettings getJoinedTableSettings(final QueryJoin join, final JoinPlan plan) {
            final long maxRows = resultStoreConfigProvider.get().getJoinMaxRows() + 1;
            final TableSettings bracketedTableSettings = join.getTableSettings();
            if (bracketedTableSettings != null) {
                // Keep the query's own limit, as in 'limit 10', at the top level if it is lower, and its
                // limits for any nested groups.
                final List<Long> maxResults = new ArrayList<>(NullSafe.list(bracketedTableSettings.getMaxResults()));
                if (maxResults.isEmpty()) {
                    maxResults.add(maxRows);
                } else {
                    maxResults.set(0, Math.min(maxResults.getFirst(), maxRows));
                }
                return bracketedTableSettings.copy().maxResults(maxResults).build();
            }
            final List<Column> columns = new ArrayList<>();
            final List<String> storedFields = plan.getStoredFields();
            for (int i = 0; i < storedFields.size(); i++) {
                final String field = storedFields.get(i);
                columns.add(Column.builder()
                        .id("field" + i)
                        .name(field)
                        .expression("${" + field + "}")
                        .build());
            }
            return TableSettings.builder()
                    .columns(columns)
                    .extractValues(true)
                    .maxResults(List.of(maxRows))
                    .build();
        }

        // Searches the first source, with its rows going through the joins, and puts its result store
        // in the placeholder's place. Joins with few keys narrow the search of the first source.
        private void searchFirstSource(final List<JoinPushdown.Keys> keys) {
            info = "Searching";
            final Query pushedDown = joinPushdown.apply(query, keys, ParamUtil.createParamMap(query.getParams()));
            final ResultStore searchStore = starter.start(searchRequest.copy().query(pushedDown).build());
            copyMessages(placeholder, searchStore, null);
            handOver(searchStore);
        }

        // Searches the bracketed query in from as a child search, then gives its rows to a result
        // store of the query's own, through the joins, in the placeholder's place.
        private void searchBracketedFirstSource() {
            final String name = Objects.requireNonNullElse(query.getAlias(), "from");
            info = "Searching the bracketed query in from";
            final Query innerQuery = query.copy()
                    .alias(null)
                    .joins(null)
                    .sourceTableSettings(null)
                    .build();
            final ResultStore child = searchChild(innerQuery, query.getSourceTableSettings(), name);

            final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                    searchRequest,
                    DataStoreSettings.createBasicSearchResultStoreSettings());
            final ResultStore searchStore = resultStoreFactory.create(
                    searchRequest.getSearchRequestSource(), coprocessors);
            // Terminating the store that takes the placeholder's place stops the rows being given.
            searchStore.setSearchProcess(this);
            copyMessages(placeholder, searchStore, null);
            handOver(searchStore);
            info = "Joining the rows of the bracketed query in from";
            try {
                final FieldIndex fieldIndex = coprocessors.getFieldIndex();
                final List<String> columnNames = getColumnNames(child);
                readRows(child, name, columnNames, values -> {
                    final Val[] row = new Val[fieldIndex.size()];
                    for (int i = 0; i < columnNames.size(); i++) {
                        final Integer pos = fieldIndex.getPos(columnNames.get(i));
                        if (pos != null) {
                            row[pos] = values[i];
                        }
                    }
                    coprocessors.accept(row);
                });
            } catch (final JoinSearchTerminatedException e) {
                LOGGER.debug("Join search {} terminated while joining its rows", searchRequest.getKey());
            } catch (final RuntimeException e) {
                searchStore.addError(e);
            } finally {
                searchStore.signalComplete();
            }
        }

        // Starts a child search and waits for it to complete, adding its warnings and errors to the
        // placeholder. A child that failed fails the join search.
        private ResultStore searchChild(final Query childQuery,
                                        final TableSettings tableSettings,
                                        final String name) {
            checkNotTerminated();
            final ResultRequest resultRequest = ResultRequest.builder()
                    .componentId(CHILD_COMPONENT_ID)
                    .searchRequestSource(searchRequest.getSearchRequestSource())
                    .mappings(List.of(tableSettings))
                    .resultStyle(ResultStyle.TABLE)
                    .fetch(Fetch.ALL)
                    .groupSelection(new GroupSelection())
                    .build();
            final SearchRequest childRequest = new SearchRequest(
                    searchRequest.getSearchRequestSource(),
                    null,
                    childQuery.copy().params(query.getParams()).build(),
                    List.of(resultRequest),
                    searchRequest.getDateTimeSettings(),
                    false);
            final ResultStore child = starter.start(childRequest);
            synchronized (children) {
                children.add(child);
            }
            if (terminated) {
                terminate(child);
            }
            awaitCompletion(child);
            copyMessages(child, placeholder, name);
            final boolean failed = child.getErrors()
                    .stream()
                    .anyMatch(message -> message.getSeverity().greaterThanOrEqual(Severity.ERROR));
            if (failed) {
                throw new IllegalStateException("The search of '" + name + "' to join failed");
            }
            return child;
        }

        private void awaitCompletion(final ResultStore child) {
            try {
                while (!child.awaitCompletion(AWAIT_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
                    checkNotTerminated();
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new JoinSearchTerminatedException();
            }
            checkNotTerminated();
        }

        // Reads every row of a complete child search, as the values of the given columns.
        private void readRows(final ResultStore child,
                              final String name,
                              final List<String> fields,
                              final Consumer<Val[]> consumer) {
            final DataStore dataStore = child.getData(CHILD_COMPONENT_ID);
            final List<Column> columns = dataStore.getColumns();
            final Map<String, Integer> columnIndexes = new HashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                columnIndexes.putIfAbsent(columns.get(i).getName(), i);
            }
            final int[] indexes = fields.stream()
                    .mapToInt(field -> columnIndexes.getOrDefault(field, -1))
                    .toArray();
            LOGGER.debug("Reading the rows of '{}' to join", name);
            // A failure, such as a full join table, is kept and thrown once the fetch is done, as the
            // store logs any failure thrown from within a fetch as an error.
            final AtomicReference<RuntimeException> failure = new AtomicReference<>();
            dataStore.fetch(
                    columns,
                    OffsetRange.UNBOUNDED,
                    OpenGroups.NONE,
                    null,
                    IdentityItemMapper.INSTANCE,
                    item -> {
                        if (failure.get() == null) {
                            try {
                                checkNotTerminated();
                                final Val[] row = new Val[indexes.length];
                                for (int i = 0; i < indexes.length; i++) {
                                    if (indexes[i] >= 0) {
                                        row[i] = item.getValue(indexes[i]);
                                    }
                                }
                                consumer.accept(row);
                            } catch (final RuntimeException e) {
                                failure.set(e);
                            }
                        }
                    },
                    null);
            if (failure.get() != null) {
                throw failure.get();
            }
        }

        private List<String> getColumnNames(final ResultStore child) {
            return child.getData(CHILD_COMPONENT_ID)
                    .getColumns()
                    .stream()
                    .map(Column::getName)
                    .toList();
        }

        // Copies warnings and errors from one store to another, naming the joined source they came from.
        private void copyMessages(final ResultStore from, final ResultStore to, final String name) {
            for (final ErrorMessage message : from.getErrors()) {
                if (message.getSeverity().greaterThanOrEqual(Severity.WARNING)) {
                    final String text = name == null
                            ? message.getMessage()
                            : "'" + name + "': " + message.getMessage();
                    to.getCoprocessors().getErrorConsumer().add(message.getSeverity(), () -> text);
                }
            }
        }

        private void checkNotTerminated() {
            if (terminated) {
                throw new JoinSearchTerminatedException();
            }
        }

        private void cleanUp(final List<JoinTable> tables, final boolean tablesRegistered) {
            if (tablesRegistered) {
                joinTables.remove(searchRequest.getKey());
            } else {
                tables.forEach(JoinTable::close);
            }
        }

        private void destroyChild(final ResultStore child) {
            synchronized (children) {
                children.remove(child);
            }
            destroy(child);
        }

        private void destroyChildren() {
            final List<ResultStore> remaining;
            synchronized (children) {
                remaining = new ArrayList<>(children);
                children.clear();
            }
            remaining.forEach(child -> {
                try {
                    destroy(child);
                } catch (final RuntimeException e) {
                    LOGGER.error(() -> "Error destroying a joined source's search: " + e.getMessage(), e);
                }
            });
        }

        @Override
        public SearchTaskProgress getSearchTaskProgress() {
            return new SearchTaskProgress(
                    "StroomQL join search",
                    info,
                    placeholder.getUserRef(),
                    null,
                    placeholder.getNodeName(),
                    submitTimeMs,
                    System.currentTimeMillis());
        }

        @Override
        public void onTerminate() {
            terminated = true;
            final List<ResultStore> running;
            synchronized (children) {
                running = new ArrayList<>(children);
            }
            running.forEach(this::terminate);
        }
    }


    // --------------------------------------------------------------------------------


    // Thrown to stop a join search that was terminated.
    private static final class JoinSearchTerminatedException extends RuntimeException {

        private JoinSearchTerminatedException() {
            super("The search was terminated");
        }
    }
}
