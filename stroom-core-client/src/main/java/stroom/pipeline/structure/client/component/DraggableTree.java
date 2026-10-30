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

package stroom.pipeline.structure.client.component;

import stroom.widget.contextmenu.client.ContextMenuManager;
import stroom.widget.contextmenu.client.TextEditTarget;
import stroom.widget.contextmenu.client.TextField;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.widget.datagrid.client.MouseHelper;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiChild;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/// A tree whose items can be dragged, with the items after them, to another parent.
///
/// While dragging, the item under the pointer is marked: as the drop target if it would take what
/// is dragged (see [#isValidTarget]), else as refusing it. Releasing over a drop target moves what
/// is dragged there; releasing anywhere else (over nothing, or an item that refuses it), or pressing
/// Escape, puts it back where it was. Releasing over where it came from makes it its parent's last
/// child, e.g. to reorder an expression's terms, unless that is set to put it back too (see
/// [#setPutBackOnOwnParent]). Dragging to an edge of what scrolls the tree scrolls it that way, as
/// far as the tree goes.
///
/// Pressing on the background (not an item) and dragging pans the tree, if it scrolls.
///
/// A template declares it with its tree inside (`<pv:tree>`, see [#addTree]); its owner gives it
/// the panel that draws a copy of what is dragged ([#setDraggedCopy]) and says, through callbacks,
/// which item may take which, and what selecting, dragging and dropping do.
///
/// @param <E> The type of the tree's items.
public class DraggableTree<E> extends Composite implements HasContextMenuHandlers {

    private static final int DRAGGING_THRESHOLD = 20;
    // How far the pointer moves before pressing on the background pans rather than clicks
    private static final int PANNING_THRESHOLD = 3;
    // How near an edge of what scrolls the tree dragging scrolls it, and how fast (at the edge, per
    // tick)
    private static final int EDGE_SIZE = 30;
    private static final int EDGE_SPEED = 16;
    private static final int EDGE_TICK_MILLIS = 16;
    // Room left beyond the tree's items when scrolling to its extents
    private static final int EXTENT_MARGIN = 20;
    // The BEM block draggable-tree (see draggable-tree.css)
    private static final String BLOCK = "draggable-tree";
    private static final String PANNABLE_CLASS = BLOCK + "--pannable";
    private static final String PANNING_CLASS = BLOCK + "--panning";
    // The copy of what is dragged, which follows the pointer, and once it has moved
    private static final String DRAGGED_CLASS = BLOCK + "__dragged";
    private static final String MOVING_CLASS = DRAGGED_CLASS + "--moving";
    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    HTMLPanel layoutPanel;
    // Where the tree goes, and the live region for the keyboard's moves (TreeKeyboard)
    @UiField
    Element treeSlot;
    @UiField
    Element status;
    private TreePanel<E> treePanel;
    private TreePanel<E> subTreePanel;
    private BiPredicate<E, E> isValidTarget;
    private Consumer<E> onSelect;
    private BiConsumer<E, E> onDragStart;
    private BiConsumer<E, E> onDragEnd;
    private Box<E> box;
    // The item under the pointer, if it would take what is dragged: where it is dropped
    private Box<E> targetBox;
    // The item under the pointer, if it refuses what is dragged
    private Box<E> refusedBox;
    // Where what is dragged came from, to put it back if it isn't dropped on a target
    private E dragParent;
    private int dragIndex;
    // Where the pointer was last, to mark the item under it again once the tree is redrawn
    private int pointerX;
    private int pointerY;
    // Listens for Escape while dragging
    private HandlerRegistration escapeRegistration;
    private int startX = -1;
    private int startY = -1;
    private int offsetX = -1;
    private int offsetY = -1;
    private Element mouseDownElement;
    private boolean allowDragging;
    private boolean putBackOnOwnParent;
    private boolean dragging;
    // What scrolls the tree (found when needed, as it is an ancestor), if anything
    private Element scroller;
    // Scrolls while dragging near an edge
    private Timer edgeScrollTimer;
    // Panning: where it started (the pointer, and the scroll), and whether it has moved enough
    private boolean panning;
    private boolean panned;
    private int panStartX;
    private int panStartY;
    private int panScrollLeft;
    private int panScrollTop;

