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
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.ExpressionItemRenderer;
import stroom.query.client.ExpressionModel;
import stroom.query.client.FieldSelectionListModel;
import stroom.query.client.Item;
import stroom.query.client.Operator;
import stroom.query.client.Term;
import stroom.query.client.TermEditor;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.Listeners;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.util.client.SingleItemSelectionModel;

import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// An expression's tree of terms and operators ([ExpressionTree]): reads and writes the
/// expression, keeps its selection, adds, copies, disables and deletes its items, and gives the
/// context menu's items for moving them. It attaches a [TermEditorController] to each of the tree's
/// term editors.
public class ExpressionTreeController extends Controller implements HasContextMenuHandlers {

    private final Provider<TermEditorController> termEditorControllerProvider;
    private ExpressionTree view;
    private DefaultTreeForTreeLayout<Item> tree;
    private SingleItemSelectionModel<Item> selectionModel;
    private Runnable onChange = () -> {
    };

    private final Listeners<Item> selectionListeners = new Listeners<>();

    /// Made by GIN; [#attach(ExpressionTree)] gives it its tree.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param termEditorControllerProvider Makes what each of its term editors needs.
    @Inject
    public ExpressionTreeController(final EventBus eventBus,
                                    final Provider<TermEditorController> termEditorControllerProvider) {
        super(eventBus);
        this.termEditorControllerProvider = termEditorControllerProvider;
        selectionModel = new SingleItemSelectionModel<>();
        own(selectionModel.addSelectionChangeHandler(event -> {
            selectionListeners.fire(selectionModel.getSelectedObject());
            view.refresh();
        }));
    }

    /// Attaches this to a tree, e.g. one a template declares, giving each of its term editors what
    /// it needs. The tree is then this controller's widget.
    ///
    /// @param view The tree.
    /// @return This, for its owner to keep.
    public ExpressionTreeController attach(final ExpressionTree view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        for (final TermEditor termEditor : view.getTermEditors()) {
            make(termEditorControllerProvider).attach(termEditor);
        }
        view.setSelectionModel(selectionModel);
        // The Delete key deletes the focused item, as the toolbar's Delete does the selected one
        view.setRemoveHandler(item -> {
            if (selectionModel != null) {
                selectionModel.setSelected(item, true);
                delete();
            }
        });
        return this;
    }

    /// Listens for the selection changing, e.g. to enable the buttons that act on it.
    ///
    /// @param handler Told when the selection changes.
    /// @return The registration, or null if the tree has no selection (it is read only).
    public HandlerRegistration addSelectionChangeHandler(final SelectionChangeEvent.Handler handler) {
        if (selectionModel == null) {
            return null;
        }

        return selectionModel.addSelectionChangeHandler(handler);
    }

    /// Sets what a term's field is chosen from, and its values suggested from.
    ///
    /// @param dataSource              The data source.
    /// @param fieldSelectionListModel The data source's fields.
    public void init(final DocRef dataSource,
                     final FieldSelectionListModel fieldSelectionListModel) {
        view.init(dataSource, fieldSelectionListModel);
    }

    /// Offer only the conditions that pass `conditionFilter`, on top of each field's own
    /// `ConditionSet`.
    ///
    /// @param conditionFilter Which conditions are offered.
    public void setConditionFilter(final Predicate<Condition> conditionFilter) {
        view.setConditionFilter(conditionFilter);
    }

    /// Shows an expression, with nothing selected.
    ///
    /// @param root The expression.
    public void read(final ExpressionOperator root) {
        clearSelection();

        tree = new ExpressionModel().getTreeFromExpression(root);
        view.setTree(tree);
        view.refresh();
    }

    /// @return The expression as edited, with any term still being edited written to it.
    public ExpressionOperator write() {
        // Write any current editing values.
        view.write();
        // Now get the data from the tree.
        return new ExpressionModel().getExpressionFromTree(tree);
    }

    /// Adds an operator to the selected operator (or to the selected term's), or to the root.
    public void addOperator() {
        addNewItem(new Operator());
    }

    /// Adds a term to the selected operator (or to the selected term's), or to the root.
    public void addTerm() {
        addNewItem(new Term());
    }

