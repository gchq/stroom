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

package stroom.util.client;

import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.info.client.ColourSwatchCell;
import stroom.cell.info.client.CommandLink;
import stroom.cell.info.client.CommandLinkCell;
import stroom.cell.info.client.PercentBarCell;
import stroom.cell.info.client.RedGreenTextCell;
import stroom.cell.info.client.SvgCell;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxCell.DefaultAppearance;
import stroom.cell.tickbox.client.TickBoxCell.NoBorderAppearance;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.client.ValueSpinnerCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CopyTextCell;
import stroom.data.client.presenter.DocRefCell;
import stroom.data.client.presenter.FeedRefCell;
import stroom.data.client.presenter.HasContextMenusCell;
import stroom.data.client.presenter.UserRefCell;
import stroom.data.grid.client.ColSpec;
import stroom.data.grid.client.ColumnBuilder;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.HasContextMenus;
import stroom.data.grid.client.HeadingBuilder;
import stroom.data.grid.client.MyDataGrid;
import stroom.docref.DocRef;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.Preset;
import stroom.util.client.DataGridComparatorFactory.Builder;
import stroom.util.shared.Expander;
import stroom.util.shared.GwtUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TreeAction;
import stroom.util.shared.UserRef;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.SafeHtmlHeader;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DataGridUtil {

    public static final int DEFAULT_PCT_WARNING_THRESHOLD = 80;
    public static final int DEFAULT_PCT_DANGER_THRESHOLD = 90;

    private static final String DISABLED_CELL_CLASS = "dataGridDisabledCell";
    private static final String LOW_LIGHT_COLOUR = "#666666";

    private DataGridUtil() {
    }

    public static Header<SafeHtml> createRightAlignedHeader(final String headerText) {
        final Header<SafeHtml> header = new SafeHtmlHeader(SafeHtmlUtils.fromString(headerText));
        header.setHeaderStyleNames(GwtUtil.appendStyles(
                header.getHeaderStyleNames(), "right-align"));
        return header;
    }

    public static Header<SafeHtml> createCenterAlignedHeader(final String headerText) {
        final Header<SafeHtml> header = new SafeHtmlHeader(SafeHtmlUtils.fromString(headerText));
        header.setHeaderStyleNames(GwtUtil.appendStyles(
                header.getHeaderStyleNames(),
                "center-align"));
        return header;
    }

    public static <T_ROW> Function<T_ROW, SafeHtml> highlightedCellExtractor(
            final Function<T_ROW, String> extractor,
            final Predicate<T_ROW> isHighlightedPredicate) {

        return row ->
                SafeHtmlUtil.getColouredText(
                        extractor.apply(row),
                        LOW_LIGHT_COLOUR,
                        !isHighlightedPredicate.test(row));
    }

    public static <T_ROW> Function<T_ROW, SafeHtml> highlightedCellExtractor(
            final Function<T_ROW, String> extractor,
            final Predicate<T_ROW> isHighlightedPredicate,
            final String highlightCssColour) {

        return row ->
                SafeHtmlUtil.getColouredText(
                        extractor.apply(row),
                        highlightCssColour,
                        isHighlightedPredicate.test(row));
    }

    public static <T_ROW> Function<T_ROW, SafeHtml> colouredCellExtractor(
            final Function<T_ROW, String> extractor,
            final Function<String, String> valueToColourFunc) {

        return row -> {
            final String value = extractor.apply(row);
            final String colourCode = valueToColourFunc.apply(value);
            return SafeHtmlUtil.getColouredText(extractor.apply(row), colourCode);
        };
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

    /**
     * Non-clickable SVG icon column
     */
    public static <T_ROW> Column<T_ROW, Preset> svgStatusColumn(
            final Function<T_ROW, Preset> cellValueExtractor) {
        final Column<T_ROW, Preset> column = column(cellValueExtractor, () -> new SvgCell(false));
        column.setCellStyleNames("statusIcon");
        return column;
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

    public static <T_ROW> Column<T_ROW, Expander> expanderColumn(
            final Function<T_ROW, Expander> expanderExtractor) {

        Objects.requireNonNull(expanderExtractor);

        // Explicit generics typing for GWT
        return new Column<T_ROW, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final T_ROW row) {
                if (row == null) {
                    return null;
                }
                return expanderExtractor.apply(row);
            }
        };
    }

    public static <T_ROW> Column<T_ROW, String> endColumn() {
        return new EndColumn<T_ROW>();
    }

//    public static <T_VIEW extends MyDataGrid<T_ROW>, T_ROW> void addResizableTextColumn(
//            final T_VIEW view,
//            final Function<T_ROW, String> cellValueExtractor,
//            final String name,
//            final int width) {
//        view.addResizableColumn(buildColumn(cellValueExtractor, TextCell::new), name, width);
//    }
//
//    public static <T_VIEW extends MyDataGrid<T_ROW>, T_ROW> void addResizableNumericTextColumn(
//            final T_VIEW view,
//            final Function<T_ROW, Number> cellValueExtractor,
//            final String name,
//            final int width) {
//        view.addResizableColumn(numericTextColumn(cellValueExtractor), name, width);
//    }
//
//    public static <T_VIEW extends MyDataGrid<T_ROW>, T_ROW> void addResizableSafeHtmlColumn(
//            final T_VIEW view,
//            final Function<T_ROW, SafeHtml> cellValueExtractor,
//            final String name,
//            final int width) {
//        view.addResizableColumn(safeHtmlColumn(cellValueExtractor), name, width);
//    }


    public static <T_VIEW extends MyDataGrid<T_ROW>, T_ROW> void addExpanderColumn(
            final T_VIEW view,
            final Function<T_ROW, Expander> expanderExtractor,
            final TreeAction<T_ROW> treeAction,
            final Runnable onExpanderChange,
            final int width) {
        view.addColumn(
                expanderColumn(expanderExtractor, treeAction, onExpanderChange),
                "",
                width);
    }

    public static <T_VIEW extends MyDataGrid<T_ROW>, T_ROW> void addExpanderColumn(
            final T_VIEW view,
            final Function<T_ROW, Expander> expanderExtractor,
            final int width) {
        view.addColumn(
                expanderColumn(expanderExtractor),
                "",
                width);
    }

    public static <T_VIEW extends MyDataGrid<T_ROW>, T_ROW> void addStatusIconColumn(
            final T_VIEW view,
            final Function<T_ROW, Preset> statusIconExtractor) {

        view.addColumn(
                svgStatusColumn(statusIconExtractor),
                "",
                ColumnSizeConstants.ICON_COL);
    }

    public static void addEndColumn(final MyDataGrid<?> view) {
        view.addEndColumn(new EndColumn<>());
    }

    public static void addEndColumn(final DataGrid<?> view) {
        view.addColumn(new EndColumn<>());
    }

    /**
     * @return "Yes" or "No"
     */
    public static String formatAsYesNo(final boolean val) {
        return val
                ? "Yes"
                : "No";
    }

    /**
     * @return A IEC byte string (e.g. '369G') or '?'
     */
    public static String formatAsIECByteString(final OptionalLong optSizeBytes) {
        return optSizeBytes != null && optSizeBytes.isPresent()
                ? ModelStringUtil.formatIECByteSizeString(optSizeBytes.getAsLong())
                : "?";
    }

    /**
     * @return An integer percentage suffixed with '%' or '?'
     */
    public static String formatPercentage(final OptionalDouble optPct) {
        return optPct != null && optPct.isPresent()
                ? ((long) optPct.getAsDouble()) + "%"
                : "?";
    }

    // There ought to be a better way of doing this so we don't have to have so many
    // methods to initiate the builder
    public static <T_ROW, T_CELL_VAL, T_CELL extends Cell<T_CELL_VAL>> ColumnBuilder<
            T_ROW, T_CELL_VAL, T_CELL> columnBuilder(
            final Function<T_ROW, T_CELL_VAL> valueExtractor,
            final Supplier<T_CELL> cellSupplier) {
        return new ColumnBuilder<T_ROW, T_CELL_VAL, T_CELL>(valueExtractor, cellSupplier);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, String, Cell<String>> textColumnBuilder(
            final Function<T_ROW, String> valueExtractor) {
        return new ColumnBuilder<T_ROW, String, Cell<String>>(valueExtractor, TextCell::new);
    }

    /**
     * A simple text cell with the value of the cell also as the hover tool tip value.
     */
    public static <T_ROW> ColumnBuilder<T_ROW, String, Cell<String>> textWithTooltipColumnBuilder(
            final Function<T_ROW, String> valueExtractor) {
        return textWithTooltipColumnBuilder(valueExtractor, Function.identity());
    }

    /**
     * A simple text cell with the value of the hover tooltip provided by tooltipFunction, which is
     * applied to the cell value.
     */
    public static <T_ROW> ColumnBuilder<T_ROW, String, Cell<String>> textWithTooltipColumnBuilder(
            final Function<T_ROW, String> valueExtractor,
            final Function<String, String> tooltipFunction) {

        final Supplier<Cell<String>> cellSupplier = () -> new TextCell() {

            @Override
            public void render(final Context context, final String data, final SafeHtmlBuilder sb) {
                if (tooltipFunction != null) {
                    final String tooltip = tooltipFunction.apply(data);
                    if (data != null) {
                        if (tooltip != null) {
                            sb.appendHtmlConstant("<div title=\"")
                                    .appendEscaped(tooltip)
                                    .appendHtmlConstant("\">");
                        }
                        sb.append(SafeHtmlUtils.fromString(data));
                        if (tooltip != null) {
                            sb.appendHtmlConstant("</div>");
                        }
                    }
                } else {
                    super.render(context, data, sb);
                }
            }
        };
        return new ColumnBuilder<T_ROW, String, Cell<String>>(valueExtractor, cellSupplier);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, Boolean, Cell<Boolean>> redGreenTextColumnBuilder(
            final Function<T_ROW, Boolean> valueExtractor,
            final String greenText,
            final String redText) {
        return new ColumnBuilder<T_ROW, Boolean, Cell<Boolean>>(
                valueExtractor,
                () -> new RedGreenTextCell(greenText, redText));
    }

    public static <T_ROW> ColumnBuilder<T_ROW, Number, ValueSpinnerCell> valueSpinnerColumnBuilder(
            final Function<T_ROW, Number> valueExtractor, final long minValue, final long maxValue) {
        return new ColumnBuilder<T_ROW, Number, ValueSpinnerCell>(
                valueExtractor,
                () -> new ValueSpinnerCell(minValue, maxValue));
    }

    public static <T_ROW> ColumnBuilder<T_ROW, String, ColourSwatchCell> colourSwatchColumnBuilder(
            final Function<T_ROW, String> cssColourExtractor) {
        return new ColumnBuilder<T_ROW, String, ColourSwatchCell>(cssColourExtractor, ColourSwatchCell::new);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, String, TextCell> iecByteStringColumnBuilder(
            final Function<T_ROW, Long> bytesExtractor, final String valueIfNull) {

        final Function<T_ROW, String> bytesAsStrExtractor = row -> {
            if (row == null || bytesExtractor == null) {
                return valueIfNull;
            } else {
                final Long bytes = bytesExtractor.apply(row);
                if (bytes == null) {
                    return valueIfNull;
                } else {
                    return ModelStringUtil.formatIECByteSizeString(bytes);
                }
            }
        };

        return new ColumnBuilder<T_ROW, String, TextCell>(bytesAsStrExtractor, TextCell::new);
    }

    /**
     * Builds a read only tick box that is either ticked or un-ticked.
     *
     * @param valueExtractor Function to extract a boolean from {@code T_ROW}.
     * @param <T_ROW>        The row type
     */
    public static <T_ROW> ColumnBuilder<T_ROW, TickBoxState, TickBoxCell> readOnlyTickBoxColumnBuilder(
            final Function<T_ROW, TickBoxState> valueExtractor) {

        return updatableTickBoxColumnBuilder(valueExtractor, false);
    }

    /**
     * Builds an updatable tick box that is either ticked or un-ticked.
     *
     * @param valueExtractor Function to extract a boolean from {@code T_ROW}.
     * @param <T_ROW>        The row type
     */
    public static <T_ROW> ColumnBuilder<T_ROW, TickBoxState, TickBoxCell> updatableTickBoxColumnBuilder(
            final Function<T_ROW, TickBoxState> valueExtractor) {

        return updatableTickBoxColumnBuilder(valueExtractor, true);
    }

    /**
     * Builds an updatable tick box that is either ticked or un-ticked.
     *
     * @param valueExtractor Function to extract a boolean from {@code T_ROW}.
     * @param <T_ROW>        The row type
     */
    public static <T_ROW> ColumnBuilder<T_ROW, TickBoxState, TickBoxCell> updatableTickBoxColumnBuilder(
            final Function<T_ROW, TickBoxState> valueExtractor,
            final boolean isUpdatable) {

        final DefaultAppearance defaultAppearance = isUpdatable
                ? new DefaultAppearance()
                : new NoBorderAppearance();

        return new ColumnBuilder<T_ROW, TickBoxState, TickBoxCell>(valueExtractor, () -> TickBoxCell.create(
                defaultAppearance,
                false,
                false,
                isUpdatable));
    }

    public static <T_ROW> Function<T_ROW, TickBoxState> createTickBoxExtractor(
            final Function<T_ROW, Boolean> booleanExtractor) {
        return row -> {
            final Boolean bool = Objects.requireNonNull(booleanExtractor).apply(row);
            return TickBoxState.fromBoolean(bool);
        };
    }

    /**
     * A builder for creating a text column with a hover icon to copy the text content of the cell
     *
     * @param valueExtractor Function to extract a cell value from the {@code T_ROW}.
     * @param <T_ROW>        The row type
     */
    public static <T_ROW> ColumnBuilder<T_ROW, String, Cell<String>> copyTextColumnBuilder(
            final Function<T_ROW, String> valueExtractor,
            final EventBus eventBus) {

        return new ColumnBuilder<T_ROW, String, Cell<String>>(valueExtractor, () -> new CopyTextCell(eventBus));
    }

    public static <T_ROW> ColumnBuilder<T_ROW, Number, Cell<Number>> percentBarColumnBuilder(
            final Function<T_ROW, Number> valueExtractor,
            final int warningThreshold,
            final int dangerThreshold) {

        return new ColumnBuilder<T_ROW, Number, Cell<Number>>(valueExtractor,
                () -> new PercentBarCell(warningThreshold, dangerThreshold));
    }

    /**
     * A builder for creating a column for a {@link UserRef} with hover icons to copy the name of the doc
     * and to open the user/group.
     *
     * @param valueExtractor Function to extract a {@link UserRef} from the {@code T_ROW}.
     * @param <T_ROW>        The row type
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static <T_ROW> ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>> userRefColumnBuilder(
            final Function<T_ROW, UserRef> valueExtractor,
            final EventBus eventBus,
            final ClientSecurityContext securityContext,
            final UserRef.DisplayType displayType) {
        return userRefColumnBuilder(valueExtractor, eventBus, securityContext, false, displayType);
    }

    /**
     * A builder for creating a column for a {@link UserRef} with hover icons to copy the name of the doc
     * and to open the user/group.
     *
     * @param userRefFunction Function to extract a {@link UserRef} from the {@code T_ROW}.
     * @param <T_ROW>         The row type
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static <T_ROW> ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>> userRefColumnBuilder(
            final Function<T_ROW, UserRef> userRefFunction,
            final EventBus eventBus,
            final ClientSecurityContext securityContext,
            final boolean showIcon,
            final UserRef.DisplayType displayType) {

        Objects.requireNonNull(userRefFunction);

        return new ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>>(Function.identity(), () -> new UserRefCell
                .Builder<T_ROW>()
                .eventBus(eventBus)
                .securityContext(securityContext)
                .showIcon(showIcon)
                .displayType(displayType)
                .userRefFunction(userRefFunction)
                .build());
    }

    public static <T_ROW> void addDocRefColumn(final EventBus eventBus,
                                               final MyDataGrid<T_ROW> dataGrid,
                                               final String name,
                                               final Function<T_ROW, DocRef> docRefExtractionFunction) {
        final Column<T_ROW, T_ROW> column = new ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>>(Function.identity(),
                () -> new DocRefCell
                        .Builder<T_ROW>()
                        .eventBus(eventBus)
                        .docRefFunction(docRefExtractionFunction)
                        .showIcon(true)
                        .build())
                .build();

        final ColSpec<T_ROW> colSpec = new ColSpec.Builder<T_ROW>()
                .column(column)
                .resizable(true)
                .name(name)
                .width(ColumnSizeConstants.BIG_COL)
                .build();
        dataGrid.addColumn(colSpec);
    }

    public static <T_ROW> void addFeedColumn(final EventBus eventBus,
                                             final MyDataGrid<T_ROW> dataGrid,
                                             final String name,
                                             final Function<T_ROW, String> nameExtractionFunction) {
        final Column<T_ROW, T_ROW> column = new ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>>(Function.identity(),
                () -> new FeedRefCell.Builder<T_ROW>()
                        .eventBus(eventBus)
                        .nameFunction(nameExtractionFunction)
                        .showIcon(true)
                        .build())
                .build();

        final ColSpec<T_ROW> colSpec = new ColSpec.Builder<T_ROW>()
                .column(column)
                .resizable(true)
                .name(name)
                .width(ColumnSizeConstants.BIG_COL)
                .build();
        dataGrid.addColumn(colSpec);
    }

    /**
     * A builder for creating a column for a {@link DocRef} with hover icons to copy the name of the doc
     * and to open the doc.
     *
     * @param docRefExtractionFunction Function to extract a {@link DocRef} from the {@code T_ROW}.
     * @param <T_ROW>                  The row type
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static <T_ROW> ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>> docRefColumnBuilder(
            final Function<T_ROW, DocRef> docRefExtractionFunction,
            final EventBus eventBus) {

        Objects.requireNonNull(docRefExtractionFunction);

        return new ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>>(
                Function.identity(),
                () -> new DocRefCell
                        .Builder<T_ROW>()
                        .eventBus(eventBus)
                        .docRefFunction(docRefExtractionFunction)
                        .build());
    }

    public static <T_ROW> ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>> feedRefColumnBuilder(
            final Function<T_ROW, String> nameExtractionFunction,
            final EventBus eventBus) {

        Objects.requireNonNull(nameExtractionFunction);

        return new ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>>(
                Function.identity(),
                () -> new FeedRefCell
                        .Builder<T_ROW>()
                        .eventBus(eventBus)
                        .nameFunction(nameExtractionFunction)
                        .build());
    }

    /**
     * A builder for creating a column for a {@link DocRef} with hover icons to copy the name of the doc
     * and to open the doc.
     */
    public static <T_ROW> ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>> docRefColumnBuilder(
            final DocRefCell.Builder<T_ROW> cellBuilder) {
        return new ColumnBuilder<T_ROW, T_ROW, Cell<T_ROW>>(Function.identity(), cellBuilder::build);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, CommandLink, Cell<CommandLink>> commandLinkColumnBuilder(
            final Function<T_ROW, CommandLink> valueExtractor) {
        return new ColumnBuilder<T_ROW, CommandLink, Cell<CommandLink>>(valueExtractor, CommandLinkCell::new);
    }

    public static void addCommandLinkFieldUpdater(final Column<?, CommandLink> column) {
        column.setFieldUpdater((index, object, value) -> {
            if (NullSafe.allNonNull(value, value.getCommand())) {
                value.getCommand().execute();
            }
        });
    }

    public static <T_ROW> ColumnBuilder<T_ROW, SafeHtml, Cell<SafeHtml>> htmlColumnBuilder(
            final Function<T_ROW, SafeHtml> valueExtractor) {
        return new ColumnBuilder<T_ROW, SafeHtml, Cell<SafeHtml>>(valueExtractor, SafeHtmlCell::new);
    }

    public static <T_ROW> ColumnBuilder<
            T_ROW, SafeHtml, HasContextMenusCell<SafeHtml>> hasContextMenusColumnBuilder(
            final Function<T_ROW, SafeHtml> valueExtractor, final HasContextMenus<SafeHtml> hasContextMenus) {
        return new ColumnBuilder<>(valueExtractor, () -> new HasContextMenusCell<>(hasContextMenus));
    }

    public static <T_ROW> ColumnBuilder<T_ROW, Preset, Cell<Preset>> svgPresetColumnBuilder(
            final boolean isButton,
            final Function<T_ROW, Preset> valueExtractor) {
        return new ColumnBuilder<T_ROW, Preset, Cell<Preset>>(valueExtractor, () -> new SvgCell(isButton));
    }

    public static HeadingBuilder headingBuilder(final String headingText) {
        return new HeadingBuilder(headingText);
    }

    public static HeadingBuilder headingBuilder() {
        return new HeadingBuilder("");
    }

    /**
     * Returns a {@link Function} that will call the toString method on the value returned
     * from passing the row into valueExtractor, but only if the value is non-null.
     */
    public static <T_ROW> Function<T_ROW, String> toStringFunc(final Function<T_ROW, Object> valueExtractor) {
        return (T_ROW row) ->
                NullSafe.toStringOrElse(row, valueExtractor, "");
    }

    /**
     * Returns a {@link Function} that will extract a boolean value from the row and
     * render it as Yes/No.
     */
    public static <T_ROW> Function<T_ROW, String> toYesNoFunc(final Function<T_ROW, Boolean> valueExtractor) {
        return (T_ROW row) ->
                DataGridUtil.formatAsYesNo(valueExtractor.apply(row));
    }

    /**
     * Returns a {@link Function} that will call the toString method on the value returned
     * from passing the row through valueExtractor1 then valueExtractor2. If any intermediate value
     * is null, the returned function will return an empty string.
     */
    public static <T_ROW, T_VAL1> Function<T_ROW, String> toStringFunc(
            final Function<T_ROW, T_VAL1> valueExtractor1,
            final Function<T_VAL1, Object> valueExtractor2) {
        return (T_ROW row) ->
                NullSafe.toStringOrElse(row, valueExtractor1, valueExtractor2, "");
    }

    public static <T> Builder<T> comparatorFactoryBuilder(final DataGrid<T> dataGrid) {
        return DataGridComparatorFactory.builder(dataGrid);
    }
}
