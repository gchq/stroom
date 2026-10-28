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

package stroom.planb.client.component;

import stroom.item.client.SelectionBox;
import stroom.planb.shared.HistogramValueSchema;
import stroom.planb.shared.MaxValueSize;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

public class HistogramValueSchemaSettingsWidget extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    SelectionBox<MaxValueSize> maxValue;

    /// Makes the settings, at their defaults.
    public HistogramValueSchemaSettingsWidget() {
        initWidget(BINDER.createAndBindUi(this));
        maxValue.addItems(MaxValueSize.ORDERED_LIST);
        setValueSchema(new HistogramValueSchema.Builder().build());
    }

    public HistogramValueSchema getValueSchema() {
        return new HistogramValueSchema.Builder()
                .valueType(maxValue.getValue())
                .build();
    }

    public void setValueSchema(final HistogramValueSchema valueSchema) {
        final HistogramValueSchema schema = new HistogramValueSchema.Builder(valueSchema).build();
        maxValue.setValue(schema.getValueType());
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        maxValue.setReadOnly(readOnly);
    }

    @UiHandler("maxValue")
    public void onMaxValue(final ValueChangeEvent<MaxValueSize> event) {
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


    interface Binder extends UiBinder<Widget, HistogramValueSchemaSettingsWidget> {

    }
}
