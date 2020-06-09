package stroom.util.client;

import stroom.cell.expander.client.ExpanderCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.OrderByColumn;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.Expander;
import stroom.util.shared.Sort;
import stroom.util.shared.TreeAction;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasHorizontalAlignment.HorizontalAlignmentConstant;
import com.google.gwt.user.client.ui.HasVerticalAlignment.VerticalAlignmentConstant;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class DataGridUtil {

    private DataGridUtil() {
    }

    public static <T_ROW, T_CELL> Column<T_ROW, T_CELL> column(
            final Function<T_ROW, T_CELL> cellValueExtractor,
            final Supplier<Cell<T_CELL>> cellProvider) {

        Objects.requireNonNull(cellValueExtractor);
        Objects.requireNonNull(cellProvider);

        // Explicit generics typing for GWT
        return new Column<T_ROW, T_CELL>(cellProvider.get()) {
            @Override
            public T_CELL getValue(final T_ROW row) {
                if (row == null) {
                    return null;
                }
                return cellValueExtractor.apply(row);
            }
        };
    }

    public static <T_ROW> Column<T_ROW, SafeHtml> safeHtmlColumn(
            final Function<T_ROW, SafeHtml> cellValueExtractor) {
        return column(cellValueExtractor, SafeHtmlCell::new);
    }

    public static <T_ROW> Column<T_ROW, Expander> expanderColumn(
            final Function<T_ROW, Expander> expanderExtractor,
            final TreeAction<T_ROW> treeAction,
            final Runnable expanderChangeAction) {

        Objects.requireNonNull(expanderExtractor);
        Objects.requireNonNull(treeAction);

        // Explicit generics typing for GWT
        final Column<T_ROW, Expander> expanderColumn = new Column<T_ROW, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final T_ROW row) {
                if (row == null) {
                    return null;
                }
                return expanderExtractor.apply(row);
            }
        };

        expanderColumn.setFieldUpdater((index, row, value) -> {
            treeAction.setRowExpanded(row, !value.isExpanded());
            if (expanderChangeAction != null) {
                expanderChangeAction.run();
            }
        });
        return expanderColumn;
    }

    public static <T_ROW> Column<T_ROW, String> endColumn() {
        Column<T_ROW, String> column = new EndColumn<T_ROW>();
        return column;
    }

//    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addResizableTextColumn(
//            final T_VIEW view,
//            final Function<T_ROW, String> cellValueExtractor,
//            final String name,
//            final int width) {
//        view.addResizableColumn(buildColumn(cellValueExtractor, TextCell::new), name, width);
//    }
//
//    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addResizableNumericTextColumn(
//            final T_VIEW view,
//            final Function<T_ROW, Number> cellValueExtractor,
//            final String name,
//            final int width) {
//        view.addResizableColumn(numericTextColumn(cellValueExtractor), name, width);
//    }
//
//    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addResizableSafeHtmlColumn(
//            final T_VIEW view,
//            final Function<T_ROW, SafeHtml> cellValueExtractor,
//            final String name,
//            final int width) {
//        view.addResizableColumn(safeHtmlColumn(cellValueExtractor), name, width);
//    }

    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addExpanderColumn(
            final T_VIEW view,
            final Function<T_ROW, Expander> expanderExtractor,
            final TreeAction<T_ROW> treeAction,
            final Runnable onExpanderChange) {
        view.addColumn(
                expanderColumn(expanderExtractor, treeAction, onExpanderChange),
                "",
                ColumnSizeConstants.CHECKBOX_COL * 2);
    }

//    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addResizableColumn(
//            final T_VIEW view,
//            final Column<T_ROW, ?> column,
//            final String name,
//            final int width) {
//        view.addResizableColumn(column, name, width);
//    }

    public static void addEndColumn(final DataGridView<?> view) {
        view.addEndColumn(new EndColumn<>());
    }

    public static void addColumnSortHandler(final DataGridView<?> view,
                                            final BaseCriteria criteria,
                                            final Runnable onSortChange) {

            view.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                if (event.isSortAscending()) {
                    criteria.setSort(
                            orderByColumn.getField(),
                            Sort.Direction.ASCENDING,
                            orderByColumn.isIgnoreCase());
                } else {
                    criteria.setSort(
                            orderByColumn.getField(),
                            Sort.Direction.DESCENDING,
                            orderByColumn.isIgnoreCase());
                }
                onSortChange.run();
            }
        });
    }