    /// Makes a panel with no tree yet, e.g. one a template declares; see [#addTree].
    public DraggableTree() {
        initWidget(BINDER.createAndBindUi(this));
        sinkEvents(Event.ONMOUSEMOVE | Event.ONMOUSEDOWN | Event.ONMOUSEUP | Event.ONCONTEXTMENU);
    }

    /// Gives the panel its tree, once, from a template's `<pv:tree>` child.
    ///
    /// @param treePanel The tree.
    @UiChild(tagname = "tree", limit = 1)
    public void addTree(final TreePanel<E> treePanel) {
        if (this.treePanel != null) {
            throw new IllegalStateException("Already has its tree");
        }
        this.treePanel = Objects.requireNonNull(treePanel, "treePanel");
        layoutPanel.addAndReplaceElement(treePanel, treeSlot);
    }

    /// Gives the panel, once, the second tree panel that draws a copy of what is dragged. It is made
    /// in Java, not declared, as it is on the page only while dragging.
    ///
    /// @param subTreePanel The copy of what is dragged, shown only while dragging.
    public void setDraggedCopy(final TreePanel<E> subTreePanel) {
        if (this.subTreePanel != null) {
            throw new IllegalStateException("Already has its dragged copy");
        }
        this.subTreePanel = Objects.requireNonNull(subTreePanel, "subTreePanel");
        // The copy of what is dragged, which follows the pointer, is for sight only: not a second
        // tree for assistive technology, nor focusable
        subTreePanel.getElement().setAttribute("aria-hidden", "true");
        subTreePanel.getElement().setAttribute("inert", "");
    }

    /// @return The live region in which the keyboard's moves are told (see [TreeKeyboard]).
    Element getStatusElement() {
        return status;
    }

    /// Sets which item may take which as a child, by dragging.
    ///
    /// @param isValidTarget Given a parent and an item, whether the parent may take it (and the
    ///                      items after it).
    public void setIsValidTarget(final BiPredicate<E, E> isValidTarget) {
        this.isValidTarget = isValidTarget;
    }

    /// Sets what a click on an item, or on nothing, does, e.g. selecting it.
    ///
    /// @param onSelect Given the item clicked, or null for a click on nothing.
    public void setOnSelect(final Consumer<E> onSelect) {
        this.onSelect = onSelect;
    }

    /// Sets what is told when an item starts being dragged.
    ///
    /// @param onDragStart Given its parent and the item.
    public void setOnDragStart(final BiConsumer<E, E> onDragStart) {
        this.onDragStart = onDragStart;
    }

