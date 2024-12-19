package stroom.data.grid.client;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasHorizontalAlignment.HorizontalAlignmentConstant;
import com.google.gwt.user.client.ui.HasVerticalAlignment.VerticalAlignmentConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractColumnBuilder<
        T_ROW,
        T_RAW_VAL,
        T_CELL_VAL,
        T_CELL extends Cell<T_CELL_VAL>,
        B extends AbstractColumnBuilder<T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL, ?>> {

    private Function<T_ROW, T_RAW_VAL> valueExtractor;
    private Function<T_RAW_VAL, T_CELL_VAL> formatter;
    private Supplier<T_CELL> cellSupplier;
    private boolean isSorted = false;
    private BooleanSupplier isSortableSupplier = () -> false;
    private HorizontalAlignmentConstant horizontalAlignment = null;
    private VerticalAlignmentConstant verticalAlignment = null;
    private String fieldName;
    private boolean isIgnoreCaseOrdering = false;
    private List<String> styleNames = null;
    private List<Function<T_ROW, String>> styleFunctions = null;
    private FieldUpdater<T_ROW, T_CELL_VAL> fieldUpdater = null;
    private BrowserEventHandler<T_ROW> browserEventHandler = null;

    public AbstractColumnBuilder() {
    }

    public B valueExtractor(final Function<T_ROW, T_RAW_VAL> valueExtractor) {
        this.valueExtractor = valueExtractor;
        return self();
    }

    public B formatter(final Function<T_RAW_VAL, T_CELL_VAL> formatter) {
        this.formatter = formatter;
        return self();
    }

    public B cellSupplier(final Supplier<T_CELL> cellSupplier) {
        this.cellSupplier = cellSupplier;
        return self();
    }

    public B withSorting(final String fieldName) {
        this.isSorted = true;
        this.isSortableSupplier = () -> true;
        this.fieldName = Objects.requireNonNull(fieldName);
        return self();
    }

    public B withSorting(final HasDisplayValue field) {
        this.isSorted = true;
        this.isSortableSupplier = () -> true;
        this.fieldName = Objects.requireNonNull(field).getDisplayValue();
        return self();
    }

    public B withSorting(
            final String fieldName,
            final boolean isIgnoreCase) {
        this.isSorted = true;
        this.isIgnoreCaseOrdering = isIgnoreCase;
        this.isSortableSupplier = () -> true;
        this.fieldName = Objects.requireNonNull(fieldName);
        return self();
    }

    public B withSorting(
            final String fieldName,
            final boolean isIgnoreCase,
            final boolean isSorted) {
        this.isSorted = isSorted;
        this.isIgnoreCaseOrdering = isIgnoreCase;
        this.isSortableSupplier = () -> true;
        this.fieldName = Objects.requireNonNull(fieldName);
        return self();
    }

    public B rightAligned() {
        horizontalAlignment = HasHorizontalAlignment.ALIGN_RIGHT;
        return self();
    }

    public B centerAligned() {
        horizontalAlignment = HasHorizontalAlignment.ALIGN_CENTER;
        return self();
    }

    /**
     * Add a style that will be applied to all rows in this column
     */
    public B withStyleName(final String styleName) {
        if (styleNames == null) {
            styleNames = new ArrayList<>();
        }
        styleNames.add(Objects.requireNonNull(styleName));
        return self();
    }

    /**
     * Add a style (or space separated styles) to rows in this column conditionally based on the row value
     *
     * @param styleFunction A function to return the style name, an empty string or null
     */
    public B withConditionalStyleName(
            final Function<T_ROW, String> styleFunction) {
        if (styleFunctions == null) {
            styleFunctions = new ArrayList<>();
        }
        styleFunctions.add(Objects.requireNonNull(styleFunction));
        return self();
    }

    /**
     * Add a class to the cell if it is not enabled, so enabled and disabled rows appear
     * differently.
     *
     * @param enabledFunction A function to return true if the row is enabled.
     */
    public B enabledWhen(
            final Function<T_ROW, Boolean> enabledFunction) {
        if (styleFunctions == null) {
            styleFunctions = new ArrayList<>();
        }
        if (enabledFunction != null) {
            final Function<T_ROW, String> sytleFunction = row ->
                    enabledFunction.apply(row)
                            ? ""
                            : "dataGridDisabledCell";
            styleFunctions.add(sytleFunction);
        }
        return self();
    }

    public B withFieldUpdater(
            final FieldUpdater<T_ROW, T_CELL_VAL> fieldUpdater) {
        this.fieldUpdater = fieldUpdater;
        return self();
    }

    public B withBrowserEventHandler(
            final BrowserEventHandler<T_ROW> browserEventHandler) {
        this.browserEventHandler = browserEventHandler;
        return self();
    }

    private String buildCellStyles(final String baseStyleNames,
                                   final T_ROW object) {

        final String styleNames = String.join(" ", GwtNullSafe.list(this.styleNames));
        final String functionStyleNames = GwtNullSafe.stream(styleFunctions)
                .filter(Objects::nonNull)
                .map(func -> func.apply(object))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        return String.join(" ",
                GwtNullSafe.string(baseStyleNames),
                styleNames,
                functionStyleNames);
    }

    private T_CELL_VAL extractFormattedValue(final T_ROW row) {
        return Optional.ofNullable(row)
                .map(valueExtractor)
                .map(formatter)
                .orElse(null);
    }

    abstract B self();

    public Column<T_ROW, T_CELL_VAL> build() {
        Objects.requireNonNull(valueExtractor);
        Objects.requireNonNull(formatter);
        Objects.requireNonNull(cellSupplier);

        final Column<T_ROW, T_CELL_VAL> column;
        if (isSorted) {
            // Explicit generics typing for GWT
            column = createSortableColumn();
        } else {
            // Explicit generics typing for GWT
            column = createNonSortableColumn();
        }
        if (horizontalAlignment != null) {
            column.setHorizontalAlignment(horizontalAlignment);
        }

        if (verticalAlignment != null) {
            column.setVerticalAlignment(verticalAlignment);
        }

        if (fieldUpdater != null) {
            column.setFieldUpdater(fieldUpdater);
        }

        return column;
    }

    private Column<T_ROW, T_CELL_VAL> createNonSortableColumn() {
        return new Column<T_ROW, T_CELL_VAL>(cellSupplier.get()) {
            @Override
            public T_CELL_VAL getValue(final T_ROW row) {
                return extractFormattedValue(row);
            }

            @Override
            public String getCellStyleNames(final Context context, final T_ROW object) {
                return buildCellStyles(super.getCellStyleNames(context, object), object);
            }

            @Override
            public void onBrowserEvent(final Context context,
                                       final Element elem,
                                       final T_ROW object,
                                       final NativeEvent event) {
                super.onBrowserEvent(context, elem, object, event);
                if (browserEventHandler != null) {
                    browserEventHandler.handle(context, elem, object, event);
                }
            }
        };
    }

    private Column<T_ROW, T_CELL_VAL> createSortableColumn() {
        return new OrderByColumn<T_ROW, T_CELL_VAL>(
                cellSupplier.get(),
                fieldName,
                isIgnoreCaseOrdering) {

            @Override
            public T_CELL_VAL getValue(final T_ROW row) {
                return extractFormattedValue(row);
            }

            @Override
            public boolean isSortable() {
                return isSortableSupplier.getAsBoolean();
            }

            @Override
            public String getCellStyleNames(final Context context, final T_ROW object) {
                return buildCellStyles(super.getCellStyleNames(context, object), object);
            }

            @Override
            public void onBrowserEvent(final Context context,
                                       final Element elem,
                                       final T_ROW object,
                                       final NativeEvent event) {
                super.onBrowserEvent(context, elem, object, event);
                if (browserEventHandler != null) {
                    browserEventHandler.handle(context, elem, object, event);
                }
            }
        };
    }

    public interface BrowserEventHandler<T_ROW> {

        void handle(final Context context,
                    final Element elem,
                    final T_ROW row,
                    final NativeEvent event);
    }
}
