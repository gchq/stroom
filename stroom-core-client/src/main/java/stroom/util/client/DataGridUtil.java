package stroom.util.client;

import stroom.cell.expander.client.ExpanderCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.EndColumn;
import stroom.util.shared.Expander;
import stroom.util.shared.TreeAction;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;

import java.util.Objects;
import java.util.function.Function;

public class DataGridUtil {

    private DataGridUtil() {
    }

    public static <T_ROW> Column<T_ROW, String> buildTextColumn(
            final Function<T_ROW, String> cellValueExtractor) {
        return new Column<T_ROW, String>(new TextCell()) {
            @Override
            public String getValue(final T_ROW row) {
                if (row == null) {
                    return null;
                }
                return cellValueExtractor.apply(row);
            }
        };
    }

    public static <T_ROW, T_VAL extends Number> Column<T_ROW, String> buildNumericTextColumn(
            final Function<T_ROW, T_VAL> cellValueExtractor) {
        return new Column<T_ROW, String>(new TextCell()) {
            @Override
            public String getValue(final T_ROW row) {
                if (row == null) {
                    return null;
                }
                T_VAL value = cellValueExtractor.apply(row);
                return value != null
                        ? value.toString()
                        : null;
            }
        };
    }

    public static <T_ROW> Column<T_ROW, SafeHtml> buildSafeHtmlColumn(
            final Function<T_ROW, SafeHtml> cellValueExtractor) {
        return new Column<T_ROW, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final T_ROW row) {
                if (row == null) {
                    return null;
                }
                return cellValueExtractor.apply(row);
            }
        };
    }

    public static <T_ROW> Column<T_ROW, Expander> buildExpanderColumn(
            final Function<T_ROW, Expander> expanderExtractor,
            final TreeAction<T_ROW> treeAction,
            final Runnable expanderChangeAction) {

        Objects.requireNonNull(expanderExtractor);
        Objects.requireNonNull(treeAction);

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

    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addResizableTextColumn(
            final T_VIEW view,
            final Function<T_ROW, String> cellValueExtractor,
            final String name,
            final int width) {
        view.addResizableColumn(buildTextColumn(cellValueExtractor), name, width);
    }

    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addResizableNumericTextColumn(
            final T_VIEW view,
            final Function<T_ROW, Number> cellValueExtractor,
            final String name,
            final int width) {
        view.addResizableColumn(buildNumericTextColumn(cellValueExtractor), name, width);
    }

    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addResizableSafeHtmlColumn(
            final T_VIEW view,
            final Function<T_ROW, SafeHtml> cellValueExtractor,
            final String name,
            final int width) {
        view.addResizableColumn(buildSafeHtmlColumn(cellValueExtractor), name, width);
    }

    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addExpanderColumn(
            final T_VIEW view,
            final Function<T_ROW, Expander> expanderExtractor,
            final TreeAction<T_ROW> treeAction,
            final Runnable onExpanderChange) {
        view.addColumn(
                buildExpanderColumn(expanderExtractor, treeAction, onExpanderChange),
                "",
                ColumnSizeConstants.CHECKBOX_COL * 2);
    }

    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addResizableColumn(
            final T_VIEW view,
            final Column<T_ROW, ?> column,
            final String name,
            final int width) {
        view.addResizableColumn(column, name, width);
    }

    public static <T_VIEW extends DataGridView<T_ROW>, T_ROW> void addEndColumn(
            final T_VIEW view) {
        view.addEndColumn(new EndColumn<>());
    }
}