    /// Sets the selected term's value, e.g. one chosen from a list.
    ///
    /// @param value The value.
    public void insertValue(final String value) {
        if (selectionModel != null) {
            final Item selectedItem = selectionModel.getSelectedObject();
            if (selectedItem instanceof final Term term) {
                term.setValue(value);
                fireDirty();
                view.refresh();
            }
        }
    }

    /// Copies the selected item (and the items in it), adding the copy to the same operator.
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

                view.refresh();
            }
        }
    }

    private void copy(final Item parent, final Item item) {
        view.endEditing();
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

    /// Disables the selected item, or enables it if it is disabled.
    public void disable() {
        view.endEditing();
        if (selectionModel != null) {
            final Item selectedItem = selectionModel.getSelectedObject();
            if (selectedItem != null) {
                selectedItem.setEnabled(!selectedItem.isEnabled());

                fireDirty();

                view.refresh();
            }
        }
    }

    /// Deletes the selected item (and the items in it).
    public void delete() {
        view.endEditing();
        view.cancelDetach();
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
        view.endEditing();
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
                view.edit(item);
                selectionModel.setSelected(item, true);
            }

            fireDirty();
        }
    }

    /// Ends any editing and selects nothing.
    public void clearSelection() {
        view.endEditing();
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

    /// Tells the owner that the expression has changed.
    public void fireDirty() {
        onChange.run();
    }

    /// @param onChange Run when the user changes the expression.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
        view.setOnChange(onChange);
    }

    /// @param onSearch Run when Enter is pressed in a term's value, to run the query.
    public void setOnSearch(final Runnable onSearch) {
        view.setOnSearch(onSearch);
    }

    /// @return The selection, or null if items can't be selected (the tree is read only).
    public SingleItemSelectionModel<Item> getSelectionModel() {
        return selectionModel;
    }

    /// @param selectionModel The selection, or null for none, so that items can't be selected (for
    ///                       an expression that can't be changed).
    public void setSelectionModel(final SingleItemSelectionModel<Item> selectionModel) {
        this.selectionModel = selectionModel;
        view.setSelectionModel(selectionModel);
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final Handler handler) {
        return view.addContextMenuHandler(handler);
    }

    /// Gives (or takes away) the tree the read-only look, its items greyed as a read-only field's
    /// value is, e.g. for an expression the user can't change.
    ///
    /// @param readOnly Whether it has the look.
    public void setReadOnlyLook(final boolean readOnly) {
        view.setReadOnlyLook(readOnly);
    }

    /// Gets the context menu's items for moving the selected item (and the items in it) to another
    /// operator, as dragging it does, and as Ctrl+X and Ctrl+V do in the tree: Detach, and while an
    /// item is detached "Attach '…' here" (only where it would be taken) and "Put '…' back".
    ///
    /// @param priority The priority of the first, after which the others come.
    /// @return The items, none if the tree can't be changed.
    public List<stroom.widget.menu.client.Item> getMoveMenuItems(final int priority) {
        final List<stroom.widget.menu.client.Item> menuItems = new ArrayList<>();
        if (selectionModel == null) {
            return menuItems;
        }
        final Item selectedItem = selectionModel.getSelectedObject();
        final Item detached = view.getDetached();
        if (detached == null) {
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority)
                    .icon(SvgImage.MOVE)
                    .text("Detach")
                    .enabled(selectedItem != null && tree.getParent(selectedItem) != null)
                    .command(() -> view.detach(selectedItem))
                    .build());
        } else {
            final String name = ExpressionItemRenderer.getName(tree, detached);
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority)
                    .icon(SvgImage.MOVE)
                    .text("Attach '" + name + "' here")
                    .enabled(view.canAttach(selectedItem))
                    .command(() -> view.attach(selectedItem))
                    .build());
            menuItems.add(new IconMenuItem.Builder()
                    .priority(priority + 1)
                    .icon(SvgImage.UNDO)
                    .text("Put '" + name + "' back")
                    .command(() -> view.cancelDetach())
                    .build());
        }
        return menuItems;
    }
}
