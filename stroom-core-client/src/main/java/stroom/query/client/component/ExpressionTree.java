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

package stroom.query.client.component;

import stroom.docref.DocRef;
import stroom.pipeline.structure.client.component.Box;
import stroom.pipeline.structure.client.component.DraggableTreePanel;
import stroom.pipeline.structure.client.component.TreeKeyboard;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.ExpressionItemRenderer;
import stroom.query.client.ExpressionTreePanel;
import stroom.query.client.FieldSelectionListModel;
import stroom.query.client.Item;
import stroom.query.client.Operator;
import stroom.query.client.TermEditor;
import stroom.widget.component.client.Component;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// An expression's tree of terms and operators.
///
/// It is a tree for assistive technology (`role="tree"`, see [ExpressionItemRenderer]) and for the
/// keyboard (see [TreeKeyboard]): the arrow keys move the selection, showing the item selected as
/// its label; a click edits an item (its editor in place of its label), as do Enter and F2, which
/// focus its first field. Tab and Shift+Tab move through its fields, and past the last or first
/// back to the item, which stops editing it (keeping what was entered), as Escape does. Its items
/// can be dragged to another operator, or moved with the keyboard (Ctrl+X and Ctrl+V) or the context
/// menu (see [#detach]).
public class ExpressionTree extends Component implements HasContextMenuHandlers {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DraggableTreePanel<Item> layoutPanel;

    // DraggableTreePanel takes its two panels through setTreePanels, not as template children
    private final ExpressionTreePanel treePanel = new ExpressionTreePanel();
    private final ExpressionTreePanel subTreePanel = new ExpressionTreePanel();
    private final TreeKeyboard<Item> keyboard;
    private MySingleSelectionModel<Item> selectionModel;
    private Consumer<Item> removeHandler;
    private Runnable onChange = () -> {
    };

    /// Makes the tree, with no expression yet.
    public ExpressionTree() {
        initWidget(BINDER.createAndBindUi(this));
        layoutPanel.setTreePanels(treePanel, subTreePanel);

        keyboard = new TreeKeyboard<>(treePanel, layoutPanel, "item", "items");
        keyboard.setNameOf(item -> ExpressionItemRenderer.getName(treePanel.getTree(), item));
        keyboard.setCanTake((parent, child) -> parent instanceof Operator);
        keyboard.setOnMove((parent, child) -> {
            final DefaultTreeForTreeLayout<Item> tree = treePanel.getTree();
            tree.removeChild(child);
            tree.addChild(parent, child);
            treePanel.refresh();
            onChange.run();
        });
        keyboard.setOnRemove(item -> {
            if (removeHandler != null) {
                keyboard.cancelDetach();
                treePanel.setEditingItem(null);
                removeHandler.accept(item);
                // The focus goes to the item selected in its place, shown as its label
                if (selectionModel != null && selectionModel.getSelectedObject() != null) {
                    keyboard.moveTo(selectionModel.getSelectedObject());
                }
            }
        });
        // An operator's items
        keyboard.setBranchPlace("in it");
        keyboard.setOnKey((event, item) -> {
            final int keyCode = event.getNativeKeyCode();
            if (selectionModel != null
                && (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_F2)
                && !event.isAnyModifierKeyDown()) {
                startEditing(item);
                return true;
            }
            return false;
        });
        keyboard.setOnNavigate(item -> {
            // Leaving an item ends its editing (keeping what was entered); the item moved to is
            // shown as its label
            endEditing();
            treePanel.setEditingItem(null);
        });

        layoutPanel.setIsValidTarget((parent, child) -> parent instanceof Operator);
        layoutPanel.setOnSelect(item -> {
            if (selectionModel != null) {
                if (item == null) {
                    endEditing();
                    selectionModel.setSelected(null, true);
                } else if (!selectionModel.isSelected(item)) {
                    // A clicked item is edited, and has the focus, so that the keyboard carries
                    // on from it
                    endEditing();
                    treePanel.setEditingItem(item);
                    keyboard.moveTo(item);
                } else if (!treePanel.isEditingItem(item)) {
                    // Selected with the keyboard: a click edits it
                    edit(item);
                }
            }
        });
        layoutPanel.setOnDragStart((parent, child) -> {
            // Dragging replaces moving with the keyboard
            keyboard.cancelDetach();
            if (selectionModel != null) {
                endEditing();
                selectionModel.clear();
            }
        });
        layoutPanel.setOnDragEnd((parent, child) -> {
            onChange.run();
        });
        // A detached item and the items in it are faded, with the brackets to their items
        treePanel.setFaded(keyboard::isDetached);
        // Before the fields' own handlers, which e.g. close a date's calendar on Escape
        addKeyDownCaptureHandler(layoutPanel.getElement(), this::onFieldKeyDown);
    }

