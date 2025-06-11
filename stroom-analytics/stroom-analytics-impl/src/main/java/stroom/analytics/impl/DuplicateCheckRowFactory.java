package stroom.analytics.impl;

import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateNotificationConfig;
import stroom.query.api.Row;
import stroom.query.common.v2.CompiledColumn;
import stroom.query.common.v2.CompiledColumns;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

class DuplicateCheckRowFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckRowSerde.class);

    private final Function<Row, List<String>> function;
    private final List<Integer> selectedIndexes;
    private final List<String> columnNames;

    public DuplicateCheckRowFactory(final DuplicateNotificationConfig duplicateNotificationConfig,
                                    final CompiledColumns compiledColumns) {
        final List<String> allColumnNames = new ArrayList<>();
        final List<String> selectedColumnNames = new ArrayList<>();

        selectedIndexes = new ArrayList<>(compiledColumns.getCompiledColumns().length);
        boolean useSelectedIndexes = false;
        for (int i = 0; i < compiledColumns.getCompiledColumns().length; i++) {
            final CompiledColumn compiledColumn = compiledColumns.getCompiledColumns()[i];
            allColumnNames.add(compiledColumn.getColumn().getName());

            // If we are told to choose columns then add chosen columns.
            if (duplicateNotificationConfig.isChooseColumns()) {
                if (duplicateNotificationConfig.getColumnNames() != null &&
                        duplicateNotificationConfig.getColumnNames().contains(compiledColumn.getColumn().getName())) {
                    selectedColumnNames.add(compiledColumn.getColumn().getName());
                    selectedIndexes.add(i);
                }
                useSelectedIndexes = true;
            } else if (compiledColumn.getGroupDepth() >= 0) {
                // Treat grouped columns as selected columns if the user has not chosen specific columns.
                selectedColumnNames.add(compiledColumn.getColumn().getName());
                selectedIndexes.add(i);
                useSelectedIndexes = true;
            }
        }

        if (useSelectedIndexes) {
            this.columnNames = selectedColumnNames;
            function = row -> {
                final List<String> values = new ArrayList<>(row.getValues().size());
                for (final Integer index : selectedIndexes) {
                    final String value = row.getValues().get(index);
                    if (value != null) {
                        LOGGER.trace(() -> "Adding selected string (" + index + ") = " + value);
                        values.add(value);
                    } else {
                        values.add("");
                    }
                }
                LOGGER.trace(() -> "Selected row values = " + String.join(", ", values));
                return values;
            };
        } else {
            this.columnNames = allColumnNames;
            function = row -> {
                final List<String> values = new ArrayList<>(row.getValues().size());
                for (final String value : row.getValues()) {
                    if (value != null) {
                        LOGGER.trace(() -> "Adding string = " + value);
                        values.add(value);
                    } else {
                        values.add("");
                    }
                }
                LOGGER.trace(() -> "Row values = " + String.join(", ", values));
                return values;
            };
        }
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public DuplicateCheckRow createDuplicateCheckRow(final Row row) {
        final List<String> values = function.apply(row);
        return new DuplicateCheckRow(values);
    }
}
