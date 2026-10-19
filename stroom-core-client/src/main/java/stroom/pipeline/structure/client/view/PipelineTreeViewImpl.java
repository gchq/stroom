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

package stroom.pipeline.structure.client.view;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.pipeline.structure.client.presenter.PipelineTreePresenter.PipelineTreeView;
import stroom.pipeline.structure.client.presenter.PipelineTreeUiHandlers;
import stroom.pipeline.structure.client.presenter.StructureValidationUtil;
import stroom.util.shared.Severity;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.MySingleSelectionModel;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.SelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The pipeline structure editor's tree of elements.
///
/// It is a tree for assistive technology (`role="tree"`, see [PipelineElementRenderer]) and for
/// the keyboard, one tab stop: Left moves to an element's parent and Right to its first child (the
/// tree grows to the right); Up and Down to its previous and next sibling, and Home and End to its
/// first and last, stopping at the ends. Selection follows the focus. Delete removes the element
/// and the context menu key (or Shift+F10) opens its menu.
///
/// Where it may be changed, an element and the elements after it can be moved to another parent,
/// as dragging them does: Ctrl+X detaches it (it stays where it is, faded, until it is attached),
/// the arrow keys choose its new parent (which shows whether it would take it: green if so, red if
/// not, as when dragging), Ctrl+V attaches it there, and Escape puts it back.
public class PipelineTreeViewImpl extends ViewWithUiHandlers<PipelineTreeUiHandlers> implements PipelineTreeView {

    private final PipelineTreePanel treePanel;
    private final PipelineTreePanel subTreePanel;
    private final DraggableTreePanel<PipelineElement> layoutPanel;
    // Tells screen reader users what detaching and attaching did
    private final Element liveRegion;
    private SelectionModel<PipelineElement> selectionModel;
    private boolean allowNullSelection = true;
    private boolean allowDragging;
    private PipelineModel pipelineModel;
    // The element detached to be attached to another parent, if any
    private PipelineElement detached;
    // The element whose box to focus once the tree is redrawn (which replaces the boxes)
    private PipelineElement focusAfterRedraw;

    @Inject
    public PipelineTreeViewImpl() {
        treePanel = new PipelineTreePanel();
        subTreePanel = new PipelineTreePanel();
        // The copy of what is dragged, which follows the pointer, is for sight only: not a second
        // tree for assistive technology, nor focusable
        subTreePanel.getElement().setAttribute("aria-hidden", "true");
        subTreePanel.getElement().setAttribute("inert", "");

        layoutPanel = new DraggableTreePanel<>(treePanel, subTreePanel) {
            @Override
            protected boolean isValidTarget(final PipelineElement parent, final PipelineElement child) {
                return canTake(parent, child);
            }

            @Override
            protected void startDragging(final PipelineElement parent, final PipelineElement child) {
                // Dragging replaces moving with the keyboard
                cancelDetach();
            }

            @Override
            protected void endDragging(final PipelineElement parent, final PipelineElement child) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onMove(parent, child);
                }
            }

