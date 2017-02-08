/*
 *
 * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.data.grid.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.resources.client.ImageResource.ImageOptions;
import com.google.gwt.resources.client.ImageResource.RepeatStyle;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.AbstractCellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.cellview.client.DataGrid.Resources;
import com.google.gwt.user.cellview.client.DataGrid.Style;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent.Handler;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.data.pager.client.Pager;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.button.client.ImageButtonView;
import stroom.widget.util.client.DoubleSelectTest;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SelectionType;

import java.util.ArrayList;
import java.util.List;

public class DataGridViewImpl<R> extends ViewImpl implements DataGridView<R>, NativePreviewHandler {
    public static final int DEFAULT_LIST_PAGE_SIZE = 100;
    public static final int MASSIVE_LIST_PAGE_SIZE = 100000;
    private static volatile DefaultResources resources;
    private final SimplePanel emptyTableWidget = new SimplePanel();
    private final SimplePanel loadingTableWidget = new SimplePanel();
    private final List<ColSettings> colSettings = new ArrayList<>();

    private MultiSelectionModel<R> selectionModel;
    private final DoubleSelectTest doubleClickTest = new DoubleSelectTest();
    private final boolean allowMultiSelect;
    // Required for multiple selection using shift and control key modifiers.
    private R multiSelectStart;

    /**
     * The main DataGrid.
     */
    @UiField(provided = true)
    DataGrid<R> dataGrid;

    /**
     * The pager used to change the range of data.
     */
    @UiField
    Pager pager;

    @UiField
    ButtonPanel buttonPanel;
    private Widget widget;
    private HeadingListener headingListener;
    private HandlerRegistration handlerRegistration;
    private ResizeHandle<R> resizeHandle;
    private MoveHandle<R> moveHandle;
    private Heading moveHeading;

    public DataGridViewImpl() {
        this(false);
    }

    public DataGridViewImpl(final boolean supportsSelection) {
        this(supportsSelection, false, DEFAULT_LIST_PAGE_SIZE, (Binder) GWT.create(Binder.class));
    }

    public DataGridViewImpl(final boolean supportsSelection, final boolean allowMultiSelect) {
        this(supportsSelection, allowMultiSelect, DEFAULT_LIST_PAGE_SIZE, (Binder) GWT.create(Binder.class));
    }

    public DataGridViewImpl(final boolean supportsSelection, final int size) {
        this(supportsSelection, size, (Binder) GWT.create(Binder.class));
    }

    public DataGridViewImpl(final boolean supportsSelection, final int size, final Binder binder) {
        this(supportsSelection, false, size, binder);
    }

    public DataGridViewImpl(final boolean supportsSelection, final boolean allowMultiSelect, final int size, final Binder binder) {
        this.allowMultiSelect = allowMultiSelect;

        if (resources == null) {
            synchronized (DataGridViewImpl.class) {
                if (resources == null) {
                    resources = GWT.create(DefaultResources.class);
                    resources.dataGridStyle().ensureInjected();
                }
            }
        }

        dataGrid = createDataGrid(supportsSelection, size);
        // Sink all mouse events.
        dataGrid.sinkEvents(Event.MOUSEEVENTS);

        // Create the UiBinder.
        if (binder != null) {
            widget = binder.createAndBindUi(this);
            pager.setDisplay(dataGrid);
        } else {
            widget = dataGrid;
        }
    }

    private DataGrid<R> createDataGrid(final boolean supportsSelection, final int size) {
        final DataGrid<R> dataGrid = new DataGrid<R>(size, resources) {
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
                            if (!isBusy() && resizeHandle.update(event, heading)) {
                                resizeHandle.show();
                            }

                            handlerRegistration = Event.addNativePreviewHandler(DataGridViewImpl.this);
                        }
                    }
                }
                super.onBrowserEvent2(event);
            }
        };

        dataGrid.setWidth("100%");

        // Set the message to display when the table is empty.
        dataGrid.setEmptyTableWidget(emptyTableWidget);
        dataGrid.setLoadingIndicator(loadingTableWidget);

        // Remove min height on header.
        final Node header = dataGrid.getElement().getChild(0);
        final Element e = (Element) header;
        e.addClassName(resources.dataGridStyle().dataGridHeaderBackground());
        e.getStyle().setPropertyPx("minHeight", 5);

        if (supportsSelection) {
            final MultiSelectionModelImpl<R> multiSelectionModel = new MultiSelectionModelImpl<R>() {
                @Override
                public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
                    return dataGrid.addHandler(handler, MultiSelectEvent.getType());
                }

                @Override
                protected void fireChange() {
                    MultiSelectEvent.fire(dataGrid, new SelectionType(false, false, allowMultiSelect, false, false));
                }
            };

            dataGrid.setSelectionModel(multiSelectionModel, new MySelectionEventManager(dataGrid));
            selectionModel = multiSelectionModel;
            dataGrid.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);

            dataGrid.getRowContainer().getStyle().setCursor(Cursor.POINTER);
        } else {
            selectionModel = null;
            dataGrid.getRowContainer().getStyle().setCursor(Cursor.DEFAULT);
        }

        return dataGrid;
    }

    @Override
    public void onPreviewNativeEvent(final NativePreviewEvent nativePreviewEvent) {
        final NativeEvent event = nativePreviewEvent.getNativeEvent();

        if (Event.ONMOUSEMOVE == nativePreviewEvent.getTypeInt()) {
            if (!isBusy()) {
                final ResizeHandle<R> resizeHandle = getResizeHandle();
                final MoveHandle<R> moveHandle = getMoveHandle();

                if (resizeHandle.isResizing()) {
                    resizeHandle.resize(event);

                } else {
                    if (moveHandle.isMoving()) {
                        moveHandle.move(event);

                    } else {
                        // Try and start moving the current column.
                        moveHandle.startMove(event);

                        // Hide the resize handle if we are dragging a column.
                        if (moveHandle.isMoving()) {
                            resizeHandle.hide();

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
            }

        } else if (Event.ONMOUSEDOWN == nativePreviewEvent.getTypeInt()) {
            final ResizeHandle<R> resizeHandle = getResizeHandle();
            final MoveHandle<R> moveHandle = getMoveHandle();

            moveHeading = null;

            if (!isBusy()) {
                if (!resizeHandle.isResizing() && MouseHelper.mouseIsOverElement(event, resizeHandle.getElement())) {
                    resizeHandle.startResize(event);

                } else {
                    if ((event.getButton() & NativeEvent.BUTTON_RIGHT) != 0) {
                        if (headingListener != null) {
                            final Heading heading = getHeading(event);
                            headingListener.onContextMenu(event, heading);

                            // Detatch event preview handler.
                            resizeHandle.hide();
                            if (handlerRegistration != null) {
                                handlerRegistration.removeHandler();
                                handlerRegistration = null;
                            }
                        }
                    } else if ((event.getButton() & NativeEvent.BUTTON_LEFT) != 0) {
                        final Heading heading = getHeading(event);
                        moveHeading = heading;
                    }
                }
            }

            // Set the heading that the move handle will use.
            moveHandle.setHeading(event, moveHeading);

        } else if (Event.ONMOUSEUP == nativePreviewEvent.getTypeInt()) {
            if (!isBusy()) {
                final ResizeHandle<R> resizeHandle = getResizeHandle();
                final MoveHandle<R> moveHandle = getMoveHandle();

                if (resizeHandle.isResizing()) {
                    // Stop resizing.
                    resizeHandle.endResize(event);

                    // If the mouse is no longer over a viable handle then
                    // remove it.
                    final Heading heading = getHeading(event);
                    if (!resizeHandle.update(event, heading)) {
                        // Detatch event preview handler.
                        resizeHandle.hide();
                        if (handlerRegistration != null) {
                            handlerRegistration.removeHandler();
                            handlerRegistration = null;
                        }
                    }
                } else if (moveHandle.isMoving()) {
                    // Stop moving column.
                    moveHandle.endMove(event);
                }
            }

            // Set the heading that the move handle will use.
            moveHeading = null;
            moveHandle.setHeading(event, moveHeading);

        } else if (Event.ONMOUSEOUT == nativePreviewEvent.getTypeInt()) {
            final ResizeHandle<R> resizeHandle = getResizeHandle();

            // Hide the resize handle once the mouse moves outside the data
            // grid.
            if (!resizeHandle.isResizing() && moveHeading == null
                    && !MouseHelper.mouseIsOverElement(event, resizeHandle.getElement())) {
                // Detatch event preview handler.
                resizeHandle.hide();
                if (handlerRegistration != null) {
                    handlerRegistration.removeHandler();
                    handlerRegistration = null;
                }
            }
        }
    }

    private boolean isBusy() {
        boolean busy = false;
        if (headingListener != null) {
            busy = headingListener.isBusy();
        }
        return busy;
    }

    private ResizeHandle<R> getResizeHandle() {
        if (resizeHandle == null) {
            resizeHandle = new ResizeHandle<>(this, dataGrid, colSettings, resources);
        }
        return resizeHandle;
    }

    private MoveHandle<R> getMoveHandle() {
        if (moveHandle == null) {
            moveHandle = new MoveHandle<>(this, dataGrid, colSettings, resources);
        }

        return moveHandle;
    }

    private Heading getHeading(final NativeEvent event) {
        final Element target = event.getEventTarget().cast();
        int childIndex = -1;
        Element th = target;
        Element headerRow = null;

        // Get parent th.
        while (th != null && !"th".equalsIgnoreCase(th.getTagName())) {
            th = th.getParentElement();
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

                return new Heading(dataGrid.getElement(), th, childIndex, event.getClientX());
            }
        }

        return null;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void addColumn(final Column<R, ?> column, final String name) {
        addColumn(column, name, 200);
    }

    @Override
    public void addColumn(final Column<R, ?> column, final Header<?> header, final int width) {
        dataGrid.addColumn(column, header);
        setColumnWidth(column, width, Unit.PX);
        colSettings.add(new ColSettings(false, false));
    }

    @Override
    public void addColumn(final Column<R, ?> column, final String name, final int width) {
        dataGrid.addColumn(column, SafeHtmlUtils.fromSafeConstant(name));
        setColumnWidth(column, width, Unit.PX);
        colSettings.add(new ColSettings(false, false));
    }

    @Override
    public void addResizableColumn(final Column<R, ?> column, final String name, final int width) {
        dataGrid.addColumn(column, SafeHtmlUtils.fromSafeConstant(name));
        setColumnWidth(column, width, Unit.PX);
        colSettings.add(new ColSettings(true, true));
    }

    @Override
    public void addResizableColumn(final Column<R, ?> column, final Header<?> header, final int width) {
        dataGrid.addColumn(column, header);
        setColumnWidth(column, width, Unit.PX);
        colSettings.add(new ColSettings(true, true));
    }

    @Override
    public void addEndColumn(final EndColumn<R> column) {
    }

    @Override
    public void removeColumn(final Column<R, ?> column) {
        final int index = dataGrid.getColumnIndex(column);
        if (index != -1) {
            colSettings.remove(index);
            dataGrid.removeColumn(column);
        }
    }

    public void moveColumn(final int fromIndex, final int toIndex) {
        if (headingListener != null) {
            headingListener.moveColumn(fromIndex, toIndex);
        }

        final Column<R, ?> col = dataGrid.getColumn(fromIndex);
        final Header<?> header = dataGrid.getHeader(fromIndex);

        dataGrid.removeColumn(fromIndex);
        final ColSettings settings = colSettings.remove(fromIndex);

        int newIndex = toIndex;
        if (fromIndex < toIndex) {
            newIndex = toIndex - 1;
        }

        dataGrid.insertColumn(newIndex, col, header);
        colSettings.add(newIndex, settings);
    }

    public void resizeColumn(final int index, final int width) {
        if (headingListener != null) {
            headingListener.resizeColumn(index, width);
        }

        final Column<R, ?> column = dataGrid.getColumn(index);
        setColumnWidth(column, width, Unit.PX);
    }

    @Override
    public void setColumnWidth(final Column<R, ?> column, final int width, final Unit unit) {
        dataGrid.setColumnWidth(column, width, unit);

        resizeTableToFitColumns();
    }

    @Override
    public HandlerRegistration addRangeChangeHandler(final Handler handler) {
        return dataGrid.addRangeChangeHandler(handler);
    }

    @Override
    public HandlerRegistration addRowCountChangeHandler(
            final com.google.gwt.view.client.RowCountChangeEvent.Handler handler) {
        return dataGrid.addRowCountChangeHandler(handler);
    }

    @Override
    public int getRowCount() {
        return dataGrid.getRowCount();
    }

    @Override
    public void setRowCount(final int count) {
        dataGrid.setRowCount(count);
    }

    @Override
    public Range getVisibleRange() {
        return dataGrid.getVisibleRange();
    }

    @Override
    public void setVisibleRange(final Range range) {
        dataGrid.setVisibleRange(range);
    }

    @Override
    public boolean isRowCountExact() {
        return dataGrid.isRowCountExact();
    }

    @Override
    public void setRowCount(final int count, final boolean isExact) {
        dataGrid.setRowCount(count, isExact);
    }

    @Override
    public void setVisibleRange(final int start, final int length) {
        dataGrid.setVisibleRange(start, length);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        dataGrid.fireEvent(event);
    }

    @Override
    public HasData<R> getDataDisplay() {
        return dataGrid;
    }

    //    @Override
//    public HandlerRegistration addCellPreviewHandler(
//            final com.google.gwt.view.client.CellPreviewEvent.Handler<R> handler) {
//        return dataGrid.addCellPreviewHandler(handler);
//    }
//
//    @SuppressWarnings("unchecked")
//    @Override
//    public SelectionModel<R> getSelectionModel() {
//        return (SelectionModel<R>) dataGrid.getSelectionModel();
//    }
//
//    @Override
//    public void setSelectionModel(final SelectionModel<? super R> selectionModel) {
//        dataGrid.setSelectionModel(selectionModel);
//    }
//
//    @Override
//    public R getVisibleItem(final int indexOnPage) {
//        return dataGrid.getVisibleItem(indexOnPage);
//    }
//
//    @Override
//    public int getVisibleItemCount() {
//        return dataGrid.getVisibleItemCount();
//    }
//
//    @Override
//    public Iterable<R> getVisibleItems() {
//        return dataGrid.getVisibleItems();
//    }
//
    @Override
    public void setRowData(final int start, final List<? extends R> values) {
        dataGrid.setRowData(start, values);
    }
//
//    @Override
//    public void setVisibleRangeAndClearData(final Range range, final boolean forceRangeChangeEvent) {
//        dataGrid.setVisibleRangeAndClearData(range, forceRangeChangeEvent);
//    }

    @Override
    public ImageButtonView addButton(final String title, final ImageResource enabledImage,
                                     final ImageResource disabledImage, final boolean enabled) {
        return buttonPanel.add(title, enabledImage, disabledImage, enabled);
    }

    @Override
    public GlyphButtonView addButton(final GlyphIcon preset) {
        return buttonPanel.add(preset);
    }

//    @Override
//    public HandlerRegistration addDoubleClickHandler(final DoubleClickEvent.Handler handler) {
//        if (getSelectionModel() != null && getSelectionModel() instanceof MySingleSelectionModel) {
//            return ((MySingleSelectionModel) getSelectionModel()).addDoubleClickHandler(handler);
//        }
//        return null;
//    }

    @Override
    public HandlerRegistration addColumnSortHandler(final ColumnSortEvent.Handler handler) {
        return dataGrid.addColumnSortHandler(handler);
    }

    @Override
    public void resizeTableToFitColumns() {
        int totalWidth = 0;

        for (int i = 0; i < dataGrid.getColumnCount(); i++) {
            final Column<R, ?> col = dataGrid.getColumn(i);
            String stringWidth = dataGrid.getColumnWidth(col);
            int w = 0;
            if (stringWidth != null) {
                final int index = stringWidth.toLowerCase().indexOf("px");
                if (index != -1) {
                    stringWidth = stringWidth.substring(0, index);

                    try {
                        w = Integer.valueOf(stringWidth);
                    } catch (final NumberFormatException e) {
                        // Ignore.
                    }
                }
            }

            if (w == 0) {
                w = 1;
                dataGrid.setColumnWidth(col, w + "px");
            }

            totalWidth += w;
        }

        dataGrid.setTableWidth(totalWidth, Unit.PX);
        emptyTableWidget.getElement().getStyle().setWidth(totalWidth, Unit.PX);
        emptyTableWidget.getElement().getStyle().setHeight(20, Unit.PX);
        loadingTableWidget.getElement().getStyle().setWidth(totalWidth, Unit.PX);
        loadingTableWidget.getElement().getStyle().setHeight(20, Unit.PX);
    }

    @Override
    public void redrawHeaders() {
        dataGrid.redrawHeaders();
    }

    @Override
    public void setHeadingListener(final HeadingListener headingListener) {
        this.headingListener = headingListener;
    }

    @Override
    public void setRefreshing(final boolean refreshing) {
        if (pager != null) {
            pager.setRefreshing(refreshing);
        }
    }

    @Override
    public DefaultResources getResources() {
        return resources;
    }

    @Override
    public TableRowElement getRowElement(final int row) {
        return dataGrid.getRowElement(row);
    }

    public interface Binder extends UiBinder<Widget, DataGridViewImpl<?>> {
    }

    public interface DefaultStyle extends Style {
        String dataGridHeaderBackground();

        String dataGridHeaderSelected();

        String dataGridResizableCell();

        String dataGridCellWrapText();

        String resizeHandle();

        String resizeLine();

        String resizeGlass();

        String moveHandle();

        String moveLine();

        String moveGlass();
    }

    public interface HeadingListener {
        void onContextMenu(NativeEvent event, Heading heading);

        void moveColumn(int fromIndex, int toIndex);

        void resizeColumn(int colIndex, int size);

        boolean isBusy();
    }

    public interface DefaultResources extends Resources {
        String DEFAULT_CSS = "stroom/data/grid/client/DataGrid.css";

        /**
         * The styles used in this widget.
         */
        @Override
        @Source(DEFAULT_CSS)
        DefaultStyle dataGridStyle();

        /**
         * The background image applied to headers and footers.
         */
        @ImageOptions(repeatStyle = RepeatStyle.Horizontal)
        ImageResource header();

        @ImageOptions(repeatStyle = RepeatStyle.Horizontal)
        ImageResource headerHighlight();
    }

    public static class ColSettings {
        private final boolean resizable;
        private final boolean movable;

        public ColSettings(final boolean resizable, final boolean movable) {
            this.resizable = resizable;
            this.movable = movable;
        }

        public boolean isResizable() {
            return resizable;
        }

        public boolean isMovable() {
            return movable;
        }
    }

    public static class Heading {
        private final Element tableElement;
        private final Element element;
        private final int colIndex;
        private final int initialX;

        public Heading(final Element tableElement, final Element element, final int colIndex, final int initialX) {
            this.tableElement = tableElement;
            this.element = element;
            this.colIndex = colIndex;
            this.initialX = initialX;
        }

        public Element getTableElement() {
            return tableElement;
        }

        public Element getElement() {
            return element;
        }

        public int getColIndex() {
            return colIndex;
        }

        public int getInitialX() {
            return initialX;
        }
    }

    private class MySelectionEventManager extends AbstractCellTable.CellTableKeyboardSelectionHandler<R> {
        MySelectionEventManager(AbstractCellTable<R> table) {
            super(table);
        }

        @Override
        public void onCellPreview(CellPreviewEvent<R> event) {
            final NativeEvent nativeEvent = event.getNativeEvent();
            final String type = nativeEvent.getType();

            if ("mousedown".equals(type)) {
                // We set focus here so that we can use the keyboard to navigate once we have focus.
                dataGrid.setFocus(true);

                final int x = nativeEvent.getClientX();
                final int y = nativeEvent.getClientY();
                final int button = nativeEvent.getButton();

//                if ((button & NativeEvent.BUTTON_RIGHT) != 0) {
//                    dataGrid.setKeyboardSelectedRow(event.getIndex());
//                    ShowExplorerMenuEvent.fire(ExplorerTree.this, selectionModel, x, y);
//                } else if ((button & NativeEvent.BUTTON_LEFT) != 0) {
                final R selectedItem = event.getValue();
                if (selectedItem != null && (button & NativeEvent.BUTTON_LEFT) != 0) {
                    final boolean doubleClick = doubleClickTest.test(selectedItem);
                    doSelect(selectedItem, new SelectionType(doubleClick, false, allowMultiSelect, event.getNativeEvent().getCtrlKey(), event.getNativeEvent().getShiftKey()));
                    super.onCellPreview(event);
                }
//                }
//            } else if ("keydown".equals(type)) {
//                final int keyCode = nativeEvent.getKeyCode();
//                onKeyDown(keyCode);
//                super.onCellPreview(event);
            } else {
                super.onCellPreview(event);
            }
        }
    }

    protected void doSelect(final R selection, final SelectionType selectionType) {
        if (selection == null) {
            multiSelectStart = null;
            selectionModel.clear();
        } else if (selectionType.isAllowMultiSelect() && selectionType.isShiftPressed() && multiSelectStart != null) {
            // If control isn't pressed as well as shift then we are selecting a new range so clear.
            if (!selectionType.isControlPressed()) {
                selectionModel.clear();
            }

            List<R> rows = dataGrid.getVisibleItems();
            final int index1 = rows.indexOf(multiSelectStart);
            final int index2 = rows.indexOf(selection);
            if (index1 != -1 && index2 != -1) {
                final int start = Math.min(index1, index2);
                final int end = Math.max(index1, index2);
                for (int i = start; i <= end; i++) {
                    selectionModel.setSelected(rows.get(i), true);
                }
            } else if (selectionType.isControlPressed()) {
                multiSelectStart = selection;
                selectionModel.setSelected(selection, !selectionModel.isSelected(selection));
            } else {
                multiSelectStart = selection;
                selectionModel.setSelected(selection);
            }
        } else if (selectionType.isAllowMultiSelect() && selectionType.isControlPressed()) {
            multiSelectStart = selection;
            selectionModel.setSelected(selection, !selectionModel.isSelected(selection));
        } else {
            multiSelectStart = selection;
            selectionModel.setSelected(selection);
        }

        MultiSelectEvent.fire(dataGrid, selectionType);
    }

//    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
//        return dataGrid.addHandler(handler, MultiSelectEvent.getType());
//    }

    @Override
    public MultiSelectionModel<R> getSelectionModel() {
        return selectionModel;
    }
}
