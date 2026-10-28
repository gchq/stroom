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

package stroom.query.client.component;

import stroom.query.api.TimeRange;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Shows the Time Range dialog ([TimeRangePicker]) for one use, below what opens it (a
/// [stroom.query.client.component.TimeRangeSelector]), as a picker whose value goes back into a field
/// is. So it is a `dialog` named by its title, the focus is kept in it (starting in After Time),
/// Escape cancels it, and the focus goes back to the selector when it closes.
public class TimeRangePickerController extends Controller {

    private static final String CAPTION = "Time Range";

    private final TimeRangePicker view;

    /// Made by GIN for one use, with the dialog's content and what opens its times' dialogs.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          Its component.
    /// @param dateTimeBoxControllerProvider Makes the date time box controller.
    @Inject
    public TimeRangePickerController(final EventBus eventBus,
                                     final TimeRangePicker view,
                                     final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        make(dateTimeBoxControllerProvider).attach(view.getTimeFrom());
        make(dateTimeBoxControllerProvider).attach(view.getTimeTo());
    }

    /// Shows the dialog, owned by `opener` until it is closed. OK gives the range chosen to `onOk`.
    ///
    /// @param opener What shows it.
    /// @param value  The range it starts with.
    /// @param anchor The element it is shown below, e.g. the selector.
    /// @param onOk   Given the range chosen.
    public void edit(final HasControllerNode opener,
                     final TimeRange value,
                     final Element anchor,
                     final Consumer<TimeRange> onOk) {
        view.setValue(value);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(CAPTION)
                .modal()
                .popupPosition(new PopupPosition(new Rect(anchor), PopupLocation.BELOW))
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(view.getValue());
                    }
                    e.hide();
                })
                .fire();
    }
}
