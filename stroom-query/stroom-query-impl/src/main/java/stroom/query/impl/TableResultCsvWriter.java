package stroom.query.impl;

import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class TableResultCsvWriter {

    private final TableResult tableResult;

    public TableResultCsvWriter(final TableResult tableResult) {
        this.tableResult = tableResult;
    }

    private static final Predicate<Column> IS_VISIBLE_COLUMN = c -> c.isVisible() && !c.isSpecial();

    public String toCsv() {
        final String csv = getColumnNames() + "\n" + String.join("\n", getRows());
        return csv.isBlank() ? null : csv.trim();
    }

    private String getColumnNames() {
        final List<Column> columns = tableResult.getColumns();
        final List<String> headers = NullSafe.stream(columns)
                .filter(IS_VISIBLE_COLUMN)
                .map(Column::getName)
                .map(this::wrapQuotes)
                .toList();
        return String.join(",", headers);
    }

    private List<String> getRows() {
        return NullSafe.stream(tableResult.getRows())
                .map(this::toCsvRow)
                .filter(Predicate.not(String::isBlank))
                .toList();
    }

    private String toCsvRow(final Row row) {
        final List<Column> columns = tableResult.getColumns();
        final List<String> values = row.getValues();
        final List<String> csvValues = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            if (IS_VISIBLE_COLUMN.test(columns.get(i))) {
                csvValues.add(wrapQuotes(values.get(i)));
            }
        }

        return String.join(",", csvValues);
    }

    private String wrapQuotes(final String value) {
        return value == null ? null : "\"" + value + "\"";
    }
}
