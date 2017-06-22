/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.client;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocRef;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.pipeline.structure.client.view.DraggableTreePanel;
import stroom.query.client.ExpressionTreePresenter.ExpressionTreeView;
import stroom.query.shared.IndexField;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.List;

public class ExpressionTreeViewImpl extends ViewWithUiHandlers<ExpressionUiHandlers> implements ExpressionTreeView {
    private final ExpressionTreePanel treePanel;
    private final DraggableTreePanel<Item> layoutPanel;
    private MySingleSelectionModel<Item> selectionModel;

    @Inject
    public ExpressionTreeViewImpl(final Provider<EntityDropDownPresenter> dictionaryProvider) {
        treePanel = new ExpressionTreePanel(dictionaryProvider);
        final ExpressionTreePanel subTreePanel = new ExpressionTreePanel(dictionaryProvider);

        layoutPanel = new DraggableTreePanel<Item>(treePanel, subTreePanel) {
            @Override
            protected boolean isValidTarget(final Item parent, final Item child) {
                return parent instanceof Operator;
            }

            @Override
            protected void setSelected(final Item item) {
                if (selectionModel != null) {
                    selectionModel.setSelected(item, true);
                }
            }

            @Override
            protected void startDragging(final Item parent, final Item child) {
                if (selectionModel != null) {
                    selectionModel.clear();
                }
            }

            @Override
            protected void endDragging(final Item parent, final Item child) {
                if (getUiHandlers() != null) {
                    getUiHandlers().fireDirty();
                }
            }
        };
        layoutPanel.setAllowDragging(true);
    }

    @Override
    public void setTree(final DefaultTreeForTreeLayout<Item> tree) {
        treePanel.setTree(tree);
    }

    @Override
    public void setSelectionModel(final MySingleSelectionModel<Item> selectionModel) {
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
    public void init(final ClientDispatchAsync dispatcher, final DocRef dataSource, final List<IndexField> indexFields) {
        treePanel.init(dispatcher, dataSource, indexFields);
    }

    @Override
    public void endEditing() {
        treePanel.endEditing();
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
