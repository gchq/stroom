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

package stroom.planb.client.component;

import stroom.planb.shared.DurationSetting;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

public class CondenseSettingsForm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    TickBox condenseEnabled;
    @UiField
    FormGroup condenseAgePanel;
    @UiField
    ValueSpinner condenseAge;
    @UiField
    SelectionBox<TimeUnit> condenseTimeUnit;

    /// Makes the settings, at their defaults.
    public CondenseSettingsForm() {
        initWidget(BINDER.createAndBindUi(this));
        condenseEnabled.setOnChange(value -> onCondenseEnabled());
        condenseAge.setOnChange(value -> onCondenseAge());
        condenseTimeUnit.addValueChangeHandler(this::onCondenseTimeUnit);

        condenseAge.setValue(1);

        condenseTimeUnit.addItem(TimeUnit.DAYS);
        condenseTimeUnit.addItem(TimeUnit.WEEKS);
        condenseTimeUnit.addItem(TimeUnit.MONTHS);
        condenseTimeUnit.addItem(TimeUnit.YEARS);
        condenseTimeUnit.setValue(TimeUnit.YEARS);

        setCondenseEnabled(this.condenseEnabled.getValue());
    }

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
        // Whether the condense age applies is independent of read only
        condenseAgePanel.setStyleName("section--disabled", !enabled);
        condenseAge.setEnabled(enabled);
        condenseTimeUnit.setEnabled(enabled);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        condenseEnabled.setReadOnly(readOnly);
        condenseAge.setReadOnly(readOnly);
        condenseTimeUnit.setReadOnly(readOnly);
    }

    private void onCondenseEnabled() {
        setCondenseEnabled(condenseEnabled.getValue());
        changed();
    }

    private void onCondenseAge() {
        changed();
    }

    private void onCondenseTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        changed();
    }

    /// Sets what is told when the user changes a setting.
    ///
    /// @param onChange Run each time, or null for nothing.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange != null
                ? onChange
                : () -> {
                };
    }

    // Says that the user changed a setting
    private void changed() {
        onChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CondenseSettingsForm> {

    }
}
