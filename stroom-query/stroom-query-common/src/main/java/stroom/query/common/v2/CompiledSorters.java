package stroom.query.common.v2;

import stroom.query.api.v2.Column;
import stroom.query.api.v2.Sort;
import stroom.query.language.functions.Val;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompiledSorters<E extends Item> {

    private final int maxDepth;
    private final CompiledColumn[] compiledColumns;
    private CompiledSorter<Item>[] compiledSorters;
    private final boolean hasSort;
    private final boolean limitResultCount;

    public CompiledSorters(final CompiledDepths compiledDepths,
                           final CompiledColumn[] compiledColumns) {
        this.maxDepth = compiledDepths.getMaxDepth();
        final CompiledSorter<Item>[] sorters = new CompiledSorter[maxDepth + 1];
        boolean hasSort = false;

        for (int depth = 0; depth <= maxDepth; depth++) {
            for (int columnIndex = 0; columnIndex < compiledColumns.length; columnIndex++) {
                final CompiledColumn compiledColumn = compiledColumns[columnIndex];
                final Column column = compiledColumn.getColumn();
                if (column.getSort() != null && (column.getGroup() == null || column.getGroup() >= depth)) {
                    // Get an appropriate comparator.
                    final Comparator<Val> comparator = ComparatorFactory.create(column);

                    // Remember sorting info.
                    final Sort sort = column.getSort();
                    final CompiledSort compiledSort = new CompiledSort(columnIndex, sort, comparator);

                    CompiledSorter<Item> sorter = sorters[depth];
                    if (sorter == null) {
                        sorter = new CompiledSorter<>();
                        sorters[depth] = sorter;
                    }

                    sorter.add(compiledSort);
                    hasSort = true;
                }
            }
        }

        this.compiledColumns = compiledColumns;
        this.compiledSorters = sorters;
        this.hasSort = hasSort;
        limitResultCount = compiledDepths.getMaxDepth() < Sizes.MAX_SIZE &&
                !hasSort &&
                !compiledDepths.hasGroup();
    }

    public boolean hasSort() {
        return hasSort;
    }

    public CompiledSorter<Item> get(final int depth) {
        if (depth >= compiledSorters.length) {
            return null;
        }
        return compiledSorters[depth];
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
                        column.getFilter().getIncludes().trim().length() > 0) ||
                        (column.getFilter().getExcludes() != null &&
                                column.getFilter().getExcludes().trim().length() > 0));
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

    public void update(final List<Column> newColumns) {
        final CompiledSorter<Item>[] sorters = new CompiledSorter[maxDepth + 1];

        final Map<ColumnKey, Column> originalColumnMap = Arrays.stream(compiledColumns)
                .map(CompiledColumn::getColumn)
                .collect(Collectors.toMap(ColumnKey::create, Function.identity(), (e1, e2) -> e1));
        final Map<ColumnKey, Column> newColumnMap = newColumns
                .stream()
                .collect(Collectors.toMap(ColumnKey::create, Function.identity(), (e1, e2) -> e1));

        // Check that the request contains all of the same columns that affect the data structure of the stored data.
        for (final CompiledColumn compiledColumn : compiledColumns) {
            final Column column = compiledColumn.getColumn();
            // See if this is an important column for data structure.
            if (isStructuralDataColumn(column)) {
                // Important column for data structure.
                // Check it exists in the requested column set.
                if (!newColumnMap.containsKey(ColumnKey.create(column))) {
                    throw new RuntimeException("Structural column missing from request: " + column);
                }
            }
        }

        // Check that the request doesn't contain any new structural data columns that are not present in the stored
        // data.
        for (final Column column : newColumns) {
            // See if this is an important column for data structure.
            if (isStructuralDataColumn(column)) {
                // Important column for data structure.
                // Check it exists in the requested column set.
                if (!originalColumnMap.containsKey(ColumnKey.create(column))) {
                    throw new RuntimeException("Structural column missing from original data: " + column);
                }
            }
        }

        for (int depth = 0; depth <= maxDepth; depth++) {
            for (int columnIndex = 0; columnIndex < compiledColumns.length; columnIndex++) {
                final CompiledColumn compiledColumn = compiledColumns[columnIndex];
                final Column column = compiledColumn.getColumn();
                if (column.getExpression() != null) {
                    final Column newColumn = newColumnMap.get(ColumnKey.create(column));
                    if (newColumn != null) {
                        if (newColumn.getSort() != null &&
                                (newColumn.getGroup() == null || newColumn.getGroup() >= depth)) {
                            if (limitResultCount) {
                                throw new RuntimeException("Attempt to add sort to page limited results");
                            }

                            // Get an appropriate comparator.
                            final Comparator<Val> comparator = ComparatorFactory.create(newColumn);

                            // Remember sorting info.
                            final Sort sort = newColumn.getSort();
                            final CompiledSort compiledSort = new CompiledSort(columnIndex, sort, comparator);

                            CompiledSorter<Item> sorter = sorters[depth];
                            if (sorter == null) {
                                sorter = new CompiledSorter<>();
                                sorters[depth] = sorter;
                            }

                            sorter.add(compiledSort);
                        }
                    }
                }
            }
        }

        compiledSorters = sorters;
    }

    public int size() {
        return compiledSorters.length;
    }
}
