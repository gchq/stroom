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

package stroom.dataconnection;

import stroom.dataconnection.impl.DataConnectionStore;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.ExcelSettings;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/// An Excel Data Connection queried with StroomQL, through the real store and query machinery.
class TestDataConnectionExcelIntegration extends AbstractCoreIntegrationTest {

    private static final List<DataColumn> COLUMNS = List.of(
            new DataColumn("team", DataColumnType.TEXT),
            new DataColumn("size", DataColumnType.LONG),
            new DataColumn("founded", DataColumnType.DATE),
            new DataColumn("active", DataColumnType.BOOLEAN));

    @Inject
    private DataConnectionStore store;
    @Inject
    private ResultStoreManager resultStoreManager;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;

    // A workbook with an unrelated first sheet, so the sheet setting is used.
    private static byte[] workbook() {
        try (final XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Notes").createRow(0).createCell(0).setCellValue("Not this sheet");
            final Sheet sheet = workbook.createSheet("Teams");
            final CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            final Object[][] rows = {
                    {"team", "size", "founded", "active"},
                    {"Red", 4, LocalDate.of(2020, 1, 1), true},
                    {"Blue", 7, LocalDate.of(2021, 6, 30), false},
                    {"Green", 6, LocalDate.of(2026, 11, 5), true}};
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                final org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    final Object value = rows[rowIndex][column];
                    if (value instanceof final String text) {
                        row.createCell(column).setCellValue(text);
                    } else if (value instanceof final Integer number) {
                        row.createCell(column).setCellValue(number);
                    } else if (value instanceof final LocalDate date) {
                        row.createCell(column).setCellValue(date);
                        row.getCell(column).setCellStyle(dateStyle);
                    } else if (value instanceof final Boolean bool) {
                        row.createCell(column).setCellValue(bool);
                    }
                }
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private DataConnectionDoc saveDocument(final UnaryOperator<DataConnectionDoc.Builder> changes) {
        final DocRef docRef = store.createDocument("Teams " + UUID.randomUUID());
        final DataConnectionDoc doc = store.readDocument(docRef);
        return store.writeDocument(changes.apply(doc.copy()
                        .sourceType(DataConnectionType.EXCEL)
                        .excel(ExcelSettings.builder()
                                .fileName("teams.xlsx")
                                .sheet("Teams")
                                .columns(COLUMNS)
                                .data(workbook())
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
    void workbookIsKeptBySaving() {
        final DataConnectionDoc doc = saveDocument(UnaryOperator.identity());

        final DataConnectionDoc read = store.readDocument(doc.asDocRef());

        assertThat(read.getExcel().getData()).isNotEmpty();
        assertThat(read.getExcel().getFileName()).isEqualTo("teams.xlsx");
        assertThat(read.getExcel().getColumns()).isEqualTo(COLUMNS);
    }

    @Test
    void stroomQlQueriesTheNamedSheet() {
        final DataConnectionDoc doc = saveDocument(UnaryOperator.identity());

        final SearchResponse response = search("from \"" + doc.getName() + "\"\n"
                                               + "where active = true\n"
                                               + "sort by size desc\n"
                                               + "select team, size", null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(messages(response, Severity.WARNING)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(List.of("Green", "6"), List.of("Red", "4"));
    }

    @Test
    void timeRangeFiltersOnTheDateColumn() {
        final DataConnectionDoc doc = saveDocument(builder -> builder.timeField("founded"));

        final SearchResponse response = search("from \"" + doc.getName() + "\"\nselect team",
                new TimeRange(null, "2021-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z"));

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 1)).containsExactly(List.of("Blue"));
    }
}
