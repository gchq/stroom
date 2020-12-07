package stroom.search.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.docref.DocRef;
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
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Data.DataItem;
import stroom.query.common.v2.Data.DataItems;
import stroom.query.common.v2.SearchDebugUtil;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.SizesProvider;
import stroom.search.extraction.ExtractionReceiver;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSearchResultCreation {
    // Make sure the search request is the same as the one we expected to make.
    private final Path resourcesDir = SearchDebugUtil.initialise();

    @Test
    void test() throws Exception {
        final SearchRequest searchRequest = createSearchRequest();

        // Validate the search request.
        validateSearchRequest(searchRequest);

        // Get sizes.
        final SizesProvider sizesProvider = createSizesProvider();

        // Create coprocessors.
        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(sizesProvider);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final Coprocessors coprocessors = coprocessorsFactory.create(coprocessorSettings, searchRequest.getQuery().getParams());
        final ExtractionReceiver consumer = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(consumer);

        // Add data to the consumer.
        final String[] lines = getLines();
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final String[] values = line.split(",");
            supplyValues(values, mappings, consumer);
        }
        consumer.getCompletionConsumer().accept((long) lines.length);

        final ClusterSearchResultCollector collector = new ClusterSearchResultCollector(
                null,
                null,
                null,
                null,
                null,
                null,
                coprocessors);

        collector.complete();

        final SearchResponseCreator searchResponseCreator = new SearchResponseCreator(sizesProvider, collector);
        final SearchResponse searchResponse = searchResponseCreator.create(searchRequest);

        // Validate the search response.
        validateSearchResponse(searchResponse);
    }

    @Test
    void testPayloadTransfer() throws Exception {
        final SearchRequest searchRequest = createSearchRequest();

        // Validate the search request.
        validateSearchRequest(searchRequest);

        // Get sizes.
        final SizesProvider sizesProvider = createSizesProvider();

        // Create coprocessors.
        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(sizesProvider);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final Coprocessors coprocessors = coprocessorsFactory.create(coprocessorSettings, searchRequest.getQuery().getParams());

        final ExtractionReceiver consumer = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(consumer);

        final Coprocessors coprocessors2 = coprocessorsFactory.create(coprocessorSettings, searchRequest.getQuery().getParams());

        // Add data to the consumer.
        final String[] lines = getLines();
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final String[] values = line.split(",");
            supplyValues(values, mappings, consumer);
        }
        consumer.getCompletionConsumer().accept((long) lines.length);


        transferPayloads(coprocessors, coprocessors2);


        final ClusterSearchResultCollector collector = new ClusterSearchResultCollector(
                null,
                null,
                null,
                null,
                null,
                null,
                coprocessors2);

        collector.complete();

        final SearchResponseCreator searchResponseCreator = new SearchResponseCreator(sizesProvider, collector);
        final SearchResponse searchResponse = searchResponseCreator.create(searchRequest);

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
        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(sizesProvider);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final Coprocessors coprocessors = coprocessorsFactory.create(coprocessorSettings, searchRequest.getQuery().getParams());

        final ExtractionReceiver consumer = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(consumer);

        final Coprocessors coprocessors2 = coprocessorsFactory.create(coprocessorSettings, searchRequest.getQuery().getParams());

        // Add data to the consumer.
        final String[] lines = getLines();
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final String[] values = line.split(",");
            supplyValues(values, mappings, consumer);

            transferPayloads(coprocessors, coprocessors2);
        }
        consumer.getCompletionConsumer().accept((long) lines.length);


        final ClusterSearchResultCollector collector = new ClusterSearchResultCollector(
                null,
                null,
                null,
                null,
                null,
                null,
                coprocessors2);

        collector.complete();

        final SearchResponseCreator searchResponseCreator = new SearchResponseCreator(sizesProvider, collector);
        final SearchResponse searchResponse = searchResponseCreator.create(searchRequest);

        // Validate the search response.
        validateSearchResponse(searchResponse);
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
        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(sizesProvider);
        final List<CoprocessorSettings> coprocessorSettings = coprocessorsFactory.createSettings(searchRequest);
        final Coprocessors coprocessors = coprocessorsFactory.create(coprocessorSettings, searchRequest.getQuery().getParams());

        final ExtractionReceiver consumer = createExtractionReceiver(coprocessors);

        // Reorder values if field mappings have changed.
        final int[] mappings = createMappings(consumer);

        final Coprocessors coprocessors2 = coprocessorsFactory.create(coprocessorSettings, searchRequest.getQuery().getParams());


        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final String line = "2010-01-01T00:02:00.000Z,user3,694,3";
        final String[] values = line.split(",");
        final int count = 100000000;
        final int threads = 1000;
        final int perThread = count / threads;

        // Create value supply futures.
        final CompletableFuture[] futures = new CompletableFuture[threads];
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
        final CompletableFuture completableFuture = CompletableFuture.runAsync(() -> {
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


        consumer.getCompletionConsumer().accept((long) count);


        final ClusterSearchResultCollector collector = new ClusterSearchResultCollector(
                null,
                null,
                null,
                null,
                null,
                null,
                coprocessors2);

        collector.complete();

        final Data data = collector.getData("table-78LF4");
        final DataItems dataItems = data.get();
        final DataItem dataItem = dataItems.iterator().next();
        final Val val = dataItem.getValue(2);
        assertThat(val.toLong()).isEqualTo(count);


//        final SearchResponseCreator searchResponseCreator = new SearchResponseCreator(sizesProvider, collector);
//        final SearchResponse searchResponse = searchResponseCreator.create(searchRequest);
//        searchResponse.getResults().
    }

    private void supplyValues(final String[] values, final int[] mappings, final ExtractionReceiver receiver) {
        final Val[] vals = new Val[values.length];
        for (int j = 0; j < values.length; j++) {
            final String value = values[j];
            final int target = mappings[j];
            vals[target] = ValString.create(value);
        }
        receiver.getValuesConsumer().accept(vals);
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

//    private Coprocessors transfer(final String[] lines,
//                                  final int[] mappings,
//                                  final ExtractionReceiver consumer,
//                                  final CoprocessorsFactory coprocessorsFactory,
//                                  final Coprocessors coprocessors) {
//        for (int i = 0; i < lines.length; i++) {
//            final String line = lines[i];
//            final String[] values = line.split(",");
//            final Val[] vals = new Val[values.length];
//            for (int j = 0; j < values.length; j++) {
//                final String value = values[j];
//                final int target = mappings[j];
//                vals[target] = ValString.create(value);
//            }
//            consumer.getValuesConsumer().accept(vals);
//        }
//        consumer.getCompletionConsumer().accept((long) lines.length);
//
//
//
//
//
//
//        final Coprocessors coprocessors2 = coprocessorsFactory.create(coprocessorSettings, searchRequest.getQuery().getParams());
//
//        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//        try (final Output output = new Output(outputStream)) {
//            coprocessors.writePayloads(output);
//        }
//
//        try (final Input input = new Input(new ByteArrayInputStream(outputStream.toByteArray()))) {
//            coprocessors2.readPayloads(input);
//        }
//    }

    private int[] createMappings(ExtractionReceiver receiver) {
        final FieldIndex fieldIndex = receiver.getFieldMap();
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

    private ExtractionReceiver createExtractionReceiver(final Coprocessors coprocessors) {
        final Map<DocRef, ExtractionReceiver> receivers = new HashMap<>();
        coprocessors.forEachExtractionCoprocessor((docRef, coprocessorSet) -> {
            // Create a receiver that will send data to all coprocessors.
            ExtractionReceiver receiver;
            if (coprocessorSet.size() == 1) {
                final Coprocessor coprocessor = coprocessorSet.iterator().next();
                final FieldIndex fieldIndex = coprocessors.getFieldIndex();
                final Consumer<Val[]> valuesConsumer = coprocessor.getValuesConsumer();
                final Consumer<Throwable> errorConsumer = coprocessor.getErrorConsumer();
                final Consumer<Long> completionConsumer = coprocessor.getCompletionConsumer();
                receiver = new ExtractionReceiver(valuesConsumer, errorConsumer, completionConsumer, fieldIndex);
            } else {
                // We assume all coprocessors for the same extraction use the same field index map.
                // This is only the case at the moment as the CoprocessorsFactory creates field index maps this way.
                final FieldIndex fieldIndex = coprocessors.getFieldIndex();
                final Consumer<Val[]> valuesConsumer = values -> coprocessorSet.forEach(coprocessor -> coprocessor.getValuesConsumer().accept(values));
                final Consumer<Throwable> errorConsumer = error -> coprocessorSet.forEach(coprocessor -> coprocessor.getErrorConsumer().accept(error));
                final Consumer<Long> completionConsumer = delta -> coprocessorSet.forEach(coprocessor -> coprocessor.getCompletionConsumer().accept(delta));
                receiver = new ExtractionReceiver(valuesConsumer, errorConsumer, completionConsumer, fieldIndex);
            }

            receivers.put(docRef, receiver);
        });

        assertThat(receivers.size()).isEqualTo(1);

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

    private SearchRequest createSearchRequest() {
        final QueryKey key = new QueryKey("e177cf16-da6c-4c7d-a19c-09a201f5a2da|Test Dashboard|query-MRGPM|57UG_1605699732322");
        final DocRef dataSource = new DocRef("Index", "57a35b9a-083c-4a93-a813-fc3ddfe1ff44", "Example index");
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm("EventTime", Condition.BETWEEN, "2010-01-01T00:00:00.000Z,2010-01-01T00:10:00.000Z")
                .build();
        final Query query = Query.builder()
                .dataSource(dataSource)
                .expression(expression)
                .addParam("currentUser()", "admin")
                .build();

        final String dateTimeLocale = "Europe/London";
        return SearchRequest.builder()
                .key(key)
                .query(query)
                .addResultRequests(createGroupedUserTableResultRequest())
                .addResultRequests(createDonutResultRequest())
                .addResultRequests(createGroupedUserAndEventTimeTableResultRequest())
                .addResultRequests(createBubbleResultRequest())
                .addResultRequests(createLineResultRequest())
                .dateTimeLocale(dateTimeLocale)
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
                .extractionPipeline(new DocRef("Pipeline", "e5ecdf93-d433-45ac-b14a-1f77f16ae4f7", "Example extraction"))
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
                .extractionPipeline(new DocRef("Pipeline", "e5ecdf93-d433-45ac-b14a-1f77f16ae4f7", "Example extraction"))
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
                        .sort(new Sort(1, SortDirection.ASCENDING)) // TODO : The original was not sorted but this makes the test results consistent
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
