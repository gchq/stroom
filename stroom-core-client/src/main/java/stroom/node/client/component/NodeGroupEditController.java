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

import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeClient;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeStatusResult;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.PageRequest;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// Edits a node group ([NodeGroupEdit]) in a dialog made for one use: whether it is enabled, and
/// which nodes it includes or excludes.
public class NodeGroupEditController extends Controller {

    private final NodeGroupEdit view;
    private final NodeGroupStateListController nodeGroupStateList;
    private final NodeClient nodeClient;
    private final NodeGroupClient nodeGroupClient;

    private Set<Integer> allNodes = new HashSet<>();
    private final Set<Integer> selected = new HashSet<>();
    private boolean invertSelection;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus           The application's event bus.
    /// @param view               The content.
    /// @param nodeGroupStateList The nodes, ticked if selected.
    /// @param nodeClient         Lists the nodes.
    /// @param nodeGroupClient    Fetches and saves the group's nodes.
    @Inject
    public NodeGroupEditController(final EventBus eventBus,
                                   final NodeGroupEdit view,
                                   final NodeGroupStateListController nodeGroupStateList,
                                   final NodeClient nodeClient,
                                   final NodeGroupClient nodeGroupClient) {
        super(eventBus);
        this.view = view;
        this.nodeGroupStateList = own(nodeGroupStateList);
        this.nodeClient = nodeClient;
        this.nodeGroupClient = nodeGroupClient;
        initWidget(view);
        view.setList(nodeGroupStateList);
        view.setOnInvertSelectionChange(this::onInvertSelectionChange);
    }

    /// Fetches the nodes and the group's selection, then shows the group in a dialog, owned by
    /// `opener` until it is closed. OK saves it.
    ///
    /// @param opener    What shows it.
    /// @param nodeGroup The group.
    /// @param title     The dialog's caption.
    /// @param consumer  Given the save's result once OK has saved the group.
    public void show(final HasControllerNode opener,
                     final NodeGroup nodeGroup,
                     final String title,
                     final Consumer<Boolean> consumer) {
        nodeClient.fetchNodeStatus(guard(response -> {
            allNodes = response.getValues()
                    .stream()
                    .map(NodeStatusResult::getNode)
                    .map(Node::getId)
                    .collect(Collectors.toSet());

            nodeGroupStateList.setAllNodes(allNodes);
            nodeGroupStateList.setSelectedNodes(selected);
            nodeGroupStateList.setInvertSelection(nodeGroup.isInvertSelection());
            invertSelection = nodeGroup.isInvertSelection();

            nodeGroupClient.getNodeGroupState(nodeGroup.getId(), guard(result -> {
                selected.clear();
                selected.addAll(result.getSelected());
                nodeGroupStateList.refresh();
                open(opener, nodeGroup, title, consumer);
            }), taskMonitor());
        }), null, new FindNodeStatusCriteria(PageRequest.unlimited(), null), taskMonitor());
    }

    private void open(final HasControllerNode opener,
                      final NodeGroup nodeGroup,
                      final String title,
                      final Consumer<Boolean> consumer) {
        view.setName(nodeGroup.getName());
        view.setEnabled(nodeGroup.isEnabled());
        view.setInvertSelection(nodeGroup.isInvertSelection());

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 600))
                .caption(title)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final NodeGroup updated = nodeGroup
                                .copy()
                                .enabled(view.isEnabled())
                                .invertSelection(view.isInvertSelection())
                                .build();
                        final NodeGroupChange change = new NodeGroupChange(updated, selected);
                        nodeGroupClient.updateNodeGroupState(change,
                                guard(result -> {
                                    e.hide();
                                    consumer.accept(result);
                                }),
                                RestErrorHandler.forPopup(this, e),
                                taskMonitor());
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    // Inverts the selection when the inclusion behaviour changes, so the same nodes stay in the
    // group
    private void onInvertSelectionChange() {
        if (invertSelection != view.isInvertSelection()) {
            allNodes.forEach(node -> {
                if (selected.contains(node)) {
                    selected.remove(node);
                } else {
                    selected.add(node);
                }
            });
            invertSelection = view.isInvertSelection();
            nodeGroupStateList.setInvertSelection(invertSelection);
            nodeGroupStateList.refresh();
        }
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
