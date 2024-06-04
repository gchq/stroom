package stroom.analytics.impl;

import stroom.analytics.shared.DuplicateCheckRow;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.CompiledColumn;
import stroom.query.common.v2.CompiledColumns;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;

class DuplicateCheckRowFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckRowSerde.class);

    private final boolean grouped;
    private final List<Integer> groupIndexes;
    private final List<String> columnNames;

    public DuplicateCheckRowFactory(final CompiledColumns compiledColumns) {
        final List<String> allColumnNames = new ArrayList<>();
        final List<String> groupedColumnNames = new ArrayList<>();

        groupIndexes = new ArrayList<>(compiledColumns.getCompiledColumns().length);
        boolean grouped = false;
        for (int i = 0; i < compiledColumns.getCompiledColumns().length; i++) {
            final CompiledColumn compiledColumn = compiledColumns.getCompiledColumns()[i];
            allColumnNames.add(compiledColumn.getColumn().getName());
            if (compiledColumn.getGroupDepth() >= 0) {
                groupedColumnNames.add(compiledColumn.getColumn().getName());
                groupIndexes.add(i);
                grouped = true;
            }
        }
        this.grouped = grouped;

        if (grouped) {
            this.columnNames = groupedColumnNames;
        } else {
            this.columnNames = allColumnNames;
        }
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public DuplicateCheckRow createDuplicateCheckRow(final Row row) {
        final List<String> values = new ArrayList<>(row.getValues().size());
        if (grouped) {
            for (final Integer index : groupIndexes) {
                final String value = row.getValues().get(index);
                if (value != null) {
                    LOGGER.trace(() -> "Adding grouped string (" + index + ") = " + value);
                    values.add(value);
                } else {
                    values.add("");
                }
            }
            LOGGER.trace(() -> "Grouped row values = " + String.join(", ", values));

        } else {
            for (final String value : row.getValues()) {
                if (value != null) {
                    LOGGER.trace(() -> "Adding ungrouped string = " + value);
                    values.add(value);
                } else {
                    values.add("");
                }
            }
            LOGGER.trace(() -> "Ungrouped row values = " + String.join(", ", values));
        }
        return new DuplicateCheckRow(values);
    }
}
