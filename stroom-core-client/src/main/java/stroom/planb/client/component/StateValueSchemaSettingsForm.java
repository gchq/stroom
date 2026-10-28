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

import stroom.item.client.SelectionBox;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

public class StateValueSchemaSettingsForm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    SelectionBox<StateValueType> stateValueType;
    @UiField
    SelectionBox<HashLength> hashLength;

    /// Makes the settings, at their defaults.
    public StateValueSchemaSettingsForm() {
        initWidget(BINDER.createAndBindUi(this));
        stateValueType.addValueChangeHandler(this::onStateValueType);
        hashLength.addValueChangeHandler(this::onHashLength);
        stateValueType.addItems(StateValueType.ORDERED_LIST);
        hashLength.addItems(HashLength.ORDERED_LIST);
        setValueSchema(new StateValueSchema.Builder().build());
        onStateValueTypeChange();
    }

    public StateValueSchema getValueSchema() {
        return new StateValueSchema.Builder()
                .stateValueType(stateValueType.getValue())
                .hashLength(hashLength.getValue())
                .build();
    }

    public void setValueSchema(final StateValueSchema valueSchema) {
        final StateValueSchema schema = new StateValueSchema.Builder(valueSchema).build();
        stateValueType.setValue(schema.getStateValueType());
        hashLength.setValue(schema.getHashLength());
        onStateValueTypeChange();
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        stateValueType.setReadOnly(readOnly);
        hashLength.setReadOnly(readOnly);
    }

    private void onStateValueTypeChange() {
        final StateValueType value = stateValueType.getValue();
        // Hash length only applies to some types; read only is set separately in onReadOnlyChange()
        hashLength.setEnabled(Objects.equals(value, StateValueType.HASH_LOOKUP) ||
                              Objects.equals(value, StateValueType.VARIABLE));
    }

    private void onStateValueType(final ValueChangeEvent<StateValueType> event) {
        onStateValueTypeChange();
        changed();
    }

    private void onHashLength(final ValueChangeEvent<HashLength> event) {
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


    interface Binder extends UiBinder<Widget, StateValueSchemaSettingsForm> {

    }
}
