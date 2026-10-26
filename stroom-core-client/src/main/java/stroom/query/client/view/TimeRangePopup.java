/*
 * Copyright 2022 Crown Copyright
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

import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.widget.button.client.QuickSettings;
import stroom.widget.datepicker.client.DateTimeBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

/// The content of the Time Range dialog ([TimeRangeDialog]): quick settings for common ranges, and
/// the After and Before times, each a date-time box that also takes expressions such as `now()-1d`.
public class TimeRangePopup extends ViewImpl implements TimeRangeDialog.TimeRangeView {

    private final Widget widget;

    @UiField
    SimplePanel quickSettings;
    @UiField
    DateTimeBox timeFrom;
    @UiField
    DateTimeBox timeTo;

    public TimeRangePopup() {
        final Binder binder = GWT.create(Binder.class);
        widget = binder.createAndBindUi(this);

        final QuickSettings settings = new QuickSettings();
        addRanges(settings.addGroup("Relative"), stroom.query.api.TimeRanges.RELATIVE_RANGES);
        addRanges(settings.addGroup("Present"), stroom.query.api.TimeRanges.PRESENT_RANGES);
        addRanges(settings.addGroup("Past"), stroom.query.api.TimeRanges.PAST_RANGES);
        addRanges(settings.addGroup("All"), stroom.query.api.TimeRanges.ALL_TIME);
        this.quickSettings.setWidget(settings);
    }

    @Override
    public void focus() {
        timeFrom.focus();
    }

    @Override
    public TimeRange getValue() {
        final String from = normalise(timeFrom.getText());
        final String to = normalise(timeTo.getText());

        final TimeRange range = new TimeRange(null, Condition.BETWEEN, from, to);
        // See if this is a quick select range.
        for (final TimeRange timeRange : stroom.query.api.TimeRanges.ALL_RANGES) {
            if (timeRange.equals(range)) {
                return timeRange;
            }
        }
        return range;
    }

    @Override
    public void setValue(final TimeRange value) {
        setValue(value, false);
    }

    @Override
    public void setValue(final TimeRange value, final boolean fireEvents) {
        timeFrom.setText(value.getFrom());
        timeTo.setText(value.getTo());
        if (fireEvents) {
            ValueChangeEvent.fire(this, value);
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<TimeRange> handler) {
        return widget.addHandler(handler, ValueChangeEvent.getType());
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        widget.fireEvent(event);
    }

    private String normalise(final String string) {
        if (string != null && !string.trim().isEmpty()) {
            return string.trim();
        }
        return null;
    }

    // A quick setting fills in both times
    private void addRanges(final QuickSettings.Group group, final TimeRange... timeRanges) {
        for (final TimeRange timeRange : timeRanges) {
            group.add(timeRange.getName(), () -> setValue(timeRange, true));
        }
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, TimeRangePopup> {

    }
}
