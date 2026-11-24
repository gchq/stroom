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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionTestResponse;
import stroom.dataconnection.shared.StoredDataSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.util.shared.NullSafe;

import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Queries data held in the document itself, such as CSV or an Excel sheet.
///
/// The document's columns table gives each column's name and type, in the order the values appear in
/// each row, so the fields are known without reading the data. Rows are read once per document
/// version and cached. A value that is not of its column's type is empty, and the query warns.
///
/// ## The time range
///
/// If the document names a time column, which must be a Date column, rows are filtered on it in
/// Stroom. Otherwise the time range is not used, and the query warns that it was ignored.
abstract class StoredDataConnector implements Connector {

    static final String TIME_RANGE_IGNORED = "The time range was ignored, as the Data Connection has no "
                                             + "time column.";
    static final String HEADER_CHANGED = "The header row no longer matches the columns of the Data "
                                         + "Connection, so values may be under the wrong fields. Detect the columns "
                                         + "again in its settings.";

    private final DataTableCache tableCache;
    private final Provider<DataConnectionConfig> configProvider;

    /// @param tableCache     Caches the rows read from each document.
    /// @param configProvider Gives the size limit and row limits.
    StoredDataConnector(final DataTableCache tableCache,
                        final Provider<DataConnectionConfig> configProvider) {
        this.tableCache = tableCache;
        this.configProvider = configProvider;
    }

    /// @param doc A Data Connection of this connector's type.
    /// @return Its settings, or `null` if it has none.
    abstract StoredDataSettings settings(DataConnectionDoc doc);

    /// @return What the data is called in messages, e.g. "CSV" or "workbook".
    abstract String dataName();

    /// @param doc A Data Connection of this connector's type.
    /// @return True if it holds any data.
    abstract boolean hasData(DataConnectionDoc doc);

    /// @param doc A Data Connection of this connector's type.
    /// @return The size of its data in bytes, as stored.
    abstract long dataSize(DataConnectionDoc doc);

    /// @param doc A Data Connection of this connector's type, holding data.
    /// @return A key standing for its data and the settings for reading it, for caching what is read.
    abstract Object dataKey(DataConnectionDoc doc);

    /// Reads a document's data.
    ///
    /// @param doc     A Data Connection of this connector's type, holding data.
    /// @param maxRows The most rows to read after the header.
    /// @return The rows.
    /// @throws IllegalArgumentException If the data cannot be read.
    abstract DataTable read(DataConnectionDoc doc, int maxRows);

    /// Data held in the document is queried with Stroom's own clauses, so a query of it has no SQL.
    @Override
    public boolean takesNativeQuery() {
        return false;
    }

    /// The fields are the document's columns, so the data is not read.
    @Override
    public List<QueryField> getFields(final DataConnectionDoc doc, final String nativeQuery) {
        return SqlColumn.toQueryFields(columns(doc));
    }

    /// The data only changes when the document does, so there is no minimum.
    @Override
    public int getMinRefreshMinutes(final DataConnectionDoc doc) {
        return 0;
    }

