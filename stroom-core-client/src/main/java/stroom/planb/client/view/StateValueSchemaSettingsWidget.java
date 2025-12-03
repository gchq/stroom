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
import stroom.planb.shared.HashLength;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.Objects;

public class StateValueSchemaSettingsWidget extends AbstractSettingsWidget implements StateValueSchemaSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<StateValueType> stateValueType;
    @UiField
    SelectionBox<HashLength> hashLength;

    private boolean readOnly;

    @Inject
    public StateValueSchemaSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
        stateValueType.addItems(StateValueType.ORDERED_LIST);
        hashLength.addItems(HashLength.ORDERED_LIST);
        setValueSchema(new StateValueSchema.Builder().build());
        onStateValueTypeChange();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public StateValueSchema getValueSchema() {
        return new StateValueSchema.Builder()
                .stateValueType(stateValueType.getValue())
                .hashLength(hashLength.getValue())
                .build();
    }

    @Override
    public void setValueSchema(final StateValueSchema valueSchema) {
        final StateValueSchema schema = new StateValueSchema.Builder(valueSchema).build();
        stateValueType.setValue(schema.getStateValueType());
        hashLength.setValue(schema.getHashLength());
        onStateValueTypeChange();
    }

    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        stateValueType.setEnabled(!readOnly);
        hashLength.setEnabled(!readOnly);
    }

    private void onStateValueTypeChange() {
        final StateValueType value = stateValueType.getValue();
        hashLength.setEnabled(!readOnly &&
                              (Objects.equals(value, StateValueType.HASH_LOOKUP) ||
                               Objects.equals(value, StateValueType.VARIABLE)));
    }

    @UiHandler("stateValueType")
    public void onStateValueType(final ValueChangeEvent<StateValueType> event) {
        onStateValueTypeChange();
        getUiHandlers().onChange();
    }

    @UiHandler("hashLength")
    public void onHashLength(final ValueChangeEvent<HashLength> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, StateValueSchemaSettingsWidget> {

    }
}