    /// Sets what is told when an item is dropped on a new parent (which the tree already shows).
    ///
    /// @param onDragEnd Given its new parent and the item.
    public void setOnDragEnd(final BiConsumer<E, E> onDragEnd) {
        this.onDragEnd = onDragEnd;
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
        } else if (Event.ONCONTEXTMENU == eventType) {
            onTextFieldContextMenu(event);
        } else {
            // e.g. key presses, for handlers added with addDomHandler
            super.onBrowserEvent(event);
        }
    }

    private void reset() {
        startX = -1;
        startY = -1;
        offsetX = -1;
        offsetY = -1;
        box = null;
        setTargetBox(null);
        setRefusedBox(null);
        dragParent = null;
        dragIndex = -1;
        dragging = false;
        stopEdgeScroll();
        stopPanning();
        if (escapeRegistration != null) {
            escapeRegistration.removeHandler();
            escapeRegistration = null;
        }
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

                    // Only an item with a parent (not the root) can be dragged
                    dragParent = treePanel.getTree().getParent(box.getItem());

                    // We are going to start dragging so capture all subsequent
                    // mouse events.
                    Event.setCapture(getElement());
                } else if (isScrollable()) {
                    // The background: pan, if the pointer moves
                    panning = true;
                    panStartX = event.getClientX();
                    panStartY = event.getClientY();
                    panScrollLeft = scroller.getScrollLeft();
                    panScrollTop = scroller.getScrollTop();
                    Event.setCapture(getElement());
                    event.preventDefault();
                }
            }
        }

        mouseDownElement = element;
    }

    private void onMouseUp(final Event event) {
        // A pan isn't a click
        final boolean wasPanned = panned;
        stopPanning();
        if (wasPanned) {
            // Nothing else
        } else if (dragging) {
            final E target = targetBox == null
                    ? null
                    : targetBox.getItem();
            if (target == null || (putBackOnOwnParent && target.equals(dragParent))) {
                // Not dropped on a target (or dropped where it came from, if that changes nothing):
                // put it back
                cancelDrag();
            } else {
                final E child = box.getItem();
                treePanel.getTree().addChild(target, child);
                removeDraggedCopy();
                setTargetBox(null);
                setRefusedBox(null);
                treePanel.refresh();
                endDragging(target, child);
            }
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

            // A right-click on a text field (e.g. a term's value) opens the menu with the edit items
            // instead, on contextmenu (see onTextFieldContextMenu)
            if (MouseUtil.isSecondary(event) && !TextField.isTextField(element)) {
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
        if (panning) {
            pan(event);
        } else if (allowDragging && dragParent != null) {
            pointerX = event.getClientX();
            pointerY = event.getClientY();
            if (dragging) {
                final int x = MouseHelper.getRelativeX(event, getElement());
                final int y = MouseHelper.getRelativeY(event, getElement());

                placeDragged(x - offsetX, y - offsetY);
                subTreePanel.addStyleName(MOVING_CLASS);

                updateHotspot(treePanel.getTargetBox(event, true));
                updateEdgeScroll();

            } else if (box != null && Math.abs(startX - event.getClientX()) > DRAGGING_THRESHOLD
                    || Math.abs(startY - event.getClientY()) > DRAGGING_THRESHOLD) {
                dragging = true;

                final E child = box.getItem();
                startDragging(dragParent, child);
                escapeRegistration = Event.addNativePreviewHandler(this::onPreviewWhileDragging);

                final DefaultTreeForTreeLayout<E> subTree = new DefaultTreeForTreeLayout<>(child);
                addChildren(subTree, child);

                subTreePanel.setTree(subTree);
                subTreePanel.refresh();

                dragIndex = treePanel.getTree().getChildrenList(dragParent).indexOf(child);
                treePanel.getTree().removeChild(child);
                // Redrawing replaces the boxes, so the one under the pointer is marked again after
                treePanel.refresh(this::markUnderPointer);
                markUnderPointer();

                // Unseen until it moves (draggable-tree.css)
                subTreePanel.removeStyleName(MOVING_CLASS);
                subTreePanel.addStyleName(DRAGGED_CLASS);
                placeDragged(0, 0);
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
        } else if (box == null) {
            // Over the background, a grab cursor says that it can be panned
            final boolean pannable = treePanel.getTargetBox(event, true) == null && isScrollable();
            if (pannable) {
                getElement().addClassName(PANNABLE_CLASS);
            } else {
                getElement().removeClassName(PANNABLE_CLASS);
            }
        }
    }

    // Pans the tree by how far the pointer has moved since pressing on the background
    private void pan(final Event event) {
        final int dx = event.getClientX() - panStartX;
        final int dy = event.getClientY() - panStartY;
        if (!panned && (Math.abs(dx) > PANNING_THRESHOLD || Math.abs(dy) > PANNING_THRESHOLD)) {
            panned = true;
            getElement().addClassName(PANNING_CLASS);
        }
        if (panned) {
            scroller.setScrollLeft(panScrollLeft - dx);
            scroller.setScrollTop(panScrollTop - dy);
        }
        event.preventDefault();
    }

    private void stopPanning() {
        if (panning) {
            panning = false;
            panned = false;
            getElement().removeClassName(PANNING_CLASS);
        }
    }

    // Whether what scrolls the tree can scroll (finding it if need be)
    private boolean isScrollable() {
        if (scroller == null) {
            scroller = findScroller(getElement());
        }
        return scroller != null
               && (scroller.getScrollWidth() > scroller.getClientWidth()
                   || scroller.getScrollHeight() > scroller.getClientHeight());
    }

    // Starts (or stops) scrolling the tree while dragging near (or past) an edge of what scrolls it
    private void updateEdgeScroll() {
        if (getEdgeScrollX() != 0 || getEdgeScrollY() != 0) {
            if (edgeScrollTimer == null) {
                edgeScrollTimer = new Timer() {
                    @Override
                    public void run() {
                        edgeScroll();
                    }
                };
                edgeScrollTimer.scheduleRepeating(EDGE_TICK_MILLIS);
            }
        } else {
            stopEdgeScroll();
        }
    }

    private void edgeScroll() {
        final int dx = getEdgeScrollX();
        final int dy = getEdgeScrollY();
        if (!dragging || (dx == 0 && dy == 0)) {
            stopEdgeScroll();
            return;
        }
        scroller.setScrollLeft(scroller.getScrollLeft() + dx);
        scroller.setScrollTop(scroller.getScrollTop() + dy);
        // What is dragged stays under the pointer, and what is under it is marked
        final int x = pointerX - getElement().getAbsoluteLeft() + getElement().getScrollLeft()
                      + Document.get().getScrollLeft();
        final int y = pointerY - getElement().getAbsoluteTop() + getElement().getScrollTop()
                      + Document.get().getScrollTop();
        placeDragged(x - offsetX, y - offsetY);
        updateHotspot(getBoxAt(pointerX, pointerY));
    }

    // Places the copy of what is dragged, relative to the panel
    private void placeDragged(final int left, final int top) {
        CssProperties.set(subTreePanel.getElement(), "--draggable-tree__drag-left", left + "px");
        CssProperties.set(subTreePanel.getElement(), "--draggable-tree__drag-top", top + "px");
    }

    private void stopEdgeScroll() {
        if (edgeScrollTimer != null) {
            edgeScrollTimer.cancel();
            edgeScrollTimer = null;
        }
    }

    // How far to scroll across this tick: faster nearer (or past) an edge, and none beyond the
    // tree's extents
    private int getEdgeScrollX() {
        if (!isScrollable()) {
            return 0;
        }
        final int left = scroller.getAbsoluteLeft() - Document.get().getScrollLeft();
        final int right = left + scroller.getClientWidth();
        final int speed = getEdgeSpeed(pointerX, left, right);
        if (speed < 0 && scroller.getScrollLeft() > 0) {
            return speed;
        }
        if (speed > 0 && scroller.getScrollLeft() + scroller.getClientWidth() < getContentRight()) {
            return speed;
        }
        return 0;
    }

    private int getEdgeScrollY() {
        if (!isScrollable()) {
            return 0;
        }
        final int top = scroller.getAbsoluteTop() - Document.get().getScrollTop();
        final int bottom = top + scroller.getClientHeight();
        final int speed = getEdgeSpeed(pointerY, top, bottom);
        if (speed < 0 && scroller.getScrollTop() > 0) {
            return speed;
        }
        if (speed > 0 && scroller.getScrollTop() + scroller.getClientHeight() < getContentBottom()) {
            return speed;
        }
        return 0;
    }

    // Negative to scroll back, positive to scroll on, 0 if not near either edge
    private static int getEdgeSpeed(final int pointer, final int start, final int end) {
        if (pointer < start + EDGE_SIZE) {
            final int depth = Math.min(EDGE_SIZE, start + EDGE_SIZE - pointer);
            return -Math.max(1, EDGE_SPEED * depth / EDGE_SIZE);
        }
        if (pointer > end - EDGE_SIZE) {
            final int depth = Math.min(EDGE_SIZE, pointer - (end - EDGE_SIZE));
            return Math.max(1, EDGE_SPEED * depth / EDGE_SIZE);
        }
        return 0;
    }

    // The right and bottom of the tree's items, in what scrolls it, with a margin (what is dragged
    // isn't counted, so that dragging past the items doesn't keep scrolling)
    private int getContentRight() {
        return getContentExtent(true);
    }

    private int getContentBottom() {
        return getContentExtent(false);
    }

    private int getContentExtent(final boolean across) {
        final DefaultTreeForTreeLayout<E> tree = treePanel.getTree();
        int extent = 0;
        if (tree != null && tree.getRoot() != null) {
            extent = getContentExtent(tree, tree.getRoot(), across);
        }
        return extent + EXTENT_MARGIN;
    }

    private int getContentExtent(final DefaultTreeForTreeLayout<E> tree, final E item, final boolean across) {
        int extent = 0;
        final Box<E> itemBox = treePanel.getBox(item);
        if (itemBox != null) {
            final Element element = itemBox.getElement();
            extent = across
                    ? element.getAbsoluteRight() - scroller.getAbsoluteLeft() + scroller.getScrollLeft()
                    : element.getAbsoluteBottom() - scroller.getAbsoluteTop() + scroller.getScrollTop();
        }
        for (final E child : tree.getChildrenList(item)) {
            extent = Math.max(extent, getContentExtent(tree, child, across));
        }
        return extent;
    }

    // The nearest ancestor that scrolls (its overflow is auto or scroll), if any
    private static native Element findScroller(Element element) /*-{
        var e = element.parentElement;
        while (e && e !== $doc.body) {
            var style = $wnd.getComputedStyle(e);
            if (/(auto|scroll)/.test(style.overflowX + " " + style.overflowY)) {
                return e;
            }
            e = e.parentElement;
        }
        return null;
    }-*/;

    private void onContextMenu(final Event event) {
        final PopupPosition popupPosition = new PopupPosition(event.getClientX(), event.getClientY());
        ContextMenuEvent.fire(this, popupPosition);
    }

    // A right-click on a text field in an item (or Shift+F10 or the context menu key there) opens the
    // tree's menu with the field's edit items at its top, in place of the browser's menu. A
    // Shift+right-click is left to the browser
    private void onTextFieldContextMenu(final Event event) {
        final TextEditTarget textField = ContextMenuManager.findEditTarget(event);
        if (textField != null) {
            event.preventDefault();
            event.stopPropagation();
            ContextMenuEvent.fire(this,
                    ContextMenuManager.getPosition(event, textField),
                    textField);
        }
    }

    // Marks the item under the pointer, if any, as the drop target if it would take what is dragged,
    // else as refusing it; and nothing else
    private void updateHotspot(final Box<E> under) {
        final boolean valid = under != null && box != null && isValidTarget(under.getItem(), box.getItem());
        setTargetBox(valid
                ? under
                : null);
        setRefusedBox(under != null && !valid
                ? under
                : null);
    }

    private void markUnderPointer() {
        if (dragging) {
            // The boxes may have been replaced, so forget those marked (they are no longer shown)
            targetBox = null;
            refusedBox = null;
            updateHotspot(getBoxAt(pointerX, pointerY));
        }
    }

    // The box of the tree's item under a point of the window, if any
    private Box<E> getBoxAt(final int clientX, final int clientY) {
        final DefaultTreeForTreeLayout<E> tree = treePanel.getTree();
        return tree == null || tree.getRoot() == null
                ? null
                : getBoxAt(tree, tree.getRoot(), clientX, clientY);
    }

    private Box<E> getBoxAt(final DefaultTreeForTreeLayout<E> tree,
                            final E item,
                            final int clientX,
                            final int clientY) {
        final Box<E> itemBox = treePanel.getBox(item);
        if (itemBox != null) {
            final Element element = itemBox.getElement();
            if (element.getAbsoluteLeft() <= clientX && clientX <= element.getAbsoluteRight()
                && element.getAbsoluteTop() <= clientY && clientY <= element.getAbsoluteBottom()) {
                return itemBox;
            }
        }
        for (final E child : tree.getChildrenList(item)) {
            final Box<E> childBox = getBoxAt(tree, child, clientX, clientY);
            if (childBox != null) {
                return childBox;
            }
        }
        return null;
    }

    // Escape while dragging puts what is dragged back
    private void onPreviewWhileDragging(final NativePreviewEvent previewEvent) {
        if (dragging
            && previewEvent.getTypeInt() == Event.ONKEYDOWN
            && previewEvent.getNativeEvent().getKeyCode() == KeyCodes.KEY_ESCAPE) {
            previewEvent.cancel();
            previewEvent.getNativeEvent().preventDefault();
            cancelDrag();
            reset();
            Event.releaseCapture(getElement());
        }
    }

    // Takes the copy of what was dragged off the page, as it was before the drag
    private void removeDraggedCopy() {
        layoutPanel.remove(subTreePanel);
        subTreePanel.removeStyleName(DRAGGED_CLASS);
        subTreePanel.removeStyleName(MOVING_CLASS);
    }

    // Puts what is dragged back where it was, as it was not dropped on a target
    private void cancelDrag() {
        final E child = box.getItem();
        treePanel.getTree().addChild(dragParent, child, dragIndex);
        removeDraggedCopy();
        setTargetBox(null);
        setRefusedBox(null);
        treePanel.refresh();
    }

    private void setTargetBox(final Box<E> target) {
        if (target != targetBox) {
            if (targetBox != null) {
                targetBox.showHotspot(false);
            }
            targetBox = target;
            if (targetBox != null) {
                targetBox.showHotspot(true);
            }
        }
    }

    private void setRefusedBox(final Box<E> refused) {
        if (refused != refusedBox) {
            if (refusedBox != null) {
                refusedBox.showRefused(false);
            }
            refusedBox = refused;
            if (refusedBox != null) {
                refusedBox.showRefused(true);
            }
        }
    }

    private boolean isValidTarget(final E parent, final E child) {
        return isValidTarget != null && isValidTarget.test(parent, child);
    }

    private void addChildren(final DefaultTreeForTreeLayout<E> subTree, final E parent) {
        final List<E> children = treePanel.getTree().getChildren(parent);
        if (children != null) {
            for (final E child : children) {
                subTree.addChild(parent, child);
                addChildren(subTree, child);
            }
        }
    }

    private void setSelected(final E item) {
        if (onSelect != null) {
            onSelect.accept(item);
        }
    }

    private void startDragging(final E parent, final E child) {
        if (onDragStart != null) {
            onDragStart.accept(parent, child);
        }
    }

    private void endDragging(final E parent, final E child) {
        if (onDragEnd != null) {
            onDragEnd.accept(parent, child);
        }
    }

    public void setAllowDragging(final boolean allowDragging) {
        this.allowDragging = allowDragging;
    }

    /// Sets whether releasing what is dragged over where it came from puts it back where it was
    /// (as when it isn't dropped on a target), rather than making it its parent's last child; e.g.
    /// where the order of an item's children doesn't matter.
    ///
    /// @param putBackOnOwnParent Whether to put it back.
    public void setPutBackOnOwnParent(final boolean putBackOnOwnParent) {
        this.putBackOnOwnParent = putBackOnOwnParent;
    }

    /// @return Whether releasing what is dragged over where it came from puts it back where it was
    /// (see [#setPutBackOnOwnParent]).
    public boolean isPutBackOnOwnParent() {
        return putBackOnOwnParent;
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final ContextMenuEvent.Handler handler) {
        return addHandler(handler, ContextMenuEvent.getType());
    }


    // --------------------------------------------------------------------------------


    // The owner is raw: a binder can't name a type variable
    @SuppressWarnings("rawtypes")
    interface Binder extends UiBinder<Widget, DraggableTree> {

    }
}
