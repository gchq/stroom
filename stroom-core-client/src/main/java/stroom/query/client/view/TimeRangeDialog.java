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

package stroom.query.client.view;

import stroom.query.api.TimeRange;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.datepicker.client.component.DateTimePicker;
import stroom.widget.datepicker.client.component.DateTimePickerController;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/// The Time Range dialog a [TimeRangeSelector] opens: its quick settings and its After and Before
/// times ([TimeRangePopup]), shown as a modal OK/Cancel dialog below the selector, as a picker whose
/// value goes back into a field is. So it is a `dialog` named by its title, the focus is kept in it
/// (starting in After Time), Escape cancels it, and the focus goes back to the selector when it
/// closes.
public class TimeRangeDialog extends MyPresenterWidget<TimeRangeDialog.TimeRangeView> {

    private static final String CAPTION = "Time Range";

    /// @param eventBus The event bus the dialog is shown on.
    /// @param view     The dialog's content.
    public TimeRangeDialog(final EventBus eventBus, final TimeRangeView view) {
        super(eventBus, view);
        for (final DateTimeBox box : view.getDateTimeBoxes()) {
            own(new DateTimeBoxController(() ->
                    new DateTimePickerController(eventBus, new DateTimePicker()))).attach(box);
        }
    }

    /// Shows the dialog on a time range.
    ///
    /// @param value  The time range it starts with.
    /// @param anchor The element it is shown below (the selector).
    /// @param onOk   Given the time range chosen when OK is pressed.
    public void show(final TimeRange value, final Element anchor, final Consumer<TimeRange> onOk) {
        getView().setValue(value, false);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(CAPTION)
                .modal()
                .popupPosition(new PopupPosition(new Rect(anchor), PopupLocation.BELOW))
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(getView().getValue());
                    }
                    e.hide();
                })
                .fire();
    }


    // --------------------------------------------------------------------------------


    /// The dialog's content: the time range it edits.
    public interface TimeRangeView extends View, Focus, HasValue<TimeRange> {

        /// @return Its After and Before boxes, for a controller to open their dialogs.
        List<DateTimeBox> getDateTimeBoxes();

    }
}
