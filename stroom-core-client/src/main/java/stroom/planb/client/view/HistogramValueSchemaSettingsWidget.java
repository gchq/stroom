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
import stroom.planb.shared.HistogramValueSchema;
import stroom.planb.shared.MaxValueSize;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class HistogramValueSchemaSettingsWidget
        extends AbstractSettingsWidget
        implements HistogramValueSchemaSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<MaxValueSize> maxValue;

    @Inject
    public HistogramValueSchemaSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
        maxValue.addItems(MaxValueSize.ORDERED_LIST);
        setValueSchema(new HistogramValueSchema.Builder().build());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public HistogramValueSchema getValueSchema() {
        return new HistogramValueSchema.Builder()
                .valueType(maxValue.getValue())
                .build();
    }

    @Override
    public void setValueSchema(final HistogramValueSchema valueSchema) {
        final HistogramValueSchema schema = new HistogramValueSchema.Builder(valueSchema).build();
        maxValue.setValue(schema.getValueType());
    }

    public void onReadOnly(final boolean readOnly) {
        maxValue.setEnabled(!readOnly);
    }

    @UiHandler("maxValue")
    public void onMaxValue(final ValueChangeEvent<MaxValueSize> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, HistogramValueSchemaSettingsWidget> {

    }
}
