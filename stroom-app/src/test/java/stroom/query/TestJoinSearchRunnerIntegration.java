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
    void semiAndAntiJoins() {
        final SearchResponse kept = search("""
                from "%s"
                semi join "%s" as hr on UserId = hr.Username
                sort by Host
                select Host""".formatted(events, hr));
        final SearchResponse dropped = search("""
                from "%s"
                anti join "%s" as hr on UserId = hr.Username
                select Host""".formatted(events, hr));

        assertThat(rows(kept, 1)).containsExactly(List.of("h1"), List.of("h2"), List.of("h4"));
        assertThat(rows(dropped, 1)).containsExactly(List.of("h3"));
    }

    @Test
    void rightJoinGivesTheJoinedRowsWithNoMatchToo() {
        final SearchResponse response = search("""
                from "%s"
                right outer join "%s" as hr on UserId = hr.Username
                sort by hr.Username, Host
                select hr.Username, Host""".formatted(events, hr));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(
                List.of("jbloggs", "h1"),
                List.of("jbloggs", "h4"),
                List.of("leaver", ""),
                List.of("ssmith", "h2"));
    }

    @Test
    void fullJoinGivesTheRowsWithNoMatchOfBothSources() {
        final SearchResponse response = search("""
                from "%s"
                full join "%s" as hr on UserId = hr.Username
                select Host, hr.Username""".formatted(events, hr));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 2)).containsExactlyInAnyOrder(
                List.of("h1", "jbloggs"),
                List.of("h2", "ssmith"),
                List.of("h3", ""),
                List.of("h4", "jbloggs"),
                List.of("", "leaver"));
    }

    @Test
    void fullJoinToABracketedFirstSource() {
        final SearchResponse response = search("""
                from (from "%s" where Bytes > 150 select UserId, Host) as e
                full join "%s" as hr on e.UserId = hr.Username
                select e.Host, hr.Username""".formatted(events, hr));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 2)).containsExactlyInAnyOrder(
                List.of("h2", "ssmith"),
                List.of("h3", ""),
                List.of("h4", "jbloggs"),
                List.of("", "leaver"));
    }

    @Test
    void whereIsTestedOnTheJoinedRowsWithNoMatchToo() {
        // The leaver has no events, so its row has a blank Host and fails this.
        assertThat(rows(search("""
                from "%s"
                full join "%s" as hr on UserId = hr.Username
                where Host = 'h1'
                select Host, hr.Username""".formatted(events, hr)), 2))
                .containsExactly(List.of("h1", "jbloggs"));
        // Only the leaver is inactive, so only its row passes this.
        assertThat(rows(search("""
                from "%s"
                right join "%s" as hr on UserId = hr.Username
                where hr.Active = false
                select Host, hr.Username""".formatted(events, hr)), 2))
                .containsExactly(List.of("", "leaver"));
    }

    @Test
    void whereFieldWithNoValueAfterAFullJoinGivesAWarning() {
        // Events has no Region field, so where can never match it after the join.
        final SearchResponse response = search("""
                from "%s"
                full join "%s" as hr on UserId = hr.Username
                where Region = 'North'
                select Host, hr.Username""".formatted(events, hr));

        assertThat(rows(response, 2)).isEmpty();
        assertThat(response.getErrorMessages())
                .extracting(ErrorMessage::getMessage)
                .anySatisfy(message -> assertThat(message).contains("'Region' had no value"));
    }

    @Test
    void joinedRowsWithNoMatchAreGrouped() {
        final SearchResponse response = search("""
                from "%s"
                right join "%s" as hr on UserId = hr.Username
                group by hr.Department
                sort by hr.Department
                select hr.Department, count() as people""".formatted(events, hr));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(List.of("IT", "1"), List.of("Sales", "3"));
    }

    @Test
    void existsAndInGiveTheSameRowsAsTheirJoins() {
        final String semi = """
                from "%s" as e
                semi join "%s" as hr on e.UserId = hr.Username
                sort by Host
                select Host""".formatted(events, hr);
        final String anti = """
                from "%s" as e
                anti join "%s" as hr on e.UserId = hr.Username
                sort by Host
                select Host""".formatted(events, hr);
        final String exists = """
                from "%s" as e
                where exists (from "%s" as hr where hr.Username = e.UserId)
                sort by Host
                select Host""".formatted(events, hr);
        final String notExists = """
                from "%s" as e
                where not exists (from "%s" as hr where hr.Username = e.UserId)
                sort by Host
                select Host""".formatted(events, hr);
        final String in = """
                from "%s" as e
                where UserId in (from "%s" as hr select Username)
                sort by Host
                select Host""".formatted(events, hr);
        final String notIn = """
                from "%s" as e
                where UserId not in (from "%s" as hr select Username)
                sort by Host
                select Host""".formatted(events, hr);

        assertThat(rows(search(semi), 1)).containsExactly(List.of("h1"), List.of("h2"), List.of("h4"));
        assertThat(rows(search(anti), 1)).containsExactly(List.of("h3"));
        assertThat(rows(search(exists), 1)).isEqualTo(rows(search(semi), 1));
        assertThat(rows(search(in), 1)).isEqualTo(rows(search(semi), 1));
        assertThat(rows(search(notExists), 1)).isEqualTo(rows(search(anti), 1));
        assertThat(rows(search(notIn), 1)).isEqualTo(rows(search(anti), 1));
    }

    @Test
    void existsWithAConditionNarrowsTheJoinedSource() {
        // Only IT counts, so the anti join keeps everyone not in IT, including jbloggs in Sales.
        final SearchResponse response = search("""
                from "%s" as e
                where not exists (from "%s" as hr where hr.Username = e.UserId and hr.Department = 'IT')
                sort by Host
                select Host""".formatted(events, hr));

        assertThat(errors(response)).isEmpty();
        assertThat(rows(response, 1)).containsExactly(List.of("h1"), List.of("h3"), List.of("h4"));
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
