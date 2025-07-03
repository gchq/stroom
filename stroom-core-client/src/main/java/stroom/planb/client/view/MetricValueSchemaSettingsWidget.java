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
import stroom.planb.shared.MaxValueSize;
import stroom.planb.shared.MetricValueSchema;
import stroom.util.shared.NullSafe;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class MetricValueSchemaSettingsWidget
        extends AbstractSettingsWidget
        implements MetricValueSchemaSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<MaxValueSize> maxValue;
    @UiField
    CustomCheckBox storeLatestValue;
    @UiField
    CustomCheckBox storeMin;
    @UiField
    CustomCheckBox storeMax;
    @UiField
    CustomCheckBox storeCount;
    @UiField
    CustomCheckBox storeSum;

    @Inject
    public MetricValueSchemaSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
        maxValue.addItems(MaxValueSize.ORDERED_LIST);
        maxValue.setValue(MetricValueSchema.DEFAULT_MAX_VALUE_SIZE);
        storeLatestValue.setValue(MetricValueSchema.DEFAULT_STORE_LATEST_VALUE);
        storeMin.setValue(MetricValueSchema.DEFAULT_STORE_MIN);
        storeMax.setValue(MetricValueSchema.DEFAULT_STORE_MAX);
        storeCount.setValue(MetricValueSchema.DEFAULT_STORE_COUNT);
        storeSum.setValue(MetricValueSchema.DEFAULT_STORE_SUM);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
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

    @Override
    public void setValueSchema(final MetricValueSchema valueSchema) {
        maxValue.setValue(NullSafe.getOrElse(valueSchema,
                MetricValueSchema::getValueType, MetricValueSchema.DEFAULT_MAX_VALUE_SIZE));
        storeLatestValue.setValue(NullSafe.getOrElse(valueSchema,
                MetricValueSchema::getStoreLatestValue, MetricValueSchema.DEFAULT_STORE_LATEST_VALUE));
        storeMin.setValue(NullSafe.getOrElse(valueSchema,
                MetricValueSchema::getStoreMin, MetricValueSchema.DEFAULT_STORE_MIN));
        storeMax.setValue(NullSafe.getOrElse(valueSchema,
                MetricValueSchema::getStoreMax, MetricValueSchema.DEFAULT_STORE_MAX));
        storeCount.setValue(NullSafe.getOrElse(valueSchema,
                MetricValueSchema::getStoreCount, MetricValueSchema.DEFAULT_STORE_COUNT));
        storeSum.setValue(NullSafe.getOrElse(valueSchema,
                MetricValueSchema::getStoreSum, MetricValueSchema.DEFAULT_STORE_SUM));
    }

    public void onReadOnly(final boolean readOnly) {
        maxValue.setEnabled(!readOnly);
    }

    @UiHandler("maxValue")
    public void onMaxValue(final ValueChangeEvent<MaxValueSize> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("storeLatestValue")
    public void onStoreLatestValue(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("storeMin")
    public void onStoreMin(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("storeMax")
    public void onStoreMax(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("storeCount")
    public void onStoreCount(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("storeSum")
    public void onStoreSum(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, MetricValueSchemaSettingsWidget> {

    }
}
