/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.grid.client;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.NullSafe;

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

public class ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL extends Cell<T_CELL_VAL>> {

    private final Function<T_ROW, T_CELL_VAL> valueExtractor;
    private final Supplier<T_CELL> cellSupplier;
    private boolean isSorted = false;
    private BooleanSupplier isSortableSupplier = () -> false;
    private boolean isDefaultSortAscending = true;
    private HorizontalAlignmentConstant horizontalAlignment = null;
    private VerticalAlignmentConstant verticalAlignment = null;
    private String fieldName;
    private boolean isIgnoreCaseOrdering = false;
    private List<String> styleNames = null;
    private List<Function<T_ROW, String>> styleFunctions = null;
    private FieldUpdater<T_ROW, T_CELL_VAL> fieldUpdater = null;
    private BrowserEventHandler<T_ROW> browserEventHandler = null;

//    public ColumnBuilder() {
//    }

    public ColumnBuilder(final Function<T_ROW, T_CELL_VAL> valueExtractor,
                         final Supplier<T_CELL> cellSupplier) {
        this.valueExtractor = valueExtractor;
        this.cellSupplier = cellSupplier;
    }

    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withSorting(final String fieldName) {
        this.isSorted = true;
        this.isSortableSupplier = () -> true;
        this.fieldName = Objects.requireNonNull(fieldName);
        return this;
    }

    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withSorting(final HasDisplayValue field) {
        this.isSorted = true;
        this.isSortableSupplier = () -> true;
        this.fieldName = Objects.requireNonNull(field).getDisplayValue();
        return this;
    }

    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withSorting(
            final String fieldName,
            final boolean isIgnoreCase) {
        this.isSorted = true;
        this.isIgnoreCaseOrdering = isIgnoreCase;
        this.isSortableSupplier = () -> true;
        this.fieldName = Objects.requireNonNull(fieldName);
        return this;
    }

    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withSorting(
            final String fieldName,
            final boolean isIgnoreCase,
            final boolean isSorted) {
        this.isSorted = isSorted;
        this.isIgnoreCaseOrdering = isIgnoreCase;
        this.isSortableSupplier = () -> true;
        this.fieldName = Objects.requireNonNull(fieldName);
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

    /**
     * Add a style that will be applied to all rows in this column
     */
    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withStyleName(final String styleName) {
        if (styleNames == null) {
            styleNames = new ArrayList<>();
        }
        styleNames.add(Objects.requireNonNull(styleName));
        return this;
    }

    /**
     * Add a style (or space separated styles) to rows in this column conditionally based on the row value
     *
     * @param styleFunction A function to return the style name, an empty string or null
     */
    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withConditionalStyleName(
            final Function<T_ROW, String> styleFunction) {
        if (styleFunctions == null) {
            styleFunctions = new ArrayList<>();
        }
        styleFunctions.add(Objects.requireNonNull(styleFunction));
        return this;
    }

    /**
     * Add a class to the cell if it is not enabled, so enabled and disabled rows appear
     * differently.
     *
     * @param enabledFunction A function to return true if the row is enabled.
     */
    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> enabledWhen(final Function<T_ROW, Boolean> enabledFunction) {
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
        return this;
    }

    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withFieldUpdater(
            final FieldUpdater<T_ROW, T_CELL_VAL> fieldUpdater) {
        this.fieldUpdater = fieldUpdater;
        return this;
    }

    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> withBrowserEventHandler(
            final BrowserEventHandler<T_ROW> browserEventHandler) {
        this.browserEventHandler = browserEventHandler;
        return this;
    }

    public ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL> defaultSortAscending(final boolean defaultSortAscending) {
        isDefaultSortAscending = defaultSortAscending;
        return this;
    }

    private String buildCellStyles(final String baseStyleNames,
                                   final T_ROW object) {

        final String styleNames = String.join(" ", NullSafe.list(this.styleNames));
        final String functionStyleNames = NullSafe.stream(styleFunctions)
                .filter(Objects::nonNull)
                .map(func -> func.apply(object))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        return String.join(" ",
                NullSafe.string(baseStyleNames),
                styleNames,
                functionStyleNames);
    }

    private T_CELL_VAL extractFormattedValue(final T_ROW row) {
        return Optional.ofNullable(row)
                .map(valueExtractor)
                .orElse(null);
    }

    public Column<T_ROW, T_CELL_VAL> build() {
        Objects.requireNonNull(valueExtractor);
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

        column.setDefaultSortAscending(isDefaultSortAscending);

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
