package stroom.query.common.v2;

import stroom.query.api.v2.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

public class ColumnMap {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ColumnMap.class);

    // Map new columns to original columns as close as we can.
    private final Map<Column, Column> newColumnToOriginalColumnMap;

    // Create inverse map.
    private final Map<Column, Column> originalColumnToNewColumnMap;

    public ColumnMap(final List<Column> originalColumns,
                     final List<Column> newColumns) {
        newColumnToOriginalColumnMap = new HashMap<>();
        List<Column> remaining = newColumns;

        // Try equality.
        if (!remaining.isEmpty()) {
            final List<Column> list = remaining;
            remaining = new ArrayList<>();
            for (final Column newColumn : list) {
                final Column originalColumn = findExactEquals(originalColumns, newColumn);
                if (originalColumn != null) {
                    newColumnToOriginalColumnMap.putIfAbsent(newColumn, originalColumn);
                } else {
                    remaining.add(newColumn);
                }
            }
        }
        // Try id.
        if (!remaining.isEmpty()) {
            final List<Column> list = remaining;
            remaining = new ArrayList<>();
            for (final Column newColumn : list) {
                final Column originalColumn = findIdEquals(originalColumns, newColumn);
                if (originalColumn != null) {
                    newColumnToOriginalColumnMap.putIfAbsent(newColumn, originalColumn);
                } else {
                    remaining.add(newColumn);
                }
            }
        }
        // Try expression.
        if (!remaining.isEmpty()) {
            final List<Column> list = remaining;
            remaining = new ArrayList<>();
            for (final Column newColumn : list) {
                final Column originalColumn = findExpressionEquals(originalColumns, newColumn);
                if (originalColumn != null) {
                    newColumnToOriginalColumnMap.putIfAbsent(newColumn, originalColumn);
                } else {
                    remaining.add(newColumn);
                }
            }
        }

        // Validate mappings.
        // Check that the request doesn't contain any new structural data columns that are not present in the stored
        // data.
        for (final Column column : newColumns) {
            final Column originalColumn = newColumnToOriginalColumnMap.get(column);
            LOGGER.debug(() -> "Column mapping " + column + " -> " + originalColumn);

            // See if this is an important column for data structure.
            if (isStructuralDataColumn(column)) {
                // Important column for data structure.
                // Check it exists in the requested column set.
                if (originalColumn == null) {
                    throw new RuntimeException("Structural column missing from original data: " + column);
                }
            }
        }

        originalColumnToNewColumnMap = newColumnToOriginalColumnMap
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getValue, Entry::getKey));

        // Check that the request contains all of the same columns that affect the data structure of the stored data.
        for (final Column originalColumn : originalColumns) {
            // See if this is an important column for data structure.
            if (isStructuralDataColumn(originalColumn)) {
                // Important column for data structure.
                // Check it exists in the requested column set.
                final Column newColumn = originalColumnToNewColumnMap.get(originalColumn);
                if (newColumn == null) {
                    throw new RuntimeException("Structural column missing from request: " + originalColumn);
                }
            }
        }
    }

    private Column findExactEquals(final List<Column> originalColumns,
                                   final Column newColumn) {
        for (final Column originalColumn : originalColumns) {
            if (Objects.equals(newColumn, originalColumn)) {
                return originalColumn;
            }
        }
        return null;
    }

    private Column findIdEquals(final List<Column> originalColumns,
                                final Column newColumn) {
        for (final Column originalColumn : originalColumns) {
            if (Objects.equals(newColumn.getId(), originalColumn.getId()) &&
                    Objects.equals(newColumn.getExpression(), originalColumn.getExpression()) &&
                    Objects.equals(newColumn.getFilter(), originalColumn.getFilter()) &&
                    Objects.equals(newColumn.getGroup(), originalColumn.getGroup())) {
                return originalColumn;
            }
        }
        return null;
    }

    private Column findExpressionEquals(final List<Column> originalColumns,
                                        final Column newColumn) {
        for (final Column originalColumn : originalColumns) {
            if (Objects.equals(newColumn.getExpression(), originalColumn.getExpression()) &&
                    Objects.equals(newColumn.getFilter(), originalColumn.getFilter()) &&
                    Objects.equals(newColumn.getGroup(), originalColumn.getGroup())) {
                return originalColumn;
            }
        }
        return null;
    }

    /**
     * A column affects the structure of the stored data by either filtering the incoming data with include/exclude
     * rules or by grouping the data.
     *
     * @param column The column to test to see if it is structural.
     * @return True if the column is structural, false otherwise.
     */
    private boolean isStructuralDataColumn(final Column column) {
        return hasFilter(column) || (column.getGroup() != null && column.getGroup() >= 0);
    }

    /**
     * Test to see if a column has a pre-storage value filter
     *
     * @param column The column to test.
     * @return True if a filter exists, false otherwise.
     */
    private boolean hasFilter(final Column column) {
        return column.getFilter() != null && (
                (column.getFilter().getIncludes() != null &&
                        !column.getFilter().getIncludes().isBlank()) ||
                        (column.getFilter().getExcludes() != null &&
                                !column.getFilter().getExcludes().isBlank()));
    }

    public Column getOrignalColumnFromNewColumn(final Column newColumn) {
        return newColumnToOriginalColumnMap.get(newColumn);
    }

    public Column getNewColumnFromOriginalColumn(final Column originalColumn) {
        return originalColumnToNewColumnMap.get(originalColumn);
    }
}
