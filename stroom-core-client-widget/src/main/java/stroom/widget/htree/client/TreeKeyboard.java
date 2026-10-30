/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.htree.client;

import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.SingleItemSelectionModel;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.Event;
import com.google.gwt.view.client.SelectionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/// The keyboard for a tree drawn by a [TreePanel] (e.g. the pipeline structure and query expression
/// trees), one tab stop: Left moves to an item's parent and Right to its first child (the trees
/// grow to the right); Up and Down to its previous and next sibling, and Home and End to its first
/// and last, stopping at the ends. Selection follows the focus, which is put back on the item's box
/// each time the tree is redrawn (which replaces the boxes), and the focused box is kept in view.
/// Delete asks for the item to be removed (see [#remove]) and the context menu key (or Shift+F10)
/// opens its menu.
///
/// Where the tree may be changed (see [#setAllowMoving]), an item and the items after it can be
/// moved to another parent, as dragging them does: Ctrl+X detaches it (its box shows it, see
/// [Box#setDetached], until it is attached), the arrow keys choose its new parent (whose box shows
/// whether it would take it, see [#setCanTake]), Ctrl+V attaches it there, and Escape puts it back. A
/// live region tells screen reader users what each of these did.
///
/// @param <E> The tree's items.
public class TreeKeyboard<E> {

    private final TreePanel<E> treePanel;
    private final DraggableTree<E> root;
    // Tells screen reader users what detaching and attaching did
    private final Element liveRegion;
    // What the tree's items are called, e.g. "element" and "elements"
    private final String itemName;
    private final String itemsName;
    private SelectionModel<E> selectionModel;
    private boolean allowMoving;
    // The item detached to be attached to another parent, if any
    private E detached;
    // The item whose box to focus once the tree is redrawn
    private E focusAfterRedraw;
    // What its owner says about the tree's items, and is told (see the setters)
    private Function<E, String> nameOf = String::valueOf;
    private BiPredicate<E, E> canTake;
    private BiConsumer<E, E> onMove;
    private Consumer<E> onRemove;
    private Consumer<E> onNavigate;
    private BiPredicate<KeyDownEvent, E> onKey;
    private String branchPlace = "after it";

    /// @param treePanel The tree.
    /// @param root      The draggable panel holding it, whose key presses are the tree's, which
    ///                  fires its context menu events, and whose template has the live region
    ///                  (`draggable-tree__status`, also `sr-only`, so it isn't seen).
    /// @param itemName  What one of the tree's items is called, e.g. "element".
    /// @param itemsName What more than one are called, e.g. "elements".
    public TreeKeyboard(final TreePanel<E> treePanel,
                           final DraggableTree<E> root,
                           final String itemName,
                           final String itemsName) {
        this.treePanel = treePanel;
        this.root = root;
        this.itemName = itemName;
        this.itemsName = itemsName;
        root.addDomHandler(this::onKeyDown, KeyDownEvent.getType());
        treePanel.setAfterRefresh(this::afterRefresh);
        liveRegion = root.getStatusElement();
    }

    /// Sets what gives an item's name, e.g. for telling screen reader users that it was detached.
    ///
    /// @param nameOf Given an item, its name.
    public void setNameOf(final Function<E, String> nameOf) {
        this.nameOf = Objects.requireNonNull(nameOf, "nameOf");
    }

    /// Sets which item may take which as a child.
    ///
    /// @param canTake Given a parent and an item, whether the parent may take it (and the items
    ///                after it).
    public void setCanTake(final BiPredicate<E, E> canTake) {
        this.canTake = canTake;
    }

    /// Sets what moves an item (and the items after it) to another parent.
    ///
    /// @param onMove Given its new parent and the item.
    public void setOnMove(final BiConsumer<E, E> onMove) {
        this.onMove = onMove;
    }

    /// Sets what removes an item when Delete is pressed on it (e.g. with a confirmation first).
    ///
    /// @param onRemove Given the item.
    public void setOnRemove(final Consumer<E> onRemove) {
        this.onRemove = onRemove;
    }

    /// Sets what is told that the arrow keys (or Home or End) are about to move the focus, and the
    /// selection, to an item, e.g. to stop editing the item being left.
    ///
    /// @param onNavigate Given the item.
    public void setOnNavigate(final Consumer<E> onNavigate) {
        this.onNavigate = onNavigate;
    }

