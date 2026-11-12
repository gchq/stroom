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

package stroom.query;

import stroom.dataconnection.impl.DataConnectionStore;
import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.JoinTables;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/// StroomQL joins run end to end through the real query machinery: CSV Data Connections searched by
/// their own provider, joined through join tables in LMDB, into a result store.
class TestJoinSearchRunnerIntegration extends AbstractCoreIntegrationTest {

    private static final String EVENTS_CSV = """
            UserId,Host,Bytes
            jbloggs,h1,100
            ssmith,h2,200
            nobody,h3,300
            jbloggs,h4,400
            """;
    private static final String HR_CSV = """
            Username,Department,Active
            jbloggs,Sales,true
            ssmith,IT,true
            leaver,Sales,false
            """;
    private static final String DEPTS_CSV = """
            Name,CostCentre
            Sales,CC1
            IT,CC2
            """;

    @Inject
    private DataConnectionStore store;
    @Inject
    private ResultStoreManager resultStoreManager;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;
    @Inject
    private JoinTables joinTables;

    private String events;
    private String hr;
    private String depts;

    @BeforeEach
    void createDataConnections() {
        events = createCsv("Events", EVENTS_CSV, List.of(
                new DataColumn("UserId", DataColumnType.TEXT),
                new DataColumn("Host", DataColumnType.TEXT),
                new DataColumn("Bytes", DataColumnType.LONG)));
        hr = createCsv("HR", HR_CSV, List.of(
                new DataColumn("Username", DataColumnType.TEXT),
                new DataColumn("Department", DataColumnType.TEXT),
                new DataColumn("Active", DataColumnType.BOOLEAN)));
        depts = createCsv("Depts", DEPTS_CSV, List.of(
                new DataColumn("Name", DataColumnType.TEXT),
                new DataColumn("CostCentre", DataColumnType.TEXT)));
    }

    @Test
    void leftJoinAddsTheJoinedFields() {
        final SearchResponse response = search("""
                from "%s"
                left join "%s" as hr on UserId = hr.Username
                sort by Host
                select UserId, Host, hr.Department""".formatted(events, hr));

        // No warnings either: neither search warns about the other's fields.
        assertThat(response.getErrorMessages()).isNullOrEmpty();
        assertThat(rows(response, 3)).containsExactly(
                List.of("jbloggs", "h1", "Sales"),
                List.of("ssmith", "h2", "IT"),
                List.of("nobody", "h3", ""),
                List.of("jbloggs", "h4", "Sales"));
    }

    @Test
    void innerJoinWithAConditionOnTheJoinedSource() {
        final SearchResponse response = search("""
                from "%s"
                join "%s" as hr on UserId = hr.Username
                where hr.Department = 'IT'
                select UserId, Host""".formatted(events, hr));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(List.of("ssmith", "h2"));
    }

    @Test
    void keepAndDropJoins() {
        final SearchResponse kept = search("""
                from "%s"
                keep join "%s" as hr on UserId = hr.Username
                sort by Host
                select Host""".formatted(events, hr));
        final SearchResponse dropped = search("""
                from "%s"
                drop join "%s" as hr on UserId = hr.Username
                select Host""".formatted(events, hr));

        assertThat(rows(kept, 1)).containsExactly(List.of("h1"), List.of("h2"), List.of("h4"));
        assertThat(rows(dropped, 1)).containsExactly(List.of("h3"));
    }

    @Test
    void chainedJoinsAndGroupingAfterTheJoin() {
        final SearchResponse response = search("""
                from "%s"
                join "%s" as hr on UserId = hr.Username
                join "%s" as dept on hr.Department = dept.Name
                eval total = sum(Bytes)
                group by dept.CostCentre
                sort by dept.CostCentre
                select dept.CostCentre, total""".formatted(events, hr, depts));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(List.of("CC1", "500"), List.of("CC2", "200"));
    }

    @Test
    void joinToABracketedGroupedQuery() {
        final SearchResponse response = search("""
                from "%s"
                left join (
                  from "%s"
                  where Active = true
                  group by Department
                  select Department, count() as Staff
                ) as d on Name = d.Department
                sort by Name
                select Name, d.Staff""".formatted(depts, hr));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(List.of("IT", "1"), List.of("Sales", "1"));
    }

    @Test
    void bracketedFirstSourceJoined() {
        final SearchResponse response = search("""
                from (
                  from "%s"
                  eval total = sum(Bytes)
                  group by UserId
                  select UserId, total
                ) as e
                left join "%s" as hr on e.UserId = hr.Username
                sort by UserId
                select UserId, total, hr.Department""".formatted(events, hr));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 3)).containsExactly(
                List.of("jbloggs", "500", "Sales"),
                List.of("nobody", "300", ""),
                List.of("ssmith", "200", "IT"));
    }

    @Test
    void whereConditionAcrossBothSourcesRunsOnTheJoinedRows() {
        final SearchResponse response = search("""
                from "%s"
                left join "%s" as hr on UserId = hr.Username
                where Host = 'h3' or hr.Department = 'IT'
                sort by Host
                select Host""".formatted(events, hr));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 1)).containsExactly(List.of("h2"), List.of("h3"));
    }

    @Test
    void joinTablesAreDeletedWithTheSearch() {
        final SearchResponse response = search("""
                from "%s"
                join "%s" as hr on UserId = hr.Username
                select UserId""".formatted(events, hr));

        // The search helper destroys the search once it has its response.
        assertThat(joinTables.get(response.getKey())).isEmpty();
    }

    private String createCsv(final String name, final String csv, final List<DataColumn> columns) {
        final DocRef docRef = store.createDocument(name + " " + UUID.randomUUID());
        final DataConnectionDoc doc = store.readDocument(docRef);
        store.writeDocument(doc.copy()
                .sourceType(DataConnectionType.CSV)
                .csv(CsvSettings.builder()
                        .columns(columns)
                        .data(csv)
                        .build())
                .build());
        return docRef.getName();
    }

    // Runs a query as an API client would, waiting for the whole result, then destroys the search.
    private SearchResponse search(final String stroomQl) {
        final SearchRequest sampleRequest = new SearchRequest(
                null, null, Query.builder().build(), null, DateTimeSettings.builder().build(), false);
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        final SearchRequest request = searchRequestFactory.create(stroomQl, sampleRequest, expressionContext);
        final SearchResponse response = resultStoreManager.search(request);
        resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);
        assertThat(response.complete()).as("search is complete").isTrue();
        return response;
    }

    private static List<List<String>> rows(final SearchResponse response, final int selectedFields) {
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

    private static List<String> errors(final SearchResponse response) {
        if (response.getErrorMessages() == null) {
            return List.of();
        }
        return response.getErrorMessages()
                .stream()
                .filter(message -> message.getSeverity().greaterThanOrEqual(Severity.ERROR))
                .map(ErrorMessage::getMessage)
                .toList();
    }
}
