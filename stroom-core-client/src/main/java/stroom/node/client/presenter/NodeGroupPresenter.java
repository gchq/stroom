/*
 * Copyright 2026 Crown Copyright
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

package stroom.node.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.node.client.NodeGroupClient;
import stroom.node.client.component.NewNodeGroupController;
import stroom.node.client.component.NodeGroupListController;
import stroom.node.shared.NodeGroup;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.datagrid.client.WrapperView;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class NodeGroupPresenter extends ContentTabPresenter<WrapperView> {

    public static final String TAB_TYPE = "NodeGroups";

    private final NodeGroupListController nodeGroupList;
    private final Provider<NodeGroupEditPresenter> editProvider;
    private final NodeGroupClient nodeGroupClient;
    private final Provider<NewNodeGroupController> newNodeGroupControllerProvider;

    @Inject
    public NodeGroupPresenter(
            final EventBus eventBus,
            final WrapperView view,
            final NodeGroupListController nodeGroupList,
            final Provider<NodeGroupEditPresenter> editProvider,
            final NodeGroupClient nodeGroupClient,
            final Provider<NewNodeGroupController> newNodeGroupControllerProvider) {

        super(eventBus, view);
        own(nodeGroupList);
        this.nodeGroupList = nodeGroupList;
        this.editProvider = editProvider;
        this.nodeGroupClient = nodeGroupClient;
        this.newNodeGroupControllerProvider = newNodeGroupControllerProvider;

        nodeGroupList.setOnNew(this::add);
        nodeGroupList.setOnEdit(this::edit);
        nodeGroupList.setOnDelete(this::delete);

        view.setView(nodeGroupList);
        refresh();
    }

    private void add() {
        newNodeGroupControllerProvider.get().show(this, "", nodeGroup -> {
            edit(nodeGroup);
            refresh();
        });
    }

    private void edit() {
        final NodeGroup nodeGroup = nodeGroupList.getSelectionModel().getSelected();
        if (nodeGroup != null) {
            nodeGroupClient.fetchById(nodeGroup.getId(), this::edit, this);
        }
    }

    private void edit(final NodeGroup nodeGroup) {
        final NodeGroupEditPresenter editor = makeDialog(editProvider);
        editor.show(nodeGroup, "Edit Node Group - " + nodeGroup.getName(), result -> refresh());
    }

    private void delete() {
        final List<NodeGroup> list = nodeGroupList.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected node group?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected node groups?";
            }
            ConfirmEvent.fire(NodeGroupPresenter.this, message,
                    result -> {
                        if (result) {
                            nodeGroupList.getSelectionModel().clear();
                            for (final NodeGroup nodeGroup : list) {
                                nodeGroupClient.delete(nodeGroup.getId(), response -> refresh(), this);
                            }
                        }
                    });
        }
    }

    private void refresh() {
        nodeGroupList.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.NODES;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Node Groups";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
