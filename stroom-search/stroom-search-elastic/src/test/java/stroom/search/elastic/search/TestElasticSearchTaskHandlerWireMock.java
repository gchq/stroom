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

package stroom.search.elastic.search;

import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ResultStore;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.search.elastic.ElasticWireMock;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.task.api.TaskContext;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.time.StroomDuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.util.NamedValue;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Tests [ElasticSearchTaskHandler#searchSlice] using the real Elasticsearch Java client against a WireMock
/// server standing in for an Elasticsearch cluster, so that the scroll request flow is exercised over HTTP.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestElasticSearchTaskHandlerWireMock {

    private static final String INDEX_NAME = "test-index";
    private static final String SEARCH_PATH = "/" + INDEX_NAME + "/_search";
    private static final String SCROLL_PATH = "/_search/scroll";
    private static final String SCROLL_ID = "scroll-id-1";
    private static final String SCROLL_SCENARIO = "scroll";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @RegisterExtension
    static final WireMockExtension ELASTIC = ElasticWireMock.createExtension();

    @Mock
    private TaskContext taskContext;
    @Mock
    private Coprocessors coprocessors;
    @Mock
    private ResultStore resultStore;
    @Mock
    private ValuesConsumer valuesConsumer;
    @Mock
    private ErrorConsumer errorConsumer;

    private ElasticsearchClient client;
    private FieldIndex fieldIndex;
    private boolean highlight;

    @BeforeEach
    void setUp() {
        highlight = false;
        fieldIndex = new FieldIndex();
        fieldIndex.create("host");
        fieldIndex.create("user.name");
        fieldIndex.create("count");
        when(coprocessors.getFieldIndex()).thenReturn(fieldIndex);
        when(taskContext.isTerminated()).thenReturn(false);

        client = ElasticWireMock.createClient(ELASTIC);

        ELASTIC.stubFor(WireMock.delete(WireMock.urlPathEqualTo(SCROLL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, """
                        {"succeeded": true, "num_freed": 1}""")));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testScrollErrorResponse_scrollCleared() {
        stubSearch(ElasticWireMock.elasticResponse(200, searchResponse("""
                {"_index": "test-index", "_id": "1", "fields": {"host": ["web01"]}}""")));
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(SCROLL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(500, """
                        {"error": {"type": "exception", "reason": "failed"}, "status": 500}""")));

        searchSlice(index(1, 10), 0);

        assertScrollCleared();
    }

    @Test
    void testInterrupted_scrollClearedAndRethrown() {
        final UncheckedInterruptedException exception = new UncheckedInterruptedException(
                new InterruptedException("interrupted"));
        doThrow(exception).when(valuesConsumer).accept(any());
        stubSearch(ElasticWireMock.elasticResponse(200, searchResponse("""
                {"_index": "test-index", "_id": "1", "fields": {"host": ["web01"]}}""")));
        stubScrollPages(searchResponse(""));

        assertThatThrownBy(() -> searchSlice(index(1, 10), 0))
                .isSameAs(exception);

        assertThat(requests(RequestMethod.POST, SCROLL_PATH))
                .isEmpty();
        assertScrollCleared();
        verify(errorConsumer, never()).add(any(Throwable.class));
    }

    @Test
    void testClearScrollFailure_notReportedAsSearchError() {
        ELASTIC.stubFor(WireMock.delete(WireMock.urlPathEqualTo(SCROLL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(500, """
                        {"error": {"type": "exception", "reason": "clear failed"}, "status": 500}""")));
        stubSearch(ElasticWireMock.elasticResponse(200, searchResponse("""
                {"_index": "test-index", "_id": "1", "fields": {"host": ["web01"]}}""")));
        stubScrollPages(searchResponse(""));

        final AtomicLong hitCount = searchSlice(index(1, 10), 0);

        assertThat(captureRows(1))
                .containsExactly(new Val[]{ValString.create("web01"), null, null});
        assertThat(hitCount)
                .hasValue(1);
        assertScrollCleared();
        verify(errorConsumer, never()).add(any(Throwable.class));
    }

    @Test
    void testClearScrollFailure_doesNotMaskScrollError() {
        ELASTIC.stubFor(WireMock.delete(WireMock.urlPathEqualTo(SCROLL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(500, """
                        {"error": {"type": "exception", "reason": "clear failed"}, "status": 500}""")));
        stubSearch(ElasticWireMock.elasticResponse(200, searchResponse("""
                {"_index": "test-index", "_id": "1", "fields": {"host": ["web01"]}}""")));
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(SCROLL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(500, """
                        {"error": {"type": "search_context_missing_exception", "reason": "No search context"},
                         "status": 500}""")));

        searchSlice(index(1, 10), 0);

        // Only the original scroll failure is reported
        assertThat(captureError())
                .hasMessageContaining("search_context_missing_exception");
        assertScrollCleared();
    }

    private AtomicLong searchSlice(final ElasticIndexDoc index, final int slice) {
        final AtomicLong hitCount = new AtomicLong();
        final Highlight highlightBuilder = Highlight.of(h -> h
                .fields(NamedValue.of("host", HighlightField.of(f -> f))));
        handler().searchSlice(index, null, queryParams(), highlightBuilder, coprocessors, resultStore,
                valuesConsumer, errorConsumer, hitCount, client, slice, taskContext);
        return hitCount;
    }

    private ElasticSearchTaskHandler handler() {
        final ElasticSearchConfig config = new ElasticSearchConfig(
                highlight, StroomDuration.ofMinutes(1), null, null);
        return new ElasticSearchTaskHandler(() -> config, null, null, null, null, null, null);
    }

    private static ElasticQueryParams queryParams() {
        final ElasticQueryParams queryParams = new ElasticQueryParams();
        queryParams.setQuery(Query.of(q -> q.matchAll(m -> m)));
        return queryParams;
    }

    private static ElasticIndexDoc index(final int searchSlices, final int searchScrollSize) {
        return ElasticIndexDoc.builder()
                .uuid("index-uuid")
                .name("test")
                .indexName(INDEX_NAME)
                .searchSlices(searchSlices)
                .searchScrollSize(searchScrollSize)
                .build();
    }

    private static void stubSearch(final ResponseDefinitionBuilder response) {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(SEARCH_PATH))
                .withQueryParam("scroll", WireMock.matching(".+"))
                .willReturn(response));
    }

    /// Stubs successive responses to scroll requests. Once the pages run out, the last page is repeated.
    private static void stubScrollPages(final String... pages) {
        String state = Scenario.STARTED;
        for (int i = 0; i < pages.length; i++) {
            final boolean last = i == pages.length - 1;
            final String nextState = last
                    ? state
                    : "page" + (i + 2);
            ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(SCROLL_PATH))
                    .inScenario(SCROLL_SCENARIO)
                    .whenScenarioStateIs(state)
                    .willSetStateTo(nextState)
                    .willReturn(ElasticWireMock.elasticResponse(200, pages[i])));
            state = nextState;
        }
    }

    private static String searchResponse(final String hits) {
        return """
                {
                  "_scroll_id": "%s",
                  "took": 1,
                  "timed_out": false,
                  "_shards": {"total": 1, "successful": 1, "skipped": 0, "failed": 0},
                  "hits": {
                    "total": {"value": 0, "relation": "eq"},
                    "max_score": 1.0,
                    "hits": [%s]
                  }
                }""".formatted(SCROLL_ID, hits);
    }

    private static List<LoggedRequest> requests(final RequestMethod method, final String path) {
        final List<LoggedRequest> requests = new ArrayList<>();
        for (final ServeEvent serveEvent : ELASTIC.getAllServeEvents()) {
            final LoggedRequest request = serveEvent.getRequest();
            if (method.equals(request.getMethod()) && request.getUrl().split("\\?")[0].equals(path)) {
                requests.add(request);
            }
        }
        return requests;
    }

    private static LoggedRequest singleRequest(final RequestMethod method, final String path) {
        final List<LoggedRequest> requests = requests(method, path);
        assertThat(requests)
                .hasSize(1);
        return requests.getFirst();
    }

    private static JsonNode singleRequestBody(final RequestMethod method, final String path) throws IOException {
        return JSON_MAPPER.readTree(singleRequest(method, path).getBodyAsString());
    }

    private static List<String> requestedFields(final JsonNode body) {
        final List<String> fields = new ArrayList<>();
        for (final JsonNode field : body.get("fields")) {
            fields.add(field.get("field").asText());
        }
        return fields;
    }

    private static void assertScrollCleared() {
        final LoggedRequest request = singleRequest(RequestMethod.DELETE, SCROLL_PATH);
        assertThat(request.getBodyAsString())
                .contains(SCROLL_ID);
    }

    private List<Val[]> captureRows(final int expectedCount) {
        final ArgumentCaptor<Val[]> captor = ArgumentCaptor.forClass(Val[].class);
        verify(valuesConsumer, times(expectedCount)).accept(captor.capture());
        return captor.getAllValues();
    }

    private Throwable captureError() {
        final ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(errorConsumer).add(captor.capture());
        return captor.getValue();
    }
}
