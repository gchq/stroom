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

package stroom.explorer.client.presenter;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.shared.ExplorerData;

import java.util.Set;

public class EntityCheckTreePresenter extends MyPresenterWidget<EntityCheckTreePresenter.EntityCheckTreeView>
        implements HasDataSelectionHandlers<Set<ExplorerData>> {
    //    private final TickBoxSelectionModel<ExplorerData> selectionModel;
    private final ExplorerTickBoxTree explorerTree;

    @Inject
    public EntityCheckTreePresenter(final EntityCheckTreeView view, final ClientDispatchAsync dispatcher) {
        super(new SimpleEventBus(), view);

//        selectionModel = new TickBoxSelectionModel<ExplorerData>() {
//            @Override
//            public ExplorerData getParent(final ExplorerData object) {
//                if (object instanceof EntityCheckTreeData) {
//                    return ((EntityCheckTreeData) object).getParent();
//                }
//
//                return null;
//            }
//
//            @Override
//            public List<ExplorerData> getChildren(final ExplorerData object) {
//                if (object instanceof EntityCheckTreeData) {
//                    return ((EntityCheckTreeData) object).getChildren();
//                }
//
//                return null;
//            }
//        };
//
//        final TickBoxTreeCell<ExplorerData> cell = new TickBoxTreeCell<ExplorerData>(selectionModel) {
//            @Override
//            protected Image getIcon(final ExplorerData item) {
//                return new Image(ImageUtil.getImageURL() + item.getIconUrl());
//            }
//        };

//        treeModel = new ExplorerTreeModel(selectionModel, new TickBoxSelectionManager<ExplorerData>(), cell,
//                dispatcher);
//        final MyCellTree cellTree = new MyCellTree(treeModel);
//        cellTree.addOpenHandler(treeModel);
//        cellTree.addCloseHandler(treeModel);
//        treeModel.setRootTreeNode(cellTree.getRootTreeNode());
//
//        final UpdateHandler<ExplorerData> updateHandler = new UpdateHandler<ExplorerData>() {
//            @Override
//            public List<ExplorerData> onUpdate(final ExplorerData parent, final List<ExplorerData> result) {
//                List<ExplorerData> children = result;
//                if (result != null && selectionModel.isAffectRelatives()) {
//                    children = new ArrayList<ExplorerData>();
//                    for (final ExplorerData child : result) {
//                        // Select if the parent is selected. If is is then set
//                        // this item to be selected.
//                        if (TickBoxState.TICK.equals(selectionModel.getState(parent))) {
//                            selectionModel.setState(child, TickBoxState.TICK, true);
//
//                        } else {
//                            final TickBoxState childTickBoxState = selectionModel.getState(child);
//                            if (TickBoxState.TICK.equals(childTickBoxState)) {
//                                selectionModel.modifyState(child, TickBoxState.UNTICK);
//                                selectionModel.setState(child, TickBoxState.TICK, true);
//                            }
//                        }
//
//                        // Convert children to tree nodes so we can add parents
//                        // and children.
//                        final EntityCheckTreeData entityCheckTreeData = new EntityCheckTreeData(child);
//                        entityCheckTreeData.setParent(parent);
//                        if (parent != null && parent instanceof EntityCheckTreeData) {
//                            ((EntityCheckTreeData) parent).addChild(entityCheckTreeData);
//                        }
//                        children.add(entityCheckTreeData);
//                    }
//                }
//
//                return children;
//            }
//        };
//        treeModel.setUpdateHandler(updateHandler);
//
//        selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
//            @Override
//            public void onSelectionChange(final SelectionChangeEvent event) {
//                if (!selectionModel.isIgnorable()) {
//                    DataSelectionEvent.fire(EntityCheckTreePresenter.this, selectionModel.getSelectedSet(), false);
//                }
//            }
//        });

        explorerTree = new ExplorerTickBoxTree(dispatcher);

        view.setCellTree(explorerTree);
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerTree.getTreeModel().setIncludedTypes(includedTypes);
    }

    public void setTags(final String... tags) {
        explorerTree.getTreeModel().setTags(tags);
    }

    public void setRequiredPermissions(final String... requiredPermissions) {
        explorerTree.getTreeModel().setRequiredPermissions(requiredPermissions);
    }

    public void refresh() {
        explorerTree.getTreeModel().refresh();
    }

//    public void setRemoveOrphans(final boolean removeOrphans) {
//        treeModel.setRemoveOrphans(removeOrphans);
//    }
//
//    public void refresh(final Set<ExplorerData> openItems, final Integer depth) {
//        treeModel.refresh(openItems, depth);
//    }

    public TickBoxSelectionModel getSelectionModel() {
        return explorerTree.getSelectionModel();
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Set<ExplorerData>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    public interface EntityCheckTreeView extends View {
        void setCellTree(Widget cellTree);
    }
}
