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

package stroom.dashboard.client.main;

import stroom.query.api.v2.TimeRange;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class TimeRangePopup {

    private final Widget widget;

    @UiField
    SimplePanel quickSettings;
    @UiField
    SimplePanel timeRangeContainer;

    private final TimeRangeEditor timeRangeEditor;

    public TimeRangePopup() {
        final Binder binder = GWT.create(Binder.class);
        widget = binder.createAndBindUi(this);

        timeRangeEditor = new TimeRangeEditor();
        timeRangeContainer.setWidget(timeRangeEditor);

        final FlowPanel recent = createPanel("Recent");
        for (final TimeRange timeRange : TimeRanges.RECENT_RANGES) {
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

    public void setUtc(final boolean utc) {
        timeRangeEditor.setUtc(utc);
    }

    public void read(final TimeRange timeRange) {
        timeRangeEditor.read(timeRange);
    }

    public TimeRange write() {
        return timeRangeEditor.write();
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
            timeRangeEditor.read(timeRange);
        });
        return label;
    }

    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, TimeRangePopup> {

    }
}
