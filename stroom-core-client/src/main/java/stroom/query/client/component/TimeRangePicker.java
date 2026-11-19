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

import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.query.api.TimeRanges;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.form.client.QuickSettings;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Time Range dialog's content: quick settings for common ranges, and the After and Before
/// times, each a date-time box that also takes expressions such as `now()-1d`. Its markup is
/// `TimeRangePicker.ui.xml`; [TimeRangePickerController] shows it.
public class TimeRangePicker extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickSettings quickSettings;
    @UiField
    DateTimeBox timeFrom;
    @UiField
    DateTimeBox timeTo;

    /// Makes the dialog's content.
    public TimeRangePicker() {
        initWidget(BINDER.createAndBindUi(this));
        addRanges(quickSettings.addGroup("Relative"), TimeRanges.RELATIVE_RANGES);
        addRanges(quickSettings.addGroup("Present"), TimeRanges.PRESENT_RANGES);
        addRanges(quickSettings.addGroup("Past"), TimeRanges.PAST_RANGES);
        addRanges(quickSettings.addGroup("All"), TimeRanges.ALL_TIME);
    }

    /// Puts the focus on After Time.
    public void focus() {
        timeFrom.focus();
    }

    /// @return The range entered: a quick setting's, if it is one.
    public TimeRange getValue() {
        final TimeRange range = new TimeRange(null, Condition.BETWEEN, normalise(timeFrom.getText()),
                normalise(timeTo.getText()));
        for (final TimeRange timeRange : TimeRanges.ALL_RANGES) {
            if (timeRange.equals(range)) {
                return timeRange;
            }
        }
        return range;
    }

    /// @param value The range to show.
    public void setValue(final TimeRange value) {
        timeFrom.setText(value.getFrom());
        timeTo.setText(value.getTo());
    }

    /// @return The After Time box, for a controller to open its dialog.
    public DateTimeBox getTimeFrom() {
        return timeFrom;
    }

    /// @return The Before Time box, for a controller to open its dialog.
    public DateTimeBox getTimeTo() {
        return timeTo;
    }

    private static String normalise(final String string) {
        if (string != null && !string.trim().isEmpty()) {
            return string.trim();
        }
        return null;
    }

    // A quick setting fills in both times
    private void addRanges(final QuickSettings.Group group, final TimeRange... timeRanges) {
        for (final TimeRange timeRange : timeRanges) {
            group.add(timeRange.getName(), () -> setValue(timeRange));
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TimeRangePicker> {

    }
}
