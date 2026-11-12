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

package stroom.search;

import stroom.dataconnection.impl.DataConnectionStore;
import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.JoinPushdown;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/// StroomQL joins whose first source is a Lucene index, searched on this node with extraction, joined
/// to a CSV Data Connection. The index's `UserId` is indexed for searching; the extraction pipeline
/// gives the same value as `User`, which is what the joins use.
class TestLuceneSearchProviderJoinIntegration extends AbstractCoreIntegrationTest {

    private static final String UNKNOWN_STREAM_ID = "999999";

    private static boolean indexed;

    @Inject
    private CommonIndexingTestHelper commonIndexingTestHelper;
    @Inject
    private DataConnectionStore dataConnectionStore;
    @Inject
    private ResultStoreManager resultStoreManager;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;
    @Inject
    private JoinPushdown joinPushdown;

    private String users;

    @BeforeEach
    void setUp() {
        if (!indexed) {
            commonIndexingTestHelper.setup();
            indexed = true;
        }
        users = createCsv(
                List.of(new DataColumn("Name", DataColumnType.TEXT), new DataColumn("Team", DataColumnType.TEXT)),
                "Name,Team\nuser5,Red\nuser6,Blue\n");
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    @Test
    void indexEventsLeftJoinedToACsv() {
        final List<List<String>> rows = search("""
                from "Test index"
                left join "%s" as u on User = u.Name
                where UserId = user5 and Description = e0567
                  and EventTime >= 2000-01-01T00:00:00.000Z
                  and EventTime <= 2016-01-02T00:00:00.000Z
                select User, u.Team""".formatted(users), 2);

        assertThat(rows).hasSize(5).allSatisfy(row -> assertThat(row).containsExactly("user5", "Red"));
    }

    @Test
    void indexEventsKeptOnlyForUsersInTheCsvAndGrouped() {
        final List<List<String>> rows = search("""
                from "Test index"
                keep join "%s" as u on User = u.Name
                where EventTime >= 2000-01-01T00:00:00.000Z
                  and EventTime <= 2016-01-02T00:00:00.000Z
                group by User
                select User, count() as Events""".formatted(users), 2);

        assertThat(rows).isNotEmpty();
        assertThat(rows).extracting(row -> row.getFirst()).containsAnyOf("user5", "user6");
        assertThat(rows).extracting(row -> row.getFirst()).isSubsetOf("user5", "user6");
    }

    @Test
    void keepJoinOnAnIdFieldIsPushedDownToTheIndex() {
        final Map<String, Long> all = eventsPerStream("");
        assertThat(all).as("streams indexed").isNotEmpty();
        final String streamId = all.keySet().iterator().next();
        final String kept = "keep join \"" + createStreamsCsv(streamId) + "\" as s on StreamId = s.Id";
        final String noneKept = "keep join \"" + createStreamsCsv(UNKNOWN_STREAM_ID) + "\" as s on StreamId = s.Id";

        assertThat(getPushedDownTerms(kept, streamId)).contains("StreamId in " + streamId);
        assertThat(eventsPerStream(kept)).containsExactlyEntriesOf(Map.of(streamId, all.get(streamId)));
        assertThat(eventsPerStream(noneKept)).isEmpty();
    }

    @Test
    void dropJoinOnAnIdFieldIsPushedDownToTheIndex() {
        final Map<String, Long> all = eventsPerStream("");
        assertThat(all).as("streams indexed").isNotEmpty();
        final String streamId = all.keySet().iterator().next();
        final String dropped = "drop join \"" + createStreamsCsv(streamId) + "\" as s on StreamId = s.Id";
        final String noneDropped = "drop join \"" + createStreamsCsv(UNKNOWN_STREAM_ID) + "\" as s on StreamId = s.Id";

        assertThat(getPushedDownTerms(dropped, streamId)).contains("NOT {StreamId in " + streamId + "}");
        final Map<String, Long> expected = new HashMap<>(all);
        expected.remove(streamId);
        assertThat(eventsPerStream(dropped)).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(eventsPerStream(noneDropped)).containsExactlyInAnyOrderEntriesOf(all);
    }

    private String createStreamsCsv(final String streamId) {
        return createCsv(List.of(new DataColumn("Id", DataColumnType.TEXT)), "Id\n" + streamId + "\n");
    }

    // The number of events in the time range of each stream, through the given join line.
    private Map<String, Long> eventsPerStream(final String joinLine) {
        final List<List<String>> rows = search("""
                from "Test index"
                %s
                where EventTime >= 2000-01-01T00:00:00.000Z
                  and EventTime <= 2016-01-02T00:00:00.000Z
                group by StreamId
                select StreamId, count() as Events""".formatted(joinLine), 2);
        final Map<String, Long> eventsPerStream = new LinkedHashMap<>();
        rows.forEach(row -> eventsPerStream.put(row.get(0), Long.parseLong(row.get(1))));
        return eventsPerStream;
    }

    // The where of the index search once a join line with one key is pushed down, as the join search
    // runner pushes it down, using the index's real fields.
    private String getPushedDownTerms(final String joinLine, final String key) {
        final SearchRequest request = createRequest("""
                from "Test index"
                %s
                select StreamId""".formatted(joinLine));
        final JoinPushdown.Keys keys = JoinPushdown.Keys.forJoin(request.getQuery().getJoins().getFirst());
        keys.add(key.length() + ":" + key);
        return joinPushdown.apply(request.getQuery(), List.of(keys), Map.of()).getExpression().toString();
    }

    private String createCsv(final List<DataColumn> columns, final String data) {
        final DocRef docRef = dataConnectionStore.createDocument("CSV " + UUID.randomUUID());
        dataConnectionStore.writeDocument(dataConnectionStore.readDocument(docRef).copy()
                .sourceType(DataConnectionType.CSV)
                .csv(CsvSettings.builder()
                        .columns(columns)
                        .data(data)
                        .build())
                .build());
        return docRef.getName();
    }

    private SearchRequest createRequest(final String stroomQl) {
        final SearchRequest sampleRequest = new SearchRequest(
                null, null, null, null, DateTimeSettings.builder().build(), false);
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        return searchRequestFactory.create(stroomQl, sampleRequest, expressionContext);
    }

    // Runs a query with the search result extraction pipeline, as the index's own tests do, and
    // returns the values of the selected fields, failing on any error or warning.
    private List<List<String>> search(final String stroomQl, final int selectedFields) {
        final SearchRequest parsed = createRequest(stroomQl);
        final ResultRequest resultRequest = parsed.getResultRequests().getFirst();
        final TableSettings tableSettings = resultRequest.getMappings().getFirst().copy()
                .extractionPipeline(commonIndexingTestHelper.getSearchResultPipeline())
                .build();
        final SearchRequest request = parsed.copy()
                .resultRequests(List.of(resultRequest.copy().mappings(List.of(tableSettings)).build()))
                .build();

        final SearchResponse response = resultStoreManager.search(request);
        resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);
        assertThat(response.getErrorMessages()).as("errors and warnings").isNullOrEmpty();
        assertThat(response.complete()).as("search is complete").isTrue();

        final List<List<String>> rows = new ArrayList<>();
        for (final Result result : response.getResults()) {
            for (final Row row : ((TableResult) result).getRows()) {
                final List<String> values = new ArrayList<>();
                for (final String value : row.getValues().subList(0, selectedFields)) {
                    values.add(value == null
                            ? ""
                            : value);
                }
                rows.add(values);
            }
        }
        return rows;
    }
}
