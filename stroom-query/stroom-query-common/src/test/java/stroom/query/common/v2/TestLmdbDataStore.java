/*
 * Copyright 2017-2024 Crown Copyright
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

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.lmdb.LmdbLibrary;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestLmdbDataStore extends AbstractDataStoreTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdbDataStore.class);

    private Path tempDir;
    private ExecutorService executorService;

    @BeforeEach
    void setup(@TempDir final Path tempDir) {
        this.tempDir = tempDir;
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    void after() {
        executorService.shutdown();
    }

    @Override
    DataStore create(final SearchRequestSource searchRequestSource,
                     final QueryKey queryKey,
                     final String componentId,
                     final TableSettings tableSettings,
                     final SearchResultStoreConfig resultStoreConfig,
                     final DataStoreSettings dataStoreSettings,
                     final String subDirectory) {
        final FieldIndex fieldIndex = new FieldIndex();

        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig();
        final LmdbEnvDirFactory lmdbEnvDirFactory = new LmdbEnvDirFactory(
                new LmdbLibrary(pathCreator, tempDirProvider, () -> lmdbLibraryConfig), pathCreator);
        final LmdbEnvDir lmdbEnvDir = lmdbEnvDirFactory
                .builder()
                .config(resultStoreConfig.getLmdbConfig())
                .subDir(subDirectory)
                .build();
        final LmdbEnv.Builder lmdbEnvBuilder = LmdbEnv
                .builder()
                .config(resultStoreConfig.getLmdbConfig())
                .lmdbEnvDir(lmdbEnvDir);
        final ErrorConsumerImpl errorConsumer = new ErrorConsumerImpl();
        return new LmdbDataStore(
                searchRequestSource,
                lmdbEnvBuilder,
                resultStoreConfig,
                queryKey,
                componentId,
                tableSettings,
                new ExpressionContext(),
                fieldIndex,
                Collections.emptyMap(),
                dataStoreSettings,
                () -> executorService,
                errorConsumer,
                new ByteBufferFactoryImpl());
    }

    @Test
    void testBigValues() {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final ColumnFormatter columnFormatter = new ColumnFormatter(formatterFactory);

        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamSubstituteUtil.makeParam("Text"))
                        .format(Format.TEXT)
                        .group(0)
                        .build())
                .addColumns(Column.builder()
                        .id("Text2")
                        .name("Text2")
                        .expression(ParamSubstituteUtil.makeParam("Text2"))
                        .format(Format.TEXT)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        Metrics.setEnabled(true);
        Metrics.measure("Added data", () -> {
            for (int i = 0; i < 300_000; i++) {
                final Val val = ValString.create("Text " + i + "test".repeat(1000));
                dataStore.accept(Val.of(val, val));
            }

            // Wait for all items to be added.
            try {
                dataStore.getCompletionState().signalComplete();
                dataStore.getCompletionState().awaitCompletion();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
        Metrics.report();

        Metrics.measure("Retrieved data", () -> {
            // Make sure we only get 50 results.
            final ResultRequest tableResultRequest = ResultRequest.builder()
                    .componentId("componentX")
                    .addMappings(tableSettings)
                    .requestedRange(new OffsetRange(0, 3000))
                    .build();
            final TableResultCreator tableComponentResultCreator = new TableResultCreator(columnFormatter);
            final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                    dataStore,
                    tableResultRequest);
            assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
            assertThat(searchResult.getTotalResults().intValue()).isEqualTo(50);
        });
        Metrics.report();
    }

    @Disabled
    @Test
    void testMultiThread2() {
        final int threadCount = 100;
        final Executor executor = Executors.newFixedThreadPool(threadCount * 2);
        final CompletableFuture<?>[] writers = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            writers[i] = CompletableFuture.runAsync(this::testMultiThread, executor);
        }
        CompletableFuture.allOf(writers).join();
    }

    @Disabled
    @Test
    void testMultiThread() {
        final int threadCount = 100;
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final ColumnFormatter columnFormatter = new ColumnFormatter(formatterFactory);

        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamSubstituteUtil.makeParam("Text"))
                        .format(Format.TEXT)
                        .group(0)
                        .build())
                .addColumns(Column.builder()
                        .id("Text2")
                        .name("Text2")
                        .expression(ParamSubstituteUtil.makeParam("Text2"))
                        .format(Format.TEXT)
                        .group(1)
                        .build())
                .addColumns(Column.builder()
                        .id("Text2")
                        .name("Text2")
                        .expression("first(" + ParamSubstituteUtil.makeParam("Text2") + ")")
                        .format(Format.TEXT)
                        .build())
                .addColumns(Column.builder()
                        .id("count")
                        .name("count")
                        .expression("count()")
                        .format(Format.NUMBER)
                        .build())
                .addColumns(Column.builder()
                        .id("countGroups")
                        .name("countGroups")
                        .expression("countGroups()")
                        .format(Format.NUMBER)
                        .build())
                .showDetail(true)
                .build();

        final DataStore dataStore = create(tableSettings);

        // Start reading data store.
        final AtomicBoolean reading = new AtomicBoolean(true);
        final CompletableFuture<?>[] readers = new CompletableFuture[threadCount];
        final CompletableFuture<?>[] writers = new CompletableFuture[threadCount];
        final Executor executor = Executors.newFixedThreadPool(threadCount * 2);

        for (int j = 0; j < threadCount; j++) {
            final int run = j;
            readers[j] = CompletableFuture.runAsync(() -> {
                LOGGER.info(() -> "Reading " + run);
                while (reading.get()) {
                    final ResultRequest tableResultRequest = ResultRequest.builder()
                            .componentId("componentX")
                            .addMappings(tableSettings)
                            .requestedRange(new OffsetRange(0, 3000))
                            .build();
                    final TableResultCreator tableComponentResultCreator = new TableResultCreator(columnFormatter);
                    final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                            dataStore,
                            tableResultRequest);
                    final Set<String> openGroups = searchResult
                            .getRows()
                            .stream()
                            .map(Row::getGroupKey)
                            .collect(Collectors.toSet());

                    final ResultRequest tableResultRequest2 = ResultRequest.builder()
                            .componentId("componentX")
                            .addMappings(tableSettings)
                            .requestedRange(new OffsetRange(0, 3000))
                            .openGroups(openGroups)
                            .build();

//                    if (!openGroups.isEmpty()) {
//                        LOGGER.info("Open groups: " + openGroups.size());
//                    }

                    final TableResultCreator tableComponentResultCreator2 = new TableResultCreator(columnFormatter);
                    final TableResult searchResult2 = (TableResult) tableComponentResultCreator2.create(
                            dataStore,
                            tableResultRequest2);


                    final Set<String> openGroups2 = searchResult2
                            .getRows()
                            .stream()
                            .map(Row::getGroupKey)
                            .collect(Collectors.toSet());

                    final ResultRequest tableResultRequest3 = ResultRequest.builder()
                            .componentId("componentX")
                            .addMappings(tableSettings)
                            .requestedRange(new OffsetRange(0, 3000))
                            .openGroups(openGroups2)
                            .build();

//                    if (!openGroups.isEmpty()) {
//                        LOGGER.info("Open groups: " + openGroups.size());
//                    }

                    final TableResultCreator tableComponentResultCreator3 = new TableResultCreator(columnFormatter);
                    final TableResult searchResult3 = (TableResult) tableComponentResultCreator2.create(
                            dataStore,
                            tableResultRequest3);
                }
            }, executor);
        }

        for (int j = 0; j < threadCount; j++) {
            final int run = j;
            writers[j] = CompletableFuture.runAsync(() -> {
                LOGGER.info(() -> "Writing " + run);
                for (int i = 0; i < 1_000_00; i++) {
                    final Val val1 = ValString.create("Text " + run);
                    final Val val2 = ValString.create("Text " + i);
                    dataStore.accept(Val.of(val1, val2));
                }
            }, executor);
        }
        CompletableFuture.allOf(writers).join();

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        reading.set(false);
        CompletableFuture.allOf(readers).join();

        Metrics.measure("Retrieved data", () -> {
            final ResultRequest tableResultRequest = ResultRequest.builder()
                    .componentId("componentX")
                    .addMappings(tableSettings)
                    .requestedRange(new OffsetRange(0, 3000))
                    .build();
            final TableResultCreator tableComponentResultCreator = new TableResultCreator(columnFormatter);
            final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                    dataStore,
                    tableResultRequest);
        });
        Metrics.report();
    }

    @Test
    void testReload() throws Exception {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final ColumnFormatter columnFormatter = new ColumnFormatter(formatterFactory);

        final TableSettings tableSettings = TableSettings.builder()
                .addColumns(Column.builder()
                        .id("StreamId")
                        .name("StreamId")
                        .expression(ParamSubstituteUtil.makeParam("StreamId"))
                        .format(Format.NUMBER)
                        .build())
                .addColumns(Column.builder()
                        .id("EventId")
                        .name("EventId")
                        .expression(ParamSubstituteUtil.makeParam("EventId"))
                        .format(Format.NUMBER)
                        .build())
                .addColumns(Column.builder()
                        .id("EventTime")
                        .name("EventTime")
                        .expression(ParamSubstituteUtil.makeParam("EventTime"))
                        .format(Format.DATE_TIME)
                        .build())
                .build();

        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final SearchResultStoreConfig resultStoreConfig = new SearchResultStoreConfig();
        final DataStoreSettings dataStoreSettings = DataStoreSettings.createAnalyticStoreSettings();
        final SearchRequestSource searchRequestSource = SearchRequestSource
                .builder()
                .sourceType(SourceType.TABLE_BUILDER_ANALYTIC)
                .build();
        LmdbDataStore dataStore = (LmdbDataStore)
                create(
                        searchRequestSource,
                        queryKey,
                        "0",
                        tableSettings,
                        resultStoreConfig,
                        dataStoreSettings,
                        "reload");

        for (int i = 1; i <= 100; i++) {
            for (int j = 1; j <= 100; j++) {
                final Val streamId = ValLong.create(i);
                final Val eventId = ValLong.create(j);
                final Val eventTime = ValLong.create(System.currentTimeMillis());
                dataStore.accept(Val.of(streamId, eventId, eventTime));
            }
        }

        // Wait for all items to be added.
        CurrentDbState currentDbState = dataStore.sync();
        assertThat(currentDbState.getStreamId()).isEqualTo(100);
        assertThat(currentDbState.getEventId()).isEqualTo(100);

        // Make sure we only get 50 results.
        final ResultRequest tableResultRequest = ResultRequest.builder()
                .componentId("0")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 50))
                .build();
        final TableResultCreator tableComponentResultCreator = new TableResultCreator(columnFormatter);
        TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(10000);

        // Close the db.
        dataStore.getCompletionState().signalComplete();
        dataStore.getCompletionState().awaitCompletion();
        dataStore.close();

        // Try and open the datastore again.
        LmdbDataStore dataStore2 = (LmdbDataStore)
                create(
                        searchRequestSource,
                        queryKey,
                        "0",
                        tableSettings,
                        resultStoreConfig,
                        dataStoreSettings,
                        "reload");

        currentDbState = dataStore2.sync();
        assertThat(currentDbState.getStreamId()).isEqualTo(100);
        assertThat(currentDbState.getEventId()).isEqualTo(100);

        // Make sure we only get 50 results.
        searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore2,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(10000);

        // Load some more data.
        for (int i = 101; i <= 200; i++) {
            for (int j = 101; j <= 200; j++) {
                final Val streamId = ValLong.create(i);
                final Val eventId = ValLong.create(j);
                final Val eventTime = ValLong.create(System.currentTimeMillis());
                dataStore2.accept(Val.of(streamId, eventId, eventTime));
            }
        }

        // Wait for all items to be added.
        currentDbState = dataStore2.sync();
        assertThat(currentDbState.getStreamId()).isEqualTo(200);
        assertThat(currentDbState.getEventId()).isEqualTo(200);

        // Make sure we still only get 50 results.
        searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore2,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(20000);
    }

    @Test
    void basicTest() {
        super.basicTest();
    }

    @Test
    void nestedTest() {
        super.nestedTest();
    }

    @Test
    void noValuesTest() {
        super.noValuesTest();
    }

    @Test
    void sortedTextTest() {
        super.sortedTextTest();
    }

    @Test
    void sortedNumberTest() {
        super.sortedNumberTest();
    }

    @Test
    void sortedCountedTextTest1() {
        super.sortedCountedTextTest1();
    }

    @Test
    void sortedCountedTextTest2() {
        super.sortedCountedTextTest2();
    }

    @Test
    void sortedCountedTextTest3() {
        super.sortedCountedTextTest3();
    }

    @Test
    void firstLastSelectorTest() {
        super.firstLastSelectorTest();
    }
}
