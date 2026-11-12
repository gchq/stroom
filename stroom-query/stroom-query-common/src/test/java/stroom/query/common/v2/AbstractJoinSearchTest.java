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

import stroom.lmdb.LmdbLibrary;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.node.api.NodeInfo;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.OffsetRange;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.JoinSearchRunner.SearchStarter;
import stroom.query.language.MockDataSourceResolver;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.util.io.ByteSize;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/// The set-up shared by the tests of searches with joins: a [JoinSearchRunner] with real
/// coprocessors, an in-memory result store and LMDB join tables, and in-memory data sources served
/// by a fake search starter.
abstract class AbstractJoinSearchTest {

    static final List<Map<String, String>> EVENTS = List.of(
            Map.of("UserId", "a", "Host", "h1"),
            Map.of("UserId", "b", "Host", "h2"),
            Map.of("UserId", "c", "Host", "h3"),
            Map.of("UserId", "a", "Host", "h4"));
    static final List<Map<String, String>> HR = List.of(
            Map.of("Username", "a", "Department", "Sales"),
            Map.of("Username", "b", "Department", "IT"),
            Map.of("Username", "d", "Department", "Unused"));

    @TempDir
    Path tempDir;
    ExecutorService executor;
    JoinTables joinTables;
    CoprocessorsFactory coprocessorsFactory;
    ResultStoreFactory resultStoreFactory;
    final List<SearchRequest> startedRequests = Collections.synchronizedList(new ArrayList<>());
    final List<ResultStore> createdStores = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        createdStores.forEach(ResultStore::destroy);
        executor.close();
    }

    Run run(final JoinSearchRunner runner, final String stroomQl, final FakeSources sources) {
        return run(runner, stroomQl, sources, new AtomicReference<>());
    }

    // Runs a query, giving the placeholder to the holder before the search starts.
    Run run(final JoinSearchRunner runner,
            final String stroomQl,
            final FakeSources sources,
            final AtomicReference<ResultStore> placeholderHolder) {
        final SearchRequest request = parse(stroomQl).copy().key(new QueryKey(UUID.randomUUID().toString())).build();
        final ResultStore placeholder = runner.createPlaceholder(request);
        placeholderHolder.set(placeholder);
        createdStores.add(placeholder);
        final AtomicReference<ResultStore> searchStore = new AtomicReference<>(placeholder);
        final SearchStarter starter = childRequest -> {
            startedRequests.add(childRequest);
            final ResultStore store = sources.start(childRequest);
            createdStores.add(store);
            return store;
        };
        runner.start(request, placeholder, starter, searchStore::set);
        return new Run(request, placeholder, searchStore);
    }

    SearchRequest parse(final String stroomQl) {
        final SearchRequestFactory factory = new SearchRequestFactory(
                (keywordGroup, parentTableSettings) -> null,
                MockDataSourceResolver.getInstance(),
                () -> criteria -> ResultPage.createUnboundedList(List.of(QueryField.createText("x"))),
                MockSecurityContext.getInstance());
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        final SearchRequest in = new SearchRequest(
                null, null, Query.builder().build(), new ArrayList<ResultRequest>(0), dateTimeSettings, false);
        final ExpressionContext expressionContext = ExpressionContext.builder()
                .dateTimeSettings(dateTimeSettings)
                .maxStringLength(100)
                .build();
        return factory.create(stroomQl, in, expressionContext);
    }

    JoinSearchRunner createRunner(final long joinMaxRows) {
        final SearchResultStoreConfig config = new SearchResultStoreConfig(
                null, null, null, null, null, null, null,
                ResultStoreLmdbConfig.builder()
                        .localDir("search_results")
                        .maxStoreSize(ByteSize.ofMebibytes(100))
                        .build(),
                null,
                joinMaxRows,
                null);
        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig();
        final LmdbEnvDirFactory lmdbEnvDirFactory = new LmdbEnvDirFactory(
                new LmdbLibrary(pathCreator, tempDirProvider, () -> lmdbLibraryConfig), pathCreator);
        joinTables = new JoinTables(lmdbEnvDirFactory, () -> config);
        final MapDataStoreFactory dataStoreFactory = new MapDataStoreFactory(() -> config);
        final SizesProvider sizesProvider = () -> Sizes.create(1_000_000);
        coprocessorsFactory = new CoprocessorsFactory(
                dataStoreFactory, new ExpressionContextFactory(), sizesProvider, () -> executor, () -> joinTables);
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");
        resultStoreFactory = new ResultStoreFactory(
                sizesProvider,
                MockSecurityContext.getInstance(),
                nodeInfo,
                new ResultStoreSettingsFactory(),
                dataStoreFactory,
                new ExpressionPredicateFactory(),
                () -> executor);
        return new JoinSearchRunner(
                coprocessorsFactory, resultStoreFactory, joinTables, new SimpleTaskContextFactory(), executor,
                () -> config,
                MockSecurityContext.getInstance());
    }

    static void awaitComplete(final ResultStore store) {
        try {
            assertThat(store.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    // The values of the named columns of every row of a store, as text, once it is complete.
    static List<List<String>> rows(final ResultStore store, final String... columnNames) {
        awaitComplete(store);
        final DataStore dataStore = store.getData(SearchRequestFactory.TABLE_COMPONENT_ID);
        final List<Column> columns = dataStore.getColumns();
        final List<Integer> indexes = new ArrayList<>();
        for (final String name : columnNames) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).getName().equals(name)) {
                    indexes.add(i);
                    break;
                }
            }
        }
        final List<List<String>> rows = new ArrayList<>();
        dataStore.fetch(columns, OffsetRange.UNBOUNDED, OpenGroups.NONE, null, IdentityItemMapper.INSTANCE, item -> {
            final List<String> row = new ArrayList<>();
            for (final int index : indexes) {
                final Val val = item.getValue(index);
                row.add(val == null || !val.type().isValue()
                        ? ""
                        : val.toString());
            }
            rows.add(row);
        }, null);
        return rows;
    }


    // --------------------------------------------------------------------------------


    // A search with joins under way: its request, its placeholder and the store polls would find.
    record Run(SearchRequest request, ResultStore placeholder, AtomicReference<ResultStore> searchStore) {

        // Waits for another store to take the placeholder's place, and returns it.
        ResultStore awaitSearchStore() throws InterruptedException {
            final long end = System.currentTimeMillis() + 10_000;
            while (searchStore.get() == placeholder && System.currentTimeMillis() < end) {
                if (placeholder.isComplete()) {
                    throw new AssertionError("The join search failed: " + placeholder.getErrors());
                }
                Thread.sleep(10);
            }
            assertThat(searchStore.get()).isNotSameAs(placeholder);
            return searchStore.get();
        }
    }


    // --------------------------------------------------------------------------------


    // In-memory data sources, searched as a provider would search them: a store with the request's
    // coprocessors, given the source's rows in the background, filtered by the query's equals terms.
    final class FakeSources {

        final Map<String, List<Map<String, String>>> tables = Map.of("events", EVENTS, "hr", HR);
        final Map<String, CountDownLatch> heldBack = new ConcurrentHashMap<>();
        final Map<String, CountDownLatch> started = new ConcurrentHashMap<>();
        final Map<String, Boolean> terminated = new ConcurrentHashMap<>();
        final Map<String, Boolean> failing = new ConcurrentHashMap<>();
        final Map<String, Boolean> warning = new ConcurrentHashMap<>();
        final Map<String, Runnable> onStart = new ConcurrentHashMap<>();

        void holdBack(final String name) {
            heldBack.put(name, new CountDownLatch(1));
            started.put(name, new CountDownLatch(1));
        }

        void release(final String name) {
            heldBack.get(name).countDown();
        }

        void awaitStarted(final String name) throws InterruptedException {
            assertThat(started.get(name).await(10, TimeUnit.SECONDS)).isTrue();
        }

        boolean wasTerminated(final String name) {
            return terminated.getOrDefault(name, false);
        }

        void fail(final String name) {
            failing.put(name, true);
        }

        void warn(final String name) {
            warning.put(name, true);
        }

        // Runs the action while the named source's search is being started, before its store is
        // returned.
        void whileStarting(final String name, final Runnable action) {
            onStart.put(name, action);
        }

        ResultStore start(final SearchRequest request) {
            final SearchRequest keyed = request.getKey() == null
                    ? request.copy().key(new QueryKey(UUID.randomUUID().toString())).build()
                    : request;
            final String name = keyed.getQuery().getDataSource().getName();
            // Created as most providers create them, from the request's parts rather than the request.
            final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                    keyed.getSearchRequestSource(),
                    keyed.getDateTimeSettings(),
                    keyed.getKey(),
                    coprocessorsFactory.createSettings(keyed),
                    keyed.getQuery().getParams(),
                    DataStoreSettings.createBasicSearchResultStoreSettings());
            final ResultStore store = resultStoreFactory.create(keyed.getSearchRequestSource(), coprocessors);
            store.setSearchProcess(new SearchProcess() {
                @Override
                public SearchTaskProgress getSearchTaskProgress() {
                    return null;
                }

                @Override
                public void onTerminate() {
                    terminated.put(name, true);
                    NullSafeLatch.countDown(heldBack.get(name));
                }
            });
            executor.execute(() -> feed(name, keyed.getQuery(), coprocessors, store));
            final Runnable action = onStart.get(name);
            if (action != null) {
                action.run();
            }
            return store;
        }

        void feed(final String name,
                          final Query query,
                          final CoprocessorsImpl coprocessors,
                          final ResultStore store) {
            try {
                NullSafeLatch.countDown(started.get(name));
                final CountDownLatch latch = heldBack.get(name);
                if (latch != null) {
                    latch.await(10, TimeUnit.SECONDS);
                }
                if (terminated.getOrDefault(name, false)) {
                    return;
                }
                if (failing.getOrDefault(name, false)) {
                    store.addError(new RuntimeException(name + " is down"));
                    return;
                }
                if (warning.getOrDefault(name, false)) {
                    coprocessors.getErrorConsumer().add(Severity.WARNING, () -> name + " is slow");
                }
                final FieldIndex fieldIndex = coprocessors.getFieldIndex();
                for (final Map<String, String> source : tables.get(name)) {
                    if (matches(query.getExpression(), source)) {
                        final Val[] row = new Val[fieldIndex.size()];
                        for (int i = 0; i < row.length; i++) {
                            final String value = source.get(fieldIndex.getField(i));
                            row[i] = value == null
                                    ? ValNull.INSTANCE
                                    : ValString.create(value);
                        }
                        coprocessors.accept(row);
                    }
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final RuntimeException e) {
                store.addError(e);
            } finally {
                store.signalComplete();
            }
        }

        // True if the row has every value the query's equals terms ask for.
        boolean matches(final ExpressionOperator expression, final Map<String, String> source) {
            if (expression == null || expression.getChildren() == null) {
                return true;
            }
            for (final ExpressionItem item : expression.getChildren()) {
                if (item instanceof final ExpressionTerm term
                    && !term.getValue().equals(source.get(term.getField()))) {
                    return false;
                } else if (item instanceof final ExpressionOperator operator && !matches(operator, source)) {
                    return false;
                }
            }
            return true;
        }
    }


    // --------------------------------------------------------------------------------


    static final class NullSafeLatch {

        static void countDown(final CountDownLatch latch) {
            if (latch != null) {
                latch.countDown();
            }
        }
    }
}
