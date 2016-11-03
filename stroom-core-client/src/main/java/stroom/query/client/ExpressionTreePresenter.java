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

import java.util.List;

import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import stroom.query.shared.ExpressionItem;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionTerm;
import stroom.query.shared.IndexField;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.HasRead;
import stroom.entity.client.presenter.HasWrite;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

public class ExpressionTreePresenter extends MyPresenterWidget<ExpressionTreePresenter.ExpressionTreeView>
        implements HasRead<ExpressionOperator>, HasWrite<ExpressionOperator>, HasDataSelectionHandlers<ExpressionItem>,
        HasContextMenuHandlers {
    public interface ExpressionTreeView extends View, HasContextMenuHandlers, HasUiHandlers<ExpressionUiHandlers> {
        void setTree(DefaultTreeForTreeLayout<ExpressionItem> model);

        void setSelectionModel(MySingleSelectionModel<ExpressionItem> selectionModel);

        void setFields(List<IndexField> fields);

        void endEditing();

        void refresh();
    }

    private DefaultTreeForTreeLayout<ExpressionItem> tree;
    private MySingleSelectionModel<ExpressionItem> selectionModel;
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
            registerHandler(selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
                @Override
                public void onSelectionChange(final SelectionChangeEvent event) {
                    DataSelectionEvent.fire(ExpressionTreePresenter.this, selectionModel.getSelectedObject(), false);
                    getView().refresh();
                }
            }));
        }
    }

    public HandlerRegistration addSelectionChangeHandler(final SelectionChangeEvent.Handler handler) {
        if (selectionModel == null) {
            return null;
        }

        return selectionModel.addSelectionChangeHandler(handler);
    }

    public void setFields(final List<IndexField> indexFields) {
        getView().setFields(indexFields);
    }

    @Override
    public void read(final ExpressionOperator root) {
        clearSelection();

        tree = new ExpressionModel().getTreeFromExpression(root);
        getView().setTree(tree);
        getView().refresh();
    }

    @Override
    public void write(final ExpressionOperator root) {
        clearSelection();

        final ExpressionOperator r = new ExpressionModel().getExpressionFromTree(tree);
        if (r != null) {
            root.setType(r.getType());
            root.setChildren(r.getChildren());
        }
    }

    public void addOperator() {
        addNewItem(new ExpressionOperator());
    }

    public void addTerm() {
        addNewItem(new ExpressionTerm());
    }

    public void disable() {
        if (selectionModel != null) {
            final ExpressionItem selectedItem = selectionModel.getSelectedObject();
            if (selectedItem != null) {
                selectedItem.setEnabled(!selectedItem.isEnabled());

                fireDirty();

                getView().refresh();
            }
        }
    }

    public void delete() {
        if (selectionModel != null) {
            final ExpressionItem selectedItem = selectionModel.getSelectedObject();
            if (selectedItem != null) {
                final ExpressionItem nextSelection = getNextSelection(selectedItem);

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

    private ExpressionItem getNextSelection(final ExpressionItem selectedItem) {
        final ExpressionItem parent = tree.getParent(selectedItem);
        if (parent != null) {
            final List<ExpressionItem> children = tree.getChildren(parent);
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

    private void addNewItem(final ExpressionItem item) {
        if (selectionModel != null) {
            final ExpressionItem selected = selectionModel.getSelectedObject();

            ExpressionItem operator = null;

            if (selected != null) {
                if (selected instanceof ExpressionOperator) {
                    operator = selected;
                } else {
                    final ExpressionItem parent = tree.getParent(selected);
                    if (parent != null && parent instanceof ExpressionOperator) {
                        operator = parent;
                    }
                }
            }

            if (operator == null) {
                final ExpressionItem root = tree.getRoot();
                if (root != null && root instanceof ExpressionOperator) {
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
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<ExpressionItem> handler) {
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

    public void setSelectionModel(final MySingleSelectionModel<ExpressionItem> selectionModel) {
        this.selectionModel = selectionModel;
        getView().setSelectionModel(selectionModel);
    }

    public MySingleSelectionModel<ExpressionItem> getSelectionModel() {
        return selectionModel;
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final Handler handler) {
        return getView().addContextMenuHandler(handler);
    }
}
