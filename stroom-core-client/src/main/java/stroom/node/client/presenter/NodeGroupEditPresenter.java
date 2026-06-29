/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeClient;
import stroom.node.client.NodeGroupClient;
import stroom.node.client.view.NodeGroupEditUiHandlers;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeStatusResult;
import stroom.util.shared.PageRequest;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NodeGroupEditPresenter
        extends MyPresenterWidget<NodeGroupEditPresenter.NodeGroupEditView>
        implements NodeGroupEditUiHandlers {

    private final NodeGroupStateListPresenter nodeGroupStateListPresenter;
    private final NodeClient nodeClient;
    private final NodeGroupClient nodeGroupClient;

    private boolean opening;
    private boolean open;

    private Set<Integer> allNodes = new HashSet<>();
    private final Set<Integer> selected = new HashSet<>();
    private boolean invertSelection;

    @Inject
    public NodeGroupEditPresenter(final EventBus eventBus,
                                  final NodeGroupEditView view,
                                  final NodeGroupStateListPresenter nodeGroupStateListPresenter,
                                  final NodeClient nodeClient,
                                  final NodeGroupClient nodeGroupClient) {
        super(eventBus, view);
        this.nodeGroupStateListPresenter = nodeGroupStateListPresenter;
        this.nodeClient = nodeClient;
        this.nodeGroupClient = nodeGroupClient;

        getView().setUiHandlers(this);
        view.setListView(nodeGroupStateListPresenter.getView());
    }

    @Override
    public void onInvertSelectionChange() {
        if (invertSelection != getView().isInvertSelection()) {
            allNodes.forEach(node -> {
                if (selected.contains(node)) {
                    selected.remove(node);
                } else {
                    selected.add(node);
                }
            });
            invertSelection = getView().isInvertSelection();
            nodeGroupStateListPresenter.setInvertSelection(invertSelection);
            nodeGroupStateListPresenter.refresh();
        }
    }

    void show(final NodeGroup nodeGroup,
              final String title,
              final Consumer<Boolean> consumer) {
        if (!opening) {
            opening = true;

            nodeClient.fetchNodeStatus(response -> {
                allNodes = response.getValues()
                        .stream()
                        .map(NodeStatusResult::getNode)
                        .map(Node::getId)
                        .collect(Collectors.toSet());

                nodeGroupStateListPresenter.setAllNodes(allNodes);
                nodeGroupStateListPresenter.setSelectedNodes(selected);
                nodeGroupStateListPresenter.setInvertSelection(nodeGroup.isInvertSelection());
                invertSelection = nodeGroup.isInvertSelection();

                nodeGroupClient.getNodeGroupState(nodeGroup.getId(),
                        result -> {
                            selected.clear();
                            selected.addAll(result.getSelected());
                            nodeGroupStateListPresenter.refresh();
                            open(nodeGroup, title, consumer);
                        },
                        this);
            }, null,
                    new FindNodeStatusCriteria(PageRequest.unlimited(), null),
                    NodeGroupEditPresenter.this);
        }
    }

    private void open(final NodeGroup nodeGroup,
                      final String title,
                      final Consumer<Boolean> consumer) {
        if (!open) {
            open = true;

            getView().setName(nodeGroup.getName());
            getView().setEnabled(nodeGroup.isEnabled());
            getView().setInvertSelection(nodeGroup.isInvertSelection());

            final PopupSize popupSize = PopupSize.resizable(800, 600);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption(title)
                    .onShow(e -> getView().focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            final NodeGroup updated = nodeGroup
                                    .copy()
//                                    .name(getView().getName())
                                    .enabled(getView().isEnabled())
                                    .invertSelection(getView().isInvertSelection())
                                    .build();
                            final NodeGroupChange change = new NodeGroupChange(updated, selected);
                            nodeGroupClient.updateNodeGroupState(change,
                                    result -> {
                                        e.hide();
                                        consumer.accept(result);
                                    },
                                    RestErrorHandler.forPopup(this, e),
                                    this);


//                            doWithGroupNameValidation(getView().getName(),
//                                    nodeGroup.getId(),
//                                    () -> nodeGroupClient.update(updated,
//                                            consumer,
//                                            RestErrorHandler.forPopup(this, e),
//                                            this),
//                                    e);
                        } else {
                            e.hide();
                        }
                    })
                    .onHide(e -> {
                        open = false;
                        opening = false;
                    })
                    .fire();
        }
    }

//    private void doWithGroupNameValidation(final String groupName,
//                                           final Integer groupId,
//                                           final Runnable work,
//                                           final HidePopupRequestEvent event) {
//        if (groupName == null || groupName.isEmpty()) {
//            AlertEvent.fireError(
//                    NodeGroupEditPresenter.this,
//                    "You must provide a name for the node group.",
//                    event::reset);
//        } else {
//            nodeGroupClient.fetchByName(getView().getName(), grp -> {
//                if (grp != null && !Objects.equals(groupId, grp.getId())) {
//                    AlertEvent.fireError(
//                            NodeGroupEditPresenter.this,
//                            "Group name '"
//                            + groupName
//                            + "' is already in use by another group.",
//                            event::reset);
//                } else {
//                    work.run();
//                }
//            }, RestErrorHandler.forPopup(this, event), this);
//        }
//    }

    public interface NodeGroupEditView extends View, Focus, HasUiHandlers<NodeGroupEditUiHandlers> {

        void setName(String name);

        void setEnabled(boolean enabled);

        boolean isEnabled();

        void setInvertSelection(boolean invertSelection);

        boolean isInvertSelection();

        void setListView(View listView);
    }
}