    /// @return Its term editors (the tree's, and the dragged copy's), for its controller to
    /// attach to.
    public List<TermEditor> getTermEditors() {
        return Arrays.asList(treePanel.getTermEditor(), subTreePanel.getTermEditor());
    }

    // Edits an item, and focuses its first field once its editor is shown
    private void startEditing(final Item item) {
        if (selectionModel.isSelected(item)) {
            edit(item);
        } else {
            // Only the selected item shows its editor, and the focused one may not be selected (the
            // root, the tab stop while nothing is): selecting it redraws the tree at the end of
            // this event, showing its editor
            treePanel.setEditingItem(item);
            selectionModel.setSelected(item, true);
        }
        // After that redraw, and the editor showing itself (which it defers)
        Scheduler.get().scheduleFinally(() -> Scheduler.get().scheduleDeferred(() -> {
            final Box<Item> box = treePanel.getBox(item);
            if (box != null) {
                final List<Element> fields = getFields(box.getElement());
                if (!fields.isEmpty()) {
                    fields.get(0).focus();
                }
            }
        }));
    }

    // Stops editing an item, keeping what was entered, and puts the focus back on it
    private void stopEditing(final Item item) {
        treePanel.hideEditorPopups();
        endEditing();
        treePanel.setEditingItem(null);
        treePanel.refresh();
        keyboard.moveTo(item);
    }

    // A key pressed in a field of the item being edited: Escape, and Tab past its last field
    // (Shift+Tab past its first), stop editing it; the rest are the field's
    private void onFieldKeyDown(final NativeEvent event) {
        final Element target = event.getEventTarget().cast();
        final Box<Item> box = treePanel.getTargetBox(Event.as(event), false);
        if (box == null || box.getElement().equals(target) || !treePanel.isEditingItem(box.getItem())) {
            return;
        }
        final int keyCode = event.getKeyCode();
        final boolean stop;
        if (keyCode == KeyCodes.KEY_ESCAPE) {
            // Escape first closes a field's popup, which the field does
            stop = !treePanel.isEditorPopupShowing();
        } else if (keyCode == KeyCodes.KEY_TAB && !event.getCtrlKey() && !event.getAltKey()) {
            final List<Element> fields = getFields(box.getElement());
            final int index = indexOfField(fields, target);
            stop = event.getShiftKey()
                    ? index == 0
                    : index == fields.size() - 1;
        } else {
            stop = false;
        }
        if (stop) {
            event.preventDefault();
            event.stopPropagation();
            stopEditing(box.getItem());
        }
    }

