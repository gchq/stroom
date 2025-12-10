/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.widget.customdatebox.client.MyDateBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class TimeRangePopup implements HasValue<TimeRange>, Focus {

    private final Widget widget;

    @UiField
    SimplePanel quickSettings;
    @UiField
    MyDateBox timeFrom;
    @UiField
    MyDateBox timeTo;

    public TimeRangePopup() {
        final Binder binder = GWT.create(Binder.class);
        widget = binder.createAndBindUi(this);

        final FlowPanel recent = createPanel("Relative");
        for (final TimeRange timeRange : TimeRanges.RELATIVE_RANGES) {
            recent.add(createLabel(timeRange));
        }

        final FlowPanel present = createPanel("Present");
        for (final TimeRange timeRange : TimeRanges.PRESENT_RANGES) {
            present.add(createLabel(timeRange));
        }

        final FlowPanel past = createPanel("Past");
        for (final TimeRange timeRange : TimeRanges.PAST_RANGES) {
            past.add(createLabel(timeRange));
        }

        final FlowPanel other = createPanel("All");
        other.add(createLabel(TimeRanges.ALL_TIME));

        final FlowPanel quickSettingsPanel = new FlowPanel();
        quickSettingsPanel.setStyleName("timeRange-quickSettings");
        quickSettingsPanel.add(recent);
        quickSettingsPanel.add(present);
        quickSettingsPanel.add(past);
        quickSettingsPanel.add(other);

        this.quickSettings.setWidget(quickSettingsPanel);
    }

    @Override
    public void focus() {
        timeFrom.focus();
    }

    public void setUtc(final boolean utc) {
        timeFrom.setUtc(utc);
        timeTo.setUtc(utc);
    }

    @Override
    public TimeRange getValue() {
        final String from = normalise(timeFrom.getValue());
        final String to = normalise(timeTo.getValue());

        final TimeRange range = new TimeRange(null, Condition.BETWEEN, from, to);
        // See if this is a quick select range.
        for (final TimeRange timeRange : TimeRanges.ALL_RANGES) {
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
        timeFrom.setValue(value.getFrom());
        timeTo.setValue(value.getTo());
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

    private FlowPanel createPanel(final String name) {
        final FlowPanel panel = new FlowPanel();
        panel.addStyleName("timeRange-quickSettingPanel");
        final Label label = new Label(name, false);
        label.addStyleName("timeRange-quickSettingPanel-title");
        panel.add(label);
        return panel;
    }

    private Label createLabel(final TimeRange timeRange) {
        final Label label = new Label(timeRange.getName(), false);
        label.addStyleName("timeRange-quickSetting");
        label.addClickHandler(event -> {
            setValue(timeRange, true);
        });
        return label;
    }

    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, TimeRangePopup> {

    }
}
