package stroom.query.common.v2;

import stroom.query.api.v2.Column;
import stroom.query.api.v2.Sort;
import stroom.query.language.functions.Val;

import java.util.Comparator;
import java.util.List;

public class CompiledSorters<E extends Item> {

    private final int maxDepth;
    private final List<Column> columns;
    private CompiledSorter<E>[] compiledSorters;
    private final boolean hasSort;
    private final boolean limitResultCount;

    public CompiledSorters(final CompiledDepths compiledDepths,
                           final List<Column> columns) {
        this.columns = columns;
        this.maxDepth = compiledDepths.getMaxDepth();
        @SuppressWarnings("unchecked")
        final CompiledSorter<E>[] sorters = new CompiledSorter[maxDepth + 1];
        boolean hasSort = false;

        for (int depth = 0; depth <= maxDepth; depth++) {
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                final Column column = columns.get(columnIndex);
                if (column.getSort() != null && (column.getGroup() == null || column.getGroup() >= depth)) {
                    // Get an appropriate comparator.
                    final Comparator<Val> comparator = ComparatorFactory.create(column);

                    // Remember sorting info.
                    final Sort sort = column.getSort();
                    final CompiledSort compiledSort = new CompiledSort(columnIndex, sort, comparator);

                    CompiledSorter<E> sorter = sorters[depth];
                    if (sorter == null) {
                        sorter = new CompiledSorter<>();
                        sorters[depth] = sorter;
                    }

                    sorter.add(compiledSort);
                    hasSort = true;
                }
            }
        }

        this.compiledSorters = sorters;
        this.hasSort = hasSort;
        limitResultCount = compiledDepths.getMaxDepth() < Sizes.MAX_SIZE &&
                !hasSort &&
                !compiledDepths.hasGroup();
    }

    public boolean hasSort() {
        return hasSort;
    }

    public CompiledSorter<E> get(final int depth) {
        if (depth >= compiledSorters.length) {
            return null;
        }
        return compiledSorters[depth];
    }

    public void update(final List<Column> newColumns) {
        // Map new columns to original columns as close as we can.
        final ColumnMap columnMap = new ColumnMap(columns, newColumns);
        @SuppressWarnings("unchecked")
        final CompiledSorter<E>[] sorters = new CompiledSorter[maxDepth + 1];
        for (int depth = 0; depth <= maxDepth; depth++) {
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                final Column originalColumn = columns.get(columnIndex);
                if (originalColumn.getExpression() != null) {
                    final Column newColumn = columnMap.getNewColumnFromOriginalColumn(originalColumn);
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

                            CompiledSorter<E> sorter = sorters[depth];
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
