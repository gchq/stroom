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
import stroom.pipeline.structure.client.view.DraggableTreePanel;
import stroom.pipeline.structure.client.view.TreeKeyboard;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.client.ExpressionTreePresenter.ExpressionTreeView;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.function.Consumer;
import java.util.function.Predicate;

/// An expression's tree of terms and operators.
///
/// It is a tree for assistive technology (`role="tree"`, see [ExpressionItemRenderer]) and for the
/// keyboard (see [TreeKeyboard]): the arrow keys move the selection, showing the item selected as
/// its label; a click edits an item (its editor in place of its label). Its items can be dragged to
/// another operator.
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
                                  final UserPreferencesManager userPreferencesManager,
                                  final UiConfigCache uiConfigCache) {
        treePanel = new ExpressionTreePanel(
                docRefProvider,
                userRefProvider,
                uiConfigCache,
                userPreferencesManager.isUtc());
        final ExpressionTreePanel subTreePanel = new ExpressionTreePanel(
                docRefProvider,
                userRefProvider,
                uiConfigCache,
                userPreferencesManager.isUtc());

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
        layoutPanel.addStyleName("ExpressionTreeViewImpl-layoutPanel");

        keyboard = new TreeKeyboard<>(treePanel, layoutPanel, "expression-tree-status", "item", "items") {
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
                    treePanel.setEditingItem(null);
                    removeHandler.accept(item);
                    // The focus goes to the item selected in its place, shown as its label
                    if (selectionModel != null && selectionModel.getSelectedObject() != null) {
                        moveTo(selectionModel.getSelectedObject());
                    }
                }
            }

            @Override
            protected void onNavigate(final Item item) {
                // Leaving an item ends its editing (keeping what was entered); the item moved to is
                // shown as its label
                endEditing();
                treePanel.setEditingItem(null);
            }
        };
        // Moving items with the keyboard comes with their detached look
        keyboard.setAllowMoving(false);
    }

    @Override
    public void setTree(final DefaultTreeForTreeLayout<Item> tree) {
        treePanel.setTree(tree);
    }

    @Override
    public void setSelectionModel(final MySingleSelectionModel<Item> selectionModel) {
        this.selectionModel = selectionModel;
        treePanel.setSelectionModel(selectionModel);
        keyboard.setSelectionModel(selectionModel);
        // Without a selection model the tree is only for reading, so it can't be rearranged either
        layoutPanel.setAllowDragging(selectionModel != null);
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