//    public static <T_ROW> Builder<T_ROW> builder(final DataGridView<T_ROW> view) {
//        return new Builder<>(view);
//    }
//
//    public static class Builder<T_ROW> {
//
//        private final DataGridView<T_ROW> view;
//
//        public Builder(final DataGridView<T_ROW> view) {
//            this.view = view;
//        }
//
//        public Builder<T_ROW> addResizableColumn(
//                final Column<T_ROW, ?> column,
//                final String name,
//                final int width) {
//            view.addResizableColumn(column, name, width);
//            return this;
//        }
//
//        public Builder<T_ROW> addColumn(
//                final Column<T_ROW, ?> column,
//                final String name,
//                final int width) {
//            view.addColumn(column, name, width);
//            return this;
//        }
//
//        public Builder<T_ROW> addEndColumn() {
//            view.addEndColumn(new EndColumn<>());
//            return this;
//        }
//
//
//    }

    public static <T_ROW, T_CELL_VAL, T_CELL extends Cell<T_CELL_VAL>> ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> columnBuilder(
            final Function<T_ROW, T_CELL_VAL> cellExtractor,
            final Supplier<T_CELL> cellSupplier) {

        return new ColumnBuilder<>(cellExtractor, cellSupplier);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, String, Cell<String>> textColumnBuilder(
            final Function<T_ROW, String> cellExtractor) {

        return new ColumnBuilder<>(cellExtractor, TextCell::new);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, SafeHtml, Cell<SafeHtml>> htmlColumnBuilder(
            final Function<T_ROW, SafeHtml> cellExtractor) {

        return new ColumnBuilder<>(cellExtractor, SafeHtmlCell::new);
    }

    public static class ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL extends Cell<T_CELL_VAL>> {
        private final Function<T_ROW, T_CELL_VAL> cellExtractor;
        private final Supplier<T_CELL> cellSupplier;
        private boolean isSorted = false;
        private HorizontalAlignmentConstant horizontalAlignment = null;
        private VerticalAlignmentConstant verticalAlignment = null;
        private String fieldName;
        private boolean isIgnoreCaseOrdering = false;

        private ColumnBuilder(final Function<T_ROW, T_CELL_VAL> cellExtractor,
                             final Supplier<T_CELL> cellSupplier) {
            this.cellExtractor = cellExtractor;
            this.cellSupplier = cellSupplier;
        }

        public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withSorting(final String fieldName) {
            this.isSorted = true;
            this.fieldName = fieldName;
            return this;
        }

        public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withSorting(
                final String fieldName,
                final boolean isIgnoreCase) {
            this.isSorted = true;
            this.isIgnoreCaseOrdering = isIgnoreCase;
            this.fieldName = fieldName;
            return this;
        }

        public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> rightAligned() {
            horizontalAlignment = HasHorizontalAlignment.ALIGN_RIGHT;
            return this;
        }

        public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> centerAligned() {
            horizontalAlignment = HasHorizontalAlignment.ALIGN_CENTER;
            return this;
        }

        public Column<T_ROW, T_CELL_VAL> build() {

            final Column<T_ROW, T_CELL_VAL> column;
            if (isSorted) {
                // Explicit generics typing for GWT
                column = new OrderByColumn<T_ROW, T_CELL_VAL>(cellSupplier.get(), fieldName, isIgnoreCaseOrdering) {
                    @Override
                    public T_CELL_VAL getValue(final T_ROW row) {
                        if (row == null) {
                            return null;
                        }
                        return cellExtractor.apply(row);
                    }
                };
            } else {
                // Explicit generics typing for GWT
                column = new Column<T_ROW, T_CELL_VAL>(cellSupplier.get()) {
                    @Override
                    public T_CELL_VAL getValue(final T_ROW row) {
                        if (row == null) {
                            return null;
                        }
                        return cellExtractor.apply(row);
                    }
                };
            }
            if (horizontalAlignment != null) {
                column.setHorizontalAlignment(horizontalAlignment);
            }

            if (verticalAlignment != null) {
                column.setVerticalAlignment(verticalAlignment);
            }

            return column;
        }
    }
}
