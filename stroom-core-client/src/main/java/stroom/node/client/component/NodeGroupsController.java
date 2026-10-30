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

package stroom.node.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.component.ContentTabController;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.NodeGroup;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// Shows the node groups, and adds, edits and deletes them.
public class NodeGroupsController extends ContentTabController {

    public static final String TAB_TYPE = "NodeGroups";

    private final NodeGroupListController nodeGroupList;
    private final Provider<NodeGroupEditorController> editProvider;
    private final NodeGroupClient nodeGroupClient;
    private final Provider<NewNodeGroupController> newNodeGroupControllerProvider;

    /// Made by GIN, with its parts.
    ///
    /// @param eventBus                       The application's event bus.
    /// @param nodeGroupList                  The node group list.
    /// @param editProvider                   Makes the edit.
    /// @param nodeGroupClient                The node group client.
    /// @param newNodeGroupControllerProvider Makes the new node group controller.
    @Inject
    public NodeGroupsController(
            final EventBus eventBus,
            final NodeGroupListController nodeGroupList,
            final Provider<NodeGroupEditorController> editProvider,
            final NodeGroupClient nodeGroupClient,
            final Provider<NewNodeGroupController> newNodeGroupControllerProvider) {

        super(eventBus);
        own(nodeGroupList);
        this.nodeGroupList = nodeGroupList;
        this.editProvider = editProvider;
        this.nodeGroupClient = nodeGroupClient;
        this.newNodeGroupControllerProvider = newNodeGroupControllerProvider;

        nodeGroupList.setOnNew(this::add);
        nodeGroupList.setOnEdit(this::edit);
        nodeGroupList.setOnDelete(this::delete);

        initWidget(nodeGroupList.asWidget());
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
        editProvider.get().show(this, nodeGroup, "Edit Node Group - " + nodeGroup.getName(), result -> refresh());
    }

    private void delete() {
        final List<NodeGroup> list = nodeGroupList.getSelectionModel().getSelectedItems();
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to delete the selected node group?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected node groups?";
            }
            ConfirmEvent.fire(NodeGroupsController.this, message,
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
