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

import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Shows the "Set Date And Time" dialog ([DateTimePicker]) for one use, e.g. for a date-time box.
///
/// ```java
/// dateTimePickerProvider.get().edit(this, time, chosen -> box.setValue(chosen, true));
/// ```
public class DateTimePickerController extends Controller {

    private final DateTimePicker view;

    /// Made by GIN for one use, with the dialog's content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     Its component.
    @Inject
    public DateTimePickerController(final EventBus eventBus, final DateTimePicker view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK gives the time chosen to `onOk`.
    ///
    /// @param opener What shows it.
    /// @param time   The time to start from, in milliseconds since the epoch.
    /// @param onOk   Given the time chosen, in milliseconds since the epoch, when OK is pressed.
    public void edit(final HasControllerNode opener, final long time, final Consumer<Long> onOk) {
        view.setTime(time);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Set Date And Time")
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
