package stroom.search.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.docref.DocRef;
import stroom.lmdb.LmdbEnvFactory;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.ResultRequest.ResultStyle;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.DataStoreFactory;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.Item;
import stroom.query.common.v2.Items;
import stroom.query.common.v2.LmdbDataStoreFactory;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreConfig;
import stroom.query.common.v2.ResultStoreSettingsFactory;
import stroom.query.common.v2.SearchDebugUtil;
import stroom.query.common.v2.Serialisers;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.SizesProvider;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;

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
        final LmdbEnvFactory lmdbEnvFactory = new LmdbEnvFactory(
                pathCreator,
                tempDirProvider,
                () -> lmdbLibraryConfig);
        dataStoreFactory = new LmdbDataStoreFactory(
                lmdbEnvFactory,
                ResultStoreConfig::new,
                pathCreator,
                () -> executorService,
                () -> new Serialisers(new ResultStoreConfig()));
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
        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(
                sizesProvider,
                dataStoreFactory);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final Coprocessors coprocessors = coprocessorsFactory.create(
                queryKey,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.BASIC_SETTINGS);
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
                new ResultStoreSettingsFactory().get());
        // Mark the collector as artificially complete.
        resultStore.signalComplete();

        final SearchResponse searchResponse = resultStore.search(searchRequest);

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
        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(
                sizesProvider,
                dataStoreFactory);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final Coprocessors coprocessors = coprocessorsFactory.create(
                queryKey,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.PAYLOAD_PRODUCER_SETTINGS);

        final ValuesConsumer consumer = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(coprocessors.getFieldIndex());

        final QueryKey queryKey2 = new QueryKey(UUID.randomUUID().toString());
        final Coprocessors coprocessors2 = coprocessorsFactory.create(
                queryKey2,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.BASIC_SETTINGS);

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
            Thread.sleep(500);
        }

        // Ensure the target coprocessors get a chance to add the data from the payloads.
        coprocessors2.getCompletionState().signalComplete();

        // Wait for the coprocessors to complete.
        coprocessors.getCompletionState().awaitCompletion();
        coprocessors2.getCompletionState().awaitCompletion();

        final ResultStore resultStore = new ResultStore(
                searchRequest.getSearchRequestSource(),
                sizesProvider,
                "test_user_id",
                coprocessors2,
                "node",
                new ResultStoreSettingsFactory().get());
        // Mark the collector as artificially complete.
        resultStore.signalComplete();

        final SearchResponse searchResponse = resultStore.search(searchRequest);

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
        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(
                sizesProvider,
                dataStoreFactory);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final Coprocessors coprocessors = coprocessorsFactory.create(
                queryKey,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.PAYLOAD_PRODUCER_SETTINGS);

        final ValuesConsumer consumer1 = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(coprocessors.getFieldIndex());

        final QueryKey queryKey2 = new QueryKey(UUID.randomUUID().toString());
        final Coprocessors coprocessors2 = coprocessorsFactory.create(
                queryKey2,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.BASIC_SETTINGS);

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
            Thread.sleep(500);
        }

        // Ensure the target coprocessors get a chance to add the data from the payloads.
        coprocessors2.getCompletionState().signalComplete();

        // Wait for the coprocessors to complete.
        coprocessors.getCompletionState().awaitCompletion();
        coprocessors2.getCompletionState().awaitCompletion();

        final ResultStore resultStore = new ResultStore(
                searchRequest.getSearchRequestSource(),
                sizesProvider,
                "test_user_id",
                coprocessors2,
                "node",
                new ResultStoreSettingsFactory().get());
        // Mark the collector as artificially complete.
        resultStore.signalComplete();

        final SearchResponse searchResponse = resultStore.search(searchRequest);

        // Validate the search response.
        validateSearchResponse(searchResponse);
    }

    private void complete(final Coprocessors coprocessors) throws InterruptedException {
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
        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(sizesProvider, dataStoreFactory);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final Coprocessors coprocessors = coprocessorsFactory.create(
                queryKey,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.BASIC_SETTINGS);

        final ValuesConsumer consumer = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(coprocessors.getFieldIndex());

        final QueryKey queryKey2 = new QueryKey(UUID.randomUUID().toString());
        final Coprocessors coprocessors2 = coprocessorsFactory.create(
                queryKey2,
                coprocessorSettings,
                searchRequest.getQuery().getParams(),
                DataStoreSettings.BASIC_SETTINGS);


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
                new ResultStoreSettingsFactory().get());
        // Mark the collector as artificially complete.
        resultStore.signalComplete();

        final DataStore dataStore = resultStore.getData("table-78LF4");
        dataStore.getData(data -> {
            final Items dataItems = data.get();
            final Item dataItem = dataItems.iterator().next();
            final Val val = dataItem.getValue(2, true);
            assertThat(val.toLong())
                    .isEqualTo(count);
        });

