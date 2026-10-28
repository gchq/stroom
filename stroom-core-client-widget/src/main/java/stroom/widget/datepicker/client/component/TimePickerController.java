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

package stroom.widget.datepicker.client.component;

import stroom.util.shared.time.Time;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Shows the "Set Time" dialog ([TimePicker]) for one use, e.g. for a time box.
///
/// ```java
/// timePickerProvider.get().edit(this, box.getValue(), box::setValue);
/// ```
public class TimePickerController extends Controller {

    private final TimePicker view;

    /// Made by GIN for one use, with the dialog's content.
    @Inject
    public TimePickerController(final EventBus eventBus, final TimePicker view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// @param visible Whether the hour can be chosen.
    public void setHourVisible(final boolean visible) {
        view.setHourVisible(visible);
    }

    /// @param visible Whether the minute can be chosen.
    public void setMinuteVisible(final boolean visible) {
        view.setMinuteVisible(visible);
    }

    /// @param visible Whether the second can be chosen.
    public void setSecondVisible(final boolean visible) {
        view.setSecondVisible(visible);
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK gives the time chosen to `onOk`.
    ///
    /// @param opener What shows it.
    /// @param time   The time to start from, or null for midnight.
    /// @param onOk   Given the time chosen when OK is pressed.
    public void edit(final HasControllerNode opener, final Time time, final Consumer<Time> onOk) {
        view.setTime(time);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Set Time")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(view.getTime());
                    }
                    e.hide();
                })
                .fire();
    }
}
