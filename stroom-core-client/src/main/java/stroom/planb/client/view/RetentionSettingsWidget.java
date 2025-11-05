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

package stroom.planb.client.view;

import stroom.item.client.SelectionBox;
import stroom.planb.shared.RetentionSettings;
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

public class RetentionSettingsWidget extends AbstractSettingsWidget implements RetentionSettingsView {

    private final Widget widget;

    @UiField
    CustomCheckBox retentionEnabled;
    @UiField
    FormGroup retentionAgePanel;
    @UiField
    ValueSpinner retentionAge;
    @UiField
    SelectionBox<TimeUnit> retentionTimeUnit;
    @UiField
    CustomCheckBox useStateTime;

    private boolean readOnly;

    @Inject
    public RetentionSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);

        retentionAge.setMin(1);
        retentionAge.setMax(9999);
        retentionAge.setValue(1);

        retentionTimeUnit.addItem(TimeUnit.DAYS);
        retentionTimeUnit.addItem(TimeUnit.WEEKS);
        retentionTimeUnit.addItem(TimeUnit.MONTHS);
        retentionTimeUnit.addItem(TimeUnit.YEARS);
        retentionTimeUnit.setValue(TimeUnit.YEARS);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public RetentionSettings getRetention() {
        return new RetentionSettings.Builder()
                .enabled(retentionEnabled.getValue())
                .duration(SimpleDuration
                        .builder()
                        .time(retentionAge.getValue())
                        .timeUnit(retentionTimeUnit.getValue())
                        .build())
                .useStateTime(useStateTime.getValue())
                .build();
    }

    @Override
    public void setRetention(final RetentionSettings retention) {
        final RetentionSettings settings = new RetentionSettings.Builder(retention).build();
        this.retentionAge.setValue(1);
        this.retentionTimeUnit.setValue(TimeUnit.YEARS);
        this.retentionEnabled.setValue(settings.isEnabled());
        if (settings.getDuration() != null) {
            this.retentionAge.setValue(settings.getDuration().getTime());
            this.retentionTimeUnit.setValue(settings.getDuration().getTimeUnit());
        }
        this.useStateTime.setValue(settings.useStateTime());
        setRetentionEnabled(retentionEnabled.getValue());
    }

    private void setRetentionEnabled(final boolean enabled) {
        if (!readOnly) {
            if (enabled) {
                retentionAgePanel.getElement().getStyle().setOpacity(1);
            } else {
                retentionAgePanel.getElement().getStyle().setOpacity(0.5);
            }
            retentionAge.setEnabled(enabled);
            retentionTimeUnit.setEnabled(enabled);
            useStateTime.setEnabled(enabled);
        }
    }

    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        retentionEnabled.setEnabled(!readOnly);
        retentionAge.setEnabled(!readOnly);
        retentionTimeUnit.setEnabled(!readOnly);
        useStateTime.setEnabled(!readOnly);
    }

    @UiHandler("retentionEnabled")
    public void onRetentionEnabled(final ValueChangeEvent<Boolean> event) {
        setRetentionEnabled(retentionEnabled.getValue());
        getUiHandlers().onChange();
    }

    @UiHandler("retentionAge")
    public void onRetainAge(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("retentionTimeUnit")
    public void onRetainTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("useStateTime")
    public void onUseStateTime(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, RetentionSettingsWidget> {

    }
}
