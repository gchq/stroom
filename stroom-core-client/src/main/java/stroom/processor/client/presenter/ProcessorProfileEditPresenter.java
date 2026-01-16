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

package stroom.processor.client.presenter;

import stroom.item.client.SelectionBox;
import stroom.node.client.NodeGroupClient;
import stroom.node.client.presenter.NodeGroupListModel;
import stroom.node.shared.NodeGroup;
import stroom.processor.shared.ProcessorProfile;
import stroom.query.api.UserTimeZone;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class ProcessorProfileEditPresenter
        extends MyPresenterWidget<ProcessorProfileEditPresenter.ProcessorProfileEditView> {

    private final ProcessorProfileClient processorProfileClient;
    private final NodeGroupClient nodeGroupClient;
    private final ProfilePeriodListPresenter profilePeriodListPresenter;

    private ProcessorProfile processorProfile;
    private boolean opening;
    private boolean open;

    @Inject
    public ProcessorProfileEditPresenter(final EventBus eventBus,
                                         final ProcessorProfileEditView view,
                                         final ProcessorProfileClient processorProfileClient,
                                         final NodeGroupClient nodeGroupClient,
                                         final ProfilePeriodListPresenter profilePeriodListPresenter) {
        super(eventBus, view);
        this.processorProfileClient = processorProfileClient;
        this.nodeGroupClient = nodeGroupClient;
        this.profilePeriodListPresenter = profilePeriodListPresenter;
        view.setListView(profilePeriodListPresenter.getView());

        final NodeGroupListModel nodeGroupListModel = new NodeGroupListModel(eventBus, nodeGroupClient);
        nodeGroupListModel.setTaskMonitorFactory(this);
        view.getNodeGroup().setModel(nodeGroupListModel);
    }

    void show(final ProcessorProfile processorProfile,
              final String title,
              final Consumer<ProcessorProfile> consumer) {
        if (!opening) {
            opening = true;
            if (NullSafe.isBlankString(processorProfile.getNodeGroupName())) {
                getView().getNodeGroup().setValue(null);
            } else {
                nodeGroupClient.fetchByName(processorProfile.getNodeGroupName(), nodeGroup -> {
                    getView().getNodeGroup().setValue(nodeGroup);
                }, this);
            }

            open(processorProfile, title, consumer);
        }
    }

    private void open(final ProcessorProfile processorProfile,
                      final String title,
                      final Consumer<ProcessorProfile> consumer) {
        if (!open) {
            open = true;

            this.processorProfile = processorProfile;
            getView().setName(processorProfile.getName());
            getView().setUserTimeZone(processorProfile.getTimeZone());
            profilePeriodListPresenter.setProfilePeriods(processorProfile.getProfilePeriods());

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
//                                        createProcessorProfile(consumer, nodeGroup, e), e);
//                            } catch (final RuntimeException ex) {
//                                AlertEvent.fireError(
//                                        ProcessorProfileEditPresenter.this,
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
//                    ProcessorProfileEditPresenter.this,
//                    "You must provide a name for the node group.",
//                    event::reset);
//        } else {
//            nodeGroupClient.fetchByName(getView().getName(), grp -> {
//                if (grp != null && !Objects.equals(groupId, grp.getId())) {
//                    AlertEvent.fireError(
//                            ProcessorProfileEditPresenter.this,
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
//    private void createProcessorProfile(final Consumer<ProcessorProfile> consumer,
//                                 final ProcessorProfile nodeGroup,
//                                 final HidePopupRequestEvent event) {
//        nodeGroupClient.updateProcessorProfileState();
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

    public interface ProcessorProfileEditView extends View, Focus {

        String getName();

        void setName(String name);

        SelectionBox<NodeGroup> getNodeGroup();

        void setListView(View listView);

        UserTimeZone getUserTimeZone();

        void setUserTimeZone(UserTimeZone userTimeZone);
    }
}
