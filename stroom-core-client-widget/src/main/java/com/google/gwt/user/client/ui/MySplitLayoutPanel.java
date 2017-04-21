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

/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.user.client.ui;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiConstructor;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;

/**
 * A panel that adds user-positioned splitters between each of its child
 * widgets.
 *
 * <p>
 * This panel is used in the same way as {@link DockLayoutPanel}, except that
 * its children's sizes are always specified in {@link Unit#PX} units, and each
 * pair of child widgets has a splitter between them that the user can drag.
 * </p>
 *
 * <p>
 * This widget will <em>only</em> work in standards mode, which requires that
 * the HTML page in which it is run have an explicit &lt;!DOCTYPE&gt;
 * declaration.
 * </p>
 *
 * <h3>CSS Style Rules</h3>
 * <ul class='css'> <li>.gwt-SplitLayoutPanel { the panel itself }</li>
 * <li>.gwt-SplitLayoutPanel .gwt-SplitLayoutPanel-HDragger { horizontal dragger
 * }</li> <li>.gwt-SplitLayoutPanel .gwt-SplitLayoutPanel-VDragger { vertical
 * dragger } </li> </ul>
 *
 * <p> <h3>Example</h3>
 * {@example com.google.gwt.examples.SplitLayoutPanelExample} </p>
 */
public class MySplitLayoutPanel extends DockLayoutPanel {
    class HSplitter extends Splitter {
        public HSplitter(final Widget target, final boolean reverse, final int index) {
            super(target, reverse, index, false);
            getElement().getStyle().setPropertyPx("width", SPLITTER_SIZE);
            setStyleName("gwt-SplitLayoutPanel-HDragger");
        }

        @Override
        protected int getAbsolutePosition() {
            return getAbsoluteLeft();
        }

        @Override
        protected int getEventPosition(final Event event) {
            return event.getClientX();
        }

        @Override
        protected int getTargetPosition() {
            return target.getAbsoluteLeft();
        }

        @Override
        protected int getTargetSize() {
            return target.getOffsetWidth();
        }
    }

    abstract class Splitter extends Widget {
        protected final Widget target;

        private int offset;
        private boolean mouseDown;
        private ScheduledCommand layoutCommand;

        private final boolean reverse;
        private final boolean vertical;
        private final int index;
        private int minSize;

        public Splitter(final Widget target, final boolean reverse, final int index, final boolean vertical) {
            this.target = target;
            this.reverse = reverse;
            this.index = index;
            this.vertical = vertical;

            setElement(Document.get().createDivElement());
            sinkEvents(Event.ONMOUSEDOWN | Event.ONMOUSEUP | Event.ONMOUSEMOVE | Event.ONDBLCLICK);
        }

        @Override
        public void onBrowserEvent(final Event event) {
            switch (event.getTypeInt()) {
            case Event.ONMOUSEDOWN:
                mouseDown = true;
                offset = getEventPosition(event) - getAbsolutePosition();
                Event.setCapture(getElement());
                event.preventDefault();

                startResizing(vertical);
                break;

            case Event.ONMOUSEUP:
                mouseDown = false;
                Event.releaseCapture(getElement());
                event.preventDefault();

                stopResizing();
                break;

            case Event.ONMOUSEMOVE:
                if (mouseDown) {
                    int size;
                    if (reverse) {
                        size = getTargetPosition() + getTargetSize() - getEventPosition(event) - offset;
                    } else {
                        size = getEventPosition(event) - getTargetPosition() - offset;
                    }

                    int clientSize = 0;
                    final int min = 0;
                    int max = -min;
                    if (vertical) {
                        clientSize = getElement().getParentElement().getParentElement().getOffsetHeight();
                        max += clientSize - getOffsetHeight();
                    } else {
                        clientSize = getElement().getParentElement().getParentElement().getOffsetWidth();
                        max += clientSize - getOffsetWidth();
                    }
                    if (size > max) {
                        size = max;
                    } else if (size < min) {
                        size = min;
                    }

                    if (vertical) {
                        if (vSplits != null && vSplits.length > index) {
                            vSplits[index] = (double) size / (double) clientSize;
                        }
                    } else {
                        if (hSplits != null && hSplits.length > index) {
                            hSplits[index] = (double) size / (double) clientSize;
                        }
                    }

                    setAssociatedWidgetSize(size);
                    event.preventDefault();
                }
                break;
            }
        }

