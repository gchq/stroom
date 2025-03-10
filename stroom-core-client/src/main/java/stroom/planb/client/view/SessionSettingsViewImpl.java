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
import stroom.planb.client.presenter.SessionSettingsPresenter.SessionSettingsView;
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

public class SessionSettingsViewImpl
        extends ViewWithUiHandlers<PlanBSettingsUiHandlers>
        implements SessionSettingsView {

    private final Widget widget;

    @UiField
    CustomCheckBox condense;
    @UiField
    FormGroup condenseAgePanel;
    @UiField
    ValueSpinner condenseAge;
    @UiField
    SelectionBox<TimeUnit> condenseTimeUnit;
    @UiField
    CustomCheckBox retainForever;
    @UiField
    FormGroup retainAgePanel;
    @UiField
    ValueSpinner retainAge;
    @UiField
    SelectionBox<TimeUnit> retainTimeUnit;
    @UiField
    TextBox maxStoreSize;
    @UiField
    CustomCheckBox overwrite;

    private boolean readOnly;

    @Inject
    public SessionSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        condenseAge.setMin(1);
        condenseAge.setMax(9999);
        condenseAge.setValue(1);

        condenseTimeUnit.addItem(TimeUnit.DAYS);
        condenseTimeUnit.addItem(TimeUnit.WEEKS);
        condenseTimeUnit.addItem(TimeUnit.MONTHS);
        condenseTimeUnit.addItem(TimeUnit.YEARS);
        condenseTimeUnit.setValue(TimeUnit.YEARS);

        retainAge.setMin(1);
        retainAge.setMax(9999);
        retainAge.setValue(1);

        retainTimeUnit.addItem(TimeUnit.DAYS);
        retainTimeUnit.addItem(TimeUnit.WEEKS);
        retainTimeUnit.addItem(TimeUnit.MONTHS);
        retainTimeUnit.addItem(TimeUnit.YEARS);
        retainTimeUnit.setValue(TimeUnit.YEARS);

        setOverwrite(true);
        setCondenseEnabled(this.condense.getValue());
        setRetainEnabled(!retainForever.getValue());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public DurationSetting getCondense() {
        return DurationSetting
                .builder()
                .enabled(condense.getValue())
                .duration(SimpleDuration
                        .builder()
                        .time(condenseAge.getValue())
                        .timeUnit(condenseTimeUnit.getValue())
                        .build())
                .build();
    }

    @Override
    public void setCondense(final DurationSetting condense) {
        this.condense.setValue(false);
        this.condenseAge.setValue(1);
        this.condenseTimeUnit.setValue(TimeUnit.YEARS);
        if (condense != null) {
            this.condense.setValue(condense.isEnabled());
            if (condense.getDuration() != null) {
                this.condenseAge.setValue(condense.getDuration().getTime());
                this.condenseTimeUnit.setValue(condense.getDuration().getTimeUnit());
            }
        }
        setCondenseEnabled(this.condense.getValue());
    }

    @Override
    public DurationSetting getRetain() {
        return DurationSetting
                .builder()
                .enabled(!retainForever.getValue())
                .duration(SimpleDuration
                        .builder()
                        .time(retainAge.getValue())
                        .timeUnit(retainTimeUnit.getValue())
                        .build())
                .build();
    }

    @Override
    public void setRetain(final DurationSetting retain) {
        this.retainForever.setValue(true);
        this.retainAge.setValue(1);
        this.retainTimeUnit.setValue(TimeUnit.YEARS);
        if (retain != null) {
            this.retainForever.setValue(!retain.isEnabled());
            if (retain.getDuration() != null) {
                this.retainAge.setValue(retain.getDuration().getTime());
                this.retainTimeUnit.setValue(retain.getDuration().getTimeUnit());
            }
        }
        setRetainEnabled(!retainForever.getValue());
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

    private void setRetainEnabled(final boolean enabled) {
        if (!readOnly) {
            if (enabled) {
                retainAgePanel.getElement().getStyle().setOpacity(1);
            } else {
                retainAgePanel.getElement().getStyle().setOpacity(0.5);
            }
            retainAge.setEnabled(enabled);
            retainTimeUnit.setEnabled(enabled);
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        condense.setEnabled(!readOnly);
        condenseAge.setEnabled(!readOnly);
        condenseTimeUnit.setEnabled(!readOnly);
        retainForever.setEnabled(!readOnly);
        retainAge.setEnabled(!readOnly);
        retainTimeUnit.setEnabled(!readOnly);
        maxStoreSize.setEnabled(!readOnly);
        overwrite.setEnabled(!readOnly);
    }

    @UiHandler("condense")
    public void onCondense(final ValueChangeEvent<Boolean> event) {
        setCondenseEnabled(condense.getValue());
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

    @UiHandler("retainForever")
    public void onRetainForever(final ValueChangeEvent<Boolean> event) {
        setRetainEnabled(!retainForever.getValue());
        getUiHandlers().onChange();
    }

    @UiHandler("retainAge")
    public void onRetainAge(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("retainTimeUnit")
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

    public interface Binder extends UiBinder<Widget, SessionSettingsViewImpl> {

    }
}
