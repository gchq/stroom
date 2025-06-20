package stroom.util.client;

import stroom.data.grid.client.OrderByColumn;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.cellview.client.DataGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DataGridComparatorFactory<T> {

    private final DataGrid<T> dataGrid;
    private final Map<String, Function<T, ? extends Comparable<?>>> fieldNameToExtractorMap;
    private final List<String> defaultSortFields;

    private DataGridComparatorFactory(
            final DataGrid<T> dataGrid,
            final Map<String, Function<T, ? extends Comparable<?>>> fieldNameToExtractorMap,
            final List<String> defaultSortFields) {
        this.dataGrid = dataGrid;
        this.fieldNameToExtractorMap = fieldNameToExtractorMap;
        this.defaultSortFields = defaultSortFields;
    }

    public <U extends Comparable<U>> Comparator<T> create() {
        final ColumnSortList columnSortList = dataGrid.getColumnSortList();
        Comparator<T> combinedComparator = null;
        if (columnSortList.size() > 0) {
            for (int i = 0; i < columnSortList.size(); i++) {
                final ColumnSortInfo columnSortInfo = columnSortList.get(i);
                final Column<?, ?> column = columnSortInfo.getColumn();
                if (column instanceof final OrderByColumn<?, ?> orderByColumn) {
                    final String sortField = orderByColumn.getField();
                    final boolean isAscending = columnSortInfo.isAscending();
//                    GWT.log("sortField " + i + ": " + sortField + " isAscending: " + isAscending);
                    Comparator<T> comparator = null;
                    final Function<T, U> extractor = (Function<T, U>) fieldNameToExtractorMap.get(sortField);
                    if (extractor != null) {
                        comparator = Comparator.comparing(extractor);
                    }
                    comparator = CompareUtil.reverseIf(comparator, !isAscending);
                    combinedComparator = CompareUtil.combine(combinedComparator, comparator);
                }
            }
        } else {
            for (final String defaultSortField : defaultSortFields) {
                final Function<T, U> extractor = (Function<T, U>) fieldNameToExtractorMap.get(defaultSortField);
                Comparator<T> comparator = null;
                if (extractor != null) {
                    comparator = Comparator.comparing(extractor);
                }
                combinedComparator = CompareUtil.combine(combinedComparator, comparator);
            }
        }
        return NullSafe.requireNonNullElseGet(combinedComparator, CompareUtil::noOpComparator);
    }

    public static <T> Builder<T> builder(final DataGrid<T> dataGrid) {
        return new Builder<>(dataGrid);
    }


    // --------------------------------------------------------------------------------


    public static class Builder<T> {

        private final DataGrid<T> dataGrid;
        private final Map<String, Function<T, ? extends Comparable<?>>> fieldNameToExtractorMap = new HashMap<>();
        private final List<String> defaultSortFields = new ArrayList<>();

        public Builder(final DataGrid<T> dataGrid) {
            this.dataGrid = dataGrid;
        }

        public <U extends Comparable<? super U>> Builder<T> addField(final String fieldName,
                                                                     final Function<T, U> extractor) {

            if (NullSafe.isBlankString(fieldName)) {
                throw new RuntimeException("Blank field");
            }
            fieldNameToExtractorMap.put(fieldName, extractor);
            return this;
        }

        public Builder<T> addDefaultSortField(final String fieldName) {
            defaultSortFields.add(fieldName);
            return this;
        }

        public DataGridComparatorFactory<T> build() {
            return new DataGridComparatorFactory<>(dataGrid, fieldNameToExtractorMap, defaultSortFields);
        }
    }
}