        public void setMinSize(final int minSize) {
            this.minSize = minSize;
            final LayoutData layout = (LayoutData) target.getLayoutData();

            // Try resetting the associated widget's size, which will enforce
            // the new
            // minSize value.
            setAssociatedWidgetSize((int) layout.size);
        }

        protected abstract int getAbsolutePosition();

        protected abstract int getEventPosition(Event event);

        protected abstract int getTargetPosition();

        protected abstract int getTargetSize();

        private void setAssociatedWidgetSize(int size) {
            if (size < minSize) {
                size = minSize;
            }

            final LayoutData layout = (LayoutData) target.getLayoutData();
            if (size == layout.size) {
                return;
            }

            layout.size = size;

            // Defer actually updating the layout, so that if we receive many
            // mouse events before layout/paint occurs, we'll only update once.
            if (layoutCommand == null) {
                layoutCommand = () -> {
                    layoutCommand = null;
                    forceLayout();
                };
                Scheduler.get().scheduleDeferred(layoutCommand);
            }
        }
    }

    class VSplitter extends Splitter {
        public VSplitter(final Widget target, final boolean reverse, final int index) {
            super(target, reverse, index, true);
            getElement().getStyle().setPropertyPx("height", SPLITTER_SIZE);
            setStyleName("gwt-SplitLayoutPanel-VDragger");
        }

        @Override
        protected int getAbsolutePosition() {
            return getAbsoluteTop();
        }

        @Override
        protected int getEventPosition(final Event event) {
            return event.getClientY();
        }

        @Override
        protected int getTargetPosition() {
            return target.getAbsoluteTop();
        }

        @Override
        protected int getTargetSize() {
            return target.getOffsetHeight();
        }
    }

    private static final int SPLITTER_SIZE = 4;

//    private ScheduledCommand delayedLayoutCommand;
//    private ScheduledCommand resizeCommand;
//    private ScheduledCommand adjustSplitSizesCommand;
    private double[] hSplits;
    private double[] vSplits;
    private int hSplitterIndex = 0;
    private int vSplitterIndex = 0;
    private boolean isResizing;

    @UiConstructor
    public MySplitLayoutPanel() {
        super(Unit.PX);
        setStyleName("gwt-SplitLayoutPanel");
        // doDelayedLayout();
    }

    private void doDelayedLayout() {
//        if (delayedLayoutCommand == null) {
//            delayedLayoutCommand = new ScheduledCommand() {
//                @Override
//                public void execute() {
//                    delayedLayoutCommand = null;
                    onResize();
//                }
//            };
//            Scheduler.get().scheduleDeferred(delayedLayoutCommand);
//        }
    }

    public void setHSplits(final String str) {
        hSplits = parseSplits(str);
    }

    public void setVSplits(final String str) {
        vSplits = parseSplits(str);
    }

    private double[] parseSplits(final String str) {
        double[] result = null;

        if (str != null) {
            final String[] arr = str.trim().split(",");
            if (arr.length > 0) {
                result = new double[arr.length];
                for (int i = 0; i < arr.length; i++) {
                    result[i] = Double.parseDouble(arr[i].trim());
                }
            }
        }

        return result;
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        doDelayedLayout();
    }