            @Override
            protected void setSelected(final PipelineElement item) {
                if (item != null) {
                    // A clicked element has the focus too, so that the keyboard carries on from it
                    moveTo(item);
                } else if (allowNullSelection) {
                    selectionModel.setSelected(null, true);
                }
            }
        };
        // The order of an element's children doesn't matter (the model moves nothing), so dropping
        // an element where it came from leaves it as it was
        layoutPanel.setPutBackOnOwnParent(true);
        layoutPanel.setWidth("100%");
        layoutPanel.setHeight("100%");
        layoutPanel.addDomHandler(this::onKeyDown, KeyDownEvent.getType());
        treePanel.setAfterRefresh(this::afterRefresh);
        // A detached element and those after it are faded, with the connectors into them
        treePanel.setFaded(element -> detached != null && isInBranch(element, detached));

        liveRegion = Document.get().createDivElement();
        liveRegion.setClassName("sr-only pipeline-tree-status");
        liveRegion.setAttribute("role", "status");
        liveRegion.setAttribute("aria-live", "polite");
        layoutPanel.getElement().appendChild(liveRegion);
    }

    @Override
    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
        detached = null;
        treePanel.setPipelineModel(pipelineModel);
        subTreePanel.setPipelineModel(pipelineModel);
    }

    @Override
    public void setTree(final DefaultTreeForTreeLayout<PipelineElement> tree) {
        treePanel.setTree(tree);
    }

    @Override
    public void setSelectionModel(final SelectionModel<PipelineElement> selectionModel) {
        this.selectionModel = selectionModel;
        treePanel.setSelectionModel(selectionModel);
    }

    @Override
    public void refresh() {
        treePanel.refresh();
    }

    @Override
    public Widget asWidget() {
        return layoutPanel;
    }

    @Override
    public void setAllowDragging(final boolean allowDragging) {
        this.allowDragging = allowDragging;
        layoutPanel.setAllowDragging(allowDragging);
        if (!allowDragging) {
            cancelDetach();
        }
    }

    @Override
    public void setAllowNullSelection(final boolean allowNullSelection) {
        this.allowNullSelection = allowNullSelection;
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final Handler handler) {
        return layoutPanel.addContextMenuHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        layoutPanel.fireEvent(event);
    }

    @Override
    public int getTreeHeight() {
        return treePanel.getTreeHeight();
    }

    @Override
    public void setDisabledElements(final List<PipelineElement> disabledElements) {
        treePanel.setDisabledElements(disabledElements);
    }

    @Override
    public void setSeverities(final Map<String, Severity> elementIdToSeveritiesMap) {
        // The structure editor doesn't show problems (the stepping screen's pipeline bar does)
    }

    @Override
    public PipelineElement getDetached() {
        return detached;
    }

    @Override
    public void detach(final PipelineElement element) {
        if (!allowDragging || element == null || getTree() == null || getTree().getParent(element) == null) {
            // The source can't be moved
            return;
        }
        detached = element;
        redrawDetached();
        final int count = countElements(element);
        announce(element.getDisplayName() + (count > 1
                ? " and the " + (count - 1) + (count == 2
                        ? " element"
                        : " elements") + " after it"
                : "")
                 + " detached. Move to its new parent and press Control V to attach it, or Escape to put "
                 + "it back.");
    }

    @Override
    public boolean canAttach(final PipelineElement target) {
        final DefaultTreeForTreeLayout<PipelineElement> tree = getTree();
        return detached != null
               && target != null
               && tree != null
               && !isInBranch(target, detached)
               && (target.equals(tree.getParent(detached)) || canTake(target, detached));
    }

    @Override
    public void attach(final PipelineElement target) {
        if (detached == null || target == null) {
            return;
        }
        final PipelineElement child = detached;
        if (!canAttach(target)) {
            announce(target.getDisplayName() + " can't take " + child.getDisplayName() + ".");
            return;
        }
        final PipelineElement parent = getTree().getParent(child);
        detached = null;
        redrawDetached();
        focusAfterRedraw = child;
        if (target.equals(parent)) {
            // Put back where it was
            announce(child.getDisplayName() + " left under " + target.getDisplayName() + ".");
            selectionModel.setSelected(child, true);
        } else {
            if (getUiHandlers() != null) {
                getUiHandlers().onMove(target, child);
            }
            selectionModel.setSelected(child, true);
            announce(child.getDisplayName() + " attached to " + target.getDisplayName() + ".");
        }
    }

    @Override
    public void cancelDetach() {
        if (detached != null) {
            final PipelineElement element = detached;
            detached = null;
            redrawDetached();
            announce(element.getDisplayName() + " put back.");
        }
    }

    private void onKeyDown(final KeyDownEvent event) {
        final Box<PipelineElement> box = treePanel.getTargetBox(Event.as(event.getNativeEvent()), false);
        final DefaultTreeForTreeLayout<PipelineElement> tree = getTree();
        if (box == null || tree == null) {
            return;
        }
        // Keys pressed quickly may come before the tree is redrawn for the last one, so carry on
        // from the element to be focused
        final PipelineElement element = focusAfterRedraw != null
                ? focusAfterRedraw
                : box.getItem();
        final int keyCode = event.getNativeKeyCode();
        final boolean handled;
        if (event.isControlKeyDown() || event.isMetaKeyDown()) {
            if (keyCode == 'X') {
                detach(element);
                handled = true;
            } else if (keyCode == 'V') {
                attach(element);
                handled = true;
            } else {
                handled = false;
            }
        } else if (keyCode == KeyCodes.KEY_CONTEXT_MENU || (keyCode == KeyCodes.KEY_F10 && event.isShiftKeyDown())) {
            final Rect rect = new Rect(box.getElement()).growY(2);
            ContextMenuEvent.fire(this, new PopupPosition(rect, PopupLocation.BELOW));
            handled = true;
        } else if (event.isShiftKeyDown() || event.isAltKeyDown()) {
            handled = false;
        } else if (keyCode == KeyCodes.KEY_ESCAPE && detached != null) {
            cancelDetach();
            handled = true;
        } else if (keyCode == KeyCodes.KEY_DELETE) {
            if (getUiHandlers() != null) {
                getUiHandlers().onRemove(element);
            }
            handled = true;
        } else {
            final PipelineElement next = getNext(tree, element, keyCode);
            if (next != null) {
                moveTo(next);
            }
            handled = next != null || isNavigationKey(keyCode);
        }
        if (handled) {
            event.preventDefault();
            event.stopPropagation();
        }
    }

    // The element an arrow key (or Home or End) moves to from an element, or null for none
    private static PipelineElement getNext(final DefaultTreeForTreeLayout<PipelineElement> tree,
                                           final PipelineElement element,
                                           final int keyCode) {
        final PipelineElement parent = tree.getParent(element);
        final List<PipelineElement> siblings = parent == null
                ? List.of(element)
                : tree.getChildrenList(parent);
        final int index = siblings.indexOf(element);
        return switch (keyCode) {
            case KeyCodes.KEY_LEFT -> parent;
            case KeyCodes.KEY_RIGHT -> {
                final List<PipelineElement> children = tree.getChildrenList(element);
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

    // Moves the focus, and the selection with it, to an element
    private void moveTo(final PipelineElement element) {
        final boolean redrawPending = focusAfterRedraw != null;
        focusAfterRedraw = element;
        if (!selectionModel.isSelected(element)) {
            // Selecting redraws the tree, after which the box is focused
            selectionModel.setSelected(element, true);
        } else if (!redrawPending) {
            afterRefresh();
        }
        // Otherwise the pending redraw focuses it, as its box is about to be replaced
    }

    // The boxes are replaced when the tree is redrawn: shows which are detached again, and focuses
    // the box that should have the focus
    private void afterRefresh() {
        if (detached != null) {
            // (Not otherwise, as it would clear the box a drag is to be dropped on)
            showDetached();
        }
        if (focusAfterRedraw != null) {
            final Box<PipelineElement> box = treePanel.getBox(focusAfterRedraw);
            focusAfterRedraw = null;
            if (box != null) {
                box.getElement().focus();
                // All of it, its focus ring included (scroll-margin), but scrolling no more than that
                scrollIntoViewIfNeeded(box.getElement());
            }
        }
    }

    private static native void scrollIntoViewIfNeeded(Element element) /*-{
        element.scrollIntoView({block: "nearest", inline: "nearest"});
    }-*/;

    // Redraws the tree, which fades the detached element (if any) and the elements after it, with
    // the connectors into them, and marks them
    private void redrawDetached() {
        treePanel.refresh();
        showDetached();
    }

    // Marks the detached element and the elements after it, and shows whether the selected
    // element would take it: green if so, red if not
    private void showDetached() {
        final DefaultTreeForTreeLayout<PipelineElement> tree = getTree();
        if (tree == null) {
            return;
        }
        final PipelineElement target = selectionModel instanceof final MySingleSelectionModel<PipelineElement> single
                ? single.getSelectedObject()
                : null;
        for (final PipelineElement element : getAllElements(tree)) {
            final Box<PipelineElement> box = treePanel.getBox(element);
            if (box instanceof final PipelineElementBox pipelineElementBox) {
                final boolean inDetached = detached != null && isInBranch(element, detached);
                pipelineElementBox.setDetached(inDetached);
                pipelineElementBox.setDetachedName(detached == null || inDetached
                                ? null
                                : detached.getDisplayName(),
                        !inDetached && canAttach(element));
                final boolean isTarget = detached != null && element.equals(target) && !isInBranch(element, detached);
                pipelineElementBox.showHotspot(isTarget && canAttach(element));
                pipelineElementBox.showRefused(isTarget && !canAttach(element));
            }
        }
    }

    // Whether a parent can take a child, by their types and the parent's children
    private boolean canTake(final PipelineElement parent, final PipelineElement child) {
        final PipelineElementType parentType = pipelineModel.getElementType(parent);
        final PipelineElementType childType = pipelineModel.getElementType(child);
        if (parentType == null || childType == null) {
            return false;
        }
        final List<PipelineElement> children = treePanel.getTree().getChildren(parent);
        final int childCount = children == null
                ? 0
                : children.size();
        return StructureValidationUtil.isValidChildType(parentType, childType, childCount);
    }

    // Whether an element is a branch's first element or after it
    private boolean isInBranch(final PipelineElement element, final PipelineElement branch) {
        final DefaultTreeForTreeLayout<PipelineElement> tree = getTree();
        for (PipelineElement current = element; current != null; current = tree.getParent(current)) {
            if (current.equals(branch)) {
                return true;
            }
        }
        return false;
    }

    private int countElements(final PipelineElement element) {
        int count = 1;
        for (final PipelineElement child : getTree().getChildrenList(element)) {
            count += countElements(child);
        }
        return count;
    }

    private static List<PipelineElement> getAllElements(final DefaultTreeForTreeLayout<PipelineElement> tree) {
        final List<PipelineElement> elements = new ArrayList<>();
        if (tree.getRoot() != null) {
            addElements(tree, tree.getRoot(), elements);
        }
        return elements;
    }

    private static void addElements(final DefaultTreeForTreeLayout<PipelineElement> tree,
                                    final PipelineElement element,
                                    final List<PipelineElement> elements) {
        elements.add(element);
        for (final PipelineElement child : tree.getChildrenList(element)) {
            addElements(tree, child, elements);
        }
    }

    private DefaultTreeForTreeLayout<PipelineElement> getTree() {
        return treePanel.getTree();
    }

    // Tells screen reader users something, emptying the message first so that the same one is told
    // again
    private void announce(final String message) {
        liveRegion.setInnerText("");
        Scheduler.get().scheduleDeferred(() -> liveRegion.setInnerText(message));
    }
}
