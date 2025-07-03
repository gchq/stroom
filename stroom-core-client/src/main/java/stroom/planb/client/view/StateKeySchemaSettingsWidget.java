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
import stroom.planb.shared.StateKeySchema;
import stroom.util.shared.NullSafe;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.Objects;

public class StateKeySchemaSettingsWidget extends AbstractSettingsWidget implements StateKeySchemaSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<KeyType> keyType;
    @UiField
    SelectionBox<HashLength> hashLength;

    private boolean readOnly;

    @Inject
    public StateKeySchemaSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
        keyType.addItems(KeyType.ORDERED_LIST);
        keyType.setValue(StateKeySchema.DEFAULT_KEY_TYPE);
        hashLength.addItems(HashLength.ORDERED_LIST);
        hashLength.setValue(StateKeySchema.DEFAULT_HASH_LENGTH);
        onStateKeyTypeChange();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public StateKeySchema getKeySchema() {
        return new StateKeySchema(keyType.getValue(), hashLength.getValue());
    }

    @Override
    public void setKeySchema(final StateKeySchema keySchema) {
        keyType.setValue(NullSafe.getOrElse(keySchema,
                StateKeySchema::getKeyType, StateKeySchema.DEFAULT_KEY_TYPE));
        hashLength.setValue(NullSafe.getOrElse(keySchema,
                StateKeySchema::getHashLength, StateKeySchema.DEFAULT_HASH_LENGTH));
        onStateKeyTypeChange();
    }

    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        keyType.setEnabled(!readOnly);
        hashLength.setEnabled(!readOnly);
    }

    private void onStateKeyTypeChange() {
        final KeyType value = keyType.getValue();
        hashLength.setEnabled(!readOnly &&
                              (Objects.equals(value, KeyType.HASH_LOOKUP) ||
                               Objects.equals(value, KeyType.VARIABLE)));
    }

    @UiHandler("keyType")
    public void onKeyType(final ValueChangeEvent<KeyType> event) {
        onStateKeyTypeChange();
        getUiHandlers().onChange();
    }

    @UiHandler("hashLength")
    public void onHashLength(final ValueChangeEvent<HashLength> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, StateKeySchemaSettingsWidget> {

    }
}