    @Override
    public void onResize() {
        if (!isResizing) {
            if ((hSplits != null || vSplits != null)) {
                // Resize the split panel once loaded so we can update the sizes
                // of widgets based on a supplied split pos.
//                if (resizeCommand == null) {
//                    resizeCommand = new ScheduledCommand() {
//                        @Override
//                        public void execute() {
//                            resizeCommand = null;
                            adjustSplitSizes();
//                        }
//                    };
//                    Scheduler.get().scheduleDeferred(resizeCommand);
//                }
            } else {
                // Defer actually updating the layout, so that if we receive
                // many mouse events before layout/paint occurs, we'll only
                // update once.
//                if (resizeCommand == null) {
//                    resizeCommand = new ScheduledCommand() {
//                        @Override
//                        public void execute() {
//                            resizeCommand = null;
                            MySplitLayoutPanel.super.onResize();
//                        }
//                    };
//                    Scheduler.get().scheduleDeferred(resizeCommand);
//                }
            }
        } else {
            super.onResize();
        }
    }

    private void adjustSplitSizes() {
        boolean doLayout = false;
        int hSplitterIndex = 0;
        int vSplitterIndex = 0;

        final int offsetWidth = getOffsetWidth();
        final int offsetHeight = getOffsetHeight();

        if (offsetWidth > 0 && offsetHeight > 0) {
            for (int i = 0; i < getWidgetCount(); i++) {
                final Widget widget = getWidget(i);
                if (widget != null && !(widget instanceof Splitter)) {
                    final LayoutData layout = (LayoutData) widget.getLayoutData();
                    double size = layout.size;

                    final double min = 0;
                    double max = size;

                    switch (layout.direction) {
                    case WEST:
                        if (hSplits != null && hSplits.length > hSplitterIndex) {
                            size = (hSplits[hSplitterIndex++] * offsetWidth) - 2;
                            max = getOffsetWidth() - SPLITTER_SIZE;
                        }
                        break;

                    case EAST:
                        if (hSplits != null && hSplits.length > hSplitterIndex) {
                            size = (hSplits[hSplitterIndex++] * offsetWidth) + 2;
                            max = getOffsetWidth() - SPLITTER_SIZE;
                        }
                        break;

                    case NORTH:
                        if (vSplits != null && vSplits.length > vSplitterIndex) {
                            size = (vSplits[vSplitterIndex++] * offsetHeight) - 2;
                            max = getOffsetHeight() - SPLITTER_SIZE;
                        }
                        break;

                    case SOUTH:
                        if (vSplits != null && vSplits.length > vSplitterIndex) {
                            size = (vSplits[vSplitterIndex++] * offsetHeight) + 2;
                            max = getOffsetHeight() - SPLITTER_SIZE;
                        }
                        break;
                    }

                    if (size < min) {
                        size = min;
                    } else if (size > max) {
                        size = max;
                    }

                    // Prevent unnecessary resizing.
                    if (Double.compare(size, layout.size) != 0) {
                        layout.size = size;
                        doLayout = true;
                    }
                }
            }

            // Defer actually updating the layout, so that if we receive many
            // mouse events before layout/paint occurs, we'll only update once.
            if (doLayout) {
//                &&
//            } adjustSplitSizesCommand == null) {
//                adjustSplitSizesCommand = new ScheduledCommand() {
//                    @Override
//                    public void execute() {
//                        adjustSplitSizesCommand = null;
                        isResizing = true;
                        forceLayout();
                        isResizing = false;
//                    }
//                };
//                Scheduler.get().scheduleDeferred(adjustSplitSizesCommand);
            }
        }
    }

    public void maximise(final Widget widget) {
        setWidgetSize(widget, getOffsetWidth());
    }

    @Override
    public void insert(final Widget child, final Direction direction, final double size, final Widget before) {
        super.insert(child, direction, size, before);
        if (direction != Direction.CENTER) {
            insertSplitter(child, before);
        }
    }

    @Override
    public boolean remove(final Widget child) {
        assert!(child instanceof Splitter) : "Splitters may not be directly removed";

        final int idx = getWidgetIndex(child);
        if (super.remove(child)) {
            // Remove the associated splitter, if any.
            // Now that the widget is removed, idx is the index of the splitter.
            if (idx < getWidgetCount()) {
                // Call super.remove(), or we'll end up recursing.
                super.remove(getWidget(idx));
            }
            return true;
        }
        return false;
    }

