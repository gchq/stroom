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

package stroom.externaldatasource.impl;

import stroom.externaldatasource.shared.CsvSettings;
import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.DataColumnType;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceTestResponse;
import stroom.externaldatasource.shared.ExternalDataSourceType;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TestCsvConnector {

    private static final TimeRange TIME_RANGE = new TimeRange(null, "2026-11-01T00:00:00.000Z",
            "2026-11-10T00:00:00.000Z");
    private static final List<DataColumn> COLUMNS = List.of(
            new DataColumn("team", DataColumnType.TEXT),
            new DataColumn("size", DataColumnType.LONG),
            new DataColumn("founded", DataColumnType.DATE));
    private static final String DATA = """
            team,size,founded
            Red,4,2020-01-01
            Blue,x,2021-06-30
            Green,6,2026-11-05
            """;

    private final DataTableCache tableCache = Mockito.mock(DataTableCache.class);
    private final CsvConnector connector = new CsvConnector(tableCache, ExternalDataSourceConfig::new);

    @SuppressWarnings("unchecked")
    TestCsvConnector() {
        Mockito.when(tableCache.get(Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<DataTable>) invocation.getArgument(3)).get());
    }

    private static ExternalDataSourceDoc.Builder doc(final String data) {
        return ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("Teams")
                .sourceType(ExternalDataSourceType.CSV)
                .csv(CsvSettings.builder()
                        .columns(COLUMNS)
                        .data(data)
                        .build());
    }

    private static Query query(final ExpressionOperator expression, final TimeRange timeRange) {
        return Query.builder()
                .expression(expression)
                .timeRange(timeRange)
                .build();
    }

    // Runs a prepared query with the given fields, returning the rows that passed.
    private static List<Val[]> run(final PreparedQuery prepared,
                                   final List<QueryRunSummary> summaries,
                                   final String... fields) throws Exception {
        final FieldIndex fieldIndex = new FieldIndex();
        for (final String field : fields) {
            fieldIndex.create(field);
        }
        final List<Val[]> rows = new ArrayList<>();
        summaries.add(prepared.run().run(
                columns -> SqlRowConsumer.create(
                        columns,
                        fieldIndex,
                        prepared.expression(),
                        DateTimeSettings.builder().build(),
                        new ExpressionPredicateFactory(),
                        rows::add),
                new QueryCancellation(),
                message -> {
                }));
        return rows;
    }

    @Test
    void takesNoSql() {
        assertThat(connector.takesNativeQuery()).isFalse();
        assertThat(connector.getType()).isEqualTo(ExternalDataSourceType.CSV);
    }

    @Test
    void fieldsAreTheColumnsWithoutReadingTheCsv() {
        assertThat(connector.getFields(doc(DATA).build(), null))
                .extracting(QueryField::getFldName, QueryField::getFldType)
                .containsExactly(
                        tuple("team", FieldType.TEXT),
                        tuple("size", FieldType.LONG),
                        tuple("founded", FieldType.DATE));
        Mockito.verifyNoInteractions(tableCache);
    }

    @Test
    void hasNoMinimumRefresh() {
        assertThat(connector.getMinRefreshMinutes(doc(DATA).build())).isZero();
    }

    @Test
    void rowsAreReadAsTheirColumnTypes() throws Exception {
        final List<QueryRunSummary> summaries = new ArrayList<>();
        final List<Val[]> rows = run(connector.prepare(doc(DATA).build(), query(null, null),
                DateTimeSettings.builder().build()), summaries, "team", "size");

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsExactly(ValString.create("Red"), ValLong.create(4));
        final QueryRunSummary summary = summaries.getFirst();
        assertThat(summary.rowsRead()).isEqualTo(3);
        assertThat(summary.rowsPassed()).isEqualTo(3);
        assertThat(summary.limitReached()).isFalse();
    }

    @Test
    void valueNotOfItsColumnTypeIsEmptyWithAWarning() throws Exception {
        final List<QueryRunSummary> summaries = new ArrayList<>();
        final List<Val[]> rows = run(connector.prepare(doc(DATA).build(), query(null, null),
                DateTimeSettings.builder().build()), summaries, "team", "size");

        assertThat(rows.get(1)).containsExactly(ValString.create("Blue"), ValNull.INSTANCE);
        assertThat(summaries.getFirst().warnings())
                .containsExactly("1 value was not of its column's type, so it is empty, e.g. 'x' in column "
                                 + "'size' (Long).");
    }

    @Test
    void whereExpressionFiltersTheRows() throws Exception {
        final ExpressionOperator where = ExpressionOperator.builder()
                .addTerm("team", Condition.EQUALS, "Green")
                .build();
        final List<QueryRunSummary> summaries = new ArrayList<>();
        final List<Val[]> rows = run(connector.prepare(doc(DATA).build(), query(where, null),
                DateTimeSettings.builder().build()), summaries, "team");

        assertThat(rows).hasSize(1);
        assertThat(summaries.getFirst().rowsRead()).isEqualTo(3);
        assertThat(summaries.getFirst().rowsPassed()).isEqualTo(1);
    }

    @Test
    void rowLimitStopsTheRead() throws Exception {
        final List<QueryRunSummary> summaries = new ArrayList<>();
        final List<Val[]> rows = run(connector.prepare(doc(DATA).maxRows(2).build(), query(null, null),
                DateTimeSettings.builder().build()), summaries, "team");

        assertThat(rows).hasSize(2);
        assertThat(summaries.getFirst().limitReached()).isTrue();
        assertThat(summaries.getFirst().maxRows()).isEqualTo(2);
    }

    @Test
    void timeRangeFiltersOnTheDateTimeColumn() throws Exception {
        final PreparedQuery prepared = connector.prepare(doc(DATA).timeField("founded").build(),
                query(null, TIME_RANGE), DateTimeSettings.builder().build());

        assertThat(prepared.warnings()).isEmpty();
        assertThat(prepared.expression().op()).isEqualTo(Op.AND);
        final List<Val[]> rows = run(prepared, new ArrayList<>(), "team");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()[0]).isEqualTo(ValString.create("Green"));
    }

    @Test
    void timeRangeWithoutATimeColumnIsWarned() {
        final PreparedQuery prepared = connector.prepare(doc(DATA).build(), query(null, TIME_RANGE),
                DateTimeSettings.builder().build());

        assertThat(prepared.warnings()).containsExactly(CsvConnector.TIME_RANGE_IGNORED);
        assertThat(prepared.expression()).isNull();
    }

    @Test
    void timeColumnMustBeAColumn() {
        assertThatThrownBy(() -> connector.prepare(doc(DATA).timeField("created").build(),
                query(null, TIME_RANGE), DateTimeSettings.builder().build()))
                .hasMessageContaining("'created' is not a column");
    }

    @Test
    void timeColumnMustBeADateColumn() {
        assertThatThrownBy(() -> connector.prepare(doc(DATA).timeField("team").build(),
                query(null, TIME_RANGE), DateTimeSettings.builder().build()))
                .hasMessageContaining("is not a Date column");
    }

    @Test
    void documentWithoutColumnsIsRefused() {
        final ExternalDataSourceDoc doc = doc(DATA).csv(CsvSettings.builder()
                .data(DATA)
                .build()).build();

        assertThatThrownBy(() -> connector.prepare(doc, query(null, null), DateTimeSettings.builder().build()))
                .hasMessageContaining("has no columns");
    }

    @Test
    void changedHeaderIsWarned() throws Exception {
        final List<QueryRunSummary> summaries = new ArrayList<>();
        run(connector.prepare(doc("team,members,founded\nRed,4,2020-01-01\n").build(), query(null, null),
                DateTimeSettings.builder().build()), summaries, "team");

        assertThat(summaries.getFirst().warnings()).containsExactly(CsvConnector.HEADER_CHANGED);
    }

    @Test
    void cancelledQueryReadsNothing() throws Exception {
        final PreparedQuery prepared = connector.prepare(doc(DATA).build(), query(null, null),
                DateTimeSettings.builder().build());
        final QueryCancellation cancellation = new QueryCancellation();
        cancellation.cancel();

        final QueryRunSummary summary = prepared.run().run(
                columns -> SqlRowConsumer.create(columns, new FieldIndex(), null, DateTimeSettings.builder().build(),
                        new ExpressionPredicateFactory(), values -> {
                        }),
                cancellation,
                message -> {
                });

        assertThat(summary.rowsRead()).isZero();
    }

    @Test
    void testReportsRowsColumnsAndBadValues() {
        final ExternalDataSourceTestResponse response = connector.test(doc(DATA).build());

        assertThat(response.isOk()).isTrue();
        assertThat(response.getMessage())
                .startsWith("Read 3 rows of 3 columns.")
                .contains("'x' in column 'size'");
    }

    @Test
    void testWithoutCsvFails() {
        assertThat(connector.test(doc(null).build()).isOk()).isFalse();
    }

    @Test
    void testOfBadCsvFails() {
        final ExternalDataSourceTestResponse response = connector.test(doc("team\n\"Red\"x\n").build());

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).startsWith("The CSV could not be read");
    }

    @Test
    void headerWithBlankRepeatedOrMissingNamesIsNotWarnedOnceDetected() throws Exception {
        // Detect Columns names these column2, team_2 and column4, which the header does not say.
        final String data = "team,,team\nRed,1,Blue,extra\n";
        final ExternalDataSourceDoc doc = doc(data).csv(CsvSettings.builder()
                .columns(ColumnDetector.detect(CsvReader.parse(CsvSettings.builder().data(data).build(), 100),
                        null))
                .data(data)
                .build()).build();
        final List<QueryRunSummary> summaries = new ArrayList<>();

        run(connector.prepare(doc, query(null, null), DateTimeSettings.builder().build()), summaries, "team");

        assertThat(summaries.getFirst().warnings()).isEmpty();
    }

    @Test
    void csvOverTheSizeLimitIsRefusedWhenQueried() {
        // An imported document is not checked when saved.
        final CsvConnector smallLimit = new CsvConnector(tableCache, () -> new ExternalDataSourceConfig(
                null, null, null, null, null, null, null, null, null, null, null, ByteSize.ofBytes(10), null));

        assertThatThrownBy(() -> smallLimit.prepare(doc(DATA).build(), query(null, null),
                DateTimeSettings.builder().build()))
                .hasMessageContaining("maxStoredDataSize");
        Mockito.verifyNoInteractions(tableCache);
    }

    @Test
    void checkCsvRefusesCsvOverTheSizeLimit() {
        final CsvConnector smallLimit = new CsvConnector(tableCache, () -> new ExternalDataSourceConfig(
                null, null, null, null, null, null, null, null, null, null, null, ByteSize.ofBytes(10), null));

        final ExternalDataSourceTestResponse response = smallLimit.test(doc(DATA).build());

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("maxStoredDataSize");
    }

    @Test
    void columnWithNoNameIsRefused() {
        final ExternalDataSourceDoc doc = doc(DATA).csv(CsvSettings.builder()
                .columns(List.of(new DataColumn("team", DataColumnType.TEXT), new DataColumn(null, null)))
                .data(DATA)
                .build()).build();

        assertThatThrownBy(() -> connector.prepare(doc, query(null, null), DateTimeSettings.builder().build()))
                .hasMessageContaining("a column with no name");
        assertThat(connector.getFields(doc, null)).extracting(QueryField::getFldName).containsExactly("team");
    }

    @Test
    void repeatedColumnNameIsRefused() {
        final ExternalDataSourceDoc doc = doc(DATA).csv(CsvSettings.builder()
                .columns(List.of(new DataColumn("team", DataColumnType.TEXT), new DataColumn("Team", null)))
                .data(DATA)
                .build()).build();

        assertThatThrownBy(() -> connector.prepare(doc, query(null, null), DateTimeSettings.builder().build()))
                .hasMessageContaining("more than one column named 'Team'");
    }

    @Test
    void timeColumnIsMatchedWithoutRegardToCase() {
        final PreparedQuery prepared = connector.prepare(doc(DATA).timeField("FOUNDED").build(),
                query(null, TIME_RANGE), DateTimeSettings.builder().build());

        assertThat(prepared.warnings()).isEmpty();
    }

    @Test
    void exactlyTheRowLimitIsNotReportedAsReachingIt() throws Exception {
        final List<QueryRunSummary> summaries = new ArrayList<>();
        run(connector.prepare(doc(DATA).maxRows(3).build(), query(null, null),
                DateTimeSettings.builder().build()), summaries, "team");

        assertThat(summaries.getFirst().rowsRead()).isEqualTo(3);
        assertThat(summaries.getFirst().limitReached()).isFalse();
    }
}
