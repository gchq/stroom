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
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.HasComponentNode;
import stroom.widget.component.client.Listeners;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExpressionTreePresenter extends MyPresenterWidget<ExpressionTreePresenter.ExpressionTreeView>
        implements HasContextMenuHandlers {

    private DefaultTreeForTreeLayout<Item> tree;
    private MySingleSelectionModel<Item> selectionModel;
    private ExpressionUiHandlers uiHandlers;

    private final Listeners<Item> selectionListeners = new Listeners<>();

    @Inject
    public ExpressionTreePresenter(final EventBus eventBus, final ExpressionTreeView view) {
        super(eventBus, view);
        // The view owns the presenters its term editors make
        if (view instanceof final HasComponentNode hasComponentNode) {
            own(hasComponentNode);
        }
        selectionModel = new MySingleSelectionModel<>();
        view.setSelectionModel(selectionModel);
        // The Delete key deletes the focused item, as the toolbar's Delete does the selected one
        view.setRemoveHandler(item -> {
            if (selectionModel != null) {
                selectionModel.setSelected(item, true);
                delete();
            }
        });
    }

    @Override
    protected void onBind() {
        super.onBind();

        if (selectionModel != null) {
            registerHandler(selectionModel.addSelectionChangeHandler(event -> {
                selectionListeners.fire(selectionModel.getSelectedObject());
                getView().refresh();
            }));
        }
    }

    public HandlerRegistration addSelectionChangeHandler(final SelectionChangeEvent.Handler handler) {
        if (selectionModel == null) {
            return null;
        }

        return selectionModel.addSelectionChangeHandler(handler);
    }

    public void init(final RestFactory restFactory,
                     final DocRef dataSource,
                     final FieldSelectionListModel fieldSelectionListModel) {
        getView().init(restFactory, dataSource, fieldSelectionListModel);
    }

    /**
     * Offer only the conditions that pass {@code conditionFilter}, on top of each field's own
     * {@code ConditionSet}.
     */
    public void setConditionFilter(final Predicate<Condition> conditionFilter) {
        getView().setConditionFilter(conditionFilter);
    }

    public void read(final ExpressionOperator root) {
        clearSelection();

        tree = new ExpressionModel().getTreeFromExpression(root);
        getView().setTree(tree);
        getView().refresh();
    }

    public ExpressionOperator write() {
        // Write any current editing values.
        getView().write();
        // Now get the data from the tree.
        return new ExpressionModel().getExpressionFromTree(tree);
    }

    public void addOperator() {
        addNewItem(new Operator());
    }

    public void addTerm() {
        addNewItem(new Term());
    }

    public void insertValue(final String value) {
        if (selectionModel != null) {
            final Item selectedItem = selectionModel.getSelectedObject();
            if (selectedItem instanceof final Term term) {
                term.setValue(value);
                fireDirty();
                getView().refresh();
            }
        }
    }

    public void copy() {
        if (selectionModel != null) {
            final Item selectedItem = selectionModel.getSelectedObject();
            if (selectedItem != null) {
                Item parent = tree.getParent(selectedItem);
                if (parent == null) {
                    parent = tree.getRoot();
                }

                copy(parent, selectedItem);

                fireDirty();

                getView().refresh();
            }
        }
    }

    private void copy(final Item parent, final Item item) {
        getView().endEditing();
        if (item instanceof final Operator operator) {
            List<Item> children = tree.getChildren(operator);
            if (children != null) {
                children = new ArrayList<>(children);
            }

            final Operator newOperator = new Operator();
            newOperator.setOp(operator.getOp());
            newOperator.setEnabled(operator.isEnabled());
            tree.addChild(parent, newOperator);

            if (children != null) {
                for (final Item child : children) {
                    copy(newOperator, child);
                }
            }
        } else if (item instanceof final Term term) {
            final Term newTerm = new Term();
            newTerm.setField(term.getField());
            newTerm.setCondition(term.getCondition());
            newTerm.setValue(term.getValue());
            newTerm.setDocRef(term.getDocRef());
            newTerm.setEnabled(term.isEnabled());
            tree.addChild(parent, newTerm);
        }
    }

    public void disable() {
        getView().endEditing();
        if (selectionModel != null) {
            final Item selectedItem = selectionModel.getSelectedObject();
            if (selectedItem != null) {
                selectedItem.setEnabled(!selectedItem.isEnabled());

                fireDirty();

                getView().refresh();
            }
        }
    }

    public void delete() {
        getView().endEditing();
        getView().cancelDetach();
        if (selectionModel != null) {
            final Item selectedItem = selectionModel.getSelectedObject();
            if (selectedItem != null) {
                final Item nextSelection = getNextSelection(selectedItem);

                tree.removeChild(selectedItem);
                fireDirty();

                if (nextSelection != null) {
                    selectionModel.setSelected(nextSelection, true);
                } else {
                    clearSelection();
                }
            }
        }
    }

    private Item getNextSelection(final Item selectedItem) {
        final Item parent = tree.getParent(selectedItem);
        if (parent != null) {
            final List<Item> children = tree.getChildren(parent);
            if (children == null || children.isEmpty()) {
                return null;
            }

            if (children.size() == 1) {
                return parent;
            }

            final int index = children.indexOf(selectedItem);
            if (index == 0) {
                return children.get(1);
            } else {
                return children.get(index - 1);
            }
        }

        return null;
    }

    private void addNewItem(final Item item) {
        getView().endEditing();
        if (selectionModel != null) {
            final Item selected = selectionModel.getSelectedObject();

            Item operator = null;

            if (selected != null) {
                if (selected instanceof Operator) {
                    operator = selected;
                } else {
                    final Item parent = tree.getParent(selected);
                    if (parent instanceof Operator) {
                        operator = parent;
                    }
                }
            }

            if (operator == null) {
                final Item root = tree.getRoot();
                if (root instanceof Operator) {
                    operator = root;
                }
            }

            if (operator != null) {
                tree.addChild(operator, item);
                // A new item is edited at once
                getView().edit(item);
                selectionModel.setSelected(item, true);
            }

            fireDirty();
        }
    }

    public void clearSelection() {
        getView().endEditing();
        if (selectionModel != null) {
            selectionModel.clear();
        }
    }

    /// Listens for the user selecting an item in the expression, until the registration is removed.
    ///
    /// @param listener Told what is selected.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<Item> listener) {
        return selectionListeners.add(listener);
    }

    public void fireDirty() {
        if (uiHandlers != null) {
            uiHandlers.onChange();
        }
    }

    public void setUiHandlers(final ExpressionUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
        getView().setUiHandlers(uiHandlers);
    }

    public MySingleSelectionModel<Item> getSelectionModel() {
        return selectionModel;
    }

    public void setSelectionModel(final MySingleSelectionModel<Item> selectionModel) {
        this.selectionModel = selectionModel;
        getView().setSelectionModel(selectionModel);
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final Handler handler) {
        return getView().addContextMenuHandler(handler);
    }

    /// Gives (or takes away) the tree the read-only look, its items greyed as a read-only field's
    /// value is, e.g. for an expression the user can't change.
    ///
    /// @param readOnly Whether it has the look.
    public void setReadOnlyLook(final boolean readOnly) {
        getView().setReadOnlyLook(readOnly);
    }

    /// Gets the context menu's items for moving the selected item (and the items in it) to another
    /// operator, as dragging it does, and as Ctrl+X and Ctrl+V do in the tree: Detach, and while an
    /// item is detached "Attach '…' here" (only where it would be taken) and "Put '…' back".
    ///
    /// @param priority The priority of the first, after which the others come.
    /// @return The items, none if the tree can't be changed.
    public List<stroom.widget.menu.client.presenter.Item> getMoveMenuItems(final int priority) {
        final List<stroom.widget.menu.client.presenter.Item> menuItems = new ArrayList<>();
        if (selectionModel == null) {
            return menuItems;
        }
        final Item selectedItem = selectionModel.getSelectedObject();
        final Item detached = getView().getDetached();
        if (detached == null) {
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority)
                    .icon(SvgImage.MOVE)
                    .text("Detach")
                    .enabled(selectedItem != null && tree.getParent(selectedItem) != null)
                    .command(() -> getView().detach(selectedItem))
                    .build());
        } else {
            final String name = ExpressionItemRenderer.getName(tree, detached);
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority)
                    .icon(SvgImage.MOVE)
                    .text("Attach '" + name + "' here")
                    .enabled(getView().canAttach(selectedItem))
                    .command(() -> getView().attach(selectedItem))
                    .build());
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority + 1)
                    .icon(SvgImage.UNDO)
                    .text("Put '" + name + "' back")
                    .command(() -> getView().cancelDetach())
                    .build());
        }
        return menuItems;
    }


    // --------------------------------------------------------------------------------


    public interface ExpressionTreeView extends View, HasContextMenuHandlers, HasUiHandlers<ExpressionUiHandlers> {

        void setTree(DefaultTreeForTreeLayout<Item> model);

        void setSelectionModel(MySingleSelectionModel<Item> selectionModel);

        void init(RestFactory restFactory,
                  DocRef dataSource,
                  FieldSelectionListModel fieldSelectionListModel);

        void write();

        void setConditionFilter(Predicate<Condition> conditionFilter);

        void endEditing();

        void refresh();

        /// Opens an item for editing: it shows its editor while it is selected (others show their
        /// labels).
        ///
        /// @param item The item.
        void edit(Item item);

        /// Gives (or takes away) the tree the read-only look, its items greyed.
        ///
        /// @param readOnly Whether it has the look.
        void setReadOnlyLook(boolean readOnly);

        /// @return The item detached (to be attached to another operator), or null for none.
        Item getDetached();

        /// Detaches an item (and the items in it) to be attached to another operator. The root
        /// can't be.
        ///
        /// @param item The item.
        void detach(Item item);

        /// @param target An item.
        /// @return Whether the item detached can be attached to it.
        boolean canAttach(Item target);

        /// Attaches the item detached to another operator, if that can take it.
        ///
        /// @param target The operator.
        void attach(Item target);

        /// Puts the item detached back where it was, if one is.
        void cancelDetach();

        /// Sets what to do when the tree asks for an item to be removed, e.g. with the Delete key.
        ///
        /// @param removeHandler What to do.
        void setRemoveHandler(Consumer<Item> removeHandler);
    }
}