    /**
     * Sets the minimum allowable size for the given widget.
     *
     * <p>
     * Its associated splitter cannot be dragged to a position that would make
     * it smaller than this size. This method has no effect for the
     * {@link DockLayoutPanel.Direction#CENTER} widget.
     * </p>
     *
     * @param child
     *            the child whose minimum size will be set
     * @param minSize
     *            the minimum size for this widget
     */
    public void setWidgetMinSize(final Widget child, final int minSize) {
        assertIsChild(child);
        final Splitter splitter = getAssociatedSplitter(child);
        // The splitter is null for the center element.
        if (splitter != null) {
            splitter.setMinSize(minSize);
        }
    }

    private Splitter getAssociatedSplitter(final Widget child) {
        // If a widget has a next sibling, it must be a splitter, because the
        // only
        // widget that *isn't* followed by a splitter must be the CENTER, which
        // has
        // no associated splitter.
        final int idx = getWidgetIndex(child);
        if (idx > -1 && idx < getWidgetCount() - 1) {
            final Widget splitter = getWidget(idx + 1);
            assert splitter instanceof Splitter : "Expected child widget to be splitter";
            return (Splitter) splitter;
        }
        return null;
    }

    private void insertSplitter(final Widget widget, final Widget before) {
        assert getChildren().size() > 0 : "Can't add a splitter before any children";

        final LayoutData layout = (LayoutData) widget.getLayoutData();
        Splitter splitter = null;
        switch (getResolvedDirection(layout.direction)) {
        case WEST:
            splitter = new HSplitter(widget, false, hSplitterIndex++);
            break;
        case EAST:
            splitter = new HSplitter(widget, true, hSplitterIndex++);
            break;
        case NORTH:
            splitter = new VSplitter(widget, false, vSplitterIndex++);
            break;
        case SOUTH:
            splitter = new VSplitter(widget, true, vSplitterIndex++);
            break;
        default:
            assert false : "Unexpected direction";
        }

        super.insert(splitter, layout.direction, SPLITTER_SIZE, before);
    }

    /**
     * The element that masks the screen so we can catch mouse events over
     * iframes.
     */
    private static Element glassElem = null;

    private Element getGlass() {
        if (glassElem == null) {
            glassElem = DOM.createDiv();
            glassElem.getStyle().setPosition(Position.ABSOLUTE);
            glassElem.getStyle().setLeft(0, Unit.PX);
            glassElem.getStyle().setTop(0, Unit.PX);
            glassElem.getStyle().setMargin(0, Unit.PX);
            glassElem.getStyle().setPadding(0, Unit.PX);
            glassElem.getStyle().setBorderWidth(0, Unit.PX);
            glassElem.getStyle().setZIndex(2);

            // We need to set the background color or mouse events will go right
            // through the glassElem. If the SplitPanel contains an iframe, the
            // iframe will capture the event and the slider will stop moving.
            glassElem.getStyle().setBackgroundColor("white");
            glassElem.getStyle().setOpacity(0);
            glassElem.getStyle().setProperty("filter", "alpha(opacity=0)");
        }

        return glassElem;
    }

    private void startResizing(final boolean vertical) {
        final Element glassElem = getGlass();

        isResizing = true;
        // onSplitterResizeStarted(x, y);

        // Resize glassElem to take up the entire scrollable window area
        final int height = RootPanel.getBodyElement().getScrollHeight() - 1;
        final int width = RootPanel.getBodyElement().getScrollWidth() - 1;
        glassElem.getStyle().setWidth(width, Unit.PX);
        glassElem.getStyle().setHeight(height, Unit.PX);

        if (vertical) {
            glassElem.getStyle().setCursor(Cursor.ROW_RESIZE);
        } else {
            glassElem.getStyle().setCursor(Cursor.COL_RESIZE);
        }

        RootPanel.getBodyElement().appendChild(glassElem);
    }

    private void stopResizing() {
        if (glassElem != null) {
            final Element parent = glassElem.getParentElement();
            if (parent != null) {
                parent.removeChild(glassElem);
            }
        }
        isResizing = false;
        onResize();
    }
}
