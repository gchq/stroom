/*
 * Copyright 2024 Crown Copyright
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

package stroom.search.impl;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.docref.DocRef;
import stroom.lmdb.LmdbLibrary;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchResponse;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.AnnotationMapperFactory;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.DataStoreFactory;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.IdentityItemMapper;
import stroom.query.common.v2.LmdbDataStoreFactory;
import stroom.query.common.v2.MapDataStoreFactory;
import stroom.query.common.v2.OpenGroupsImpl;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreSettingsFactory;
import stroom.query.common.v2.SearchDebugUtil;
import stroom.query.common.v2.SearchResultStoreConfig;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.SizesProvider;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ParamKeys;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.security.api.UserIdentity;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.UserRef;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSearchResultCreation {

    // Make sure the search request is the same as the one we expected to make.
    private final Path resourcesDir = SearchDebugUtil.initialise();

    private DataStoreFactory dataStoreFactory;
    private ExecutorService executorService;

    @BeforeEach
    void setup(@TempDir final Path tempDir) {
        executorService = Executors.newCachedThreadPool();

        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig();
        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final LmdbEnvDirFactory lmdbEnvDirFactory = new LmdbEnvDirFactory(
                new LmdbLibrary(pathCreator, tempDirProvider, () -> lmdbLibraryConfig), pathCreator);
        dataStoreFactory = new LmdbDataStoreFactory(
                lmdbEnvDirFactory,
                SearchResultStoreConfig::new,
                pathCreator,
                () -> executorService,
                new MapDataStoreFactory(SearchResultStoreConfig::new),
                new ByteBufferFactoryImpl(),
                new ExpressionPredicateFactory(),
                AnnotationMapperFactory.NO_OP,
                null);
    }

    @AfterEach
    void afterEach() {
        executorService.shutdown();
    }

    @Test
    void test() throws Exception {
        final SearchRequest searchRequest = createSearchRequest();

        // Validate the search request.
        validateSearchRequest(searchRequest);

        // Get sizes.
        final SizesProvider sizesProvider = createSizesProvider();

        // Create coprocessors.
        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final CoprocessorsFactory coprocessorsFactory =
                new CoprocessorsFactory(dataStoreFactory, new ExpressionContextFactory(), sizesProvider);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                SearchRequestSource.createBasic(),
                DateTimeSettings.builder().build(),
                queryKey,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.createBasicSearchResultStoreSettings());
        final ValuesConsumer consumer = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(coprocessors.getFieldIndex());

        // Add data to the consumer.
        final String[] lines = getLines();
        for (final String line : lines) {
            final String[] values = line.split(",");
            supplyValues(values, mappings, consumer);
        }

        // Tell the consumer we are finished receiving data.
        complete(coprocessors);

        final ResultStore resultStore = new ResultStore(
                searchRequest.getSearchRequestSource(),
                sizesProvider,
                null,
                coprocessors,
                "node",
                new ResultStoreSettingsFactory().get(),
                new MapDataStoreFactory(SearchResultStoreConfig::new),
                new ExpressionPredicateFactory());
        // Mark the collector as artificially complete.
        resultStore.signalComplete();

        final SearchResponse searchResponse = resultStore.search(searchRequest,
                resultStore.makeDefaultResultCreators(searchRequest));

        // Validate the search response.
        validateSearchResponse(searchResponse);
    }

//
//    @Test
//    void testSinglePayloadTransfer() throws Exception {
//        final SearchRequest searchRequest = createSingleSearchRequest();
//
//        // Validate the search request.
//        validateSearchRequest(searchRequest);
//
//        // Get sizes.
//        final SizesProvider sizesProvider = createSizesProvider();
//
//        // Create coprocessors.
//        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(sizesProvider);
//        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
//        final Coprocessors coprocessors = coprocessorsFactory.create(
//        coprocessorSettings, searchRequest.getQuery().getParams());
//
//        final ExtractionReceiver consumer = createExtractionReceiver(coprocessors);
//
//        // Reorder values if field mappings have changed.
//        final int[] mappings = createMappings(consumer);
//
//        final Coprocessors coprocessors2 = coprocessorsFactory.create(
//        coprocessorSettings, searchRequest.getQuery().getParams());
//
//        // Add data to the consumer.
//        final String[] lines = getLines();
//        for (int i = 0; i < lines.length; i++) {
//            final String line = lines[i];
//            final String[] values = line.split(",");
//            supplyValues(values, mappings, consumer);
//        }
//        consumer.getCompletionConsumer().accept((long) lines.length);
//
//
//        transferPayloads(coprocessors, coprocessors2);
//
//
//        final ClusterSearchResultCollector collector = new ClusterSearchResultCollector(
//                null,
//                null,
//                null,
//                null,
//                null,
//                null,
//                coprocessors2);
//
//        collector.complete();
//
//        final SearchResponseCreator searchResponseCreator = new SearchResponseCreator(sizesProvider, collector);
//        final SearchResponse searchResponse = searchResponseCreator.create(searchRequest);
//
//        // Validate the search response.
//        validateSearchResponse(searchResponse);
//    }

    @Test
    void testPayloadTransfer() throws Exception {
        final SearchRequest searchRequest = createSearchRequest();

        // Validate the search request.
        validateSearchRequest(searchRequest);

        // Get sizes.
        final SizesProvider sizesProvider = createSizesProvider();

        // Create coprocessors.
        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final CoprocessorsFactory coprocessorsFactory =
                new CoprocessorsFactory(dataStoreFactory, new ExpressionContextFactory(), sizesProvider);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                SearchRequestSource.createBasic(),
                DateTimeSettings.builder().build(),
                queryKey,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.createPayloadProducerSearchResultStoreSettings());

        final ValuesConsumer consumer = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(coprocessors.getFieldIndex());

        final QueryKey queryKey2 = new QueryKey(UUID.randomUUID().toString());
        final CoprocessorsImpl coprocessors2 = coprocessorsFactory.create(
                SearchRequestSource.createBasic(),
                DateTimeSettings.builder().build(),
                queryKey2,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.createBasicSearchResultStoreSettings());

        // Add data to the consumer.
        final String[] lines = getLines();
        for (final String line : lines) {
            final String[] values = line.split(",");
            supplyValues(values, mappings, consumer);
        }

        // Tell the consumer we are finished receiving data.
        coprocessors.getCompletionState().signalComplete();

        // Perform final payload transfer.
        while (!coprocessors.getCompletionState().isComplete()) {
            transferPayloads(coprocessors, coprocessors2);
            ThreadUtil.sleep(500);
        }

        // Ensure the target coprocessors get a chance to add the data from the payloads.
        coprocessors2.getCompletionState().signalComplete();

        // Wait for the coprocessors to complete.
        coprocessors.getCompletionState().awaitCompletion();
        coprocessors2.getCompletionState().awaitCompletion();

        final ResultStore resultStore = new ResultStore(
                searchRequest.getSearchRequestSource(),
                sizesProvider,
                UserRef.builder().uuid(UUID.randomUUID().toString()).build(),
                coprocessors2,
                "node",
                new ResultStoreSettingsFactory().get(),
                new MapDataStoreFactory(SearchResultStoreConfig::new),
                new ExpressionPredicateFactory());
        // Mark the collector as artificially complete.
        resultStore.signalComplete();

        final SearchResponse searchResponse = resultStore.search(searchRequest,
                resultStore.makeDefaultResultCreators(searchRequest));

        // Validate the search response.
        validateSearchResponse(searchResponse);
    }

    @Test
    void testFrequentPayloadTransfer() throws Exception {
        final SearchRequest searchRequest = createSearchRequest();

        // Validate the search request.
        validateSearchRequest(searchRequest);

        // Get sizes.
        final SizesProvider sizesProvider = createSizesProvider();

        // Create coprocessors.
        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final CoprocessorsFactory coprocessorsFactory =
                new CoprocessorsFactory(dataStoreFactory, new ExpressionContextFactory(), sizesProvider);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                SearchRequestSource.createBasic(),
                DateTimeSettings.builder().build(),
                queryKey,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.createPayloadProducerSearchResultStoreSettings());

        final ValuesConsumer consumer1 = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(coprocessors.getFieldIndex());

        final QueryKey queryKey2 = new QueryKey(UUID.randomUUID().toString());
        final CoprocessorsImpl coprocessors2 = coprocessorsFactory.create(
                SearchRequestSource.createBasic(),
                DateTimeSettings.builder().build(),
                queryKey2,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.createBasicSearchResultStoreSettings());

        // Add data to the consumer.
        final String[] lines = getLines();
        for (final String line : lines) {
            final String[] values = line.split(",");
            supplyValues(values, mappings, consumer1);

            transferPayloads(coprocessors, coprocessors2);
        }

        // Tell the consumer we are finished receiving data.
        coprocessors.getCompletionState().signalComplete();

        // Perform final payload transfer.
        while (!coprocessors.getCompletionState().isComplete()) {
            transferPayloads(coprocessors, coprocessors2);
            ThreadUtil.sleep(500);
        }

        // Ensure the target coprocessors get a chance to add the data from the payloads.
        coprocessors2.getCompletionState().signalComplete();

        // Wait for the coprocessors to complete.
        coprocessors.getCompletionState().awaitCompletion();
        coprocessors2.getCompletionState().awaitCompletion();

        final ResultStore resultStore = new ResultStore(
                searchRequest.getSearchRequestSource(),
                sizesProvider,
                UserRef.builder().uuid(UUID.randomUUID().toString()).build(),
                coprocessors2,
                "node",
                new ResultStoreSettingsFactory().get(),
                new MapDataStoreFactory(SearchResultStoreConfig::new),
                new ExpressionPredicateFactory());
        // Mark the collector as artificially complete.
        resultStore.signalComplete();

        final SearchResponse searchResponse = resultStore.search(searchRequest,
                resultStore.makeDefaultResultCreators(searchRequest));

        // Validate the search response.
        validateSearchResponse(searchResponse);
    }

    private void complete(final CoprocessorsImpl coprocessors) throws InterruptedException {
        coprocessors.getCompletionState().signalComplete();
        // Wait for the coprocessors to finish processing data.
        coprocessors.getCompletionState().awaitCompletion();
    }

    //    @Test
    void testMultiAsyncPayloadTransfer() throws Exception {
        for (int i = 0; i < 1000; i++) {
            System.out.println("RUN " + i);
            testAsyncPayloadTransfer();
        }
    }

    //    @Test
    void testAsyncPayloadTransfer() throws Exception {
        final SearchRequest searchRequest = createSearchRequest();

        // Validate the search request.
        validateSearchRequest(searchRequest);

        // Get sizes.
        final SizesProvider sizesProvider = createSizesProvider();

        // Create coprocessors.
        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final CoprocessorsFactory coprocessorsFactory =
                new CoprocessorsFactory(dataStoreFactory, new ExpressionContextFactory(), sizesProvider);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                SearchRequestSource.createBasic(),
                DateTimeSettings.builder().build(),
                queryKey,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.createBasicSearchResultStoreSettings());

        final ValuesConsumer consumer = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(coprocessors.getFieldIndex());

        final QueryKey queryKey2 = new QueryKey(UUID.randomUUID().toString());
        final CoprocessorsImpl coprocessors2 = coprocessorsFactory.create(
                SearchRequestSource.createBasic(),
                DateTimeSettings.builder().build(),
                queryKey2,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.createBasicSearchResultStoreSettings());


        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final String line = "2010-01-01T00:02:00.000Z,user3,694,3";
        final String[] values = line.split(",");
        final int count = 100000000;
        final int threads = 1000;
        final int perThread = count / threads;

        // Create value supply futures.
        final CompletableFuture<?>[] futures = new CompletableFuture[threads];
        int thread = 0;
        for (; thread < threads; thread++) {
            futures[thread] = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < perThread; i++) {
                    supplyValues(values, mappings, consumer);
                }
            });
        }
        CompletableFuture.allOf(futures).thenRunAsync(countDownLatch::countDown);

        // Create payload transfer future.
        final CompletableFuture<?> completableFuture = CompletableFuture.runAsync(() -> {
            boolean complete = false;
            while (!complete) {
                try {
                    final long wait = (long) (Math.random() * 100);
                    complete = countDownLatch.await(wait, TimeUnit.MILLISECONDS);
                    transferPayloads(coprocessors, coprocessors2);
                } catch (final InterruptedException e) {
                    // Ignore.
                }
            }
        });
        completableFuture.join();

        // Tell the consumer we are finished receiving data.
        complete(coprocessors);

        // Perform final payload transfer.
        transferPayloads(coprocessors, coprocessors2);

        // Ensure the target coprocessors get a chance to add the data from the payloads.
        complete(coprocessors2);

        final ResultStore resultStore = new ResultStore(
                searchRequest.getSearchRequestSource(),
                null,
                null,
                coprocessors2,
                "node",
                new ResultStoreSettingsFactory().get(),
                new MapDataStoreFactory(SearchResultStoreConfig::new),
                new ExpressionPredicateFactory());
        // Mark the collector as artificially complete.
        resultStore.signalComplete();

        final AtomicBoolean found = new AtomicBoolean();
        final AtomicLong totalRowCount = new AtomicLong();

        final DataStore dataStore = resultStore.getData("table-78LF4");
        dataStore.fetch(
                dataStore.getColumns(),
                OffsetRange.ZERO_1000,
                OpenGroupsImpl.root(),
                null,
                IdentityItemMapper.INSTANCE,
                item -> {
                    final Val val = item.getValue(2);
                    assertThat(val.toLong())
                            .isEqualTo(count);
                    found.set(true);
                },
                totalRowCount::set);


        assertThat(totalRowCount.get()).isNotZero();
        assertThat(found.get()).isTrue();
    }

    private void supplyValues(final String[] values, final int[] mappings, final ValuesConsumer consumer) {
        final Val[] vals = new Val[values.length];
        for (int j = 0; j < values.length; j++) {
            final String value = values[j];
            final int target = mappings[j];
            vals[target] = ValString.create(value);
        }
        consumer.accept(Val.of(vals));
    }

    private void transferPayloads(final Coprocessors source, final Coprocessors target) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (final Output output = new Output(outputStream)) {
            source.writePayloads(output);
        }

        final byte[] bytes = outputStream.toByteArray();
        try (final Input input = new Input(new ByteArrayInputStream(bytes))) {
            target.readPayloads(input);
        }
    }

    private int[] createMappings(final FieldIndex fieldIndex) {
        final int[] mappings = new int[4];
        mappings[0] = fieldIndex.getPos("EventTime");
        mappings[1] = fieldIndex.getPos("UserId");
        mappings[2] = fieldIndex.getPos("StreamId");
        mappings[3] = fieldIndex.getPos("EventId");
        return mappings;
    }

    private String[] getLines() throws IOException {
        // Add data to the consumer.
        final Path dataPath = resourcesDir.resolve("data.txt");
        final String data = Files.readString(dataPath);
        return data.split("\n");
    }

    private SizesProvider createSizesProvider() throws ParseException {
        final Sizes defaultMaxResultsSizes = Sizes.parse(null);
        return () -> defaultMaxResultsSizes;
    }

    private ValuesConsumer createExtractionReceiver(final Coprocessors coprocessors) {
        final Map<DocRef, ValuesConsumer> receivers = new HashMap<>();
        coprocessors.forEachExtractionCoprocessor((docRef, coprocessorSet) -> {
            // Create a receiver that will send data to all coprocessors.
            final ValuesConsumer receiver;
            if (coprocessorSet.size() == 1) {
                receiver = coprocessorSet.iterator().next();
            } else {
                // We assume all coprocessors for the same extraction use the same field index map.
                // This is only the case at the moment as the CoprocessorsFactory creates field index maps this way.
                receiver = values -> coprocessorSet.forEach(coprocessor -> coprocessor.accept(values));
            }
            receivers.put(docRef, receiver);
        });

        assertThat(receivers.size())
                .isEqualTo(1);

        return receivers.values().iterator().next();
    }

    private void validateSearchRequest(final SearchRequest searchRequest) {
        SearchDebugUtil.writeRequest(searchRequest, true);
        SearchDebugUtil.validateRequest();
    }

    private void validateSearchResponse(final SearchResponse searchResponse) {
        SearchDebugUtil.writeResponse(searchResponse, true);
        SearchDebugUtil.validateResponse();
    }

    private SearchRequest createSingleSearchRequest() {
        final QueryKey key = new QueryKey("test_uuid");
        final DocRef dataSource = new DocRef(
                "Index",
                "57a35b9a-083c-4a93-a813-fc3ddfe1ff44",
                "Example index");
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(
                        "EventTime",
                        Condition.BETWEEN,
                        "2010-01-01T00:00:00.000Z,2010-01-01T00:10:00.000Z")
                .build();
        final Query query = Query.builder()
                .dataSource(dataSource)
                .expression(expression)
                .addParam(ParamKeys.CURRENT_USER, "admin")
                .build();

        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().localZoneId("Europe/London").build();
        return SearchRequest.builder()
                .key(key)
                .query(query)
                .addResultRequests(createGroupedUserTableResultRequest())
                .dateTimeSettings(dateTimeSettings)
                .incremental(true)
                .build();
    }

    private SearchRequest createSearchRequest() {
        final QueryKey key = new QueryKey("test_uuid");
        final DocRef dataSource = new DocRef(
                "Index",
                "57a35b9a-083c-4a93-a813-fc3ddfe1ff44",
                "Example index");
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(
                        "EventTime",
                        Condition.BETWEEN,
                        "2010-01-01T00:00:00.000Z,2010-01-01T00:10:00.000Z")
                .build();
        final Query query = Query.builder()
                .dataSource(dataSource)
                .expression(expression)
                .addParam(ParamKeys.CURRENT_USER, "admin")
                .build();

        final DateTimeSettings dateTimeSettings = DateTimeSettings
                .builder()
                .localZoneId("Europe/London")
                .referenceTime(0L)
                .build();
        return SearchRequest.builder()
                .key(key)
                .query(query)
                .addResultRequests(createGroupedUserTableResultRequest())
                .addResultRequests(createDonutResultRequest())
                .addResultRequests(createGroupedUserAndEventTimeTableResultRequest())
                .addResultRequests(createBubbleResultRequest())
                .addResultRequests(createLineResultRequest())
                .dateTimeSettings(dateTimeSettings)
                .incremental(true)
                .build();
    }

    private ResultRequest createGroupedUserTableResultRequest() {
        return ResultRequest.builder()
                .componentId("table-BKJT6")
                .addMappings(createGroupedUserTableSettings())
                .requestedRange(OffsetRange.ZERO_100)
                .resultStyle(ResultStyle.TABLE)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createGroupedUserTableSettings() {
        return TableSettings.builder()
                .queryId("query-MRGPM")
                .addColumns(Column.builder()
                        .id("table-BKJT6|RACJI")
                        .name("UserId")
                        .expression("${UserId}")
                        .format(Format.GENERAL)
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .group(0)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("table-BKJT6|89WRT")
                        .name("Count")
                        .expression("count()")
                        .format(Format.NUMBER)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("__stream_id__")
                        .name("__stream_id__")
                        .expression("${StreamId}")
                        .build()
                )
                .addColumns(Column.builder()
                        .id("__event_id__")
                        .name("__event_id__")
                        .expression("${EventId}")
                        .build()
                )
                .extractValues(true)
                .extractionPipeline(new DocRef("Pipeline",
                        "e5ecdf93-d433-45ac-b14a-1f77f16ae4f7",
                        "Example Extraction"))
                .addMaxResults(1000000L)
                .build();
    }


    private ResultRequest createDonutResultRequest() {
        return ResultRequest.builder()
                .componentId("vis-QYG7H")
                .addMappings(createGroupedUserTableSettings())
                .addMappings(createDonutVisSettings())
                .requestedRange(OffsetRange.ZERO_1000)
                .resultStyle(ResultStyle.FLAT)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createDonutVisSettings() {
        return TableSettings.builder()
                .addColumns(Column.builder()
                        .id("1")
                        .sort(new Sort(null, SortDirection.ASCENDING))
                        .format(Format.GENERAL)
                        .group(0)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("2")
                        .expression("${UserId}")
                        .format(Format.GENERAL)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("3")
                        .expression("${Count}")
                        .format(Format.NUMBER)
                        .build()
                )
                .addMaxResults(20L, 20L)
                .showDetail(true)
                .build();
    }


    private ResultRequest createGroupedUserAndEventTimeTableResultRequest() {
        return ResultRequest.builder()
                .componentId("table-78LF4")
                .addMappings(createGroupedUserAndEventTimeTableSettings())
                .requestedRange(OffsetRange.ZERO_100)
                .resultStyle(ResultStyle.TABLE)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createGroupedUserAndEventTimeTableSettings() {
        return TableSettings.builder()
                .queryId("query-MRGPM")
                .addColumns(Column.builder()
                        .id("table-78LF4|7JU9H")
                        .name("EventTime")
                        .expression("roundMinute(${EventTime})")
                        .format(Format.DATE_TIME)
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .group(0)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("table-78LF4|T5WYU")
                        .name("UserId")
                        .expression("${UserId}")
                        .format(Format.GENERAL)
                        .sort(new Sort(1, SortDirection.ASCENDING))
                        .group(0)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("table-78LF4|MT5IM")
                        .name("Count")
                        .expression("count()")
                        .format(Format.NUMBER)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("__stream_id__")
                        .name("__stream_id__")
                        .expression("${StreamId}")
                        .build()
                )
                .addColumns(Column.builder()
                        .id("__event_id__")
                        .name("__event_id__")
                        .expression("${EventId}")
                        .build()
                )
                .extractValues(true)
                .extractionPipeline(new DocRef("Pipeline",
                        "e5ecdf93-d433-45ac-b14a-1f77f16ae4f7",
                        "Example Extraction"))
                .addMaxResults(1000000L)
                .build();
    }


    private ResultRequest createBubbleResultRequest() {
        return ResultRequest.builder()
                .componentId("vis-L1AL1")
                .addMappings(createGroupedUserAndEventTimeTableSettings())
                .addMappings(createBubbleVisSettings())
                .requestedRange(OffsetRange.ZERO_1000)
                .resultStyle(ResultStyle.FLAT)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createBubbleVisSettings() {
        return TableSettings.builder()
                .addColumns(Column.builder()
                        .id("1")
                        .expression("${EventTime}")
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .format(Format.DATE_TIME)
                        .group(0)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("2")
                        .expression("${UserId}")
                        .sort(new Sort(1, SortDirection.ASCENDING))
                        .format(Format.GENERAL)
                        .group(1)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("3")
                        .expression("${UserId}")
                        .sort(new Sort(2, SortDirection.ASCENDING))
                        .format(Format.GENERAL)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("4")
                        .expression("${Count}")
                        .format(Format.NUMBER)
                        .build()
                )
                .addMaxResults(20L, 10L, 500L)
                .showDetail(true)
                .build();
    }


    private ResultRequest createLineResultRequest() {
        return ResultRequest.builder()
                .componentId("vis-SPSCW")
                .addMappings(createGroupedUserAndEventTimeTableSettings())
                .addMappings(createLineVisSettings())
                .requestedRange(OffsetRange.ZERO_1000)
                .resultStyle(ResultStyle.FLAT)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createLineVisSettings() {
        return TableSettings.builder()
                .addColumns(Column.builder()
                        .id("1")
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .format(Format.GENERAL)
                        .group(0)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("2")
                        .expression("${UserId}")
                        .sort(new Sort(1,
                                SortDirection.ASCENDING)) // TODO : The original was not sorted but this makes
                        //                                          the test results consistent
                        .format(Format.GENERAL)
                        .group(1)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("3")
                        .expression("${EventTime}")
                        .sort(new Sort(2, SortDirection.ASCENDING))
                        .format(Format.DATE_TIME)
                        .build()
                )
                .addColumns(Column.builder()
                        .id("4")
                        .expression("${Count}")
                        .format(Format.NUMBER)
                        .build()
                )
                .addMaxResults(20L, 100L, 1000L)
                .showDetail(true)
                .build();
    }

    private static final class TestUserIdentity implements UserIdentity {

        private final String subjectId;

        private TestUserIdentity(final String subjectId) {
            this.subjectId = subjectId;
        }

        @Override
        public String subjectId() {
            return null;
        }
    }
}
