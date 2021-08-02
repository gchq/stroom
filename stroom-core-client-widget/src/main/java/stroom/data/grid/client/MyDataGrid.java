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

import stroom.hyperlink.client.HyperlinkEvent;
import stroom.widget.tab.client.view.GlobalResizeObserver;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
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
    private final SimplePanel emptyTableWidget = new SimplePanel();
    private final SimplePanel loadingTableWidget = new SimplePanel();
    private final List<ColSettings> colSettings = new ArrayList<>();

    private HeadingListener headingListener;
    private HandlerRegistration handlerRegistration;
    private ResizeHandle<R> resizeHandle;
    private MoveHandle<R> moveHandle;
    private Heading moveHeading;

    public MyDataGrid() {
        this(DEFAULT_LIST_PAGE_SIZE);
    }

    public MyDataGrid(final int size) {
        super(size, RESOURCES);

        // Set the message to display when the table is empty.
        setEmptyTableWidget(emptyTableWidget);
        setLoadingIndicator(loadingTableWidget);

        // Remove min height on header.
        final Node header = getElement().getChild(0);
        final Element e = (Element) header;
        e.addClassName(RESOURCES.dataGridStyle().dataGridHeaderBackground());
        e.getStyle().setPropertyPx("minHeight", 5);

        getRowContainer().getStyle().setCursor(Cursor.DEFAULT);

        // Sink all mouse events.
        sinkEvents(Event.MOUSEEVENTS);
    }

    public MultiSelectionModelImpl<R> addDefaultSelectionModel(final boolean allowMultiSelect) {
        final MultiSelectionModelImpl selectionModel = new MultiSelectionModelImpl<>(this);
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
                    if (!isBusy() && resizeHandle.update(event, heading)) {
                        resizeHandle.show();
                    }

                    handlerRegistration = Event.addNativePreviewHandler(this);
                }
            }
        }
        super.onBrowserEvent2(event);
    }

    @Override
    public void onPreviewNativeEvent(final NativePreviewEvent nativePreviewEvent) {
        GWT.log("onPreviewNativeEvent");

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
            if (MouseUtil.isPrimary(event)) {
                final ResizeHandle<R> resizeHandle = getResizeHandle();
                final MoveHandle<R> moveHandle = getMoveHandle();

                moveHeading = null;

                if (!isBusy()) {
                    final Heading heading = getHeading(event);
                    if (headingListener != null) {
                        headingListener.onMouseDown(event, heading);
                    }

                    if (!resizeHandle.isResizing()
                            && MouseHelper.mouseIsOverElement(event, resizeHandle.getElement())) {
                        resizeHandle.startResize(event);

                    } else {
                        moveHeading = heading;
                    }
                }

                // Set the heading that the move handle will use.
                moveHandle.setHeading(event, moveHeading);
            }

        } else if (Event.ONMOUSEUP == nativePreviewEvent.getTypeInt()) {
            if (MouseUtil.isPrimary(event)) {
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
                            // Detach event preview handler.
                            resizeHandle.hide();
                            if (handlerRegistration != null) {
                                handlerRegistration.removeHandler();
                                handlerRegistration = null;
                            }
                        }
                    } else if (moveHandle.isMoving()) {
                        // Stop moving column.
                        moveHandle.endMove(event);
                    } else {
                        if (headingListener != null) {
                            final Heading heading = getHeading(event);
                            headingListener.onMouseUp(event, heading);

                            // Detach event preview handler.
                            resizeHandle.hide();
                        }
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
            if (!resizeHandle.isResizing() && moveHeading == null
                    && !MouseHelper.mouseIsOverElement(event, resizeHandle.getElement())) {
                // Detach event preview handler.
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
//            busy = headingListener.isBusy();
        }
        return busy;
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
        int childIndex;
        Element th = target;
        Element headerRow;

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

                return new Heading(getElement(), th, childIndex, event.getClientX());
            }
        }

        return null;
    }

    @Override
    public void addColumn(final Column<R, ?> column, final String name) {
        addColumn(column, name, 200);
    }

    public void addColumn(final Column<R, ?> column, final Header<?> header, final int width) {
        super.addColumn(column, header);
        setColumnWidth(column, width, Unit.PX);
        colSettings.add(new ColSettings(false, false));
    }

    public void addColumn(final Column<R, ?> column, final String name, final int width) {
        super.addColumn(column, SafeHtmlUtils.fromSafeConstant(name));
        setColumnWidth(column, width, Unit.PX);
        colSettings.add(new ColSettings(false, false));
    }

    public void addResizableColumn(final Column<R, ?> column, final String name, final int width) {
        super.addColumn(column, SafeHtmlUtils.fromSafeConstant(name));
        setColumnWidth(column, width, Unit.PX);
        colSettings.add(new ColSettings(true, true));
    }

    public void addResizableColumn(final Column<R, ?> column, final Header<?> header, final int width) {
        super.addColumn(column, header);
        setColumnWidth(column, width, Unit.PX);
        colSettings.add(new ColSettings(true, true));
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

    public void resizeTableToFitColumns() {
        int totalWidth = 0;

        for (int i = 0; i < super.getColumnCount(); i++) {
            final Column<R, ?> col = super.getColumn(i);
            String stringWidth = super.getColumnWidth(col);
            int w = 0;
            if (stringWidth != null) {
                final int index = stringWidth.toLowerCase().indexOf("px");
                if (index != -1) {
                    stringWidth = stringWidth.substring(0, index);

                    try {
                        w = Integer.parseInt(stringWidth);
                    } catch (final NumberFormatException e) {
                        // Ignore.
                    }
                }
            }

            if (w == 0) {
                w = 1;
                super.setColumnWidth(col, w + "px");
            }

            totalWidth += w;
        }

        super.setTableWidth(totalWidth, Unit.PX);
        emptyTableWidget.getElement().getStyle().setWidth(totalWidth, Unit.PX);
        emptyTableWidget.getElement().getStyle().setHeight(20, Unit.PX);
        loadingTableWidget.getElement().getStyle().setWidth(totalWidth, Unit.PX);
        loadingTableWidget.getElement().getStyle().setHeight(20, Unit.PX);
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
}
