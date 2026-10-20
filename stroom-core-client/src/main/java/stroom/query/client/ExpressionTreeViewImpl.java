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

package stroom.query.client;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.pipeline.structure.client.view.Box;
import stroom.pipeline.structure.client.view.DraggableTreePanel;
import stroom.pipeline.structure.client.view.TreeKeyboard;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.ExpressionTreePresenter.ExpressionTreeView;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.ArrayList;
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
public class ExpressionTreeViewImpl
        extends ViewWithUiHandlers<ExpressionUiHandlers>
        implements ExpressionTreeView {

    private final ExpressionTreePanel treePanel;
    private final DraggableTreePanel<Item> layoutPanel;
    private final TreeKeyboard<Item> keyboard;
    private MySingleSelectionModel<Item> selectionModel;
    private Consumer<Item> removeHandler;

    @Inject
    public ExpressionTreeViewImpl(final Provider<DocSelectionBoxPresenter> docRefProvider,
                                  final Provider<UserRefSelectionBoxPresenter> userRefProvider,
                                  final UiConfigCache uiConfigCache) {
        treePanel = new ExpressionTreePanel(
                docRefProvider,
                userRefProvider,
                uiConfigCache);
        final ExpressionTreePanel subTreePanel = new ExpressionTreePanel(
                docRefProvider,
                userRefProvider,
                uiConfigCache);
        // The copy of what is dragged, which is only to be seen
        subTreePanel.getElement().setAttribute("aria-hidden", "true");
        subTreePanel.getElement().setAttribute("inert", "");

        layoutPanel = new DraggableTreePanel<Item>(treePanel, subTreePanel) {
            @Override
            protected boolean isValidTarget(final Item parent, final Item child) {
                return parent instanceof Operator;
            }

            @Override
            protected void setSelected(final Item item) {
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
            }

            @Override
            protected void startDragging(final Item parent, final Item child) {
                // Dragging replaces moving with the keyboard
                keyboard.cancelDetach();
                if (selectionModel != null) {
                    endEditing();
                    selectionModel.clear();
                }
            }

            @Override
            protected void endDragging(final Item parent, final Item child) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onChange();
                }
            }
        };
        layoutPanel.setAllowDragging(true);
        layoutPanel.addStyleName("expression-tree-view");

        keyboard = new TreeKeyboard<>(treePanel, layoutPanel, "item", "items") {
            @Override
            protected String getName(final Item item) {
                return ExpressionItemRenderer.getName(treePanel.getTree(), item);
            }

            @Override
            protected boolean canTake(final Item parent, final Item child) {
                return parent instanceof Operator;
            }

            @Override
            protected void move(final Item parent, final Item child) {
                final DefaultTreeForTreeLayout<Item> tree = treePanel.getTree();
                tree.removeChild(child);
                tree.addChild(parent, child);
                treePanel.refresh();
                if (getUiHandlers() != null) {
                    getUiHandlers().onChange();
                }
            }

            @Override
            protected void remove(final Item item) {
                if (removeHandler != null) {
                    cancelDetach();
                    treePanel.setEditingItem(null);
                    removeHandler.accept(item);
                    // The focus goes to the item selected in its place, shown as its label
                    if (selectionModel != null && selectionModel.getSelectedObject() != null) {
                        moveTo(selectionModel.getSelectedObject());
                    }
                }
            }

            @Override
            protected String getBranchPlace() {
                // An operator's items
                return "in it";
            }

            @Override
            protected boolean onKey(final KeyDownEvent event, final Item item) {
                final int keyCode = event.getNativeKeyCode();
                if (selectionModel != null
                    && (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_F2)
                    && !event.isAnyModifierKeyDown()) {
                    startEditing(item);
                    return true;
                }
                return false;
            }

            @Override
            protected void onNavigate(final Item item) {
                // Leaving an item ends its editing (keeping what was entered); the item moved to is
                // shown as its label
                endEditing();
                treePanel.setEditingItem(null);
            }
        };
        // A detached item and the items in it are faded, with the brackets to their items
        treePanel.setFaded(keyboard::isDetached);
        // Before the fields' own handlers, which e.g. close a date's calendar on Escape
        addKeyDownCaptureHandler(layoutPanel.getElement(), this::onFieldKeyDown);
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

    @Override
    public void setTree(final DefaultTreeForTreeLayout<Item> tree) {
        keyboard.cancelDetach();
        treePanel.setTree(tree);
    }

    @Override
    public void setSelectionModel(final MySingleSelectionModel<Item> selectionModel) {
        this.selectionModel = selectionModel;
        treePanel.setSelectionModel(selectionModel);
        keyboard.setSelectionModel(selectionModel);
        // Without a selection model the tree is only for reading, so it can't be rearranged either
        layoutPanel.setAllowDragging(selectionModel != null);
        keyboard.setAllowMoving(selectionModel != null);
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
    public void init(final RestFactory restFactory,
                     final DocRef dataSource,
                     final FieldSelectionListModel fieldSelectionListModel) {
        treePanel.init(restFactory, dataSource, fieldSelectionListModel);
    }

    @Override
    public void setConditionFilter(final Predicate<Condition> conditionFilter) {
        treePanel.setConditionFilter(conditionFilter);
    }

    @Override
    public void write() {
        treePanel.write();
    }

    @Override
    public void endEditing() {
        treePanel.endEditing();
    }

    @Override
    public void edit(final Item item) {
        treePanel.setEditingItem(item);
        treePanel.refresh();
    }

    @Override
    public void setReadOnlyLook(final boolean readOnly) {
        layoutPanel.setStyleName("expression-tree-view--read-only", readOnly);
    }

    @Override
    public Item getDetached() {
        return keyboard.getDetached();
    }

    @Override
    public void detach(final Item item) {
        keyboard.detach(item);
    }

    @Override
    public boolean canAttach(final Item target) {
        return keyboard.canAttach(target);
    }

    @Override
    public void attach(final Item target) {
        keyboard.attach(target);
    }

    @Override
    public void cancelDetach() {
        keyboard.cancelDetach();
    }

    @Override
    public void setRemoveHandler(final Consumer<Item> removeHandler) {
        this.removeHandler = removeHandler;
    }

    @Override
    public void setUiHandlers(final ExpressionUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
        treePanel.setUiHandlers(uiHandlers);
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final Handler handler) {
        return layoutPanel.addContextMenuHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        layoutPanel.fireEvent(event);
    }
}
