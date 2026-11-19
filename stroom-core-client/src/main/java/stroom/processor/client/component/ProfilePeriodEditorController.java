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
import stroom.processor.shared.ProfilePeriod;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datepicker.client.component.TimeBoxController;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.Size;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Edit Period dialog ([ProfilePeriodEditor]) of a processor profile, made for one use.
public class ProfilePeriodEditorController extends Controller {

    private final ProfilePeriodEditor view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                  The application's event bus.
    /// @param view                      The content.
    /// @param timeBoxControllerProvider Makes the start and end times' controllers.
    @Inject
    public ProfilePeriodEditorController(final EventBus eventBus,
                                         final ProfilePeriodEditor view,
                                         final Provider<TimeBoxController> timeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        make(timeBoxControllerProvider).attach(view.getStartTime());
        make(timeBoxControllerProvider).attach(view.getEndTime());
    }

    /// Edits a period in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener        What shows it.
    /// @param profilePeriod The period.
    /// @param onOk          Given the period edited, on OK with valid times.
    public void edit(final HasControllerNode opener,
                     final ProfilePeriod profilePeriod,
                     final Consumer<ProfilePeriod> onOk) {
        view.setDays(profilePeriod.getDays());
        view.getStartTime().setValue(profilePeriod.getStartTime());
        view.getEndTime().setValue(profilePeriod.getEndTime());
        view.setLimitNodeThreads(profilePeriod.isLimitNodeThreads());
        view.setMaxNodeThreads(profilePeriod.getMaxNodeThreads());
        view.setLimitClusterThreads(profilePeriod.isLimitClusterThreads());
        view.setMaxClusterThreads(profilePeriod.getMaxClusterThreads());

        final PopupSize popupSize = PopupSize.builder()
                .width(Size.builder().max(1000).resizable(true).build())
                .height(Size.builder().build())
                .build();
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Edit Period")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (!e.isOk()) {
                        e.hide();
                    } else if (!view.getStartTime().isValid()) {
                        AlertEvent.fireWarn(this, "Invalid start time", e::reset);
                    } else if (!view.getEndTime().isValid()) {
                        AlertEvent.fireWarn(this, "Invalid end time", e::reset);
                    } else {
                        onOk.accept(profilePeriod.copy()
                                .days(view.getDays())
                                .startTime(view.getStartTime().getValue())
                                .endTime(view.getEndTime().getValue())
                                .limitNodeThreads(view.isLimitNodeThreads())
                                .maxNodeThreads(view.getMaxNodeThreads())
                                .limitClusterThreads(view.isLimitClusterThreads())
                                .maxClusterThreads(view.getMaxClusterThreads())
                                .build());
                        e.hide();
                    }
                })
                .fire();
    }
}
