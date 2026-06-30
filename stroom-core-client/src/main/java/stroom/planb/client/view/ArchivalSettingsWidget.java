/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.client.view;

import stroom.item.client.SelectionBox;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.ArchivalSettings;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class ArchivalSettingsWidget extends AbstractSettingsWidget implements ArchivalSettingsView {

    private final Widget widget;

    @UiField
    CustomCheckBox archivalEnabled;
    @UiField
    FormGroup archivalLeadTimePanel;
    @UiField
    ValueSpinner archivalAge;
    @UiField
    SelectionBox<TimeUnit> archivalTimeUnit;
    @UiField
    SelectionBox<ArchivalGranularity> archivalGranularity;

    private boolean readOnly;
    private boolean hasSharedPath;

    @Inject
    public ArchivalSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);

        archivalAge.setMin(1);
        archivalAge.setMax(9999);
        archivalAge.setValue(7);

        archivalTimeUnit.addItem(TimeUnit.MINUTES);
        archivalTimeUnit.addItem(TimeUnit.HOURS);
        archivalTimeUnit.addItem(TimeUnit.DAYS);
        archivalTimeUnit.addItem(TimeUnit.WEEKS);
        archivalTimeUnit.addItem(TimeUnit.MONTHS);
        archivalTimeUnit.setValue(TimeUnit.DAYS);

        archivalGranularity.addItem(ArchivalGranularity.HOUR);
        archivalGranularity.addItem(ArchivalGranularity.DAY);
        archivalGranularity.addItem(ArchivalGranularity.WEEK);
        archivalGranularity.setValue(ArchivalGranularity.DAY);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public ArchivalSettings getArchival() {
        return new ArchivalSettings.Builder()
                .enabled(archivalEnabled.getValue())
                .duration(SimpleDuration.builder()
                        .time(archivalAge.getValue())
                        .timeUnit(archivalTimeUnit.getValue())
                        .build())
                .granularity(archivalGranularity.getValue())
                .build();
    }

    @Override
    public void setArchival(final ArchivalSettings archival) {
        final ArchivalSettings settings = archival != null
                ? archival
                : new ArchivalSettings.Builder().build();
        this.archivalEnabled.setValue(settings.isEnabled());
        if (settings.getDuration() != null) {
            this.archivalAge.setValue(settings.getDuration().getTime());
            this.archivalTimeUnit.setValue(settings.getDuration().getTimeUnit());
        } else {
            this.archivalAge.setValue(7L);
            this.archivalTimeUnit.setValue(TimeUnit.DAYS);
        }
        this.archivalGranularity.setValue(
                settings.getGranularity() != null
                        ? settings.getGranularity()
                        : ArchivalGranularity.DAY);
        updateStates();
    }

    public void setHasSharedPath(final boolean hasSharedPath) {
        this.hasSharedPath = hasSharedPath;
        updateStates();
    }

    private void updateStates() {
        final boolean editable = !readOnly && hasSharedPath;
        archivalEnabled.setEnabled(editable);

        final boolean on = archivalEnabled.getValue();
        archivalLeadTimePanel.getElement().getStyle().setOpacity(editable ? 1 : 0.5);
        archivalAge.setEnabled(editable && on);
        archivalTimeUnit.setEnabled(editable && on);
        archivalGranularity.setEnabled(editable && on);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateStates();
    }

    @UiHandler("archivalEnabled")
    public void onArchivalEnabled(final ValueChangeEvent<Boolean> event) {
        updateStates();
        getUiHandlers().onChange();
    }

    @UiHandler("archivalAge")
    public void onArchivalAge(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("archivalTimeUnit")
    public void onArchivalTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("archivalGranularity")
    public void onArchivalGranularity(final ValueChangeEvent<ArchivalGranularity> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, ArchivalSettingsWidget> {

    }
}