    /// Checks the columns, the size of the data and the time column, then reads the rows, from the
    /// cache if they have been read before.
    @Override
    public PreparedQuery prepare(final DataConnectionDoc doc,
                                 final Query query,
                                 final DateTimeSettings dateTimeSettings) {
        final List<SqlColumn> columns = columns(doc);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Data Connection '" + doc.getName() + "' has no columns. "
                                               + "Detect them in its settings.");
        }
        final List<DataColumn> dataColumns = settings(doc).getColumns();
        DataColumns.requireNamed(doc, dataColumns);
        DataColumns.requireUniqueNames(doc, dataColumns, "column");
        // An imported document is not checked on save, so its size is checked before it is read.
        requireWithinSizeLimit(doc);
        final TimeRanges.Applied timeRange = TimeRanges.apply(query, false, doc.getTimeField(), TIME_RANGE_IGNORED);
        if (timeRange.filtersOnTimeColumn()) {
            TimeRanges.requireDateColumn(doc, dataColumns, doc.getTimeField(), "column", "");
        }
        final SqlQueryLimits limits = SqlQueryLimits.of(doc, configProvider.get());

        return new PreparedQuery(timeRange.expression(), timeRange.warnings(),
                (rowConsumerFactory, cancellation, progress) -> {
                // One row past the limit, to tell whether the limit was reached.
                final int rowsToRead = limits.maxRows() == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : limits.maxRows() + 1;
                final DataTable table = hasData(doc)
                        ? tableCache.get(doc, dataKey(doc), rowsToRead, () -> read(doc, rowsToRead))
                        : new DataTable(List.of(), List.of());
                final SqlRowConsumer rowConsumer = rowConsumerFactory.apply(columns);
                final BadValues badValues = new BadValues();
                final RowCounter counter = new RowCounter(limits.maxRows());
                for (final List<String> row : table.rows()) {
                    if (cancellation.isCancelled()
                        || !counter.take(() -> rowConsumer.accept(
                            columnIndex -> readValue(row, columnIndex, dataColumns, badValues)))) {
                        break;
                    }
                }

                final List<String> runWarnings = new ArrayList<>();
                headerWarning(table, dataColumns).ifPresent(runWarnings::add);
                badValues.warning().ifPresent(runWarnings::add);
                return counter.summary(rowConsumer.getUnmatchedFields(), null, runWarnings);
            });
    }

    /// Reads all of the data, and reports how many rows and columns it has, and any values that are not
    /// of their column's type.
    @Override
    public DataConnectionTestResponse testConnection(final DataConnectionDoc doc) throws Exception {
        if (!hasData(doc)) {
            return new DataConnectionTestResponse(false, "There is no " + dataName() + ".");
        }
        requireWithinSizeLimit(doc);
        final DataTable table = read(doc, Integer.MAX_VALUE);
        final List<DataColumn> dataColumns = NullSafe.list(settings(doc).getColumns());
        final BadValues badValues = new BadValues();
        for (final List<String> row : table.rows()) {
            for (int index = 0; index < dataColumns.size(); index++) {
                readValue(row, index, dataColumns, badValues);
            }
        }
        final StringBuilder sb = new StringBuilder()
                .append("Read ")
                .append(table.rows().size())
                .append(table.rows().size() == 1 ? " row" : " rows")
                .append(" of ")
                .append(table.columnCount())
                .append(" columns.");
        if (dataColumns.isEmpty()) {
            sb.append("\nNo columns have been set, so the ")
                    .append(dataName())
                    .append(" cannot be queried. Detect them first.");
        }
        headerWarning(table, dataColumns).ifPresent(warning -> sb.append("\n").append(warning));
        badValues.warning().ifPresent(warning -> sb.append("\n").append(warning));
        return new DataConnectionTestResponse(true, sb.toString());
    }

    /// @param doc A Data Connection of this connector's type.
    /// @return Its columns, as the row consumer takes them, or none. A column with no name, which only
    ///         an imported document can have, is left out; querying such a document is refused.
    List<SqlColumn> columns(final DataConnectionDoc doc) {
        final StoredDataSettings settings = settings(doc);
        final List<DataColumn> dataColumns = settings == null
                ? null
                : settings.getColumns();
        if (dataColumns == null) {
            return List.of();
        }
        return dataColumns.stream()
                .filter(column -> !NullSafe.isBlankString(column.getName()))
                .map(SqlColumn::of)
                .toList();
    }

    private static Val readValue(final List<String> row,
                                 final int columnIndex,
                                 final List<DataColumn> dataColumns,
                                 final BadValues badValues) {
        if (columnIndex >= row.size()) {
            return ValNull.INSTANCE;
        }
        final String text = row.get(columnIndex);
        final DataColumn column = dataColumns.get(columnIndex);
        try {
            return DataValues.read(text, column.getType());
        } catch (final IllegalArgumentException e) {
            badValues.add(text, column);
            return ValNull.INSTANCE;
        }
    }

    // The header names columns by position, so a header that differs from the names Detect Columns
    // would give means the data has changed since its columns were detected. Columns past the end of
    // the header are named by position, so only the header's own columns are compared.
    private static Optional<String> headerWarning(final DataTable table, final List<DataColumn> dataColumns) {
        if (table.header().isEmpty() || dataColumns == null || dataColumns.isEmpty()) {
            return Optional.empty();
        }
        final List<String> expected = ColumnDetector.uniqueNames(table.header(), table.header().size());
        final List<String> names = dataColumns.stream()
                .map(DataColumn::getName)
                .toList();
        return names.size() >= expected.size() && names.subList(0, expected.size()).equals(expected)
                ? Optional.empty()
                : Optional.of(HEADER_CHANGED);
    }

    private void requireWithinSizeLimit(final DataConnectionDoc doc) {
        StoredDataLimit.findProblem(dataName(), dataSize(doc), configProvider.get())
                .ifPresent(problem -> {
                    throw new IllegalArgumentException(problem);
                });
    }

}
