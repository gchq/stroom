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
import stroom.planb.shared.HistogramKeySchema;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.TemporalResolution;
import stroom.query.api.UserTimeZone;
import stroom.query.client.component.TimeZoneBox;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

public class HistogramKeySchemaSettingsForm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    SelectionBox<KeyType> keyType;
    @UiField
    SelectionBox<HashLength> hashLength;
    @UiField
    SelectionBox<TemporalResolution> temporalResolution;
    @UiField
    TimeZoneBox timeZoneBox;

    /// Makes the settings, at their defaults.
    public HistogramKeySchemaSettingsForm() {
        initWidget(BINDER.createAndBindUi(this));
        keyType.addValueChangeHandler(this::onKeyType);
        hashLength.addValueChangeHandler(this::onHashLength);
        temporalResolution.addValueChangeHandler(this::onTemporalResolution);
        keyType.addItems(KeyType.ORDERED_LIST);
        hashLength.addItems(HashLength.ORDERED_LIST);
        temporalResolution.addItems(TemporalResolution.ORDERED_LIST);
        setKeySchema(new HistogramKeySchema.Builder().build());
        onKeyTypeChange();
    }

    public HistogramKeySchema getKeySchema() {
        final UserTimeZone userTimeZone = timeZoneBox.getUserTimeZone();
        return new HistogramKeySchema.Builder()
                .keyType(keyType.getValue())
                .hashLength(hashLength.getValue())
                .temporalResolution(temporalResolution.getValue())
                .timeZone(userTimeZone)
                .build();
    }

    public void setKeySchema(final HistogramKeySchema keySchema) {
        final HistogramKeySchema schema = new HistogramKeySchema.Builder(keySchema).build();
        keyType.setValue(schema.getKeyType());
        hashLength.setValue(schema.getHashLength());
        temporalResolution.setValue(schema.getTemporalResolution());
        timeZoneBox.setUserTimeZone(schema.getTimeZone());
        onKeyTypeChange();
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        keyType.setReadOnly(readOnly);
        hashLength.setReadOnly(readOnly);
        temporalResolution.setReadOnly(readOnly);
        timeZoneBox.setReadOnly(readOnly);
    }

    private void onKeyTypeChange() {
        final KeyType value = keyType.getValue();
        // Hash length only applies to some types; read only is set separately in onReadOnlyChange()
        hashLength.setEnabled(Objects.equals(value, KeyType.HASH_LOOKUP) ||
                              Objects.equals(value, KeyType.VARIABLE));
    }

    private void onKeyType(final ValueChangeEvent<KeyType> event) {
        onKeyTypeChange();
        changed();
    }

    private void onHashLength(final ValueChangeEvent<HashLength> event) {
        changed();
    }

    private void onTemporalResolution(final ValueChangeEvent<TemporalResolution> event) {
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
        timeZoneBox.setOnChange(this.onChange);
    }

    // Says that the user changed a setting
    private void changed() {
        onChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, HistogramKeySchemaSettingsForm> {

    }
}
