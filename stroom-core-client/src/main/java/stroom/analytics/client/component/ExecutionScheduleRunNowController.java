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

package stroom.analytics.client.component;

import stroom.analytics.client.component.ExecutionScheduleRunNow.RunNowAction;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.Size;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Run Now dialog of the execution schedules ([ExecutionScheduleRunNow]), made for one use.
public class ExecutionScheduleRunNowController extends Controller {

    private final ExecutionScheduleRunNow view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public ExecutionScheduleRunNowController(final EventBus eventBus, final ExecutionScheduleRunNow view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener   What shows it.
    /// @param onAction Told which schedules the user runs; the dialog stays open.
    public void show(final HasControllerNode opener, final Consumer<RunNowAction> onAction) {
        view.setOnAction(onAction);
        final PopupSize popupSize = PopupSize.builder()
                .width(Size.builder()
                        .initial(500)
                        .resizable(false)
                        .build())
                .height(Size.builder()
                        .resizable(false)
                        .build())
                .build();
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .modal()
                .popupSize(popupSize)
                .onShow(e -> view.focus())
                .caption("Run Now")
                .fire();
    }
}
