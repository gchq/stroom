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

import stroom.planb.shared.MaxValueSize;
import stroom.planb.shared.MetricValueSchema;
import stroom.widget.component.client.Component;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

public class MetricValueSchemaSettingsForm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    SelectionBox<MaxValueSize> maxValue;
    @UiField
    TickBox storeLatestValue;
    @UiField
    TickBox storeMin;
    @UiField
    TickBox storeMax;
    @UiField
    TickBox storeCount;
    @UiField
    TickBox storeSum;

    /// Makes the settings, at their defaults.
    public MetricValueSchemaSettingsForm() {
        initWidget(BINDER.createAndBindUi(this));
        maxValue.addValueChangeHandler(this::onMaxValue);
        storeLatestValue.setOnChange(value -> onStoreLatestValue());
        storeMin.setOnChange(value -> onStoreMin());
        storeMax.setOnChange(value -> onStoreMax());
        storeCount.setOnChange(value -> onStoreCount());
        storeSum.setOnChange(value -> onStoreSum());
        maxValue.addItems(MaxValueSize.ORDERED_LIST);
        setValueSchema(new MetricValueSchema.Builder().build());
    }

    public MetricValueSchema getValueSchema() {
        return new MetricValueSchema.Builder()
                .valueType(maxValue.getValue())
                .storeLatestValue(storeLatestValue.getValue())
                .storeMin(storeMin.getValue())
                .storeMax(storeMax.getValue())
                .storeCount(storeCount.getValue())
                .storeSum(storeSum.getValue())
                .build();
    }

    public void setValueSchema(final MetricValueSchema valueSchema) {
        final MetricValueSchema schema = new MetricValueSchema.Builder(valueSchema).build();
        maxValue.setValue(schema.getValueType());
        storeLatestValue.setValue(schema.getStoreLatestValue());
        storeMin.setValue(schema.getStoreMin());
        storeMax.setValue(schema.getStoreMax());
        storeCount.setValue(schema.getStoreCount());
        storeSum.setValue(schema.getStoreSum());
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        maxValue.setReadOnly(readOnly);
        storeLatestValue.setReadOnly(readOnly);
        storeMin.setReadOnly(readOnly);
        storeMax.setReadOnly(readOnly);
        storeCount.setReadOnly(readOnly);
        storeSum.setReadOnly(readOnly);
    }

    private void onMaxValue(final ValueChangeEvent<MaxValueSize> event) {
        changed();
    }

    private void onStoreLatestValue() {
        changed();
    }

    private void onStoreMin() {
        changed();
    }

    private void onStoreMax() {
        changed();
    }

    private void onStoreCount() {
        changed();
    }

    private void onStoreSum() {
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


    interface Binder extends UiBinder<Widget, MetricValueSchemaSettingsForm> {

    }
}
