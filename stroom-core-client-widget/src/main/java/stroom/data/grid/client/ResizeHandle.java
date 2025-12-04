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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

public class ResizeHandle<R> extends Widget {

    private static final int HANDLE_WIDTH = 10;
    private static final int HALF_HANDLE_WIDTH = HANDLE_WIDTH / 2;
    private static final int LINE_WIDTH = 2;
    private static final int HALF_LINE_WIDTH = LINE_WIDTH / 2;
    private static final int MIN_COL_WIDTH = 30;

    private final MyDataGrid<R> dataGrid;
    private final List<ColSettings> colSettings;
    private final DefaultResources resources;

    private Glass glass;
    private Element resizeLine;
    private boolean resizing;
    private Element headerRow;
    private int colNo;
    private int offset;
    private int startPos;
    private int minPos;

    public ResizeHandle(final MyDataGrid<R> dataGrid,
                        final List<ColSettings> colSettings,
                        final DefaultResources resources) {
        this.dataGrid = dataGrid;
        this.colSettings = colSettings;
        this.resources = resources;

        final Element element = DOM.createDiv();
        element.setClassName(resources.dataGridStyle().resizeHandle());

        setElement(element);
    }

    public boolean update(final NativeEvent event, final Heading heading) {
        if (heading != null) {
            int childIndex = heading.getColIndex();
            final Element th = heading.getElement();
            final Element headerRow = th.getParentElement();

            // See if we are allowed to resize the previous column.
            boolean canResizePreviousColumn = false;
            if (childIndex > 0) {
                final ColSettings settings = colSettings.get(childIndex - 1);
                canResizePreviousColumn = settings.isResizable();
            }
            // See if we can resize this column.
            final ColSettings settings = colSettings.get(childIndex);
            final boolean canResizeThisColumn = settings.isResizable();

            // If we can't resize this column or the previous one then return no
            // handle as no resize will be possible.
            if (canResizeThisColumn || canResizePreviousColumn) {
                final com.google.gwt.dom.client.Style resizeHandleStyle = getElement().getStyle();
                setHeaderRow(headerRow);

                final int clientX = event.getClientX();
                final int diffLeft = clientX - th.getAbsoluteLeft();
                final int diffRight = th.getAbsoluteRight() - clientX;

                Element resizeHeading = th;
                if (!canResizeThisColumn ||
                        (diffLeft <= ResizeHandle.HANDLE_WIDTH && diffLeft < diffRight && canResizePreviousColumn)) {
                    // Show the resize handle on the left.
                    resizeHeading = headerRow.getChild(childIndex - 1).cast();
                    childIndex--;
                }

                minPos = resizeHeading.getAbsoluteLeft() - HALF_HANDLE_WIDTH + MIN_COL_WIDTH;
                resizeHandleStyle.setLeft(resizeHeading.getAbsoluteRight() - ResizeHandle.HALF_HANDLE_WIDTH, Unit.PX);
                setColNo(childIndex);

                resizeHandleStyle.setTop(resizeHeading.getAbsoluteTop(), Unit.PX);
                resizeHandleStyle.setHeight(resizeHeading.getOffsetHeight(), Unit.PX);

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

    public int getDiff(final NativeEvent event) {
        return event.getClientX() - startPos;
    }

    public void endResize(final int diff) {
        if (diff != 0) {
            resize(startPos + diff - offset);

            final Element col = headerRow.getChild(colNo).cast();
            final int existingWidth = col.getOffsetWidth();

            if (existingWidth > 0) {
                final ColSettings settings = colSettings.get(colNo);
                if (settings.isResizable()) {
                    final int newWidth = Math.max(existingWidth + diff, MIN_COL_WIDTH);
                    if (newWidth != existingWidth) {
                        // DataGrid allows you to resize a column by index
                        // rather than getting the column first but if you do
                        // this then the grid will not remember to column sizes
                        // if you subsequently move a column. To avoid this
                        // problem I get the column first and then use the
                        // setColumnWidth() method that takes a column instead
                        // of an index. The GWT control is not behaving consistently.
                        dataGrid.resizeColumn(colNo, newWidth);
                    }
                }

// TODO: OLD CODE THAT REDUCED SIZE OF PREVIOUS COLS TOO IF NEEDED.
//  KEPT HERE IN CASE WE WANT TO GO BACK TO IT.

//            for (int i = colNo; i >= 0; i--) {
//                final Element col = headerRow.getChild(i).cast();
//                final int existingWidth = col.getOffsetWidth();
//                int newWidth = existingWidth;
//
//                final ColSettings settings = colSettings.get(i);
//                if (settings.isResizable()) {
//                    newWidth = Math.max(existingWidth + diff, 30);
//                    if (newWidth != existingWidth) {
//                        // DataGrid allows you to resize a column by index
//                        // rather than getting the column first but if you do
//                        // this then the grid will not remember to column sizes
//                        // if you subsequently move a column. To avoid this
//                        // problem I get the column first and then use the
//                        // setColumnWidth() method that takes a column instead
//                        // of an index. The GWT control is not behaving consistently.
//                        dataGrid.resizeColumn(i, newWidth);
//                    }
//                }
//
//                diff = diff + existingWidth - newWidth;
//
//                // If there is no remaining negative diff to take off the
//                // previous column then exit.
//                if (diff >= 0) {
//                    break;
//                }
//            }


                dataGrid.resizeTableToFitColumns();
            }
        }

        resizing = false;
        glass.hide();
        resizeLine.removeFromParent();

        Event.releaseCapture(getElement());
    }

    public int getExistingWidth() {
        final Element col = headerRow.getChild(colNo).cast();
        return col.getOffsetWidth();
    }

    public void resize(final NativeEvent event) {
        final int left = event.getClientX() - offset;
        resize(left);
    }

    private void resize(int left) {
        if (resizing) {
            left = Math.max(minPos, left);
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

    public int getColNo() {
        return colNo;
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
