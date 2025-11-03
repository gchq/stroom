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
import stroom.planb.shared.HashLength;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalStateKeySchema;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.Objects;

public class TemporalStateKeySchemaSettingsWidget extends AbstractSettingsWidget
        implements TemporalStateKeySchemaSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<KeyType> keyType;
    @UiField
    SelectionBox<HashLength> hashLength;
    @UiField
    SelectionBox<TemporalPrecision> temporalPrecision;

    private boolean readOnly;

    @Inject
    public TemporalStateKeySchemaSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
        keyType.addItems(KeyType.ORDERED_LIST);
        hashLength.addItems(HashLength.ORDERED_LIST);
        temporalPrecision.addItems(TemporalPrecision.ORDERED_LIST);
        setKeySchema(new TemporalStateKeySchema.Builder().build());
        onKeyTypeChange();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TemporalStateKeySchema getKeySchema() {
        return new TemporalStateKeySchema.Builder()
                .keyType(keyType.getValue())
                .hashLength(hashLength.getValue())
                .temporalPrecision(temporalPrecision.getValue())
                .build();
    }

    @Override
    public void setKeySchema(final TemporalStateKeySchema keySchema) {
        final TemporalStateKeySchema schema = new TemporalStateKeySchema.Builder(keySchema).build();
        keyType.setValue(schema.getKeyType());
        hashLength.setValue(schema.getHashLength());
        temporalPrecision.setValue(schema.getTemporalPrecision());
        onKeyTypeChange();
    }

    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        keyType.setEnabled(!readOnly);
        hashLength.setEnabled(!readOnly);
        temporalPrecision.setEnabled(!readOnly);
    }

    private void onKeyTypeChange() {
        final KeyType value = keyType.getValue();
        hashLength.setEnabled(!readOnly &&
                              (Objects.equals(value, KeyType.HASH_LOOKUP) ||
                               Objects.equals(value, KeyType.VARIABLE)));
    }

    @UiHandler("keyType")
    public void onKeyType(final ValueChangeEvent<KeyType> event) {
        onKeyTypeChange();
        getUiHandlers().onChange();
    }

    @UiHandler("hashLength")
    public void onHashLength(final ValueChangeEvent<HashLength> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("temporalPrecision")
    public void onTemporalPrecision(final ValueChangeEvent<TemporalPrecision> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, TemporalStateKeySchemaSettingsWidget> {

    }
}
