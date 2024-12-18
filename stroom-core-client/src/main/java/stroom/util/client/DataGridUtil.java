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
import stroom.data.client.presenter.DocRefCell.Builder;
import stroom.data.client.presenter.DocRefCell.DocRefProvider;
import stroom.data.client.presenter.UserRefCell;
import stroom.data.client.presenter.UserRefCell.UserRefProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.docref.DocRef;
import stroom.explorer.shared.DocumentTypes;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.Preset;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.Expander;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.GwtUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.TreeAction;
import stroom.util.shared.UserRef;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.cellview.client.SafeHtmlHeader;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Consumer;
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
        final Column<T_ROW, Expander> expanderColumn = new Column<T_ROW, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final T_ROW row) {
                if (row == null) {
                    return null;
                }
                return expanderExtractor.apply(row);
            }
        };

        return expanderColumn;
    }

    public static <T_ROW> Column<T_ROW, String> endColumn() {
        Column<T_ROW, String> column = new EndColumn<T_ROW>();
        return column;
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

//    public static <T_VIEW extends MyDataGrid<T_ROW>, T_ROW> void addResizableColumn(
//            final T_VIEW view,
//            final Column<T_ROW, ?> column,
//            final String name,
//            final int width) {
//        view.addResizableColumn(column, name, width);
//    }

    public static void addEndColumn(final MyDataGrid<?> view) {
        view.addEndColumn(new EndColumn<>());
    }

    private static CriteriaFieldSort getSortFromEvent(final ColumnSortEvent event) {
        final Column<?, ?> column = GwtNullSafe.get(event, ColumnSortEvent::getColumn);
        if (column instanceof OrderByColumn<?, ?> && column.isSortable()) {
            final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
            return new CriteriaFieldSort(
                    orderByColumn.getField(),
                    !event.isSortAscending(),
                    orderByColumn.isIgnoreCase());
        } else {
            return null;
        }
    }

    public static void addColumnSortHandler(final MyDataGrid<?> view,
                                            final BaseCriteria criteria,
                                            final Runnable onSortChange) {
        view.addColumnSortHandler(event -> {
            final CriteriaFieldSort sort = getSortFromEvent(event);
            if (sort != null) {
                criteria.setSort(sort);
                onSortChange.run();
            }
        });
    }

    public static void addColumnSortHandler(final MyDataGrid<?> view,
                                            final BaseCriteria.AbstractBuilder<?, ?> criteria,
                                            final Runnable onSortChange) {
        view.addColumnSortHandler(event -> {
            final CriteriaFieldSort sort = getSortFromEvent(event);
            if (sort != null) {
                criteria.sortList(List.of(sort));
                onSortChange.run();
            }
        });
    }

    public static void addColumnSortHandler(final MyDataGrid<?> view,
                                            final Consumer<List<CriteriaFieldSort>> fieldSortConsumer,
                                            final Runnable onSortChange) {
        view.addColumnSortHandler(event -> {
            final CriteriaFieldSort sort = getSortFromEvent(event);
            if (sort != null) {
                fieldSortConsumer.accept(List.of(sort));
                onSortChange.run();
            }
        });
    }

    public static void addColumnSortHandler(final DataGrid<?> view,
                                            final BaseCriteria criteria,
                                            final Runnable onSortChange) {
        view.addColumnSortHandler(event -> {
            final CriteriaFieldSort sort = getSortFromEvent(event);
            if (sort != null) {
                criteria.setSort(sort);
                onSortChange.run();
            }
        });
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

    public static <T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL extends Cell<T_CELL_VAL>> ColumnBuilder<
            T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL> columnBuilder(
            final Function<T_ROW, T_RAW_VAL> valueExtractor,
            final Function<T_RAW_VAL, T_CELL_VAL> formatter,
            final Supplier<T_CELL> cellSupplier) {

        return new ColumnBuilder<>(valueExtractor, formatter, cellSupplier);
    }

    public static <T_ROW, T_CELL_VAL, T_CELL extends Cell<T_CELL_VAL>> ColumnBuilder<
            T_ROW, T_CELL_VAL, T_CELL_VAL, T_CELL> columnBuilder(
            final Function<T_ROW, T_CELL_VAL> valueExtractor,
            final Supplier<T_CELL> cellSupplier) {

        return new ColumnBuilder<>(valueExtractor, Function.identity(), cellSupplier);
    }

    public static <T_ROW, T_RAW_VAL> ColumnBuilder<T_ROW, T_RAW_VAL, String, Cell<String>> textColumnBuilder(
            final Function<T_ROW, T_RAW_VAL> cellExtractor,
            final Function<T_RAW_VAL, String> formatter) {

        return new ColumnBuilder<>(cellExtractor, formatter, TextCell::new);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, String, String, Cell<String>> textColumnBuilder(
            final Function<T_ROW, String> cellExtractor) {
        return new ColumnBuilder<>(cellExtractor, Function.identity(), TextCell::new);
    }

    /**
     * A simple text cell with the value of the cell also as the hover tool tip value.
     */
    public static <T_ROW> ColumnBuilder<T_ROW, String, String, Cell<String>> textWithTooltipColumnBuilder(
            final Function<T_ROW, String> cellExtractor) {
        return textWithTooltipColumnBuilder(cellExtractor, Function.identity());
    }

    /**
     * A simple text cell with the value of the hover tooltip provided by tooltipFunction, which is
     * applied to the cell value.
     */
    public static <T_ROW> ColumnBuilder<T_ROW, String, String, Cell<String>> textWithTooltipColumnBuilder(
            final Function<T_ROW, String> cellExtractor,
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

        return new ColumnBuilder<>(cellExtractor, Function.identity(), cellSupplier);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, Boolean, Boolean, Cell<Boolean>> redGreenTextColumnBuilder(
            final Function<T_ROW, Boolean> cellExtractor,
            final String greenText,
            final String redText) {

        return new ColumnBuilder<>(cellExtractor, Function.identity(),
                () -> new RedGreenTextCell(greenText, redText));
    }

    public static <T_ROW> ColumnBuilder<T_ROW, Number, Number, ValueSpinnerCell> valueSpinnerColumnBuilder(
            final Function<T_ROW, Number> cellExtractor, final long minValue, final long maxValue) {
        return new ColumnBuilder<>(
                cellExtractor,
                Function.identity(),
                () -> new ValueSpinnerCell(minValue, maxValue));
    }

    public static <T_ROW> ColumnBuilder<T_ROW, String, String, ColourSwatchCell> colourSwatchColumnBuilder(
            final Function<T_ROW, String> cssColourExtractor) {
        return new ColumnBuilder<>(cssColourExtractor, Function.identity(), ColourSwatchCell::new);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, String, String, TextCell> iecByteStringColumnBuilder(
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

        return new ColumnBuilder<>(bytesAsStrExtractor, Function.identity(), TextCell::new);
    }

    /**
     * Builds a read only tick box that is either ticked or un-ticked.
     *
     * @param cellExtractor Function to extract a boolean from {@code T_ROW}.
     * @param <T_ROW>       The row type
     */
    public static <T_ROW> ColumnBuilder<T_ROW, TickBoxState, TickBoxState, TickBoxCell> readOnlyTickBoxColumnBuilder(
            final Function<T_ROW, TickBoxState> cellExtractor) {

        return updatableTickBoxColumnBuilder(cellExtractor, false);
    }

    /**
     * Builds an updatable tick box that is either ticked or un-ticked.
     *
     * @param cellExtractor Function to extract a boolean from {@code T_ROW}.
     * @param <T_ROW>       The row type
     */
    public static <T_ROW> ColumnBuilder<T_ROW, TickBoxState, TickBoxState, TickBoxCell> updatableTickBoxColumnBuilder(
            final Function<T_ROW, TickBoxState> cellExtractor) {

        return updatableTickBoxColumnBuilder(cellExtractor, true);
    }

    /**
     * Builds an updatable tick box that is either ticked or un-ticked.
     *
     * @param cellExtractor Function to extract a boolean from {@code T_ROW}.
     * @param <T_ROW>       The row type
     */
    public static <T_ROW> ColumnBuilder<T_ROW, TickBoxState, TickBoxState, TickBoxCell> updatableTickBoxColumnBuilder(
            final Function<T_ROW, TickBoxState> cellExtractor,
            final boolean isUpdatable) {

        final DefaultAppearance defaultAppearance = isUpdatable
                ? new DefaultAppearance()
                : new NoBorderAppearance();

        return new ColumnBuilder<>(
                cellExtractor,
                Function.identity(),
                () -> TickBoxCell.create(
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
     * @param cellExtractor Function to extract a String from the {@code T_ROW}.
     * @param <T_ROW>       The row type
     */
    public static <T_ROW> ColumnBuilder<T_ROW, String, String, Cell<String>> copyTextColumnBuilder(
            final Function<T_ROW, String> cellExtractor) {
        return new ColumnBuilder<>(cellExtractor, Function.identity(), CopyTextCell::new);
    }

    /**
     * A builder for creating a text column with a hover icon to copy the text content of the cell
     *
     * @param cellExtractor Function to extract a T_RAW_VAL from the {@code T_ROW}.
     * @param formatter     Function to convert T_RAW_VAL into a {@link String}.
     * @param <T_ROW>       The row type
     * @param <T_RAW_VAL>>  The type of the extracted value from the row.
     */
    public static <T_ROW, T_RAW_VAL> ColumnBuilder<T_ROW, T_RAW_VAL, String, Cell<String>> copyTextColumnBuilder(
            final Function<T_ROW, T_RAW_VAL> cellExtractor,
            final Function<T_RAW_VAL, String> formatter) {
        return new ColumnBuilder<>(cellExtractor, formatter, CopyTextCell::new);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, Number, Number, Cell<Number>> percentBarColumnBuilder(
            final Function<T_ROW, Number> cellExtractor,
            final int warningThreshold,
            final int dangerThreshold) {

        return new ColumnBuilder<>(
                cellExtractor,
                Function.identity(),
                () -> new PercentBarCell(warningThreshold, dangerThreshold));
    }

    /**
     * A builder for creating a column for a {@link UserRef} with hover icons to copy the name of the doc
     * and to open the user/group.
     *
     * @param cellExtractor Function to extract a {@link UserRef} from the {@code T_ROW}.
     * @param <T_ROW>       The row type
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static <T_ROW> ColumnBuilder<T_ROW, UserRefProvider<T_ROW>, UserRefProvider<T_ROW>, Cell<UserRefProvider<T_ROW>>> userRefColumnBuilder(
            final Function<T_ROW, UserRef> cellExtractor,
            final EventBus eventBus,
            final ClientSecurityContext securityContext,
            final UserRef.DisplayType displayType) {
        return userRefColumnBuilder(cellExtractor, eventBus, securityContext, false, displayType);
    }

    /**
     * A builder for creating a column for a {@link UserRef} with hover icons to copy the name of the doc
     * and to open the user/group.
     *
     * @param cellExtractor Function to extract a {@link UserRef} from the {@code T_ROW}.
     * @param <T_ROW>       The row type
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static <T_ROW> ColumnBuilder<T_ROW, UserRefProvider<T_ROW>, UserRefProvider<T_ROW>, Cell<UserRefProvider<T_ROW>>> userRefColumnBuilder(
            final Function<T_ROW, UserRef> cellExtractor,
            final EventBus eventBus,
            final ClientSecurityContext securityContext,
            final boolean showIcon,
            final UserRef.DisplayType displayType) {

        Objects.requireNonNull(cellExtractor);

        return new ColumnBuilder<>(
                row -> new UserRefProvider<>(row, cellExtractor),
                Function.identity(),
                () -> new UserRefCell<>(eventBus, securityContext, showIcon, displayType, null));
    }

    public static <T_ROW> void addDocRefColumn(final EventBus eventBus,
                                               final MyDataGrid<T_ROW> dataGrid,
                                               final String name,
                                               final DocumentTypes documentTypes,
                                               final Function<T_ROW, DocRef> docRefExtractionFunction) {
        final DocRefCell.Builder<T_ROW> cellBuilder = new Builder<T_ROW>()
                .eventBus(eventBus)
                .documentTypes(documentTypes)
                .showIcon(true);

        final Column<T_ROW, DocRefProvider<T_ROW>> column =
                new ColumnBuilder<T_ROW, DocRefProvider<T_ROW>, DocRefProvider<T_ROW>, Cell<DocRefProvider<T_ROW>>>()
                        .valueExtractor(row -> new DocRefProvider<>(row, docRefExtractionFunction))
                        .formatter(Function.identity())
                        .cellSupplier(cellBuilder::build)
                        .build();

        dataGrid.addResizableColumn(column, name, ColumnSizeConstants.BIG_COL);
    }

    /**
     * A builder for creating a column for a {@link DocRef} with hover icons to copy the name of the doc
     * and to open the doc.
     *
     * @param cellExtractor Function to extract a {@link DocRef} from the {@code T_ROW}.
     * @param <T_ROW>       The row type
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static <T_ROW> ColumnBuilder<T_ROW, DocRefProvider<DocRef>, DocRefProvider<DocRef>, Cell<DocRefProvider<DocRef>>> docRefColumnBuilder(
            final Function<T_ROW, DocRef> cellExtractor,
            final EventBus eventBus,
            final boolean allowLinkByName) {

        Objects.requireNonNull(cellExtractor);

        final DocRefCell.Builder<DocRef> cellBuilder = new Builder<DocRef>()
                .eventBus(eventBus)
                .allowLinkByName(allowLinkByName);

        final ColumnBuilder<T_ROW, DocRefProvider<DocRef>, DocRefProvider<DocRef>, Cell<DocRefProvider<DocRef>>>
                columnBuilder = new ColumnBuilder<>();
        return columnBuilder
                .valueExtractor(row -> GwtNullSafe.get(cellExtractor.apply(row), DocRefProvider::forDocRef))
                .formatter(Function.identity())
                .cellSupplier(cellBuilder::build);
    }

    /**
     * A builder for creating a column for a {@link DocRef} with hover icons to copy the name of the doc
     * and to open the doc.
     *
     * @param cellExtractor Function to extract a {@link DocRef} from the {@code T_ROW}.
     * @param <T_ROW>       The row type
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static <T_ROW> ColumnBuilder<T_ROW, DocRefProvider<T_ROW>, DocRefProvider<T_ROW>, Cell<DocRefProvider<T_ROW>>> docRefColumnBuilder(
            final Function<T_ROW, DocRefProvider<T_ROW>> cellExtractor,
            final EventBus eventBus,
            final boolean allowLinkByName,
            final Function<T_ROW, String> cssClassFunc) {

        final DocRefCell.Builder<T_ROW> cellBuilder = new Builder<T_ROW>()
                .eventBus(eventBus)
                .allowLinkByName(allowLinkByName)
                .cssClassFunction(cssClassFunc);

        return docRefColumnBuilder(cellExtractor, cellBuilder);
    }

    /**
     * A builder for creating a column for a {@link DocRef} with hover icons to copy the name of the doc
     * and to open the doc.
     *
     * @param valueExtractor Function to extract a {@link DocRef} from the {@code T_ROW}.
     * @param <T_ROW>        The row type
     */
    @SuppressWarnings("checkstyle:LineLength")
    public static <T_ROW> ColumnBuilder<T_ROW, DocRefProvider<T_ROW>, DocRefProvider<T_ROW>, Cell<DocRefProvider<T_ROW>>> docRefColumnBuilder(
            final Function<T_ROW, DocRefProvider<T_ROW>> valueExtractor,
            final DocRefCell.Builder<T_ROW> cellBuilder) {

        final ColumnBuilder<T_ROW, DocRefProvider<T_ROW>, DocRefProvider<T_ROW>, Cell<DocRefProvider<T_ROW>>>
                columnBuilder = new ColumnBuilder<>();
        return columnBuilder
                .valueExtractor(valueExtractor)
                .formatter(Function.identity())
                .cellSupplier(cellBuilder::build);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, CommandLink, CommandLink, Cell<CommandLink>> commandLinkColumnBuilder(
            final Function<T_ROW, CommandLink> cellExtractor) {

        return new ColumnBuilder<>(cellExtractor, Function.identity(), CommandLinkCell::new);
    }

    public static void addCommandLinkFieldUpdater(Column<?, CommandLink> column) {
        column.setFieldUpdater((index, object, value) -> {
            if (GwtNullSafe.allNonNull(value, value.getCommand())) {
                value.getCommand().execute();
            }
        });
    }

    public static <T_ROW, T_RAW_VAL> ColumnBuilder<T_ROW, T_RAW_VAL, SafeHtml, Cell<SafeHtml>> htmlColumnBuilder(
            final Function<T_ROW, T_RAW_VAL> cellExtractor,
            final Function<T_RAW_VAL, SafeHtml> formatter) {

        return new ColumnBuilder<>(cellExtractor, formatter, SafeHtmlCell::new);
    }

    public static <T_ROW> ColumnBuilder<T_ROW, SafeHtml, SafeHtml, Cell<SafeHtml>> htmlColumnBuilder(
            final Function<T_ROW, SafeHtml> cellExtractor) {

        return new ColumnBuilder<>(cellExtractor, Function.identity(), SafeHtmlCell::new);
    }

//    public static <T_ROW> ColumnBuilder<T_ROW, String, SafeHtml, Cell<SafeHtml>> htmlColumnBuilder(
//            final Function<T_ROW, String> stringExtractor) {
//
//        return new ColumnBuilder<>(stringExtractor, SafeHtmlUtils::fromString, SafeHtmlCell::new);
//    }

    public static <T_ROW> ColumnBuilder<T_ROW, Preset, Preset, Cell<Preset>> svgPresetColumnBuilder(
            final boolean isButton,
            final Function<T_ROW, Preset> cellExtractor) {

        return new ColumnBuilder<T_ROW, Preset, Preset, Cell<Preset>>()
                .valueExtractor(cellExtractor)
                .formatter(Function.identity())
                .cellSupplier(() -> new SvgCell(isButton));
    }

    public static HeadingBuilder headingBuilder(final String headingText) {
        return new HeadingBuilder(headingText);
    }

    public static HeadingBuilder headingBuilder() {
        return new HeadingBuilder("");
    }


    // --------------------------------------------------------------------------------


    public static class ColumnBuilder<T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL extends Cell<T_CELL_VAL>>
            extends AbstractColumnBuilder<T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL,
            ColumnBuilder<T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL>> {

        private ColumnBuilder() {

        }

        private ColumnBuilder(final Function<T_ROW, T_RAW_VAL> valueExtractor,
                              final Function<T_RAW_VAL, T_CELL_VAL> formatter,
                              final Supplier<T_CELL> cellSupplier) {
            valueExtractor(valueExtractor);
            formatter(formatter);
            cellSupplier(cellSupplier);
        }

        @Override
        ColumnBuilder<T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL> self() {
            return this;
        }
    }

    public static class SimpleColumnBuilder<T_ROW, T_RAW_VAL, T_CELL extends Cell<T_RAW_VAL>>
            extends AbstractColumnBuilder<T_ROW, T_RAW_VAL, T_RAW_VAL, T_CELL,
            SimpleColumnBuilder<T_ROW, T_RAW_VAL, T_CELL>> {

        private SimpleColumnBuilder() {
            formatter(Function.identity());
        }

        private SimpleColumnBuilder(final Function<T_ROW, T_RAW_VAL> valueExtractor,
                                    final Supplier<T_CELL> cellSupplier) {
            valueExtractor(valueExtractor);
            formatter(Function.identity());
            cellSupplier(cellSupplier);
        }

        @Override
        SimpleColumnBuilder<T_ROW, T_RAW_VAL, T_CELL> self() {
            return this;
        }
    }


    // --------------------------------------------------------------------------------


    public static class HeadingBuilder {

        private HeadingAlignment headingAlignment = null;
        private SafeHtml headingText = SafeHtmlUtils.EMPTY_SAFE_HTML;
        private String toolTip;

        public HeadingBuilder(final String headingText) {
            this.headingText = SafeHtmlUtil.getSafeHtml(headingText);
        }

        public HeadingBuilder headingText(final String headingText) {
            this.headingText = SafeHtmlUtil.getSafeHtml(headingText);
            return this;
        }

        public HeadingBuilder headingText(final SafeHtml headingText) {
            this.headingText = GwtNullSafe.requireNonNullElse(headingText, SafeHtmlUtils.EMPTY_SAFE_HTML);
            return this;
        }

        public HeadingBuilder leftAligned() {
            this.headingAlignment = HeadingAlignment.LEFT;
            return this;
        }

        public HeadingBuilder centerAligned() {
            this.headingAlignment = HeadingAlignment.CENTER;
            return this;
        }

        public HeadingBuilder rightAligned() {
            this.headingAlignment = HeadingAlignment.RIGHT;
            return this;
        }

        public HeadingBuilder withToolTip(final String toolTip) {
            this.toolTip = toolTip;
            return this;
        }

        public Header<SafeHtml> build() {

            final boolean hasToolTip = !GwtNullSafe.isBlankString(toolTip);
            final boolean hasAlignment = headingAlignment != null
                                         && headingAlignment != HeadingAlignment.LEFT;
            final Header<SafeHtml> header;
            String headingStyle = null;
            if (hasAlignment) {
                if (HeadingAlignment.CENTER == headingAlignment) {
                    headingStyle = "center-align";
                } else if (HeadingAlignment.RIGHT == headingAlignment) {
                    headingStyle = "right-align";
                }
            }

//            if (hasToolTip || hasAlignment) {
            if (hasToolTip) {

                final SafeHtmlBuilder builder = new SafeHtmlBuilder()
                        .appendHtmlConstant("<div");
//                if (hasToolTip) {
                builder.appendHtmlConstant(" title=\"")
                        .appendEscaped(toolTip)
                        .appendHtmlConstant("\"");
//                }
//                if (hasAlignment) {
//                    if (HeadingAlignment.CENTER == headingAlignment) {
//                        builder.appendHtmlConstant(" style=\"text-align: center;\"");
//                        headingStyle = "center-align";
//                    } else if (HeadingAlignment.RIGHT == headingAlignment) {
//                        builder.appendHtmlConstant(" style=\"text-align: right;\"");
//                        headingStyle = "right-align";
//                    }
//                }

                builder.appendHtmlConstant(">")
                        .append(headingText);
//                if (GwtNullSafe.isBlankString(headingText)) {
//                    builder.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
//                } else {
//                    builder.appendEscaped(headingText);
//                }

                final SafeHtml safeHtml = builder
                        .appendHtmlConstant("</div>")
                        .toSafeHtml();
                header = new SafeHtmlHeader(safeHtml);
            } else {
                header = new SafeHtmlHeader(headingText);
            }

            // Apply a class to the header itself
            GwtNullSafe.consume(headingStyle, header::setHeaderStyleNames);
            return header;
        }
    }


    // --------------------------------------------------------------------------------


    private enum HeadingAlignment {
        LEFT,
        CENTER,
        RIGHT;
    }


    // --------------------------------------------------------------------------------


    public static interface BrowserEventHandler<T_ROW> {

        void handle(final Context context,
                    final Element elem,
                    final T_ROW row,
                    final NativeEvent event);
    }
}
