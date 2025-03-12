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
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.client.presenter.TemporalRangedStateSettingsPresenter.TemporalRangedStateSettingsView;
import stroom.planb.shared.DurationSetting;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class TemporalRangedStateSettingsViewImpl
        extends ViewWithUiHandlers<PlanBSettingsUiHandlers>
        implements TemporalRangedStateSettingsView {

    private final Widget widget;

    @UiField
    CustomCheckBox condenseEnabled;
    @UiField
    FormGroup condenseAgePanel;
    @UiField
    ValueSpinner condenseAge;
    @UiField
    SelectionBox<TimeUnit> condenseTimeUnit;
    @UiField
    CustomCheckBox retentionEnabled;
    @UiField
    FormGroup retentionAgePanel;
    @UiField
    ValueSpinner retentionAge;
    @UiField
    SelectionBox<TimeUnit> retentionTimeUnit;
    @UiField
    TextBox maxStoreSize;
    @UiField
    CustomCheckBox overwrite;

    private boolean readOnly;

    @Inject
    public TemporalRangedStateSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        condenseAge.setMin(1);
        condenseAge.setMax(9999);
        condenseAge.setValue(1);

        condenseTimeUnit.addItem(TimeUnit.DAYS);
        condenseTimeUnit.addItem(TimeUnit.WEEKS);
        condenseTimeUnit.addItem(TimeUnit.MONTHS);
        condenseTimeUnit.addItem(TimeUnit.YEARS);
        condenseTimeUnit.setValue(TimeUnit.YEARS);

        retentionAge.setMin(1);
        retentionAge.setMax(9999);
        retentionAge.setValue(1);

        retentionTimeUnit.addItem(TimeUnit.DAYS);
        retentionTimeUnit.addItem(TimeUnit.WEEKS);
        retentionTimeUnit.addItem(TimeUnit.MONTHS);
        retentionTimeUnit.addItem(TimeUnit.YEARS);
        retentionTimeUnit.setValue(TimeUnit.YEARS);

        setOverwrite(true);
        setCondenseEnabled(this.condenseEnabled.getValue());
        setRetentionEnabled(this.retentionEnabled.getValue());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public DurationSetting getCondense() {
        return DurationSetting
                .builder()
                .enabled(condenseEnabled.getValue())
                .duration(SimpleDuration
                        .builder()
                        .time(condenseAge.getValue())
                        .timeUnit(condenseTimeUnit.getValue())
                        .build())
                .build();
    }

    @Override
    public void setCondense(final DurationSetting condense) {
        this.condenseEnabled.setValue(false);
        this.condenseAge.setValue(1);
        this.condenseTimeUnit.setValue(TimeUnit.YEARS);
        if (condense != null) {
            this.condenseEnabled.setValue(condense.isEnabled());
            if (condense.getDuration() != null) {
                this.condenseAge.setValue(condense.getDuration().getTime());
                this.condenseTimeUnit.setValue(condense.getDuration().getTimeUnit());
            }
        }
        setCondenseEnabled(this.condenseEnabled.getValue());
    }

    @Override
    public DurationSetting getRetention() {
        return DurationSetting
                .builder()
                .enabled(retentionEnabled.getValue())
                .duration(SimpleDuration
                        .builder()
                        .time(retentionAge.getValue())
                        .timeUnit(retentionTimeUnit.getValue())
                        .build())
                .build();
    }

    @Override
    public void setRetention(final DurationSetting retention) {
        this.retentionEnabled.setValue(false);
        this.retentionAge.setValue(1);
        this.retentionTimeUnit.setValue(TimeUnit.YEARS);
        if (retention != null) {
            this.retentionEnabled.setValue(retention.isEnabled());
            if (retention.getDuration() != null) {
                this.retentionAge.setValue(retention.getDuration().getTime());
                this.retentionTimeUnit.setValue(retention.getDuration().getTimeUnit());
            }
        }
        setRetentionEnabled(retentionEnabled.getValue());
    }

    @Override
    public String getMaxStoreSize() {
        return maxStoreSize.getValue();
    }

    @Override
    public void setMaxStoreSize(final String maxStoreSize) {
        this.maxStoreSize.setValue(maxStoreSize);
    }

    @Override
    public Boolean getOverwrite() {
        return overwrite.getValue()
                ? null
                : overwrite.getValue();
    }

    @Override
    public void setOverwrite(final Boolean overwrite) {
        this.overwrite.setValue(overwrite == null || overwrite);
    }

    private void setCondenseEnabled(final boolean enabled) {
        if (!readOnly) {
            if (enabled) {
                condenseAgePanel.getElement().getStyle().setOpacity(1);
            } else {
                condenseAgePanel.getElement().getStyle().setOpacity(0.5);
            }
            condenseAge.setEnabled(enabled);
            condenseTimeUnit.setEnabled(enabled);
        }
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
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        condenseEnabled.setEnabled(!readOnly);
        condenseAge.setEnabled(!readOnly);
        condenseTimeUnit.setEnabled(!readOnly);
        retentionEnabled.setEnabled(!readOnly);
        retentionAge.setEnabled(!readOnly);
        retentionTimeUnit.setEnabled(!readOnly);
        maxStoreSize.setEnabled(!readOnly);
        overwrite.setEnabled(!readOnly);
    }

    @UiHandler("condenseEnabled")
    public void onCondenseEnabled(final ValueChangeEvent<Boolean> event) {
        setCondenseEnabled(condenseEnabled.getValue());
        getUiHandlers().onChange();
    }

    @UiHandler("condenseAge")
    public void onCondenseAge(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("condenseTimeUnit")
    public void onCondenseTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
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

    @UiHandler("maxStoreSize")
    public void onMaxStoreSize(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("overwrite")
    public void onOverwrite(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, TemporalRangedStateSettingsViewImpl> {

    }
}
