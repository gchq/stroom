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
import stroom.planb.shared.KeyType;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalStateKeySchema;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

public class TemporalStateKeySchemaSettingsWidget extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    SelectionBox<KeyType> keyType;
    @UiField
    SelectionBox<HashLength> hashLength;
    @UiField
    SelectionBox<TemporalPrecision> temporalPrecision;

    /// Makes the settings, at their defaults.
    public TemporalStateKeySchemaSettingsWidget() {
        initWidget(BINDER.createAndBindUi(this));
        keyType.addItems(KeyType.ORDERED_LIST);
        hashLength.addItems(HashLength.ORDERED_LIST);
        temporalPrecision.addItems(TemporalPrecision.ORDERED_LIST);
        setKeySchema(new TemporalStateKeySchema.Builder().build());
        onKeyTypeChange();
    }

    public TemporalStateKeySchema getKeySchema() {
        return new TemporalStateKeySchema.Builder()
                .keyType(keyType.getValue())
                .hashLength(hashLength.getValue())
                .temporalPrecision(temporalPrecision.getValue())
                .build();
    }

    public void setKeySchema(final TemporalStateKeySchema keySchema) {
        final TemporalStateKeySchema schema = new TemporalStateKeySchema.Builder(keySchema).build();
        keyType.setValue(schema.getKeyType());
        hashLength.setValue(schema.getHashLength());
        temporalPrecision.setValue(schema.getTemporalPrecision());
        onKeyTypeChange();
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        keyType.setReadOnly(readOnly);
        hashLength.setReadOnly(readOnly);
        temporalPrecision.setReadOnly(readOnly);
    }

    private void onKeyTypeChange() {
        final KeyType value = keyType.getValue();
        // Hash length only applies to some types; read only is set separately in onReadOnlyChange()
        hashLength.setEnabled(Objects.equals(value, KeyType.HASH_LOOKUP) ||
                              Objects.equals(value, KeyType.VARIABLE));
    }

    @UiHandler("keyType")
    public void onKeyType(final ValueChangeEvent<KeyType> event) {
        onKeyTypeChange();
        changed();
    }

    @UiHandler("hashLength")
    public void onHashLength(final ValueChangeEvent<HashLength> event) {
        changed();
    }

    @UiHandler("temporalPrecision")
    public void onTemporalPrecision(final ValueChangeEvent<TemporalPrecision> event) {
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


    interface Binder extends UiBinder<Widget, TemporalStateKeySchemaSettingsWidget> {

    }
}