    /// Sets what handles a key press on an item before the tree does, e.g. to start editing it.
    ///
    /// @param onKey Given the key press and the item, whether it handled it (and so the tree
    ///              mustn't).
    public void setOnKey(final BiPredicate<KeyDownEvent, E> onKey) {
        this.onKey = onKey;
    }

    /// Sets where the items that move with an item are, e.g. in "rawWriter and the 1 element after
    /// it detached".
    ///
    /// @param branchPlace Where they are: "after it" by default, as in a pipeline.
    public void setBranchPlace(final String branchPlace) {
        this.branchPlace = Objects.requireNonNull(branchPlace, "branchPlace");
    }

    private String getName(final E item) {
        return nameOf.apply(item);
    }

    private boolean canTake(final E parent, final E child) {
        return canTake != null && canTake.test(parent, child);
    }

    private void move(final E parent, final E child) {
        if (onMove != null) {
            onMove.accept(parent, child);
        }
    }

    private void remove(final E item) {
        if (onRemove != null) {
            onRemove.accept(item);
        }
    }

    private void onNavigate(final E item) {
        if (onNavigate != null) {
            onNavigate.accept(item);
        }
    }

    private String getBranchPlace() {
        return branchPlace;
    }

    private boolean onKey(final KeyDownEvent event, final E item) {
        return onKey != null && onKey.test(event, item);
    }

    /// @param selectionModel The tree's selection, which follows the focus.
    public void setSelectionModel(final SelectionModel<E> selectionModel) {
        this.selectionModel = selectionModel;
    }

    /// Sets whether items can be moved with the keyboard; not being able to puts back an item that
    /// was detached.
    ///
    /// @param allowMoving Whether they can.
    public void setAllowMoving(final boolean allowMoving) {
        this.allowMoving = allowMoving;
        if (!allowMoving) {
            cancelDetach();
        }
    }

    /// @return The item detached (to be attached to another parent), or null for none.
    public E getDetached() {
        return detached;
    }

    /// @param item An item.
    /// @return Whether it is the item detached, or one after it.
    public boolean isDetached(final E item) {
        return detached != null && isInBranch(item, detached);
    }

    /// Detaches an item (and the items after it) to be attached to another parent. Its box shows
    /// that it is, as do those after it, until it is attached or put back. The tree's root can't
    /// be detached.
    ///
    /// @param item The item.
    public void detach(final E item) {
        final DefaultTreeForTreeLayout<E> tree = getTree();
        if (!allowMoving || item == null || tree == null || tree.getParent(item) == null) {
            return;
        }
        detached = item;
        redrawDetached();
        final int count = countItems(item);
        announce(getName(item) + (count > 1
                ? " and the " + (count - 1) + " " + (count == 2
                        ? itemName
                        : itemsName) + " " + getBranchPlace()
                : "")
                 + " detached. Move to its new parent and press Control V to attach it, or Escape to put "
                 + "it back.");
    }

    /// @param target An item.
    /// @return Whether the item detached can be attached to it: its own parent (leaving it where it
    /// was), or one that can take it, not the item detached or one after it.
    public boolean canAttach(final E target) {
        final DefaultTreeForTreeLayout<E> tree = getTree();
        return detached != null
               && target != null
               && tree != null
               && !isInBranch(target, detached)
               && (target.equals(tree.getParent(detached)) || canTake(target, detached));
    }

    /// Attaches the item detached to another (or its own) parent, if that can take it, keeping
    /// the focus on it; else says that it can't. Attached to its own parent, it is left where it
    /// was, or moved to the parent's end where dragging it there does that (see
    /// [DraggableTree#setPutBackOnOwnParent]).
    ///
    /// @param target Its new parent.
    public void attach(final E target) {
        if (detached == null || target == null) {
            return;
        }
        final E child = detached;
        if (!canAttach(target)) {
            announce(getName(target) + " can't take " + getName(child) + ".");
            return;
        }
        final E parent = getTree().getParent(child);
        detached = null;
        redrawDetached();
        focusAfterRedraw = child;
        if (target.equals(parent) && root.isPutBackOnOwnParent()) {
            // Put back where it was, as dragging it there does
            announce(getName(child) + " left under " + getName(target) + ".");
            selectionModel.setSelected(child, true);
        } else if (target.equals(parent)) {
            // Moved to its parent's end, as dragging it there does (reordering it)
            move(target, child);
            selectionModel.setSelected(child, true);
            announce(getName(child) + " moved to the end of " + getName(target) + ".");
        } else {
            // Named as it was (e.g. "OR, 3 items"), before it takes it
            final String targetName = getName(target);
            move(target, child);
            selectionModel.setSelected(child, true);
            announce(getName(child) + " attached to " + targetName + ".");
        }
    }