//        final SearchResponseCreator searchResponseCreator = new SearchResponseCreator(sizesProvider, collector);
//        final SearchResponse searchResponse = searchResponseCreator.create(searchRequest);
//        searchResponse.getResults().
    }

    private void supplyValues(final String[] values, final int[] mappings, final ValuesConsumer consumer) {
        final Val[] vals = new Val[values.length];
        for (int j = 0; j < values.length; j++) {
            final String value = values[j];
            final int target = mappings[j];
            vals[target] = ValString.create(value);
        }
        consumer.add(Val.of(vals));
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
        final Sizes storeSize = Sizes.parse("1000000,100,10,1");
        return new SizesProvider() {
            @Override
            public Sizes getDefaultMaxResultsSizes() {
                return defaultMaxResultsSizes;
            }

            @Override
            public Sizes getStoreSizes() {
                return storeSize;
            }
        };
    }

    private ValuesConsumer createExtractionReceiver(final Coprocessors coprocessors) {
        final Map<DocRef, ValuesConsumer> receivers = new HashMap<>();
        coprocessors.forEachExtractionCoprocessor((docRef, coprocessorSet) -> {
            // Create a receiver that will send data to all coprocessors.
            ValuesConsumer receiver;
            if (coprocessorSet.size() == 1) {
                receiver = coprocessorSet.iterator().next();
            } else {
                // We assume all coprocessors for the same extraction use the same field index map.
                // This is only the case at the moment as the CoprocessorsFactory creates field index maps this way.
                receiver = values -> coprocessorSet.forEach(coprocessor -> coprocessor.add(values));
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
                .addParam("currentUser()", "admin")
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
                .addParam("currentUser()", "admin")
                .build();

        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().localZoneId("Europe/London").build();
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
                .requestedRange(OffsetRange.builder().offset(0L).length(100L).build())
                .resultStyle(ResultStyle.TABLE)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createGroupedUserTableSettings() {
        return TableSettings.builder()
                .queryId("query-MRGPM")
                .addFields(Field.builder()
                        .id("table-BKJT6|RACJI")
                        .name("UserId")
                        .expression("${UserId}")
                        .format(Format.GENERAL)
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .group(0)
                        .build()
                )
                .addFields(Field.builder()
                        .id("table-BKJT6|89WRT")
                        .name("Count")
                        .expression("count()")
                        .format(Format.NUMBER)
                        .build()
                )
                .addFields(Field.builder()
                        .id("__stream_id__")
                        .name("__stream_id__")
                        .expression("${StreamId}")
                        .build()
                )
                .addFields(Field.builder()
                        .id("__event_id__")
                        .name("__event_id__")
                        .expression("${EventId}")
                        .build()
                )
                .extractValues(true)
                .extractionPipeline(new DocRef("Pipeline",
                        "e5ecdf93-d433-45ac-b14a-1f77f16ae4f7",
                        "Example Extraction"))
                .addMaxResults(1000000)
                .build();
    }


    private ResultRequest createDonutResultRequest() {
        return ResultRequest.builder()
                .componentId("vis-QYG7H")
                .addMappings(createGroupedUserTableSettings())
                .addMappings(createDonutVisSettings())
                .resultStyle(ResultStyle.FLAT)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createDonutVisSettings() {
        return TableSettings.builder()
                .addFields(Field.builder()
                        .sort(new Sort(null, SortDirection.ASCENDING))
                        .format(Format.GENERAL)
                        .group(0)
                        .build()
                )
                .addFields(Field.builder()
                        .expression("${UserId}")
                        .format(Format.GENERAL)
                        .build()
                )
                .addFields(Field.builder()
                        .expression("${Count}")
                        .format(Format.NUMBER)
                        .build()
                )
                .addMaxResults(20, 20)
                .showDetail(true)
                .build();
    }


    private ResultRequest createGroupedUserAndEventTimeTableResultRequest() {
        return ResultRequest.builder()
                .componentId("table-78LF4")
                .addMappings(createGroupedUserAndEventTimeTableSettings())
                .requestedRange(OffsetRange.builder().offset(0L).length(100L).build())
                .resultStyle(ResultStyle.TABLE)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createGroupedUserAndEventTimeTableSettings() {
        return TableSettings.builder()
                .queryId("query-MRGPM")
                .addFields(Field.builder()
                        .id("table-78LF4|7JU9H")
                        .name("EventTime")
                        .expression("roundMinute(${EventTime})")
                        .format(Format.DATE_TIME)
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .group(0)
                        .build()
                )
                .addFields(Field.builder()
                        .id("table-78LF4|T5WYU")
                        .name("UserId")
                        .expression("${UserId}")
                        .format(Format.GENERAL)
                        .sort(new Sort(1, SortDirection.ASCENDING))
                        .group(0)
                        .build()
                )
                .addFields(Field.builder()
                        .id("table-78LF4|MT5IM")
                        .name("Count")
                        .expression("count()")
                        .format(Format.NUMBER)
                        .build()
                )
                .addFields(Field.builder()
                        .id("__stream_id__")
                        .name("__stream_id__")
                        .expression("${StreamId}")
                        .build()
                )
                .addFields(Field.builder()
                        .id("__event_id__")
                        .name("__event_id__")
                        .expression("${EventId}")
                        .build()
                )
                .extractValues(true)
                .extractionPipeline(new DocRef("Pipeline",
                        "e5ecdf93-d433-45ac-b14a-1f77f16ae4f7",
                        "Example Extraction"))
                .addMaxResults(1000000)
                .build();
    }


    private ResultRequest createBubbleResultRequest() {
        return ResultRequest.builder()
                .componentId("vis-L1AL1")
                .addMappings(createGroupedUserAndEventTimeTableSettings())
                .addMappings(createBubbleVisSettings())
                .resultStyle(ResultStyle.FLAT)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createBubbleVisSettings() {
        return TableSettings.builder()
                .addFields(Field.builder()
                        .expression("${EventTime}")
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .format(Format.DATE_TIME)
                        .group(0)
                        .build()
                )
                .addFields(Field.builder()
                        .expression("${UserId}")
                        .sort(new Sort(1, SortDirection.ASCENDING))
                        .format(Format.GENERAL)
                        .group(1)
                        .build()
                )
                .addFields(Field.builder()
                        .expression("${UserId}")
                        .sort(new Sort(2, SortDirection.ASCENDING))
                        .format(Format.GENERAL)
                        .build()
                )
                .addFields(Field.builder()
                        .expression("${Count}")
                        .format(Format.NUMBER)
                        .build()
                )
                .addMaxResults(20, 10, 500)
                .showDetail(true)
                .build();
    }


    private ResultRequest createLineResultRequest() {
        return ResultRequest.builder()
                .componentId("vis-SPSCW")
                .addMappings(createGroupedUserAndEventTimeTableSettings())
                .addMappings(createLineVisSettings())
                .resultStyle(ResultStyle.FLAT)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createLineVisSettings() {
        return TableSettings.builder()
                .addFields(Field.builder()
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .format(Format.GENERAL)
                        .group(0)
                        .build()
                )
                .addFields(Field.builder()
                        .expression("${UserId}")
                        .sort(new Sort(1,
                                SortDirection.ASCENDING)) // TODO : The original was not sorted but this makes
                        //                                          the test results consistent
                        .format(Format.GENERAL)
                        .group(1)
                        .build()
                )
                .addFields(Field.builder()
                        .expression("${EventTime}")
                        .sort(new Sort(2, SortDirection.ASCENDING))
                        .format(Format.DATE_TIME)
                        .build()
                )
                .addFields(Field.builder()
                        .expression("${Count}")
                        .format(Format.NUMBER)
                        .build()
                )
                .addMaxResults(20, 100, 1000)
                .showDetail(true)
                .build();
    }
}
