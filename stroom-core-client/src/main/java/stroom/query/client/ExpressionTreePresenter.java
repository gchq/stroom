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

import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.datasource.api.DataSourceField;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.query.api.ExpressionOperator;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.List;

public class ExpressionTreePresenter extends MyPresenterWidget<ExpressionTreePresenter.ExpressionTreeView>
        implements HasDataSelectionHandlers<Item>,
        HasContextMenuHandlers {
    private DefaultTreeForTreeLayout<Item> tree;
    private MySingleSelectionModel<Item> selectionModel;
    private ExpressionUiHandlers uiHandlers;
    @Inject
    public ExpressionTreePresenter(final EventBus eventBus, final ExpressionTreeView view,
                                   final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        selectionModel = new MySingleSelectionModel<>();
        view.setSelectionModel(selectionModel);
    }

    @Override
    protected void onBind() {
        super.onBind();

        if (selectionModel != null) {
            registerHandler(selectionModel.addSelectionChangeHandler(event -> {
                DataSelectionEvent.fire(ExpressionTreePresenter.this, selectionModel.getSelectedObject(), false);
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

    public void setFields(final List<DataSourceField> fields) {
        getView().setFields(fields);
    }

    public void read(final ExpressionOperator root) {
        clearSelection();

        tree = new ExpressionModel().getTreeFromExpression(root);
        getView().setTree(tree);
        getView().refresh();
    }

    public ExpressionOperator write() {
        clearSelection();
        return new ExpressionModel().getExpressionFromTree(tree);
    }

    public void addOperator() {
        addNewItem(new Operator());
    }

    public void addTerm() {
        addNewItem(new Term());
    }

    public void disable() {
        if (selectionModel != null) {
            final Item selectedItem = selectionModel.getSelectedObject();
            if (selectedItem != null) {
                selectedItem.setEnabled(!selectedItem.enabled());

                fireDirty();

                getView().refresh();
            }
        }
    }

    public void delete() {
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
            if (children == null || children.size() == 0) {
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
        if (selectionModel != null) {
            final Item selected = selectionModel.getSelectedObject();

            Item operator = null;

            if (selected != null) {
                if (selected instanceof Operator) {
                    operator = selected;
                } else {
                    final Item parent = tree.getParent(selected);
                    if (parent != null && parent instanceof Operator) {
                        operator = parent;
                    }
                }
            }

            if (operator == null) {
                final Item root = tree.getRoot();
                if (root != null && root instanceof Operator) {
                    operator = root;
                }
            }

            if (operator != null) {
                tree.addChild(operator, item);
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

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Item> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    public void fireDirty() {
        if (uiHandlers != null) {
            uiHandlers.fireDirty();
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

    public interface ExpressionTreeView extends View, HasContextMenuHandlers, HasUiHandlers<ExpressionUiHandlers> {
        void setTree(DefaultTreeForTreeLayout<Item> model);

        void setSelectionModel(MySingleSelectionModel<Item> selectionModel);

        void setFields(List<DataSourceField> fields);

        void endEditing();

        void refresh();
    }
}
