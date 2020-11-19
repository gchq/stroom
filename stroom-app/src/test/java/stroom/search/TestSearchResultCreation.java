package stroom.search;

import stroom.cluster.task.api.ClusterResultCollectorCache;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.docref.DocRef;
import stroom.index.shared.IndexConstants;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format.Type;
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
import stroom.query.common.v2.CoprocessorSettingsMap;
import stroom.query.common.v2.SearchDebugUtil;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.search.coprocessor.CoprocessorFactory;
import stroom.search.coprocessor.Coprocessors;
import stroom.search.coprocessor.CoprocessorsFactory;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.NewCoprocessor;
import stroom.search.coprocessor.Values;
import stroom.search.extraction.ExtractionReceiver;
import stroom.search.impl.ClusterSearchResultCollector;
import stroom.test.common.ProjectPathUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSearchResultCreation {

    @Mock
    private ClusterResultCollectorCache clusterResultCollectorCache;

    @Test
    void test() throws Exception {
        final SearchRequest searchRequest = createSearchRequest();

        // Make sure the search request is the same as the one we expected to make.
        final Path resourcesDir = ProjectPathUtil.resolveDir("stroom-app")
                .resolve("src/test/resources")
                .resolve("TestSearchResultCreation");

        // Validate the search request.
        validateSearchRequest(searchRequest);

        // Now create coprocessors to feed with data.
        // Create a coprocessor settings map.
        final CoprocessorSettingsMap coprocessorSettingsMap = CoprocessorSettingsMap.create(searchRequest);
        final Sizes defaultMaxResultsSizes = Sizes.parse(null);
        final Sizes storeSize = Sizes.parse("1000000,100,10,1");

        // Create coprocessors.
        final List<Error> errors = new ArrayList<>();
        final String[] storedFields = new String[]{IndexConstants.STREAM_ID, IndexConstants.EVENT_ID};
        final CoprocessorsFactory coprocessorsFactory = new CoprocessorsFactory(new CoprocessorFactory());
        final Coprocessors coprocessors = coprocessorsFactory.create(
                coprocessorSettingsMap.getMap(),
                storedFields,
                searchRequest.getQuery().getParams(),
                errors::add);

        // Group coprocessors by extraction pipeline.
        final Map<DocRef, Set<NewCoprocessor>> map = new HashMap<>();
        coprocessors.getSet().forEach(coprocessor -> {
            DocRef extractionPipeline = null;
            if (coprocessor.getSettings().extractValues()) {
                extractionPipeline = coprocessor.getSettings().getExtractionPipeline();
            }
            map.computeIfAbsent(extractionPipeline, k -> new HashSet<>()).add(coprocessor);
        });

        final Map<DocRef, ExtractionReceiver> receivers = new HashMap<>();
        map.forEach((docRef, coprocessorSet) -> {
            // Create a receiver that will send data to all coprocessors.
            ExtractionReceiver receiver;
            if (coprocessorSet.size() == 1) {
                final NewCoprocessor coprocessor = coprocessorSet.iterator().next();
                final FieldIndexMap fieldIndexMap = coprocessor.getFieldIndexMap();
                final Consumer<Values> valuesConsumer = coprocessor.getValuesConsumer();
                final Consumer<Error> errorConsumer = coprocessor.getErrorConsumer();
                final Consumer<Long> completionConsumer = coprocessor.getCompletionConsumer();
                receiver = new ExtractionReceiver(valuesConsumer, errorConsumer, completionConsumer, fieldIndexMap);
            } else {
                // We assume all coprocessors for the same extraction use the same field index map.
                // This is only the case at the moment as the CoprocessorsFactory creates field index maps this way.
                final FieldIndexMap fieldIndexMap = coprocessorSet.iterator().next().getFieldIndexMap();
                final Consumer<Values> valuesConsumer = values -> coprocessorSet.forEach(coprocessor -> coprocessor.getValuesConsumer().accept(values));
                final Consumer<Error> errorConsumer = error -> coprocessorSet.forEach(coprocessor -> coprocessor.getErrorConsumer().accept(error));
                final Consumer<Long> completionConsumer = delta -> coprocessorSet.forEach(coprocessor -> coprocessor.getCompletionConsumer().accept(delta));
                receiver = new ExtractionReceiver(valuesConsumer, errorConsumer, completionConsumer, fieldIndexMap);
            }

            receivers.put(docRef, receiver);
        });

        assertThat(receivers.size()).isEqualTo(1);

        final ExtractionReceiver consumer = receivers.values().iterator().next();

        // Add data to the consumer.
        final Path dataPath = resourcesDir.resolve("data.txt");
        final String data = Files.readString(dataPath);
        final String[] lines = data.split("\n");

        // Reorder values if field mappings have changed.
        final int[] mappings = new int[4];
        mappings[0] = consumer.getFieldIndexMap().get("EventTime");
        mappings[1] = consumer.getFieldIndexMap().get("UserId");
        mappings[2] = consumer.getFieldIndexMap().get("StreamId");
        mappings[3] = consumer.getFieldIndexMap().get("EventId");

        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final String[] values = line.split(",");
            final Val[] vals = new Val[values.length];
            for (int j = 0; j < values.length; j++) {
                final String value = values[j];
                final int target = mappings[j];
                vals[target] = ValString.create(value);
            }
            consumer.getValuesConsumer().accept(new Values(vals));
        }
        consumer.getCompletionConsumer().accept((long) lines.length);

        final SearchResultHandler searchResultHandler = new SearchResultHandler(
                coprocessorSettingsMap,
                defaultMaxResultsSizes,
                storeSize);
        searchResultHandler.handle(coprocessors.createPayloads());

        final ClusterSearchResultCollector collector = new ClusterSearchResultCollector(
                null,
                null,
                null,
                null,
                null,
                null,
                clusterResultCollectorCache,
                searchResultHandler,
                defaultMaxResultsSizes,
                storeSize);

        collector.getErrorSet("test");
        collector.complete();

        final SearchResponseCreator searchResponseCreator = new SearchResponseCreator(collector);
        final SearchResponse searchResponse = searchResponseCreator.create(searchRequest);

        // Validate the search response.
        validateSearchResponse(searchResponse);

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
        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addTerm("EventTime", Condition.BETWEEN, "2010-01-01T00:00:00.000Z,2010-01-01T00:10:00.000Z")
                .build();
        final Query query = new Query.Builder()
                .dataSource(dataSource)
                .expression(expression)
                .addParam("currentUser()", "admin")
                .build();

        final String dateTimeLocale = "Europe/London";
        return new SearchRequest.Builder()
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
        return new ResultRequest.Builder()
                .componentId("table-BKJT6")
                .addMappings(createGroupedUserTableSettings())
                .requestedRange(new OffsetRange.Builder().offset(0L).length(100L).build())
                .resultStyle(ResultStyle.TABLE)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createGroupedUserTableSettings() {
        return new TableSettings.Builder()
                .queryId("query-MRGPM")
                .addFields(new Field.Builder()
                        .id("table-BKJT6|RACJI")
                        .name("UserId")
                        .expression("${UserId}")
                        .format(Type.GENERAL)
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .group(0)
                        .build()
                )
                .addFields(new Field.Builder()
                        .id("table-BKJT6|89WRT")
                        .name("Count")
                        .expression("count()")
                        .format(Type.NUMBER)
                        .build()
                )
                .addFields(new Field.Builder()
                        .id("__stream_id__")
                        .name("__stream_id__")
                        .expression("${StreamId}")
                        .build()
                )
                .addFields(new Field.Builder()
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
        return new ResultRequest.Builder()
                .componentId("vis-QYG7H")
                .addMappings(createGroupedUserTableSettings())
                .addMappings(createDonutVisSettings())
                .resultStyle(ResultStyle.FLAT)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createDonutVisSettings() {
        return new TableSettings.Builder()
                .addFields(new Field.Builder()
                        .sort(new Sort(null, SortDirection.ASCENDING))
                        .format(Type.GENERAL)
                        .group(0)
                        .build()
                )
                .addFields(new Field.Builder()
                        .expression("${UserId}")
                        .format(Type.GENERAL)
                        .build()
                )
                .addFields(new Field.Builder()
                        .expression("${Count}")
                        .format(Type.NUMBER)
                        .build()
                )
                .addMaxResults(20, 20)
                .showDetail(true)
                .build();
    }


    private ResultRequest createGroupedUserAndEventTimeTableResultRequest() {
        return new ResultRequest.Builder()
                .componentId("table-78LF4")
                .addMappings(createGroupedUserAndEventTimeTableSettings())
                .requestedRange(new OffsetRange.Builder().offset(0L).length(100L).build())
                .resultStyle(ResultStyle.TABLE)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createGroupedUserAndEventTimeTableSettings() {
        return new TableSettings.Builder()
                .queryId("query-MRGPM")
                .addFields(new Field.Builder()
                        .id("table-78LF4|7JU9H")
                        .name("EventTime")
                        .expression("roundMinute(${EventTime})")
                        .format(Type.DATE_TIME)
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .group(0)
                        .build()
                )
                .addFields(new Field.Builder()
                        .id("table-78LF4|T5WYU")
                        .name("UserId")
                        .expression("${UserId}")
                        .format(Type.GENERAL)
                        .sort(new Sort(1, SortDirection.ASCENDING))
                        .group(0)
                        .build()
                )
                .addFields(new Field.Builder()
                        .id("table-78LF4|MT5IM")
                        .name("Count")
                        .expression("count()")
                        .format(Type.NUMBER)
                        .build()
                )
                .addFields(new Field.Builder()
                        .id("__stream_id__")
                        .name("__stream_id__")
                        .expression("${StreamId}")
                        .build()
                )
                .addFields(new Field.Builder()
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
        return new ResultRequest.Builder()
                .componentId("vis-L1AL1")
                .addMappings(createGroupedUserAndEventTimeTableSettings())
                .addMappings(createBubbleVisSettings())
                .resultStyle(ResultStyle.FLAT)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createBubbleVisSettings() {
        return new TableSettings.Builder()
                .addFields(new Field.Builder()
                        .expression("${EventTime}")
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .format(Type.DATE_TIME)
                        .group(0)
                        .build()
                )
                .addFields(new Field.Builder()
                        .expression("${UserId}")
                        .sort(new Sort(1, SortDirection.ASCENDING))
                        .format(Type.GENERAL)
                        .group(1)
                        .build()
                )
                .addFields(new Field.Builder()
                        .expression("${UserId}")
                        .sort(new Sort(2, SortDirection.ASCENDING))
                        .format(Type.GENERAL)
                        .build()
                )
                .addFields(new Field.Builder()
                        .expression("${Count}")
                        .format(Type.NUMBER)
                        .build()
                )
                .addMaxResults(20, 10, 500)
                .showDetail(true)
                .build();
    }


    private ResultRequest createLineResultRequest() {
        return new ResultRequest.Builder()
                .componentId("vis-SPSCW")
                .addMappings(createGroupedUserAndEventTimeTableSettings())
                .addMappings(createLineVisSettings())
                .resultStyle(ResultStyle.FLAT)
                .fetch(Fetch.CHANGES)
                .build();
    }

    private TableSettings createLineVisSettings() {
        return new TableSettings.Builder()
                .addFields(new Field.Builder()
                        .sort(new Sort(0, SortDirection.ASCENDING))
                        .format(Type.GENERAL)
                        .group(0)
                        .build()
                )
                .addFields(new Field.Builder()
                        .expression("${UserId}")
                        .format(Type.GENERAL)
                        .group(1)
                        .build()
                )
                .addFields(new Field.Builder()
                        .expression("${EventTime}")
                        .sort(new Sort(2, SortDirection.ASCENDING))
                        .format(Type.DATE_TIME)
                        .build()
                )
                .addFields(new Field.Builder()
                        .expression("${Count}")
                        .format(Type.NUMBER)
                        .build()
                )
                .addMaxResults(20, 100, 1000)
                .showDetail(true)
                .build();
    }
}
