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

package stroom.dashboard.client.component;

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;

/// The Process Search Results dialog ([ProcessorLimits]), made for one use: the limits on
/// processing a query's results.
public class ProcessorLimitsController extends Controller {

    private final ProcessorLimits view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public ProcessorLimitsController(final EventBus eventBus, final ProcessorLimits view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Asks for the limits in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener        What shows it.
    /// @param recordLimit   The number of records to start with, or null for the default.
    /// @param timeLimitMins The number of minutes to start with, or null for the default.
    /// @param onOk          Given the number of records and minutes when OK is pressed.
    public void show(final HasControllerNode opener,
                     final Long recordLimit,
                     final Long timeLimitMins,
                     final BiConsumer<Long, Long> onOk) {
        view.setRecordLimit(recordLimit);
        view.setTimeLimitMins(timeLimitMins);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Process Search Results")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(view.getRecordLimit(), view.getTimeLimitMins());
                    }
                    e.hide();
                })
                .fire();
    }
}
