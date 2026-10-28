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

package stroom.processor.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.node.client.NodeGroupClient;
import stroom.node.client.NodeGroupListModel;
import stroom.node.shared.NodeGroup;
import stroom.processor.client.presenter.ProcessorProfileClient;
import stroom.processor.shared.ProcessorProfile;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PathSafeNames;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// Creates or edits a processor profile ([ProcessorProfileEdit]) in a dialog made for one use,
/// checking its name is valid and not in use on OK.
public class ProcessorProfileEditController extends Controller {

    private final ProcessorProfileEdit view;
    private final ProcessorProfileClient processorProfileClient;
    private final NodeGroupClient nodeGroupClient;
    private final ProfilePeriodListController profilePeriodList;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   The content.
    /// @param processorProfileClient Finds and saves profiles.
    /// @param nodeGroupClient        Lists and finds the node groups.
    /// @param profilePeriodList      The processing schedules.
    @Inject
    public ProcessorProfileEditController(final EventBus eventBus,
                                          final ProcessorProfileEdit view,
                                          final ProcessorProfileClient processorProfileClient,
                                          final NodeGroupClient nodeGroupClient,
                                          final ProfilePeriodListController profilePeriodList) {
        super(eventBus);
        this.view = view;
        this.processorProfileClient = processorProfileClient;
        this.nodeGroupClient = nodeGroupClient;
        this.profilePeriodList = own(profilePeriodList);
        initWidget(view);
        view.setList(profilePeriodList);

        final NodeGroupListModel nodeGroupListModel = new NodeGroupListModel(eventBus, nodeGroupClient);
        nodeGroupListModel.setTaskMonitorFactory(() -> taskMonitor().createTaskMonitor());
        view.setNodeGroupModel(nodeGroupListModel);
    }

    /// Shows a profile in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener           What shows it.
    /// @param processorProfile The profile, new (no id) or saved.
    /// @param title            The dialog's caption.
    /// @param consumer         Given the saved profile once OK has saved it.
    public void show(final HasControllerNode opener,
                     final ProcessorProfile processorProfile,
                     final String title,
                     final Consumer<ProcessorProfile> consumer) {
        if (NullSafe.isBlankString(processorProfile.getNodeGroupName())) {
            view.setNodeGroup(null);
        } else {
            nodeGroupClient.fetchByName(processorProfile.getNodeGroupName(), guard(view::setNodeGroup),
                    new DefaultErrorHandler(this, null), taskMonitor());
        }

        view.setName(processorProfile.getName());
        view.setUserTimeZone(processorProfile.getTimeZone());
        profilePeriodList.setProfilePeriods(processorProfile.getProfilePeriods());

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(800, 600))
                .caption(title)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ProcessorProfile updated = processorProfile
                                .copy()
                                .name(view.getName())
                                .nodeGroupName(NullSafe.get(view.getNodeGroup(), NodeGroup::getName))
                                .profilePeriods(profilePeriodList.getProfilePeriods())
                                .timeZone(view.getUserTimeZone())
                                .build();

                        if (processorProfile.getId() != null && Objects.equals(updated, processorProfile)) {
                            // If the profile has not been updated then close,
                            e.hide();
                        } else {
                            try {
                                doWithNameValidation(view.getName(), processorProfile.getId(), () ->
                                        createOrUpdateProcessorProfile(consumer, updated, e), e);
                            } catch (final RuntimeException ex) {
                                AlertEvent.fireError(this, ex.getMessage(), e::reset);
                            }
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void doWithNameValidation(final String name,
                                      final Integer profileId,
                                      final Runnable work,
                                      final HidePopupRequestEvent event) {
        if (name == null || name.isEmpty()) {
            AlertEvent.fireError(this, "You must provide a name for the processor profile.", event::reset);
        } else if (PathSafeNames.validate(name) != null) {
            AlertEvent.fireError(this, PathSafeNames.validate(name), event::reset);
        } else {
            processorProfileClient.fetchByName(name, guard(profile -> {
                if (profile != null && !Objects.equals(profileId, profile.getId())) {
                    AlertEvent.fireError(this,
                            "Processor profile name '" + name + "' is already in use by another processor profile.",
                            event::reset);
                } else {
                    work.run();
                }
            }), RestErrorHandler.forPopup(this, event), taskMonitor());
        }
    }

    private void createOrUpdateProcessorProfile(final Consumer<ProcessorProfile> consumer,
                                                final ProcessorProfile processorProfile,
                                                final HidePopupRequestEvent event) {
        final Consumer<ProcessorProfile> onSaved = guard(saved -> {
            consumer.accept(saved);
            event.hide();
        });
        if (processorProfile.getId() == null) {
            processorProfileClient.create(processorProfile, onSaved,
                    RestErrorHandler.forPopup(this, event), taskMonitor());
        } else {
            processorProfileClient.update(processorProfile.getId(), processorProfile, onSaved,
                    RestErrorHandler.forPopup(this, event), taskMonitor());
        }
    }

}
