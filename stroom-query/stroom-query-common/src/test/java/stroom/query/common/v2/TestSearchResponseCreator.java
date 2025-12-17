/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.query.api.DateTimeSettings;
import stroom.query.api.OffsetRange;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeFilter;
import stroom.query.language.functions.Val;
import stroom.query.test.util.MockitoExtension;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.DurationTimer.TimedResult;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestSearchResponseCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSearchResponseCreator.class);
    private static final Duration TOLERANCE = Duration.ofMillis(500);

    @Mock
    private ResultStore mockStore;
    @Mock
    private SizesProvider sizesProvider;

    @BeforeEach
    void setup() {
        // Default mock behaviour
        Mockito.when(mockStore.getErrors()).thenReturn(Collections.emptyList());
        Mockito.when(mockStore.getHighlights()).thenReturn(Collections.emptyList());
        Mockito.when(mockStore.getData(Mockito.any())).thenReturn(createSingleItemDataObject());
        Mockito.when(sizesProvider.getDefaultMaxResultsSizes()).thenReturn(Sizes.unlimited());
    }

    @Test
    void create_nonIncremental_timesOut() {
        final Duration serverTimeout = Duration.ofMillis(500);

        //store is never complete
        Mockito.when(mockStore.isComplete()).thenReturn(false);
        makeSearchStateAfter(500, false);

        final SearchRequest searchRequest = getSearchRequest(false, null);
        final SearchResponseCreator searchResponseCreator = createSearchResponseCreator(searchRequest);
        final TimedResult<SearchResponse> timedResult = DurationTimer.measure(() ->
                searchResponseCreator.create(searchRequest,
                        searchResponseCreator.makeDefaultResultCreators(searchRequest)));

        final SearchResponse searchResponse = timedResult.getResult();
        final Duration actualDuration = timedResult.getDuration();

        assertThat(searchResponse).isNotNull();
        assertThat(searchResponse.getResults()).isNullOrEmpty();

        isWithinTolerance(
                serverTimeout,
                actualDuration,
                TOLERANCE);

        assertThat(searchResponse.getErrorMessages()).hasSize(1);
        assertThat(searchResponse.getErrorMessages().getFirst().getMessage()).containsIgnoringCase("timed out");
    }

    private SearchResponseCreator createSearchResponseCreator(final SearchRequest searchRequest) {
        return new SearchResponseCreator(
                sizesProvider,
                mockStore,
                new ExpressionContextFactory().createContext(searchRequest),
                new MapDataStoreFactory(SearchResultStoreConfig::new),
                new ExpressionPredicateFactory());
    }

    @Test
    void create_nonIncremental_completesImmediately() {
        //store is immediately complete to replicate a synchronous store
        Mockito.when(mockStore.isComplete()).thenReturn(true);
        makeSearchStateAfter(0, true);

        final SearchRequest searchRequest = getSearchRequest(false, null);
        final SearchResponseCreator searchResponseCreator = createSearchResponseCreator(searchRequest);
        final TimedResult<SearchResponse> timedResult = DurationTimer.measure(() ->
                searchResponseCreator.create(searchRequest,
                        searchResponseCreator.makeDefaultResultCreators(searchRequest)));

        final SearchResponse searchResponse = timedResult.getResult();
        final Duration actualDuration = timedResult.getDuration();

        assertResponseWithData(searchResponse);

        // Allow a period of time for java to run the code, it will never be 0
        isWithinTolerance(
                Duration.ZERO,
                actualDuration,
                TOLERANCE);
    }

    @Test
    void create_nonIncremental_completesBeforeTimeout() {
        final Duration clientTimeout = Duration.ofMillis(5_000);

        //store initially not complete
        Mockito.when(mockStore.isComplete()).thenReturn(false);
        final long sleepTime = 200L;
        makeSearchStateAfter(sleepTime, true);

        final SearchRequest searchRequest = getSearchRequest(false, clientTimeout.toMillis());
        final SearchResponseCreator searchResponseCreator = createSearchResponseCreator(searchRequest);

        final TimedResult<SearchResponse> timedResult = DurationTimer.measure(() ->
                searchResponseCreator.create(searchRequest,
                        searchResponseCreator.makeDefaultResultCreators(searchRequest)));

        final SearchResponse searchResponse = timedResult.getResult();
        final Duration actualDuration = timedResult.getDuration();

        assertResponseWithData(searchResponse);

        isWithinTolerance(
                Duration.ofMillis(sleepTime),
                actualDuration,
                TOLERANCE);
    }

    @Test
    void create_incremental_noTimeout() {
        final Duration clientTimeout = Duration.ofMillis(0);

        //store is not complete during test
        Mockito.when(mockStore.isComplete()).thenReturn(false);
        makeSearchStateAfter(0, true);

        //When getData is called it should return null as it won't have had a chance to get any data yet
        Mockito.when(mockStore.getData(Mockito.any())).thenReturn(null);

        //zero timeout
        final SearchRequest searchRequest = getSearchRequest(true, clientTimeout.toMillis());
        final SearchResponseCreator searchResponseCreator = createSearchResponseCreator(searchRequest);

        final TimedResult<SearchResponse> timedResult = DurationTimer.measure(() ->
                searchResponseCreator.create(searchRequest,
                        searchResponseCreator.makeDefaultResultCreators(searchRequest)));

        final SearchResponse searchResponse = timedResult.getResult();
        final Duration actualDuration = timedResult.getDuration();

        assertThat(searchResponse).isNotNull();
        assertThat(searchResponse.getResults()).isNullOrEmpty();

        isWithinTolerance(
                clientTimeout,
                actualDuration,
                TOLERANCE);

        assertThat(searchResponse.getErrorMessages()).isNullOrEmpty();
    }

    @Test
    void create_incremental_timesOutWithDataThenCompletes() {
        final Duration clientTimeout = Duration.ofMillis(500);

        //store is immediately complete to replicate a synchronous store
        Mockito.when(mockStore.isComplete()).thenReturn(false);
        makeSearchStateAfter(500, false);

        final SearchRequest searchRequest = getSearchRequest(true, clientTimeout.toMillis());
        final SearchResponseCreator searchResponseCreator = createSearchResponseCreator(searchRequest);

        TimedResult<SearchResponse> timedResult = DurationTimer.measure(() ->
                searchResponseCreator.create(searchRequest,
                        searchResponseCreator.makeDefaultResultCreators(searchRequest)));

        final SearchResponse searchResponse = timedResult.getResult();
        Duration actualDuration = timedResult.getDuration();

        assertResponseWithData(searchResponse);

        // Allow a period of time for java to run the code, it will never be 0
        isWithinTolerance(
                clientTimeout,
                actualDuration,
                TOLERANCE);

        //Now the search request is sent again but this time the data will be available and the search complete
        //so should return immediately
        final long sleepTime = 200L;
        makeSearchStateAfter(sleepTime, true);

        final SearchRequest searchRequest2 = getSearchRequest(true, clientTimeout.toMillis());

        timedResult = DurationTimer.measure(() ->
                searchResponseCreator.create(searchRequest2,
                        searchResponseCreator.makeDefaultResultCreators(searchRequest2)));

        final SearchResponse searchResponse2 = timedResult.getResult();
        actualDuration = timedResult.getDuration();

        assertResponseWithData(searchResponse2);

        // Allow a period of time for java to run the code, it will never be 0
        isWithinTolerance(
                Duration.ofMillis(sleepTime),
                actualDuration,
                TOLERANCE);
    }

    private void makeSearchStateAfter(final long sleepTime, final boolean state) {
        try {
            final Answer<Boolean> answer = invocation -> {
                ThreadUtil.sleep(sleepTime);
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
        final String key = UUID.randomUUID().toString();
        return SearchRequest.builder()
                .key(key)
                .addResultRequests(ResultRequest.builder()
                        .componentId(UUID.randomUUID().toString())
                        .resultStyle(ResultRequest.ResultStyle.TABLE)
                        .requestedRange(OffsetRange.ZERO_100)
                        .addMappings(TableSettings.builder()
                                .queryId("someQueryId")
                                .addColumns(
                                        Column.builder()
                                                .id("field1")
                                                .name("field1")
                                                .expression("expression1")
                                                .build(),
                                        Column.builder()
                                                .id("field2")
                                                .name("field2")
                                                .expression("expression1")
                                                .build(),
                                        Column.builder()
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
                .dateTimeSettings(DateTimeSettings.builder().build())
                .build();
    }

    private DataStore createSingleItemDataObject() {
        final Key rootKey = Key.ROOT_KEY;

        final Item item = new Item() {
            @Override
            public Key getKey() {
                return rootKey;
            }

            @Override
            public Val getValue(final int index) {
                return null;
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public Val[] toArray() {
                return Val.EMPTY_VALUES;
            }
        };

        final CompletionState completionState = new CompletionStateImpl();

        return new DataStore() {
            @Override
            public void accept(final Val[] values) {
            }

            @Override
            public List<Column> getColumns() {
                return Collections.emptyList();
            }

            @Override
            public void fetch(final List<Column> columns,
                              final OffsetRange range,
                              final OpenGroups openGroups,
                              final TimeFilter timeFilter,
                              final ItemMapper mapper,
                              final Consumer<Item> resultConsumer,
                              final Consumer<Long> totalRowCountConsumer) {
                mapper.create(item).forEach(i -> {
                    resultConsumer.accept(i);
                    if (totalRowCountConsumer != null) {
                        totalRowCountConsumer.accept(1L);
                    }
                });
            }

            @Override
            public void clear() {
            }

            @Override
            public CompletionState getCompletionState() {
                return completionState;
            }

            @Override
            public void readPayload(final Input input) {
            }

            @Override
            public void writePayload(final Output output) {
            }

            @Override
            public long getByteSize() {
                return 0;
            }

            @Override
            public KeyFactory getKeyFactory() {
                return KeyFactoryFactory.create(
                        new BasicKeyFactoryConfig(),
                        new CompiledDepths(null, false));
            }

            @Override
            public DateTimeSettings getDateTimeSettings() {
                return null;
            }
        };
    }

    private void assertResponseWithData(final SearchResponse searchResponse) {
        assertThat(searchResponse).isNotNull();
        assertThat(searchResponse.getResults()).hasSize(1);
        assertThat(searchResponse.getResults().getFirst()).isInstanceOf(TableResult.class);
        final TableResult tableResult = (TableResult) searchResponse.getResults().getFirst();
        assertThat(tableResult.getTotalResults()).isEqualTo(1);
    }

    public static void isWithinTolerance(final Duration expectedDuration,
                                         final Duration actualDuration,
                                         final Duration tolerance) {
        LOGGER.info(() -> "Expected: " +
                          expectedDuration +
                          ", actual: " +
                          actualDuration +
                          ", tolerance: " +
                          tolerance +
                          ", diff " +
                          expectedDuration.minus(actualDuration).abs());

        assertThat(actualDuration).isGreaterThanOrEqualTo(expectedDuration);
        assertThat(actualDuration).isLessThanOrEqualTo(expectedDuration.plus(tolerance));
    }
}
