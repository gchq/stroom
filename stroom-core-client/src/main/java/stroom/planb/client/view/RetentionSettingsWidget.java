/*
 * Copyright 2025 Crown Copyright
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
    FormGroup retentionCheckIntervalPanel;
    @UiField
    ValueSpinner retentionCheckInterval;
    @UiField
    SelectionBox<TimeUnit> retentionCheckIntervalTimeUnit;
    @UiField
    FormGroup useStateTimePanel;
    @UiField
    CustomCheckBox useStateTime;

    private boolean readOnly;
    private boolean useStateTimeVisible = true;
    private boolean checkIntervalVisible;

    @Inject
    public RetentionSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);

        retentionCheckIntervalPanel.setVisible(checkIntervalVisible);

        retentionAge.setMin(1);
        retentionAge.setMax(9999);
        retentionAge.setValue(1);

        retentionTimeUnit.addItem(TimeUnit.DAYS);
        retentionTimeUnit.addItem(TimeUnit.WEEKS);
        retentionTimeUnit.addItem(TimeUnit.MONTHS);
        retentionTimeUnit.addItem(TimeUnit.YEARS);
        retentionTimeUnit.setValue(TimeUnit.YEARS);

        retentionCheckInterval.setMin(1);
        retentionCheckInterval.setMax(9999);
        retentionCheckInterval.setValue(1);

        retentionCheckIntervalTimeUnit.addItem(TimeUnit.MINUTES);
        retentionCheckIntervalTimeUnit.addItem(TimeUnit.HOURS);
        retentionCheckIntervalTimeUnit.addItem(TimeUnit.DAYS);
        retentionCheckIntervalTimeUnit.setValue(TimeUnit.HOURS);
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
                .checkInterval(SimpleDuration
                        .builder()
                        .time(retentionCheckInterval.getValue())
                        .timeUnit(retentionCheckIntervalTimeUnit.getValue())
                        .build())
                .useStateTime(useStateTime.getValue())
                .build();
    }

    @Override
    public void setRetention(final RetentionSettings retention) {
        final RetentionSettings settings = new RetentionSettings.Builder(retention).build();
        this.retentionAge.setValue(1);
        this.retentionTimeUnit.setValue(TimeUnit.YEARS);
        this.retentionCheckInterval.setValue(1);
        this.retentionCheckIntervalTimeUnit.setValue(TimeUnit.HOURS);
        this.retentionEnabled.setValue(settings.isEnabled());
        if (settings.getDuration() != null) {
            this.retentionAge.setValue(settings.getDuration().getTime());
            this.retentionTimeUnit.setValue(settings.getDuration().getTimeUnit());
        }
        if (settings.getCheckInterval() != null) {
            this.retentionCheckInterval.setValue(settings.getCheckInterval().getTime());
            this.retentionCheckIntervalTimeUnit.setValue(settings.getCheckInterval().getTimeUnit());
        }
        // Clamped, so a value the user cannot see is never read back out by getRetention().
        this.useStateTime.setValue(useStateTimeVisible && settings.useStateTime());
        updateStates();
    }

    /**
     * Show the check frequency for a store that acts on it. Only the shared file store merge
     * processor reads it; a store whose retention runs on its own schedule has nothing to set here,
     * so the field stays hidden and keeps whatever value the document already holds.
     */
    public void setCheckIntervalVisible(final boolean visible) {
        this.checkIntervalVisible = visible;
        retentionCheckIntervalPanel.setVisible(visible);
        updateStates();
    }

    /**
     * Hide the Use State Time option for a store whose retention ignores it.
     */
    public void setUseStateTimeVisible(final boolean visible) {
        this.useStateTimeVisible = visible;
        if (!visible) {
            useStateTime.setValue(false);
        }
        useStateTimePanel.setVisible(visible);
    }

    private void updateStates() {
        final boolean editable = !readOnly;
        retentionEnabled.setEnabled(editable);

        final boolean retentionOn = retentionEnabled.getValue();
        if (editable) {
            if (retentionOn) {
                retentionAgePanel.getElement().getStyle().setOpacity(1);
            } else {
                retentionAgePanel.getElement().getStyle().setOpacity(0.5);
            }
        }
        if (editable && checkIntervalVisible) {
            retentionCheckIntervalPanel.getElement().getStyle()
                    .setOpacity(retentionOn ? 1 : 0.5);
        }
        retentionAge.setEnabled(editable && retentionOn);
        retentionTimeUnit.setEnabled(editable && retentionOn);
        retentionCheckInterval.setEnabled(editable && retentionOn && checkIntervalVisible);
        retentionCheckIntervalTimeUnit.setEnabled(editable && retentionOn && checkIntervalVisible);
        useStateTime.setEnabled(editable && retentionOn);
    }

    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateStates();
    }

    @UiHandler("retentionEnabled")
    public void onRetentionEnabled(final ValueChangeEvent<Boolean> event) {
        updateStates();
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

    @UiHandler("retentionCheckInterval")
    public void onRetentionCheckInterval(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("retentionCheckIntervalTimeUnit")
    public void onRetentionCheckIntervalTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("useStateTime")
    public void onUseStateTime(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, RetentionSettingsWidget> {

    }
}
