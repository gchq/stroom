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

package stroom.pipeline.structure.client.view;

import stroom.data.grid.client.MouseHelper;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;

public abstract class DraggableTreePanel<E> extends Composite implements HasContextMenuHandlers {

    private static final int DRAGGING_THRESHOLD = 20;

    private final TreePanel<E> treePanel;
    private final TreePanel<E> subTreePanel;
    private final FlowPanel layoutPanel;
    private Box<E> box;
    private Box<E> targetBox;
    private int startX = -1;
    private int startY = -1;
    private int offsetX = -1;
    private int offsetY = -1;
    private Element mouseDownElement;
    private boolean allowDragging;
    private boolean dragging;

    public DraggableTreePanel(final TreePanel<E> treePanel, final TreePanel<E> subTreePanel) {
        this.treePanel = treePanel;
        this.subTreePanel = subTreePanel;

        layoutPanel = new FlowPanel();
        layoutPanel.add(treePanel);
        initWidget(layoutPanel);

        sinkEvents(Event.ONMOUSEMOVE | Event.ONMOUSEDOWN | Event.ONMOUSEUP);
    }

    @Override
    public void onBrowserEvent(final Event event) {
        final int eventType = event.getTypeInt();
        if (Event.ONMOUSEMOVE == eventType) {
            onMouseMove(event);
        } else if (Event.ONMOUSEDOWN == eventType) {
            onMouseDown(event);
        } else if (Event.ONMOUSEUP == eventType) {
            onMouseUp(event);
        }
    }

    private void reset() {
        startX = -1;
        startY = -1;
        offsetX = -1;
        offsetY = -1;
        box = null;
        targetBox = null;
        dragging = false;
        mouseDownElement = null;
    }

    private void onMouseDown(final Event event) {
        reset();

        final Element element = event.getEventTarget().cast();

        // select and input elements steal focus and stop us receiving mouse up
        // events so ignore mouse down events on these so that we don't start
        // dragging items due to not catching the mouse up event.
        if (validEvent(element)) {
            // Only allow the possibility of dragging if the mouse button is the
            // left button.
            if (MouseUtil.isPrimary(event)) {
                box = treePanel.getTargetBox(event, true);
                if (box != null) {
                    startX = event.getClientX();
                    startY = event.getClientY();
                    offsetX = startX - box.getElement().getAbsoluteLeft();
                    offsetY = startY - box.getElement().getAbsoluteTop();

                    final E parent = treePanel.getTree().getParent(box.getItem());
                    if (parent != null) {
                        targetBox = treePanel.getBox(parent);
                    }

                    // We are going to start dragging so capture all subsequent
                    // mouse events.
                    Event.setCapture(getElement());
                }
            }
        }

        mouseDownElement = element;
    }

    private void onMouseUp(final Event event) {
        if (dragging) {
            final E child = box.getItem();
            final E parent = targetBox.getItem();
            treePanel.getTree().addChild(parent, child);
            treePanel.refresh();
            targetBox.showHotspot(false);

            layoutPanel.remove(subTreePanel);

            endDragging(parent, child);
        } else {
            // If the target is the same as the item on mouse down then select
            // the item.
            final Element element = event.getEventTarget().cast();
            if (validEvent(element)) {
                if (element == mouseDownElement) {
                    final Box<E> target = treePanel.getTargetBox(event, true);
                    if (target != null) {
                        setSelected(target.getItem());
                    } else {
                        setSelected(null);
                    }
                }
            }

            if (MouseUtil.isSecondary(event)) {
                onContextMenu(event);
            }
        }

        reset();

        // Release capture in case we were dragging and were capturing all mouse
        // events.
        Event.releaseCapture(getElement());
    }

    private boolean validEvent(final Element element) {
        final String tagName = element.getTagName();

        // select and input elements steal focus and stop us receiving mouse up
        // events so ignore mouse down events on these so that we don't start
        // dragging items due to not catching the mouse up event.
        return !"SELECT".equalsIgnoreCase(tagName) &&
                !"OPTION".equalsIgnoreCase(tagName) &&
                !"INPUT".equalsIgnoreCase(tagName);
    }

    private void onMouseMove(final Event event) {
        if (allowDragging && targetBox != null) {
            if (dragging) {
                final int x = MouseHelper.getRelativeX(event, getElement());
                final int y = MouseHelper.getRelativeY(event, getElement());

                subTreePanel.getElement().getStyle().setLeft(x - offsetX, Unit.PX);
                subTreePanel.getElement().getStyle().setTop(y - offsetY, Unit.PX);
                subTreePanel.getElement().getStyle().setOpacity(0.5);

                updateHotspot(treePanel.getTargetBox(event, true));

            } else if (box != null && Math.abs(startX - event.getClientX()) > DRAGGING_THRESHOLD
                    || Math.abs(startY - event.getClientY()) > DRAGGING_THRESHOLD) {
                dragging = true;

                final E child = box.getItem();
                final E parent = treePanel.getTree().getParent(child);
                startDragging(parent, child);

                final DefaultTreeForTreeLayout<E> subTree = new DefaultTreeForTreeLayout<>(child);
                addChildren(subTree, child);

                subTreePanel.setTree(subTree);
                subTreePanel.refresh();

                targetBox = treePanel.getBox(parent);
                targetBox.showHotspot(true);
                treePanel.getTree().removeChild(child);
                treePanel.refresh(() -> updateHotspot(treePanel.getBox(parent)));
                updateHotspot(treePanel.getTargetBox(event, true));

                subTreePanel.getElement().getStyle().setPosition(Position.ABSOLUTE);
                subTreePanel.getElement().getStyle().setZIndex(2);
                subTreePanel.getElement().getStyle().setOpacity(0);
                subTreePanel.getElement().getStyle().setLeft(0, Unit.PX);
                subTreePanel.getElement().getStyle().setTop(0, Unit.PX);
                layoutPanel.add(subTreePanel);

                final Box<E> subTreeBox = subTreePanel.getBox(box.getItem());
                if (subTreeBox != null) {
                    final int additionalXOffset = subTreeBox.getAbsoluteLeft() - subTreePanel.getAbsoluteLeft();
                    final int additionalYOffset = subTreeBox.getAbsoluteTop() - subTreePanel.getAbsoluteTop();

                    offsetX += additionalXOffset;
                    offsetY += additionalYOffset;
                }
            }

            event.preventDefault();
        }
    }

    private void onContextMenu(final Event event) {
        final PopupPosition popupPosition = new PopupPosition(event.getClientX(), event.getClientY());
        ContextMenuEvent.fire(this, popupPosition);
    }

    private void updateHotspot(final Box<E> target) {
        if (target != null && target != targetBox && isValidTarget(target.getItem(), box.getItem())) {
            target.showHotspot(true);
            targetBox.showHotspot(false);
            targetBox = target;
        }
    }

    protected abstract boolean isValidTarget(final E parent, final E child);

    private void addChildren(final DefaultTreeForTreeLayout<E> subTree, final E parent) {
        final List<E> children = treePanel.getTree().getChildren(parent);
        if (children != null) {
            for (final E child : children) {
                subTree.addChild(parent, child);
                addChildren(subTree, child);
            }
        }
    }

    protected abstract void setSelected(final E item);

    protected void startDragging(final E parent, final E child) {
    }

    protected void endDragging(final E parent, final E child) {
    }

    public void setAllowDragging(final boolean allowDragging) {
        this.allowDragging = allowDragging;
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final ContextMenuEvent.Handler handler) {
        return addHandler(handler, ContextMenuEvent.getType());
    }
}
