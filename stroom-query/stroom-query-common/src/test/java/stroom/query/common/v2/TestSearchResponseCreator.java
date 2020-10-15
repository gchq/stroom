package stroom.query.common.v2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.StaticValueFunction;
import stroom.dashboard.expression.v1.ValString;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.test.util.MockitoExtension;
import stroom.query.test.util.TimingUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSearchResponseCreator {
    private static Duration TOLLERANCE = Duration.ofMillis(100);

    @Mock
    private Store mockStore;

    @BeforeEach
    void setup() {
        // Default mock behaviour
        Mockito.when(mockStore.getErrors()).thenReturn(Collections.emptyList());
        Mockito.when(mockStore.getHighlights()).thenReturn(Collections.emptyList());
        Mockito.when(mockStore.getData(Mockito.any())).thenReturn(createSingleItemDataObject());
        Mockito.when(mockStore.getStoreSize()).thenReturn(Sizes.create(Arrays.asList(100, 10, 1)));
    }

    @Test
    void create_nonIncremental_timesOut() {
        Duration serverTimeout = Duration.ofMillis(500);
        SearchResponseCreator searchResponseCreator = new SearchResponseCreator(mockStore, serverTimeout);

        //store is never complete
        Mockito.when(mockStore.isComplete()).thenReturn(false);
        makeSearchStateAfter(500, false);

        SearchRequest searchRequest = getSearchRequest(false, null);

        TimingUtils.TimedResult<SearchResponse> timedResult = TimingUtils.timeIt(() ->
                searchResponseCreator.create(searchRequest));

        SearchResponse searchResponse = timedResult.getResult();
        Duration actualDuration = timedResult.getDuration();

        assertThat(searchResponse).isNotNull();
        assertThat(searchResponse.getResults()).isNullOrEmpty();

        assertThat(TimingUtils.isWithinTollerance(
                serverTimeout,
                actualDuration,
                TOLLERANCE)).isTrue();

        assertThat(searchResponse.getErrors()).hasSize(1);
        assertThat(searchResponse.getErrors().get(0)).containsIgnoringCase("timed out");
    }

    @Test
    void create_nonIncremental_completesImmediately() {
        //long timeout because we should return almost immediately
        Duration serverTimeout = Duration.ofMillis(5_000);
        SearchResponseCreator searchResponseCreator = new SearchResponseCreator(mockStore, serverTimeout);

        //store is immediately complete to replicate a synchronous store
        Mockito.when(mockStore.isComplete()).thenReturn(true);
        makeSearchStateAfter(0, true);

        SearchRequest searchRequest = getSearchRequest(false, null);

        TimingUtils.TimedResult<SearchResponse> timedResult = TimingUtils.timeIt(() ->
                searchResponseCreator.create(searchRequest));

        SearchResponse searchResponse = timedResult.getResult();
        Duration actualDuration = timedResult.getDuration();

        assertResponseWithData(searchResponse);

        //allow 100ms for java to run the code, it will never be 0
        assertThat(TimingUtils.isWithinTollerance(
                Duration.ZERO,
                actualDuration,
                TOLLERANCE)).isTrue();
    }

    @Test
    void create_nonIncremental_completesBeforeTimeout() {
        Duration serverTimeout = Duration.ofMillis(5_000);
        Duration clientTimeout = Duration.ofMillis(5_000);
        SearchResponseCreator searchResponseCreator = new SearchResponseCreator(mockStore, serverTimeout);

        //store initially not complete
        Mockito.when(mockStore.isComplete()).thenReturn(false);
        long sleepTime = 200L;
        makeSearchStateAfter(sleepTime, true);

        SearchRequest searchRequest = getSearchRequest(false, clientTimeout.toMillis());

        TimingUtils.TimedResult<SearchResponse> timedResult = TimingUtils.timeIt(() ->
                searchResponseCreator.create(searchRequest));

        SearchResponse searchResponse = timedResult.getResult();
        Duration actualDuration = timedResult.getDuration();

        assertResponseWithData(searchResponse);

        assertThat(TimingUtils.isWithinTollerance(
                Duration.ofMillis(sleepTime),
                actualDuration,
                TOLLERANCE)).isTrue();
    }

    @Test
    void create_incremental_noTimeout() {
        Duration serverTimeout = Duration.ofMillis(5_000);
        Duration clientTimeout = Duration.ofMillis(0);
        SearchResponseCreator searchResponseCreator = new SearchResponseCreator(mockStore, serverTimeout);

        //store is not complete during test
        Mockito.when(mockStore.isComplete()).thenReturn(false);
        makeSearchStateAfter(0, true);

        //When getData is called it should return null as it won't have had a chance to get any data yet
        Mockito.when(mockStore.getData(Mockito.any())).thenReturn(null);

        //zero timeout
        SearchRequest searchRequest = getSearchRequest(true, clientTimeout.toMillis());

        TimingUtils.TimedResult<SearchResponse> timedResult = TimingUtils.timeIt(() ->
                searchResponseCreator.create(searchRequest));

        SearchResponse searchResponse = timedResult.getResult();
        Duration actualDuration = timedResult.getDuration();

        assertThat(searchResponse).isNotNull();
        assertThat(searchResponse.getResults()).isNullOrEmpty();

        assertThat(TimingUtils.isWithinTollerance(
                clientTimeout,
                actualDuration,
                TOLLERANCE)).isTrue();

        assertThat(searchResponse.getErrors()).isNullOrEmpty();
    }

    @Test
    void create_incremental_timesOutWithDataThenCompletes() {
        //long timeout because we should return almost immediately
        Duration serverTimeout = Duration.ofMillis(500);
        Duration clientTimeout = Duration.ofMillis(500);
        SearchResponseCreator searchResponseCreator = new SearchResponseCreator(mockStore, serverTimeout);

        //store is immediately complete to replicate a synchronous store
        Mockito.when(mockStore.isComplete()).thenReturn(false);
        makeSearchStateAfter(500, false);

        SearchRequest searchRequest = getSearchRequest(true, clientTimeout.toMillis());

        TimingUtils.TimedResult<SearchResponse> timedResult = TimingUtils.timeIt(() ->
                searchResponseCreator.create(searchRequest));

        SearchResponse searchResponse = timedResult.getResult();
        Duration actualDuration = timedResult.getDuration();

        assertResponseWithData(searchResponse);

        //allow 100ms for java to run the code, it will never be 0
        assertThat(TimingUtils.isWithinTollerance(
                clientTimeout,
                actualDuration,
                TOLLERANCE)).isTrue();


        //Now the search request is sent again but this time the data will be available and the search complete
        //so should return immediately
        long sleepTime = 200L;
        makeSearchStateAfter(sleepTime, true);

        SearchRequest searchRequest2 = getSearchRequest(true, clientTimeout.toMillis());

        timedResult = TimingUtils.timeIt(() ->
                searchResponseCreator.create(searchRequest2));

        SearchResponse searchResponse2 = timedResult.getResult();
        actualDuration = timedResult.getDuration();

        assertResponseWithData(searchResponse2);

        //allow 100ms for java to run the code, it will never be 0
        assertThat(TimingUtils.isWithinTollerance(
                Duration.ofMillis(sleepTime),
                actualDuration,
                TOLLERANCE)).isTrue();
    }

    private void makeSearchStateAfter(final long sleepTime, final boolean state) {
        try {
            final Answer answer = invocation -> {
                TimingUtils.sleep(sleepTime);
                //change the store to be complete
                Mockito.when(mockStore.isComplete()).thenReturn(state);
                return state;
            };

            //200ms after the request is made another thread should complete the search
//            Mockito.doAnswer(answer).when(mockStore).awaitCompletion();
            Mockito.doAnswer(answer).when(mockStore).awaitCompletion(Mockito.anyLong(), Mockito.any(TimeUnit.class));
        } catch (final InterruptedException e) {
            // Ignore.
        }
    }

    private SearchRequest getSearchRequest(final boolean isIncremental, final Long timeout) {
        String key = UUID.randomUUID().toString();
        return new SearchRequest.Builder()
                .key(key)
                .addResultRequests(new ResultRequest.Builder()
                        .componentId(UUID.randomUUID().toString())
                        .resultStyle(ResultRequest.ResultStyle.TABLE)
                        .requestedRange(new OffsetRange.Builder()
                                .offset(0L)
                                .length(100L)
                                .build())
                        .addMappings(new TableSettings.Builder()
                                .queryId("someQueryId")
                                .addFields(
                                        new Field.Builder()
                                                .id("field1")
                                                .name("field1")
                                                .expression("expression1")
                                                .build(),
                                        new Field.Builder()
                                                .id("field2")
                                                .name("field2")
                                                .expression("expression1")
                                                .build(),
                                        new Field.Builder()
                                                .id("field3")
                                                .name("field3")
                                                .expression("expression2")
                                                .build())
                                .extractValues(false)
                                .showDetail(false)
                                .build())
                        .build())
                .incremental(isIncremental)
                .timeout(timeout)
                .dateTimeLocale("en-gb")
                .build();
    }

    private Data createSingleItemDataObject() {
        final Items<Item> items = new ItemsArrayList<>();
        final Generator[] generators = new Generator[3];
        generators[0] = new StaticValueFunction(ValString.create("A")).createGenerator();
        generators[1] = new StaticValueFunction(ValString.create("B")).createGenerator();
        generators[2] = new StaticValueFunction(ValString.create("C")).createGenerator();
        items.add(new Item(null, generators, 0));

        final Map<GroupKey, Items<Item>> map = new HashMap<>();
        map.put(null, items);

        return new Data(map, items.size(), items.size());
    }

    private void assertResponseWithData(final SearchResponse searchResponse) {
        assertThat(searchResponse).isNotNull();
        assertThat(searchResponse.getResults()).hasSize(1);
        assertThat(searchResponse.getResults().get(0)).isInstanceOf(TableResult.class);
        TableResult tableResult = (TableResult) searchResponse.getResults().get(0);
        assertThat(tableResult.getTotalResults()).isEqualTo(1);
    }
}