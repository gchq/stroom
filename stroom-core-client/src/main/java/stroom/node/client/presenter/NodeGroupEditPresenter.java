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

import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeClient;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.NodeGroup;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class NodeGroupEditPresenter
        extends MyPresenterWidget<NodeGroupEditPresenter.NodeGroupEditView> {

    private final NodeGroupStateListPresenter nodeGroupStateListPresenter;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;
    private final NodeGroupClient nodeGroupClient;

    private NodeGroup nodeGroup;
    private boolean opening;
    private boolean open;

//    private final DelayedUpdate delayedUpdate;

    @Inject
    public NodeGroupEditPresenter(final EventBus eventBus,
                                  final NodeGroupEditView view,
                                  final NodeGroupStateListPresenter nodeGroupStateListPresenter,
                                  final RestFactory restFactory,
                                  final NodeClient nodeClient,
                                  final NodeGroupClient nodeGroupClient) {
        super(eventBus, view);
        this.nodeGroupStateListPresenter = nodeGroupStateListPresenter;
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        this.nodeGroupClient = nodeGroupClient;

        view.setListView(nodeGroupStateListPresenter.getView());
    }

    void show(final NodeGroup nodeGroup,
              final String title,
              final Consumer<NodeGroup> consumer) {
        if (!opening) {
            opening = true;
//            final ExpressionOperator expression = ExpressionUtil.equalsId(IndexNodeFields.GROUP_ID,
//                    volumeGroup.getId());
//            final ExpressionCriteria expressionCriteria = new ExpressionCriteria(expression);
//            // TODO: 09/09/2022 Need to implement user defined sorting
//            expressionCriteria.setSort(IndexNodeFields.NODE_NAME.getFldName());
//            expressionCriteria.addSort(IndexNodeFields.PATH.getFldName());

            nodeGroupStateListPresenter.setNodeGroup(nodeGroup);
            open(nodeGroup, title, consumer);
        }
    }

    private void open(final NodeGroup nodeGroup,
                      final String title,
                      final Consumer<NodeGroup> consumer) {
        if (!open) {
            open = true;

            this.nodeGroup = nodeGroup;
            getView().setName(nodeGroup.getName());

            final PopupSize popupSize = PopupSize.resizable(800, 600);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.CLOSE_DIALOG)
                    .popupSize(popupSize)
                    .caption(title)
                    .onShow(e -> getView().focus())
                    .onHideRequest(e -> {
//                        if (e.isOk()) {
//                            nodeGroup.setName(getView().getName());
//                            try {
//                                doWithGroupNameValidation(getView().getName(), nodeGroup.getId(), () ->
//                                        createNodeGroup(consumer, nodeGroup, e), e);
//                            } catch (final RuntimeException ex) {
//                                AlertEvent.fireError(
//                                        NodeGroupEditPresenter.this,
//                                        ex.getMessage(),
//                                        e::reset);
//                            }
//                        } else {
                            e.hide();
//                        }
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
//
//    private void createNodeGroup(final Consumer<NodeGroup> consumer,
//                                 final NodeGroup nodeGroup,
//                                 final HidePopupRequestEvent event) {
//        nodeGroupClient.updateNodeGroupState();
//        restFactory
//                .create(INDEX_VOLUME_GROUP_RESOURCE)
//                .method(res -> res.update(nodeGroup.getId(), nodeGroup))
//                .onSuccess(r -> {
//                    consumer.accept(r);
//                    event.hide();
//                })
//                .onFailure(RestErrorHandler.forPopup(this, event))
//                .taskMonitorFactory(this)
//                .exec();
//    }


    // --------------------------------------------------------------------------------


    public interface NodeGroupEditView extends View, Focus {

        String getName();

        void setName(String name);

        void setListView(View listView);
    }
}
