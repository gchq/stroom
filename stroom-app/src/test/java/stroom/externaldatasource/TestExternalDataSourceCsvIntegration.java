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

package stroom.externaldatasource;

import stroom.docref.DocRef;
import stroom.externaldatasource.impl.ExternalDataSourceStore;
import stroom.externaldatasource.shared.CsvSettings;
import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.DataColumnType;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceType;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/// A CSV External Data Source queried with StroomQL, through the real store and query machinery.
class TestExternalDataSourceCsvIntegration extends AbstractCoreIntegrationTest {

    private static final String CSV = """
            team,size,founded,active
            Red,4,2020-01-01,true
            Blue,7,2021-06-30,false
            Green,6,2026-11-05,true
            """;
    private static final List<DataColumn> COLUMNS = List.of(
            new DataColumn("team", DataColumnType.TEXT),
            new DataColumn("size", DataColumnType.LONG),
            new DataColumn("founded", DataColumnType.DATE),
            new DataColumn("active", DataColumnType.BOOLEAN));

    @Inject
    private ExternalDataSourceStore store;
    @Inject
    private ResultStoreManager resultStoreManager;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;
    @Inject
    private DataSourceProviderRegistry dataSourceProviderRegistry;

    private ExternalDataSourceDoc saveDocument(final UnaryOperator<ExternalDataSourceDoc.Builder> changes) {
        final DocRef docRef = store.createDocument("Teams " + UUID.randomUUID());
        final ExternalDataSourceDoc doc = store.readDocument(docRef);
        return store.writeDocument(changes.apply(doc.copy()
                        .sourceType(ExternalDataSourceType.CSV)
                        .csv(CsvSettings.builder()
                                .columns(COLUMNS)
                                .data(CSV)
                                .build()))
                .build());
    }

    private SearchResponse search(final String stroomQl, final TimeRange timeRange) {
        final SearchRequest sampleRequest = new SearchRequest(
                null,
                null,
                Query.builder()
                        .timeRange(timeRange)
                        .build(),
                null,
                DateTimeSettings.builder().build(),
                false);
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
                rows.add(row.getValues().subList(0, selectedFields));
            }
        }
        return rows;
    }

    private static List<String> messages(final SearchResponse response, final Severity severity) {
        if (response.getErrorMessages() == null) {
            return List.of();
        }
        return response.getErrorMessages()
                .stream()
                .filter(errorMessage -> severity.equals(errorMessage.getSeverity()))
                .map(ErrorMessage::getMessage)
                .toList();
    }

    @Test
    void csvIsKeptBySaving() {
        final ExternalDataSourceDoc doc = saveDocument(UnaryOperator.identity());

        final ExternalDataSourceDoc read = store.readDocument(doc.asDocRef());

        assertThat(read.getCsv().getData()).isEqualTo(CSV);
        assertThat(read.getCsv().getColumns()).isEqualTo(COLUMNS);
    }

    @Test
    void stroomQlQueriesTheCsvWithNoSql() {
        final ExternalDataSourceDoc doc = saveDocument(UnaryOperator.identity());

        final SearchResponse response = search("from \"" + doc.getName() + "\"\n"
                                                + "where size > 5\n"
                                                + "sort by team\n"
                                                + "select team, size", null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(List.of("Blue", "7"), List.of("Green", "6"));
    }

    @Test
    void stroomFunctionsAndGroupingWorkOnTheRows() {
        final ExternalDataSourceDoc doc = saveDocument(UnaryOperator.identity());

        final SearchResponse response = search("from \"" + doc.getName() + "\"\n"
                                                + "eval total = sum(size)\n"
                                                + "group by active\n"
                                                + "sort by active\n"
                                                + "select active, total", null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(List.of("false", "7"), List.of("true", "10"));
    }

    @Test
    void whereClauseFiltersOnBooleanAndDateColumns() {
        final ExternalDataSourceDoc doc = saveDocument(UnaryOperator.identity());

        final SearchResponse response = search("from \"" + doc.getName() + "\"\n"
                                                + "where active = true and founded > 2020-06-01T00:00:00.000Z\n"
                                                + "select team", null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 1)).containsExactly(List.of("Green"));
    }

    @Test
    void sqlClauseIsRefused() {
        final ExternalDataSourceDoc doc = saveDocument(UnaryOperator.identity());

        assertThatThrownBy(() -> search("from \"" + doc.getName() + "\"\n"
                                        + "sql \"\"\"select * from teams\"\"\"\n"
                                        + "select team", null))
                .hasMessageContaining("can only be used with a data source that runs SQL");
    }

    @Test
    void timeRangeFiltersOnTheTimeColumn() {
        final ExternalDataSourceDoc doc = saveDocument(builder -> builder.timeField("founded"));

        final SearchResponse response = search("from \"" + doc.getName() + "\"\nselect team",
                new TimeRange(null, "2021-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z"));

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 1)).containsExactly(List.of("Blue"));
    }

    @Test
    void timeRangeIsIgnoredWithAWarningWithoutATimeColumn() {
        final ExternalDataSourceDoc doc = saveDocument(UnaryOperator.identity());

        final SearchResponse response = search("from \"" + doc.getName() + "\"\nselect team",
                new TimeRange(null, "2021-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z"));

        assertThat(rows(response, 1)).hasSize(3);
        assertThat(messages(response, Severity.WARNING)).anySatisfy(message ->
                assertThat(message).contains("time range was ignored"));
    }

    @Test
    void fieldsAreListedWithoutSql() {
        // As StroomQL completion and the dashboard Query pane ask, with no SQL.
        final ExternalDataSourceDoc doc = saveDocument(UnaryOperator.identity());

        final List<QueryField> fields = dataSourceProviderRegistry.getFieldInfo(new FindFieldCriteria(
                        PageRequest.unlimited(), FindFieldCriteria.DEFAULT_SORT_LIST, doc.asDocRef(), null, null,
                        null))
                .getValues();

        assertThat(fields)
                .extracting(QueryField::getFldName, QueryField::getFldType)
                .containsExactly(
                        tuple("team", FieldType.TEXT),
                        tuple("size", FieldType.LONG),
                        tuple("founded", FieldType.DATE),
                        tuple("active", FieldType.BOOLEAN));
        assertThat(dataSourceProviderRegistry.takesNativeQuery(doc.asDocRef())).isFalse();
    }
}
