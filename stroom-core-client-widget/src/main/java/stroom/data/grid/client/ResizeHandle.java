/*
 * Copyright 2016 Crown Copyright
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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;
import stroom.data.grid.client.DataGridViewImpl.ColSettings;
import stroom.data.grid.client.DataGridViewImpl.DefaultResources;
import stroom.data.grid.client.DataGridViewImpl.Heading;

import java.util.List;

public class ResizeHandle<R> extends Widget {
    public static final int HANDLE_WIDTH = 10;
    public static final int HALF_HANDLE_WIDTH = HANDLE_WIDTH / 2;
    public static final int LINE_WIDTH = 2;
    public static final int HALF_LINE_WIDTH = LINE_WIDTH / 2;

    private final DataGridViewImpl<R> dataGridView;
    private final DataGrid<R> dataGrid;
    private final List<ColSettings> colSettings;
    private final DefaultResources resources;

    private Glass glass;
    private Element resizeLine;
    private boolean resizing;
    private Element headerRow;
    private int colNo;
    private int offset;
    private int startPos;

    public ResizeHandle(final DataGridViewImpl<R> dataGridView, final DataGrid<R> dataGrid,
            final List<ColSettings> colSettings, final DefaultResources resources) {
        this.dataGrid = dataGrid;
        this.dataGridView = dataGridView;
        this.colSettings = colSettings;
        this.resources = resources;

        final Element element = DOM.createDiv();
        element.setClassName(resources.dataGridStyle().resizeHandle());

        setElement(element);
    }

    public boolean update(final NativeEvent event, final Heading heading) {
        if (heading != null) {
            final int childIndex = heading.getColIndex();
            final Element th = heading.getElement();
            final Element headerRow = th.getParentElement();

            // See if we are allowed to resize the previous column.
            boolean canResizePreviousColumn = false;
            if (childIndex > 0) {
                final ColSettings settings = colSettings.get(childIndex - 1);
                canResizePreviousColumn = settings.isResizable();
            }
            // See if we can resize this column.
            boolean canResizeThisColumn = false;
            final ColSettings settings = colSettings.get(childIndex);
            canResizeThisColumn = settings.isResizable();

            // If we can't resize this column or the previous one then return no
            // handle as no resize will be possible.
            if (canResizeThisColumn || canResizePreviousColumn) {
                final com.google.gwt.dom.client.Style resizeHandleStyle = getElement().getStyle();
                setHeaderRow(headerRow);

                final int clientX = event.getClientX();
                final int diffLeft = clientX - th.getAbsoluteLeft();
                final int diffRight = th.getAbsoluteRight() - clientX;

                if (diffLeft <= ResizeHandle.HANDLE_WIDTH && diffLeft < diffRight && canResizePreviousColumn) {
                    // Show the resize handle on the left.
                    resizeHandleStyle.setLeft(th.getAbsoluteLeft() - ResizeHandle.HALF_HANDLE_WIDTH, Unit.PX);
                    setColNo(childIndex - 1);
                } else if (canResizeThisColumn) {
                    // Show the resize handle on the right.
                    resizeHandleStyle.setLeft(th.getAbsoluteRight() - ResizeHandle.HALF_HANDLE_WIDTH, Unit.PX);
                    setColNo(childIndex);
                } else if (canResizePreviousColumn) {
                    // Show the resize handle on the left.
                    resizeHandleStyle.setLeft(th.getAbsoluteLeft() - ResizeHandle.HALF_HANDLE_WIDTH, Unit.PX);
                    setColNo(childIndex - 1);
                }

                resizeHandleStyle.setTop(th.getAbsoluteTop(), Unit.PX);
                resizeHandleStyle.setHeight(th.getOffsetHeight(), Unit.PX);

                return true;
            }
        }

        return false;
    }

    public void startResize(final NativeEvent event) {
        Event.setCapture(getElement());

        startPos = event.getClientX();
        offset = startPos - getAbsoluteLeft();

        if (resizeLine == null) {
            resizeLine = DOM.createDiv();
            resizeLine.setClassName(resources.dataGridStyle().resizeLine());
        }

        resizeLine.getStyle().setLeft(getAbsoluteLeft() + HALF_HANDLE_WIDTH - HALF_LINE_WIDTH, Unit.PX);
        resizeLine.getStyle().setTop(getElement().getAbsoluteBottom(), Unit.PX);
        resizeLine.getStyle().setHeight(dataGrid.getOffsetHeight() - getOffsetHeight(), Unit.PX);

        Document.get().getBody().appendChild(resizeLine);

        if (glass == null) {
            glass = new Glass(resources.dataGridStyle().resizeGlass());
        }
        glass.show();
        resizing = true;
    }

    public void endResize(final NativeEvent event) {
        int diff = event.getClientX() - startPos;

        if (diff != 0) {
            for (int i = colNo; i >= 0; i--) {
                final Element col = headerRow.getChild(i).cast();
                final int existingWidth = col.getOffsetWidth();
                int newWidth = existingWidth;

                final ColSettings settings = colSettings.get(i);
                if (settings.isResizable()) {
                    newWidth = Math.max(existingWidth + diff, 30);
                    if (newWidth != existingWidth) {
                        // DataGrid allows you to resize a column by index
                        // rather than getting the column first but if you do
                        // this then the grid will not remember to column sizes
                        // if you subsequently move a column. To avoid this
                        // problem I get the column first and then use the
                        // setColumnWidth() method that takes a column instead
                        // of an index. The GWT control is not behaving consistently.
                        dataGridView.resizeColumn(i, newWidth);
                    }
                }

                diff = diff + existingWidth - newWidth;

                // If there is no remaining negative diff to take off the
                // previous column then exit.
                if (diff >= 0) {
                    break;
                }
            }

            dataGridView.resizeTableToFitColumns();
        }

        resizing = false;
        glass.hide();
        resizeLine.removeFromParent();

        Event.releaseCapture(getElement());
    }

    public void resize(final NativeEvent event) {
        if (resizing) {
            final int left = event.getClientX() - offset;
            getElement().getStyle().setLeft(left, Unit.PX);
            resizeLine.getStyle().setLeft(left + HALF_HANDLE_WIDTH - HALF_LINE_WIDTH, Unit.PX);
        }
    }

    private void setHeaderRow(final Element headerRow) {
        this.headerRow = headerRow;
    }

    private void setColNo(final int colNo) {
        this.colNo = colNo;
    }

    public boolean isResizing() {
        return resizing;
    }

    public void show() {
        if (!isAttached()) {
            RootPanel.get().add(this);
        }
    }

    public void hide() {
        if (isAttached()) {
            RootPanel.get().remove(this);
        }
    }
}