    // The index of the field that is, or holds, an element, or -1 for none
    private static int indexOfField(final List<Element> fields, final Element element) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).isOrHasChild(element)) {
                return i;
            }
        }
        return -1;
    }

    // The fields in an item's box that Tab moves through, in order
    private static List<Element> getFields(final Element box) {
        final List<Element> fields = new ArrayList<>();
        final JsArray<Element> elements = getTabbable(box);
        for (int i = 0; i < elements.length(); i++) {
            fields.add(elements.get(i));
        }
        return fields;
    }

    private static native JsArray<Element> getTabbable(Element container) /*-{
        var all = container.querySelectorAll("input, select, textarea, button, [tabindex]");
        var tabbable = [];
        for (var i = 0; i < all.length; i++) {
            var element = all[i];
            if (element.tabIndex >= 0 && !element.disabled && element.getClientRects().length > 0) {
                tabbable.push(element);
            }
        }
        return tabbable;
    }-*/;

    private static native void addKeyDownCaptureHandler(Element element, Consumer<NativeEvent> handler) /*-{
        element.addEventListener("keydown", function (event) {
            handler.@java.util.function.Consumer::accept(*)(event);
        }, true);
    }-*/;

    /// @param tree The expression's tree of items.
    public void setTree(final DefaultTreeForTreeLayout<Item> tree) {
        keyboard.cancelDetach();
        treePanel.setTree(tree);
    }

    /// @param selectionModel The selection, or null for a tree only to be read, which can't be
    ///                       rearranged either.
    public void setSelectionModel(final MySingleSelectionModel<Item> selectionModel) {
        this.selectionModel = selectionModel;
        treePanel.setSelectionModel(selectionModel);
        keyboard.setSelectionModel(selectionModel);
        // Without a selection model the tree is only for reading, so it can't be rearranged either
        layoutPanel.setAllowDragging(selectionModel != null);
        keyboard.setAllowMoving(selectionModel != null);
    }

    /// Draws the tree again.
    public void refresh() {
        treePanel.refresh();
    }

    /// Sets what a term's field is chosen from, and its values suggested from.
    ///
    /// @param dataSource              The data source.
    /// @param fieldSelectionListModel The data source's fields.
    public void init(final DocRef dataSource,
                     final FieldSelectionListModel fieldSelectionListModel) {
        treePanel.init(dataSource, fieldSelectionListModel);
    }

    /// @param conditionFilter Which conditions a term's editor offers, beyond its field's own.
    public void setConditionFilter(final Predicate<Condition> conditionFilter) {
        treePanel.setConditionFilter(conditionFilter);
    }

    /// Writes what is entered in the editor into the item being edited.
    public void write() {
        treePanel.write();
    }

    /// Stops editing the item being edited, keeping what was entered.
    public void endEditing() {
        treePanel.endEditing();
    }

    /// Opens an item for editing: it shows its editor while it is selected (others show their
    /// labels).
    ///
    /// @param item The item.
    public void edit(final Item item) {
        treePanel.setEditingItem(item);
        treePanel.refresh();
    }

    /// Gives (or takes away) the tree the read-only look, its items greyed.
    ///
    /// @param readOnly Whether it has the look.
    public void setReadOnlyLook(final boolean readOnly) {
        layoutPanel.setStyleName("expression-tree-view--read-only", readOnly);
    }

    /// @return The item detached (to be attached to another operator), or null for none.
    public Item getDetached() {
        return keyboard.getDetached();
    }

    /// Detaches an item (and the items in it) to be attached to another operator. The root
    /// can't be.
    ///
    /// @param item The item.
    public void detach(final Item item) {
        keyboard.detach(item);
    }

    /// @param target An item.
    /// @return Whether the item detached can be attached to it.
    public boolean canAttach(final Item target) {
        return keyboard.canAttach(target);
    }

    /// Attaches the item detached to another operator, if that can take it.
    ///
    /// @param target The operator.
    public void attach(final Item target) {
        keyboard.attach(target);
    }

    /// Puts the item detached back where it was, if one is.
    public void cancelDetach() {
        keyboard.cancelDetach();
    }

    /// Sets what to do when the tree asks for an item to be removed, e.g. with the Delete key.
    ///
    /// @param removeHandler What to do.
    public void setRemoveHandler(final Consumer<Item> removeHandler) {
        this.removeHandler = removeHandler;
    }

    /// @param onChange Run when the user changes the expression: an item edited or moved.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
        treePanel.setOnChange(onChange);
    }

    /// @param onSearch Run when Enter is pressed in a term's value, to run the query.
    public void setOnSearch(final Runnable onSearch) {
        treePanel.setOnSearch(onSearch);
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final Handler handler) {
        return layoutPanel.addContextMenuHandler(handler);
    }



    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExpressionTree> {

    }
}
