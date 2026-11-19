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
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The "Set Time" dialog's content: a spinner each for the hour, minute and second, each with a
/// Reset link that zeroes it and the parts after it. Its markup is `TimePicker.ui.xml`, styled by
/// `date-time-view.css`; [TimePickerController] shows it.
public class TimePicker extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup hourGroup;
    @UiField
    ValueSpinner hour;
    @UiField
    LinkButton hourReset;
    @UiField
    FormGroup minuteGroup;
    @UiField
    ValueSpinner minute;
    @UiField
    LinkButton minuteReset;
    @UiField
    FormGroup secondGroup;
    @UiField
    ValueSpinner second;
    @UiField
    LinkButton secondReset;

    /// Makes a picker showing midnight.
    public TimePicker() {
        initWidget(BINDER.createAndBindUi(this));
        hourReset.setOnClick(() -> {
            hour.setValue(0);
            minute.setValue(0);
            second.setValue(0);
        });
        minuteReset.setOnClick(() -> {
            minute.setValue(0);
            second.setValue(0);
        });
        secondReset.setOnClick(() -> second.setValue(0));
    }

    /// @return The time chosen.
    public Time getTime() {
        return new Time(hour.getIntValue(), minute.getIntValue(), second.getIntValue());
    }

    /// @param time The time to show, or null for midnight.
    public void setTime(final Time time) {
        final Time shown = time == null
                ? Time.ZERO
                : time;
        hour.setValue(shown.getHour());
        minute.setValue(shown.getMinute());
        second.setValue(shown.getSecond());
    }

    /// @param visible Whether the hour can be chosen.
    public void setHourVisible(final boolean visible) {
        hourGroup.setVisible(visible);
    }

    /// @param visible Whether the minute can be chosen.
    public void setMinuteVisible(final boolean visible) {
        minuteGroup.setVisible(visible);
    }

    /// @param visible Whether the second can be chosen.
    public void setSecondVisible(final boolean visible) {
        secondGroup.setVisible(visible);
    }

    /// Puts the focus on the hour.
    public void focus() {
        hour.focus();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TimePicker> {

    }
}