    /// Puts the item detached back where it was, if one is.
    public void cancelDetach() {
        if (detached != null) {
            final E item = detached;
            detached = null;
            redrawDetached();
            announce(getName(item) + " put back.");
        }
    }

    /// Moves the focus, and the selection with it, to an item, e.g. one that was clicked. In a
    /// tree that can't be selected (only read), only the focus moves, and its one tab stop with it.
    ///
    /// @param item The item.
    public void moveTo(final E item) {
        if (selectionModel == null) {
            moveTabStopTo(item);
            return;
        }
        final boolean redrawPending = focusAfterRedraw != null;
        focusAfterRedraw = item;
        if (!selectionModel.isSelected(item)) {
            // Selecting redraws the tree, after which the box is focused
            selectionModel.setSelected(item, true);
        } else if (!redrawPending) {
            afterRefresh();
        }
        // Otherwise the pending redraw focuses it, as its box is about to be replaced
    }

    private void onKeyDown(final KeyDownEvent event) {
        final Box<E> box = treePanel.getTargetBox(Event.as(event.getNativeEvent()), false);
        final DefaultTreeForTreeLayout<E> tree = getTree();
        if (box == null || tree == null) {
            return;
        }
        // Only the item's own keys: not those of a field in it (e.g. an expression term's editor)
        final Element target = event.getNativeEvent().getEventTarget().cast();
        if (!box.getElement().equals(target)) {
            return;
        }
        // Keys pressed quickly may come before the tree is redrawn for the last one, so carry on
        // from the item to be focused
        final E item = focusAfterRedraw != null
                ? focusAfterRedraw
                : box.getItem();
        final int keyCode = event.getNativeKeyCode();
        final boolean handled;
        if (onKey(event, item)) {
            handled = true;
        } else if (event.isControlKeyDown() || event.isMetaKeyDown()) {
            if (keyCode == 'X') {
                detach(item);
                handled = true;
            } else if (keyCode == 'V') {
                attach(item);
                handled = true;
            } else {
                handled = false;
            }
        } else if (keyCode == KeyCodes.KEY_CONTEXT_MENU || (keyCode == KeyCodes.KEY_F10 && event.isShiftKeyDown())) {
            final Rect rect = new Rect(box.getElement()).growY(2);
            ContextMenuEvent.fire(root, new PopupPosition(rect, PopupLocation.BELOW));
            handled = true;
        } else if (event.isShiftKeyDown() || event.isAltKeyDown()) {
            handled = false;
        } else if (keyCode == KeyCodes.KEY_ESCAPE && detached != null) {
            cancelDetach();
            handled = true;
        } else if (keyCode == KeyCodes.KEY_DELETE) {
            remove(item);
            handled = true;
        } else {
            final E next = getNext(tree, item, keyCode);
            if (next != null) {
                onNavigate(next);
                moveTo(next);
            }
            handled = next != null || isNavigationKey(keyCode);
        }
        if (handled) {
            event.preventDefault();
            event.stopPropagation();
        }
    }

    // The item an arrow key (or Home or End) moves to from an item, or null for none
    private static <E> E getNext(final DefaultTreeForTreeLayout<E> tree, final E item, final int keyCode) {
        final E parent = tree.getParent(item);
        final List<E> siblings = parent == null
                ? List.of(item)
                : tree.getChildrenList(parent);
        final int index = siblings.indexOf(item);
        return switch (keyCode) {
            case KeyCodes.KEY_LEFT -> parent;
            case KeyCodes.KEY_RIGHT -> {
                final List<E> children = tree.getChildrenList(item);
                yield children.isEmpty()
                        ? null
                        : children.get(0);
            }
            case KeyCodes.KEY_UP -> index > 0
                    ? siblings.get(index - 1)
                    : null;
            case KeyCodes.KEY_DOWN -> index < siblings.size() - 1
                    ? siblings.get(index + 1)
                    : null;
            case KeyCodes.KEY_HOME -> siblings.get(0);
            case KeyCodes.KEY_END -> siblings.get(siblings.size() - 1);
            default -> null;
        };
    }

