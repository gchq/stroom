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
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

public class MoveHandle<R> extends Widget {

    public static final int LINE_WIDTH = 2;
    public static final int HALF_LINE_WIDTH = LINE_WIDTH / 2;

    private final MyDataGrid<R> dataGrid;
    private final List<ColSettings> colSettings;
    private final DefaultResources resources;

    private Heading heading;

    private Element targetLine;

    private int offset;
    private int insertPos = -1;
    private boolean moving;
    private Glass glass;

    public MoveHandle(final MyDataGrid<R> dataGrid,
                      final List<ColSettings> colSettings,
                      final DefaultResources resources) {
        this.dataGrid = dataGrid;
        this.colSettings = colSettings;
        this.resources = resources;

        final Element element = DOM.createDiv();
        setElement(element);

        getElement().setClassName(resources.dataGridStyle().moveHandle());

        final Style style = getElement().getStyle();
        style.setHeight(dataGrid.getElement().getOffsetHeight(), Unit.PX);
    }

    public void setHeading(final NativeEvent event, final Heading heading) {
        reset();

        if (heading != null) {
            final int childIndex = heading.getColIndex();
            final ColSettings settings = colSettings.get(childIndex);

            if (settings.isMovable()) {
                // See if we can move this column.
                final Style style = getElement().getStyle();
                style.setLeft(heading.getElement().getAbsoluteLeft(), Unit.PX);
                style.setTop(heading.getElement().getAbsoluteTop(), Unit.PX);
                style.setWidth(heading.getElement().getOffsetWidth(), Unit.PX);
                offset = heading.getInitialX() - heading.getElement().getAbsoluteLeft();

                this.heading = heading;
            }
        }
    }

    public boolean isDragThresholdExceeded(final NativeEvent event) {
        if (heading != null && Math.abs(event.getClientX() - heading.getInitialX()) > 10) {
            return true;
        }
        return false;
    }

    public void startMove(final NativeEvent event) {
        Event.setCapture(getElement());
        showGlass();
        moving = true;
    }

    public void endMove(final NativeEvent event) {
        final int pos = insertPos;
        if (pos >= 0 && pos != heading.getColIndex()) {
            dataGrid.moveColumn(heading.getColIndex(), pos);
        }

        reset();
    }

    private void reset() {
        heading = null;
        moving = false;
        insertPos = -1;

        hideTargetLine();
        hide();
        hideGlass();

        Event.releaseCapture(getElement());
    }

    public void move(final NativeEvent event) {
        if (heading != null && moving) {
            final Style style = getElement().getStyle();
            style.setVisibility(Visibility.VISIBLE);
            final int left = event.getClientX() - offset;
            style.setLeft(left, Unit.PX);
            style.setTop(heading.getElement().getAbsoluteTop(), Unit.PX);
            style.setWidth(heading.getElement().getOffsetWidth(), Unit.PX);
            style.setHeight(dataGrid.getElement().getOffsetHeight(), Unit.PX);

            final Element parent = heading.getElement().getParentElement();

            insertPos = parent.getChildCount();
            for (int i = 0; i < parent.getChildCount(); i++) {
                final Element child = parent.getChild(i).cast();

                if (i == heading.getColIndex() + 1) {
                    // Don't allow the user to insert an item after itself as
                    // this doesn't make sense.
                    if (child.getAbsoluteLeft() > event.getClientX()) {
                        insertPos = i - 1;
                        break;
                    }

                } else if (i == parent.getChildCount() - 1 && heading.getColIndex() == parent.getChildCount() - 1) {
                    // Don't allow the user to insert an item after itself as
                    // this doesn't make sense.
                    insertPos = i;
                    break;

                } else if (child.getAbsoluteLeft() + (child.getScrollWidth() / 2) >= event.getClientX()) {
                    insertPos = i;
                    break;
                }
            }

            // Constrain to the first movable column.
            int firstMovableCol = 0;
            for (int i = 0; i < colSettings.size(); i++) {
                final ColSettings settings = colSettings.get(i);
                if (settings.isMovable()) {
                    firstMovableCol = i;
                    break;
                }
            }
            insertPos = Math.max(insertPos, firstMovableCol);

            // Show this handle if it isn't already showing.
            show();

            // Show the target line.
            if (insertPos == 0) {
                showTargetLine(dataGrid.getAbsoluteLeft(), dataGrid.getAbsoluteTop(), dataGrid.getOffsetHeight());
            } else {
                final Element child = parent.getChild(insertPos - 1).cast();
                showTargetLine(child.getAbsoluteRight() - HALF_LINE_WIDTH, dataGrid.getAbsoluteTop(),
                        dataGrid.getOffsetHeight());
            }
        }
    }

    private void showGlass() {
        if (glass == null) {
            glass = new Glass(resources.dataGridStyle().moveGlass());
        }
        glass.show();
    }

    private void hideGlass() {
        if (glass != null) {
            glass.hide();
        }
    }

    private void showTargetLine(final int left, final int top, final int height) {
        if (targetLine == null) {
            targetLine = DOM.createDiv();
            targetLine.setClassName(resources.dataGridStyle().moveLine());
        }

        targetLine.getStyle().setLeft(left, Unit.PX);
        targetLine.getStyle().setTop(top, Unit.PX);
        targetLine.getStyle().setHeight(height, Unit.PX);

        if (!targetLine.hasParentElement()) {
            Document.get().getBody().appendChild(targetLine);
        }
    }

    private void hideTargetLine() {
        if (targetLine != null) {
            targetLine.removeFromParent();
        }
    }

    public boolean isMoving() {
        return moving;
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
