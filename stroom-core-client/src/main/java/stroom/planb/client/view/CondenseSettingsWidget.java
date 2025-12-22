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

package stroom.planb.client.view;

import stroom.item.client.SelectionBox;
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
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class CondenseSettingsWidget extends AbstractSettingsWidget implements CondenseSettingsView {

    private final Widget widget;

    @UiField
    CustomCheckBox condenseEnabled;
    @UiField
    FormGroup condenseAgePanel;
    @UiField
    ValueSpinner condenseAge;
    @UiField
    SelectionBox<TimeUnit> condenseTimeUnit;

    private boolean readOnly;

    @Inject
    public CondenseSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);

        condenseAge.setMin(1);
        condenseAge.setMax(9999);
        condenseAge.setValue(1);

        condenseTimeUnit.addItem(TimeUnit.DAYS);
        condenseTimeUnit.addItem(TimeUnit.WEEKS);
        condenseTimeUnit.addItem(TimeUnit.MONTHS);
        condenseTimeUnit.addItem(TimeUnit.YEARS);
        condenseTimeUnit.setValue(TimeUnit.YEARS);

        setCondenseEnabled(this.condenseEnabled.getValue());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public DurationSetting getCondense() {
        return new DurationSetting.Builder()
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

    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        condenseEnabled.setEnabled(!readOnly);
        condenseAge.setEnabled(!readOnly);
        condenseTimeUnit.setEnabled(!readOnly);
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

    public interface Binder extends UiBinder<Widget, CondenseSettingsWidget> {

    }
}
