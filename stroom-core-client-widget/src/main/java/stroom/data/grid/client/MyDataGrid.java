/*
 * Copyright 2017 Crown Copyright
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

import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.svg.shared.SvgImage;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconMenuItem.Builder;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tab.client.view.GlobalResizeObserver;
import stroom.widget.util.client.DoubleClickTester;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.http.client.URL;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.ui.CustomScrollPanel;
import com.google.gwt.user.client.ui.FocusUtil;
import com.google.gwt.user.client.ui.HeaderPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.RangeChangeEvent.Handler;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.List;

public class MyDataGrid<R> extends DataGrid<R> implements NativePreviewHandler {

    public static final DefaultResources RESOURCES = GWT.create(DefaultResources.class);
    public static final int DEFAULT_LIST_PAGE_SIZE = 100;
    public static final int MASSIVE_LIST_PAGE_SIZE = 100000;
    private static final String MULTI_LINE = "multiline";
    private static final String ALLOW_HEADER_SELECTION = "allow-header-selection";

    private final SimplePanel emptyTableWidget = new SimplePanel();
    private final SimplePanel loadingTableWidget = new SimplePanel();
    private final List<ColSettings> colSettings = new ArrayList<>();
    private final HasHandlers globalEventBus;

    private HeadingListener headingListener;
    private HandlerRegistration handlerRegistration;
    private ResizeHandle<R> resizeHandle;
    private MoveHandle<R> moveHandle;
    private Heading moveHeading;
    private boolean allowMove = true;
    private boolean allowResize = true;
    private boolean allowHeaderSelection = true;

    private int horzPos = 0;
    private int vertPos = 0;

    private final DoubleClickTester doubleClickTester = new DoubleClickTester();

    public MyDataGrid(final HasHandlers globalEventBus) {
        this(globalEventBus, DEFAULT_LIST_PAGE_SIZE);
    }

    public MyDataGrid(final HasHandlers globalEventBus, final int size) {
        super(size, RESOURCES);
        this.globalEventBus = globalEventBus;
        setAutoHeaderRefreshDisabled(true);
        setAutoFooterRefreshDisabled(true);

        // Set the message to display when the table is empty.
        setEmptyTableWidget(emptyTableWidget);
        setLoadingIndicator(loadingTableWidget);

        // Remove min height on header.
        final Node header = getElement().getChild(0);
        final Element e = (Element) header;
        e.addClassName(RESOURCES.dataGridStyle().dataGridHeaderBackground());
        e.addClassName(ALLOW_HEADER_SELECTION);
        e.getStyle().setPropertyPx("minHeight", 5);

        getRowContainer().getStyle().setCursor(Cursor.DEFAULT);

        // Sink all mouse events.
        sinkEvents(Event.MOUSEEVENTS);

        final HeaderPanel headerPanel = (HeaderPanel) getWidget();
        final CustomScrollPanel customScrollPanel = (CustomScrollPanel) headerPanel.getContentWidget();

        customScrollPanel.addScrollHandler(event -> {
            horzPos = customScrollPanel.getHorizontalScrollPosition();
            vertPos = customScrollPanel.getVerticalScrollPosition();
        });

        addAttachHandler(event -> {
            if (horzPos > 0 || vertPos > 0) {
                customScrollPanel.setVerticalScrollPosition(vertPos);
                customScrollPanel.setHorizontalScrollPosition(horzPos);
            }
        });
        sinkEvents(Event.ONCONTEXTMENU);
    }

    public MultiSelectionModelImpl<R> addDefaultSelectionModel(final boolean allowMultiSelect) {
        final MultiSelectionModelImpl<R> selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<R> selectionEventManager =
                new DataGridSelectionEventManager<>(this, selectionModel, allowMultiSelect);
        setSelectionModel(selectionModel, selectionEventManager);
        return selectionModel;
    }

    @Override
    public void setSelectionModel(final SelectionModel<? super R> selectionModel,
                                  final CellPreviewEvent.Handler<R> selectionEventManager) {
        super.setSelectionModel(selectionModel, selectionEventManager);
        if (selectionModel != null) {
            setKeyboardSelectionPolicy(KeyboardSelectionPolicy.ENABLED);
            // We need to set this to prevent default keyboard behaviour.
            setKeyboardSelectionHandler(event -> {
            });
            getRowContainer().getStyle().setCursor(Cursor.POINTER);
        }
    }

    public void setMultiLine(final boolean multiLine) {
        if (multiLine) {
            addStyleName(MULTI_LINE);
        } else {
            removeStyleName(MULTI_LINE);
        }
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        GlobalResizeObserver.addListener(getElement(), element -> onResize());
    }

    @Override
    protected void onDetach() {
        GlobalResizeObserver.removeListener(getElement());
        super.onDetach();
    }

    @Override
    protected void onBrowserEvent2(final Event event) {
        final int eventType = event.getTypeInt();
        if (Event.ONMOUSEMOVE == eventType) {
            final Heading heading = getHeading(event);
            if (heading != null) {
                if (handlerRegistration == null) {
                    // Show the resize handle immediately before
                    // attaching the native event preview handler.
                    final ResizeHandle<R> resizeHandle = getResizeHandle();
                    if (resizeHandle.update(event, heading)) {
                        doubleClickTester.clear();
                        resizeHandle.show();
                    }

                    handlerRegistration = Event.addNativePreviewHandler(this);
                }
            }
        } else if (event.getTypeInt() == Event.ONCONTEXTMENU) {
            event.preventDefault();
            event.stopPropagation();

            final int clientX = event.getClientX();
            final int clientY = event.getClientY();

            //finding cell/row/column info
            final Element target = event.getEventTarget().cast();
            final TableCellElement cell = findParentCell(target);
            int rowIndex = -1;
            int colIndex = -1;
            if (cell != null) {
                final TableRowElement row = cell.getParentElement().cast();
                rowIndex = row.getSectionRowIndex();
                colIndex = cell.getCellIndex();
            }

            showContextMenu(clientX, clientY, rowIndex, colIndex, target);
            return;
        }
        super.onBrowserEvent2(event);
    }

    private TableCellElement findParentCell(Element target) {
        while (target != null && !"td".equalsIgnoreCase(target.getTagName())) {
            target = target.getParentElement();
        }
        return (TableCellElement) target;
    }

    private void showContextMenu(final int x, final int y, final int rowIndex,
                                 final int colIndex, final Element target) {
        final List<Item> menuItems = new ArrayList<>();
        boolean specialItems = false;

        Hyperlink hyperlink = null;

        if (rowIndex >= 0 && colIndex >= 0) {
            final Column<R, ?> column = getColumn(colIndex);
            final com.google.gwt.cell.client.Cell<?> cell = column.getCell();

            // Checking if the cell has its own context menu
            if (cell instanceof HasContextMenus) {
                final R rowValue = getVisibleItem(rowIndex);
                final Object cellValue = column.getValue(rowValue);
                final Object key = getKeyProvider() != null
                        ? getKeyProvider().getKey(rowValue)
                        : null;
                final Context context = new Context(rowIndex, colIndex, key);

                @SuppressWarnings("unchecked") final HasContextMenus<Object> hasContextMenus =
                        (HasContextMenus<Object>) cell;
                final List<Item> cellMenuItems = hasContextMenus.getContextMenuItems(context, cellValue);

                if (cellMenuItems != null && !cellMenuItems.isEmpty()) {
                    menuItems.addAll(cellMenuItems);
                    specialItems = true;
                }
            }

            Element linkElement = target;
            while (linkElement != null && !"a".equalsIgnoreCase(linkElement.getTagName())) {
                linkElement = linkElement.getParentElement();
            }

            if (linkElement != null) {
                final String href = linkElement.getAttribute("href");
                if (href != null && !href.isEmpty() && !href.startsWith("javascript")) {
                    hyperlink = new Hyperlink(
                            normalizeWhitespace(linkElement.getInnerText()),
                            URL.decodeQueryString(href),
                            null,
                            null,
                            null);
                }
            }

            if (hyperlink == null) {
                Element customLinkElement = target;
                while (customLinkElement != null && !customLinkElement.hasAttribute("link")) {
                    customLinkElement = customLinkElement.getParentElement();
                }

                if (customLinkElement != null) {
                    final String linkAttr = customLinkElement.getAttribute("link");
                    if (linkAttr != null) {
                        hyperlink = Hyperlink.create(linkAttr);
                    }
                }
            }
        }

        boolean isLink = false;
        if (hyperlink != null) {
            isLink = true;
            final Hyperlink finalHyperlink = hyperlink;
            if (!menuItems.isEmpty()) {
                menuItems.add(new stroom.widget.menu.client.presenter.Separator(1));
            }
            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.COPY)
                    .text("Copy Link URL")
                    .command(() -> copyToClipboard(finalHyperlink.getHref()))
                    .build());
            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.OPEN)
                    .text("Follow URL")
                    .command(() -> HyperlinkEvent.fire(
                            globalEventBus,
                            finalHyperlink,
                            new DefaultTaskMonitorFactory(globalEventBus)))
                    .build());

            if ("dashboard".equals(hyperlink.getType())) {
                if ("self".equals(hyperlink.getTarget())) {
                    menuItems.add(new Builder()
                            .icon(SvgImage.OPEN)
                            .text("Open In New Tab")
                            .command(() -> HyperlinkEvent.fire(
                                    globalEventBus,
                                    finalHyperlink.copy().target("tab").build(),
                                    new DefaultTaskMonitorFactory(globalEventBus)))
                            .build());
                } else {
                    menuItems.add(new Builder()
                            .icon(SvgImage.OPEN)
                            .text("Open In This Tab")
                            .command(() -> HyperlinkEvent.fire(
                                    globalEventBus,
                                    finalHyperlink.copy().target("self").build(),
                                    new DefaultTaskMonitorFactory(globalEventBus)))
                            .build());
                }
            }
        }

        if (rowIndex >= 0 && colIndex >= 0) {
            final Column<R, ?> column = getColumn(colIndex);
            final com.google.gwt.cell.client.Cell<?> cell = column.getCell();
            boolean cellItemsAdded = false;

            if (!specialItems && cell instanceof HasContextMenus) {
                final R rowValue = getVisibleItem(rowIndex);
                final Object cellValue = column.getValue(rowValue);
                final Object key = getKeyProvider() != null
                        ? getKeyProvider().getKey(rowValue)
                        : null;
                final Context context = new Context(rowIndex, colIndex, key);

                @SuppressWarnings("unchecked") final HasContextMenus<Object> hasContextMenus =
                        (HasContextMenus<Object>) cell;
                final List<Item> cellMenuItems = hasContextMenus.getContextMenuItems(context, cellValue);

                if (cellMenuItems != null && !cellMenuItems.isEmpty()) {
                    if (!menuItems.isEmpty()) {
                        menuItems.add(new stroom.widget.menu.client.presenter.Separator(1));
                    }
                    menuItems.addAll(cellMenuItems);
                    cellItemsAdded = true;
                }
            }

            if (isLink || cellItemsAdded) {
                menuItems.add(new stroom.widget.menu.client.presenter.Separator(1));
            }

            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.COPY)
                    .text("Copy Cell")
                    .command(() -> exportCell(rowIndex, colIndex))
                    .build());

            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.COPY)
                    .text("Copy Row")
                    .command(() -> exportRow(rowIndex))
                    .build());

            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.COPY)
                    .text("Copy Selected Rows")
                    .command(this::exportSelectedRows)
                    .build());

            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.COPY)
                    .text("Copy Column")
                    .command(() -> exportColumn(colIndex))
                    .build());

            menuItems.add(new IconMenuItem.Builder()
                    .icon(SvgImage.COPY)
                    .text("Copy Column For Selected Rows")
                    .command(() -> exportColumnForSelectedRows(colIndex))
                    .build());
        }

        menuItems.add(new IconMenuItem.Builder()
                .icon(SvgImage.COPY)
                .text("Copy Page")
                .command(this::copyTableAsCSV)
                .build());

        menuItems.add(new IconMenuItem.Builder()
                .icon(SvgImage.DOWNLOAD)
                .text("Export Page")
                .command(this::exportTableAsCSV)
                .build());

        ShowMenuEvent.builder()
                .items(menuItems)
                .popupPosition(new PopupPosition(x, y))
                .fire(globalEventBus);
    }

    private String getCellText(final int row, final int col) {
        final TableRowElement rowElement = getRowElement(row);
        if (rowElement != null) {
            final TableCellElement cellElement = rowElement.getCells().getItem(col);
            if (cellElement != null) {
                //strip html tags
                return normalizeWhitespace(cellElement.getInnerText());
            }
        }
        return "";
    }

    private String escapeCsv(final String text) {
        if (text == null) {
            return "";
        }
        final String cleanedText = text.replace('\n', ' ');
        if (cleanedText.contains(",") || cleanedText.contains("\"")) {
            final String escapedText = cleanedText.replace("\"", "\"\"");
            return "\"" + escapedText + "\"";
        }
        return cleanedText;
    }

    private void exportCell(final int rowIndex, final int colIndex) {
        if (rowIndex >= 0 && colIndex >= 0) {
            copyToClipboard(getCellText(rowIndex, colIndex));
        }
    }

    private void exportRow(final int rowIndex) {
        final int columnOffset = getColumnOffset();
        if (rowIndex >= 0) {
            final StringBuilder sb = new StringBuilder();
            for (int col = columnOffset; col < getColumnCount(); col++) {
                addDelimiter(sb, col, columnOffset);
                sb.append(escapeCsv(getCellText(rowIndex, col)));
            }
            copyToClipboard(sb.toString());
        }
    }

    private void exportSelectedRows() {
        final StringBuilder sb = new StringBuilder();
        final int columnOffset = getColumnOffset();
        for (int rowNum = 0; rowNum < getVisibleItemCount(); rowNum++) {
            final R item = getVisibleItem(rowNum);
            if (item != null) {
                if (getSelectionModel().isSelected(item)) {
                    addNewLine(sb);
                    for (int col = columnOffset; col < getColumnCount(); col++) {
                        addDelimiter(sb, col, columnOffset);
                        sb.append(escapeCsv(getCellText(rowNum, col)));
                    }
                }
            }
        }
        copyToClipboard(sb.toString());
    }

    private void exportColumn(final int colIndex) {
        if (colIndex >= 0) {
            final StringBuilder sb = new StringBuilder();
            for (int row = 0; row < getVisibleItemCount(); row++) {
                addNewLine(sb);
                sb.append(getCellText(row, colIndex));
            }
            copyToClipboard(sb.toString());
        }
    }

    private void exportColumnForSelectedRows(final int colIndex) {
        if (colIndex >= 0) {
            final StringBuilder sb = new StringBuilder();
            for (int rowNum = 0; rowNum < getVisibleItemCount(); rowNum++) {
                final R item = getVisibleItem(rowNum);
                if (item != null) {
                    if (getSelectionModel().isSelected(item)) {
                        addNewLine(sb);
                        sb.append(getCellText(rowNum, colIndex));
                    }
                }
            }
            copyToClipboard(sb.toString());
        }
    }

    private void copyTableAsCSV() {
        exportTableAsCSV(this::copyToClipboard);
    }

    private void exportTableAsCSV() {
        exportTableAsCSV(csv -> downloadCSV("table.csv", csv));
    }

    private void exportTableAsCSV(final CommandWithCsv command) {
        final StringBuilder sb = new StringBuilder();
        final TableSectionElement head = getTableHeadElement();
        final int columnOffset = getColumnOffset();

        // Headers
        if (head != null && head.getRows().getLength() > 0) {
            addNewLine(sb);
            final TableRowElement headerRow = head.getRows().getItem(0);
            for (int col = columnOffset; col < getColumnCount(); col++) {
                addDelimiter(sb, col, columnOffset);
                final TableCellElement th = headerRow.getCells().getItem(col);
                sb.append(escapeCsv(normalizeWhitespace(th.getInnerText())));
            }
        }

        // Rows
        for (int row = 0; row < getVisibleItemCount(); row++) {
            addNewLine(sb);
            for (int col = columnOffset; col < getColumnCount(); col++) {
                addDelimiter(sb, col, columnOffset);
                sb.append(escapeCsv(getCellText(row, col)));
            }
        }
        command.execute(sb.toString());
    }

    /**
     * Add new line between rows if we have written output.
     *
     * @param sb The StringBuilder to append to.
     */
    private void addNewLine(final StringBuilder sb) {
        // GWT does not allow isEmpty()
        if (sb.length() > 0) {
            sb.append("\n");
        }
    }

    private void addDelimiter(final StringBuilder sb, final int col, final int columnOffset) {
        if (col > columnOffset) {
            sb.append(",");
        }
    }

    private interface CommandWithCsv {

        void execute(String csv);
    }

    //browser api to export csv or copy
    private native void copyToClipboard(String text) /*-{
        $wnd.navigator.clipboard.writeText(text);
    }-*/;

    private native void downloadCSV(String filename, String csv) /*-{
        var blob = new Blob([csv], { type: "text/csv" });
        var link = $doc.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = filename;
        link.style.display = "none";
        $doc.body.appendChild(link);
        link.click();
        $doc.body.removeChild(link);
    }-*/;

    private int getColumnOffset() {
        final TableSectionElement head = getTableHeadElement();
        int columnOffset = 0;

        if (head != null && head.getRows().getLength() > 0) {
            final TableRowElement headerRow = head.getRows().getItem(0);
            for (int col = 0; col < getColumnCount(); col++) {
                final TableCellElement th = headerRow.getCells().getItem(col);

                final String string = escapeCsv(normalizeWhitespace(th.getInnerText()));
                if (string.isEmpty()) {
                    columnOffset++;
                } else {
                    break;
                }
            }
        }
        return columnOffset;
    }

    private String normalizeWhitespace(final String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    @Override
    public void onPreviewNativeEvent(final NativePreviewEvent nativePreviewEvent) {
        final NativeEvent event = nativePreviewEvent.getNativeEvent();
        if (Event.ONMOUSEMOVE == nativePreviewEvent.getTypeInt()) {
            final ResizeHandle<R> resizeHandle = getResizeHandle();
            final MoveHandle<R> moveHandle = getMoveHandle();

            if (resizeHandle.isResizing()) {
                resizeHandle.resize(event);

            } else {
                if (moveHandle.isMoving()) {
                    moveHandle.move(event);

                } else if (allowMove) {
                    // Try and start moving the current column.
                    if (moveHandle.isDragThresholdExceeded(event)) {
                        moveHandle.startMove(event);

                        // Hide the resize handle if we are dragging a column.
                        resizeHandle.hide();

                        if (headingListener != null) {
                            headingListener.onMoveStart(event, () -> getHeading(event));
                        }

                    } else {
                        // Update the resize handle position.
                        final Heading heading = getHeading(event);
                        resizeHandle.update(event, heading);
                    }
                }
            }

            nativePreviewEvent.cancel();
            nativePreviewEvent.getNativeEvent().preventDefault();
            nativePreviewEvent.getNativeEvent().stopPropagation();

        } else if (Event.ONMOUSEDOWN == nativePreviewEvent.getTypeInt()) {
            if (MouseUtil.isPrimary(event)) {
                final ResizeHandle<R> resizeHandle = getResizeHandle();
                final MoveHandle<R> moveHandle = getMoveHandle();

                moveHeading = null;

                if (allowResize &&
                    !resizeHandle.isResizing() &&
                    MouseHelper.mouseIsOverElement(event, resizeHandle.getElement())) {
                    resizeHandle.startResize(event);

                    if (headingListener != null) {
                        headingListener.onMoveStart(event, () -> getHeading(event));
                    }

                } else {
                    moveHeading = getHeading(event);
                }

                // Set the heading that the move handle will use.
                if (allowMove) {
                    moveHandle.setHeading(event, moveHeading);
                }
            }

        } else if (Event.ONMOUSEUP == nativePreviewEvent.getTypeInt()) {
            if (MouseUtil.isPrimary(event)) {
                final ResizeHandle<R> resizeHandle = getResizeHandle();
                final MoveHandle<R> moveHandle = getMoveHandle();

                if (resizeHandle.isResizing()) {
                    if (doubleClickTester.isDoubleClick(resizeHandle)) {
                        final int colNo = resizeHandle.getColNo();

                        final Element tempDiv = DOM.createDiv();
                        tempDiv.setClassName("dataGridCell-text-measurement");

                        RootPanel.get().getElement().appendChild(tempDiv);

                        final double minHeaderWidth = getMinHeaderWidth(colNo, tempDiv);
                        final double minBodyWidth = getMinBodyWidth(colNo, tempDiv);
                        final double minWidth = Math.max(minHeaderWidth, minBodyWidth);

                        RootPanel.get().getElement().removeChild(tempDiv);

                        final int existingWidth = resizeHandle.getExistingWidth();
                        final int diff = (int) minWidth - existingWidth;
                        resizeHandle.endResize(diff);

                    } else {
                        // Find out how far we moved.
                        final int diff = resizeHandle.getDiff(event);

                        // Stop resizing.
                        resizeHandle.endResize(diff);

                        // If we have moved then we don't want to test for double click.
                        if (diff != 0) {
                            doubleClickTester.clear();
                        }

                        final Element target = event.getEventTarget().cast();
                        final boolean isOverHandle = resizeHandle.getElement().isOrHasChild(target);

                        if (!isOverHandle) {
                            hideResizeHandle();
                        }
                    }

                    if (headingListener != null) {
                        headingListener.onMoveEnd(event, () -> getHeading(event));
                    }

                } else if (moveHandle.isMoving()) {
                    // Stop moving column.
                    moveHandle.endMove(event);

                    if (headingListener != null) {
                        headingListener.onMoveEnd(event, () -> getHeading(event));
                    }

                } else {
                    if (allowHeaderSelection && headingListener != null) {
                        headingListener.onShowMenu(event, () -> getHeading(event));

                        // Detach event preview handler.
                        resizeHandle.hide();
                    }
                }

                // Set the heading that the move handle will use.
                moveHeading = null;
                moveHandle.setHeading(event, moveHeading);
            }

        } else if (Event.ONMOUSEOUT == nativePreviewEvent.getTypeInt()) {
            final ResizeHandle<R> resizeHandle = getResizeHandle();

            // Hide the resize handle once the mouse moves outside the data
            // grid.
            if (!resizeHandle.isResizing() &&
                moveHeading == null &&
                !MouseHelper.mouseIsOverElement(event, resizeHandle.getElement())) {
                final Element rel = event.getRelatedEventTarget().cast();
                final Heading heading = getHeading(rel, event.getClientX());
                if (heading != null) {
                    if (handlerRegistration != null) {
                        handlerRegistration.removeHandler();
                        handlerRegistration = null;
                    }

                    // Show the resize handle immediately before
                    // attaching the native event preview handler.
                    if (resizeHandle.update(event, heading)) {
                        doubleClickTester.clear();
                        resizeHandle.show();
                    }

                    handlerRegistration = Event.addNativePreviewHandler(this);

                } else {
                    hideResizeHandle();
                }
            }
        }
    }

    @Override
    public TableSectionElement getTableHeadElement() {
        return super.getTableHeadElement();
    }

    private double getMinHeaderWidth(final int colNo, final Element tempDiv) {
        double minWidth = 3;

        // Get the col element.
        final Element col = getTableHeadElement()
                .getFirstChildElement()
                .getChild(colNo)
                .getFirstChild()
                .cast();

        // Look for spans used by dashboard tables.
        for (int i = 0; i < col.getChildCount(); i++) {
            final Element span = col.getChild(i).cast();
            minWidth += ElementUtil.getSubPixelOffsetWidth(span);
        }

        // If we still have a narrow col see if there is any text content.
        if (minWidth < 10) {
            final String text = col.getInnerText();
            tempDiv.setInnerHTML(text);
            final double scrollWidth = ElementUtil.getSubPixelOffsetWidth(tempDiv);
            minWidth = Math.max(minWidth, scrollWidth);
        }

        return minWidth;
    }

    private double getMinBodyWidth(final int colNo, final Element tempDiv) {
        double minWidth = 3;
        for (int row = 0; row < getVisibleItemCount(); row++) {
            final TableRowElement tableRowElement = getRowElement(row);
            final NodeList<TableCellElement> cells = tableRowElement.getCells();

            Element el = cells.getItem(colNo);
            while (el.getFirstChildElement() != null) {
                el = el.getFirstChildElement();
            }

            final String text = el.getInnerText();

            if (!text.isEmpty()) {
                tempDiv.setInnerHTML(text);
                final double offsetWidth = ElementUtil.getSubPixelOffsetWidth(tempDiv) + 1;
                minWidth = Math.max(minWidth, offsetWidth);
            }
        }

        return minWidth;
    }

    private void hideResizeHandle() {
        resizeHandle.hide();
        if (handlerRegistration != null) {
            // Detach event preview handler.
            handlerRegistration.removeHandler();
            handlerRegistration = null;
        }
    }

    private ResizeHandle<R> getResizeHandle() {
        if (resizeHandle == null) {
            resizeHandle = new ResizeHandle<R>(this, colSettings, RESOURCES);
        }
        return resizeHandle;
    }

    private MoveHandle<R> getMoveHandle() {
        if (moveHandle == null) {
            moveHandle = new MoveHandle<R>(this, colSettings, RESOURCES);
        }

        return moveHandle;
    }

    private Heading getHeading(final NativeEvent event) {
        final Element target = event.getEventTarget().cast();
        return getHeading(target, event.getClientX());
    }

    private Heading getHeading(final Element target, final int initialX) {
        int childIndex;
        Element th = target;
        final Element headerRow;

        // Get parent th.
        while (th != null && !"th".equalsIgnoreCase(th.getTagName())) {
            if ("input".equalsIgnoreCase(th.getTagName())) {
                th = null;
            } else {
                th = th.getParentElement();
            }
        }

        if (th != null) {
            headerRow = th.getParentElement();
            if (headerRow != null) {
                childIndex = -1;
                for (int i = 0; i < headerRow.getChildCount(); i++) {
                    if (headerRow.getChild(i) == th) {
                        childIndex = i;
                        break;
                    }
                }

                return new Heading(getElement(), th, childIndex, initialX);
            }
        }

        return null;
    }

    @Override
    public void addColumn(final Column<R, ?> column, final String name) {
        addColumn(column, name, 200);
    }

    public void addColumn(final Column<R, ?> column, final Header<?> header, final int width) {
        colSettings.add(new ColSettings(false, false));
        super.addColumn(column, header);
        setColumnWidth(column, width, Unit.PX);
    }

    public void addColumn(final Column<R, ?> column, final String name, final int width) {
        colSettings.add(new ColSettings(false, false));
        super.addColumn(column, SafeHtmlUtils.fromSafeConstant(name));
        setColumnWidth(column, width, Unit.PX);
    }

    public void addResizableColumn(final Column<R, ?> column, final String name, final int width) {
        colSettings.add(new ColSettings(true, true));
        super.addColumn(column, SafeHtmlUtils.fromSafeConstant(name));
        setColumnWidth(column, width, Unit.PX);
    }

    public void addColumn(final ColSpec<R> colSpec) {
        colSettings.add(colSpec.getColSettings());
        super.addColumn(colSpec.getColumn(), colSpec.getHeader());
        setColumnWidth(colSpec.getColumn(), colSpec.getWidth(), Unit.PX);
    }

    public void sort(final Column<R, ?> column) {
        getColumnSortList().push(column);
    }

    /**
     * Add a resizable column that will initially expand so that the table fills the available space.
     * Unless manually resized, it will expand to fill but not go below the initialMinimumWidth.
     * Once the user resizes it, it becomes a fixed width column under their control.
     *
     * @param fillWeight          Set this when you want more than one column to expand to fill the space but with
     *                            different proportions. All columns pct values should add up to 100.
     * @param initialMinimumWidth Initial minimum width in pixels. Ignored once the user has resized it.
     */
    public void addAutoResizableColumn(final Column<R, ?> column,
                                       final String name,
                                       final int fillWeight,
                                       final int initialMinimumWidth) {
        colSettings.add(new ColSettings(true, true, true, fillWeight, initialMinimumWidth));
        super.addColumn(column, SafeHtmlUtils.fromSafeConstant(name));
        setColumnWidth(column, initialMinimumWidth, Unit.PX);
    }

    /**
     * Add a resizable column that will initially expand so that the table fills the available space.
     * Unless manually resized, it will expand to fill but not go below the initialMinimumWidth.
     * Once the user resizes it, it becomes a fixed width column under their control.
     *
     * @param initialMinimumWidth Initial minimum width in pixels. Ignored once the user has resized it.
     */
    public void addAutoResizableColumn(final Column<R, ?> column,
                                       final String name,
                                       final int initialMinimumWidth) {
        colSettings.add(new ColSettings(true, true, true, 100, initialMinimumWidth));
        super.addColumn(column, SafeHtmlUtils.fromSafeConstant(name));
        setColumnWidth(column, initialMinimumWidth, Unit.PX);
    }

    /**
     * Add a resizable column that will initially expand so that the table fills the available space.
     * Unless manually resized, it will expand to fill but not go below the initialMinimumWidth.
     * Once the user resizes it, it becomes a fixed width column under their control.
     *
     * @param initialMinimumWidth Initial minimum width in pixels. Ignored once the user has resized it.
     */
    public void addAutoResizableColumn(final Column<R, ?> column,
                                       final Header<?> header,
                                       final int initialMinimumWidth) {
        colSettings.add(new ColSettings(true, true, true, 100, initialMinimumWidth));
        super.addColumn(column, header);
        setColumnWidth(column, initialMinimumWidth, Unit.PX);
    }

    public void addResizableColumn(final Column<R, ?> column,
                                   final Header<?> header,
                                   final int width) {
        colSettings.add(new ColSettings(true, true));
        super.addColumn(column, header);
        setColumnWidth(column, width, Unit.PX);
    }

    public void addEndColumn(final EndColumn<R> column) {
    }

    @Override
    public void removeColumn(final Column<R, ?> column) {
        final int index = super.getColumnIndex(column);
        if (index != -1) {
            colSettings.remove(index);
            super.removeColumn(column);
        }
    }

    public void moveColumn(final int fromIndex, final int toIndex) {
        if (headingListener != null) {
            headingListener.moveColumn(fromIndex, toIndex);
        }

        final Column<R, ?> col = super.getColumn(fromIndex);
        final Header<?> header = super.getHeader(fromIndex);

        super.removeColumn(fromIndex);
        final ColSettings settings = colSettings.remove(fromIndex);

        int newIndex = toIndex;
        if (fromIndex < toIndex) {
            newIndex = toIndex - 1;
        }

        super.insertColumn(newIndex, col, header);
        colSettings.add(newIndex, settings);
    }

    public void resizeColumn(final int index, final int width) {
        if (headingListener != null) {
            headingListener.resizeColumn(index, width);
        }

        final Column<R, ?> column = super.getColumn(index);
        setColumnWidth(column, width, Unit.PX);
    }

    public void setColumnWidth(final Column<R, ?> column, final int width, final Unit unit) {
        super.setColumnWidth(column, width, unit);
        resizeTableToFitColumns();
    }

    @Override
    public HandlerRegistration addRangeChangeHandler(final Handler handler) {
        return super.addRangeChangeHandler(handler);
    }

    @Override
    public HandlerRegistration addRowCountChangeHandler(
            final com.google.gwt.view.client.RowCountChangeEvent.Handler handler) {
        return super.addRowCountChangeHandler(handler);
    }

    public HandlerRegistration addHyperlinkHandler(final HyperlinkEvent.Handler handler) {
        return super.addHandler(handler, HyperlinkEvent.getType());
    }

    @Override
    protected void doAttachChildren() {
        super.doAttachChildren();
        FocusUtil.forceFocus(() -> resizeTableToFitColumns(true));
    }

    public void resizeTableToFitColumns() {
        resizeTableToFitColumns(false);
    }

    public void resizeTableToFitColumns(final boolean redistribute) {
        final int totalWidth;
        final int outerWidth = getElement().getClientWidth();
        final int tableWidth = getTableBodyElement().getClientWidth();

        // If the outer width is greater than the table with then see if we can expand the columns and table to fit the
        // space.
        if (redistribute && outerWidth > 0 && outerWidth > tableWidth) {
            double totalWeight = 0;
            int totalColWidth = 0;
            for (int i = 0; i < super.getColumnCount() && i < colSettings.size(); i++) {
                final Column<R, ?> col = super.getColumn(i);
                final ColSettings settings = colSettings.get(i);
                if (settings != null) {
                    final String stringWidth = getColumnWidth(col);
                    final int w = getPx(stringWidth);
                    totalColWidth += w;
                    if (settings.isFill()) {
                        totalWeight += settings.getFillWeight();
                    }
                }
            }

            final int remaining = outerWidth - totalColWidth;
            if (remaining > 0 && totalWeight > 0) {
                totalWidth = resizeColumnsAndFill(remaining, totalWeight);
            } else {
                totalWidth = resizeColumns();
            }
        } else {
            totalWidth = resizeColumns();
        }

        super.setTableWidth(totalWidth, Unit.PX);
        emptyTableWidget.getElement().getStyle().setWidth(totalWidth, Unit.PX);
        emptyTableWidget.getElement().getStyle().setHeight(20, Unit.PX);
        loadingTableWidget.getElement().getStyle().setWidth(totalWidth, Unit.PX);
        loadingTableWidget.getElement().getStyle().setHeight(20, Unit.PX);
    }

    private int resizeColumns() {
        int totalWidth = 0;
        for (int i = 0; i < super.getColumnCount(); i++) {
            final Column<R, ?> col = super.getColumn(i);
            final String stringWidth = super.getColumnWidth(col);
            int w = getPx(stringWidth);

            if (w == 0) {
                w = 1;
                super.setColumnWidth(col, w + "px");
            }

            totalWidth += w;
        }
        return totalWidth;
    }

    private int resizeColumnsAndFill(final double remaining, final double totalWeight) {
        int totalWidth = 0;
        final double delta = remaining / totalWeight;
        for (int i = 0; i < super.getColumnCount(); i++) {
            final Column<R, ?> col = super.getColumn(i);
            final String stringWidth = super.getColumnWidth(col);
            int w = getPx(stringWidth);

            final ColSettings settings = colSettings.get(i);
            if (settings != null && settings.isFill()) {
                w = w + (int) (delta * settings.getFillWeight());
                if (w <= 0) {
                    w = 1;
                }
                super.setColumnWidth(col, w, Unit.PX);

            } else {
                if (w <= 0) {
                    w = 1;
                    super.setColumnWidth(col, w + "px");
                }
            }

            totalWidth += w;
        }
        return totalWidth;
    }

    private int getPx(final String pxString) {
        final int index = pxString.toLowerCase().indexOf("px");
        if (index != -1) {
            try {
                return Integer.parseInt(pxString.substring(0, index));
            } catch (final NumberFormatException e) {
                // Ignore.
            }
        }
        return 0;
    }

    @Override
    public void redrawHeaders() {
        super.redrawHeaders();
    }

    public void setHeadingListener(final HeadingListener headingListener) {
        this.headingListener = headingListener;
    }

    public void clearColumnSortList() {
        if (super.getColumnSortList() != null) {
            super.getColumnSortList().clear();
        }
    }

    public void setAllowMove(final boolean allowMove) {
        this.allowMove = allowMove;
    }

    public void setAllowResize(final boolean allowResize) {
        this.allowResize = allowResize;
    }

    public void setAllowHeaderSelection(final boolean allowHeaderSelection) {
        this.allowHeaderSelection = allowHeaderSelection;

        final Node header = getElement().getChild(0);
        final Element e = (Element) header;
        if (allowHeaderSelection) {
            e.addClassName(ALLOW_HEADER_SELECTION);
        } else {
            e.removeClassName(ALLOW_HEADER_SELECTION);
        }
    }
}