    private static boolean isNavigationKey(final int keyCode) {
        return keyCode == KeyCodes.KEY_LEFT
               || keyCode == KeyCodes.KEY_RIGHT
               || keyCode == KeyCodes.KEY_UP
               || keyCode == KeyCodes.KEY_DOWN
               || keyCode == KeyCodes.KEY_HOME
               || keyCode == KeyCodes.KEY_END;
    }

    // The boxes are replaced when the tree is redrawn: shows which are detached again, and focuses
    // the box that should have the focus
    private void afterRefresh() {
        if (detached != null) {
            // (Not otherwise, as it would clear the box a drag is to be dropped on)
            showDetached();
        }
        if (focusAfterRedraw != null) {
            final Box<E> box = treePanel.getBox(focusAfterRedraw);
            focusAfterRedraw = null;
            if (box != null) {
                box.getElement().focus();
                // All of it, its focus ring included (scroll-margin), but scrolling no more than that
                scrollIntoViewIfNeeded(box.getElement());
            }
        }
    }

    // Focuses an item's box, and makes it the tree's one tab stop in place of the last
    private void moveTabStopTo(final E item) {
        final Box<E> box = treePanel.getBox(item);
        if (box != null) {
            for (final E other : getAllItems(getTree())) {
                final Box<E> otherBox = treePanel.getBox(other);
                if (otherBox != null && otherBox != box) {
                    otherBox.getElement().setAttribute("tabindex", "-1");
                }
            }
            box.getElement().setAttribute("tabindex", "0");
            box.getElement().focus();
            scrollIntoViewIfNeeded(box.getElement());
        }
    }

    private static native void scrollIntoViewIfNeeded(Element element) /*-{
        element.scrollIntoView({block: "nearest", inline: "nearest"});
    }-*/;

    // Redraws the tree, which fades the detached item (if any) and the items after it, and marks
    // them
    private void redrawDetached() {
        treePanel.refresh();
        showDetached();
    }

    // Marks the detached item and the items after it, says in each other box whether it can take
    // it, and shows whether the selected item would: as a drop target if so, refusing it if not
    private void showDetached() {
        final DefaultTreeForTreeLayout<E> tree = getTree();
        if (tree == null) {
            return;
        }
        final E target = selectionModel instanceof final SingleItemSelectionModel<E> single
                ? single.getSelectedObject()
                : null;
        for (final E item : getAllItems(tree)) {
            final Box<E> box = treePanel.getBox(item);
            if (box != null) {
                final boolean inDetached = isDetached(item);
                box.setDetached(inDetached);
                box.setDetachedName(detached == null || inDetached
                                ? null
                                : getName(detached),
                        !inDetached && canAttach(item));
                final boolean isTarget = detached != null && item.equals(target) && !inDetached;
                box.showHotspot(isTarget && canAttach(item));
                box.showRefused(isTarget && !canAttach(item));
            }
        }
    }

    // Whether an item is a branch's first item or after it
    private boolean isInBranch(final E item, final E branch) {
        final DefaultTreeForTreeLayout<E> tree = getTree();
        for (E current = item; current != null; current = tree.getParent(current)) {
            if (current.equals(branch)) {
                return true;
            }
        }
        return false;
    }

    private int countItems(final E item) {
        int count = 1;
        for (final E child : getTree().getChildrenList(item)) {
            count += countItems(child);
        }
        return count;
    }

    private static <E> List<E> getAllItems(final DefaultTreeForTreeLayout<E> tree) {
        final List<E> items = new ArrayList<>();
        if (tree.getRoot() != null) {
            addItems(tree, tree.getRoot(), items);
        }
        return items;
    }

    private static <E> void addItems(final DefaultTreeForTreeLayout<E> tree, final E item, final List<E> items) {
        items.add(item);
        for (final E child : tree.getChildrenList(item)) {
            addItems(tree, child, items);
        }
    }

    private DefaultTreeForTreeLayout<E> getTree() {
        return treePanel.getTree();
    }

    // Tells screen reader users something, emptying the message first so that the same one is told
    // again
    private void announce(final String message) {
        liveRegion.setInnerText("");
        Scheduler.get().scheduleDeferred(() -> liveRegion.setInnerText(message));
    }
}
