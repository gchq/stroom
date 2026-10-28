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
import stroom.planb.shared.RangeType;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalRangeKeySchema;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

public class TemporalRangeKeySchemaSettingsWidget extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    SelectionBox<RangeType> rangeType;
    @UiField
    SelectionBox<TemporalPrecision> temporalPrecision;

    /// Makes the settings, at their defaults.
    public TemporalRangeKeySchemaSettingsWidget() {
        initWidget(BINDER.createAndBindUi(this));
        rangeType.addItems(RangeType.ORDERED_LIST);
        temporalPrecision.addItems(TemporalPrecision.ORDERED_LIST);
        setKeySchema(new TemporalRangeKeySchema.Builder().build());
    }

    public TemporalRangeKeySchema getKeySchema() {
        return new TemporalRangeKeySchema.Builder()
                .rangeType(rangeType.getValue())
                .temporalPrecision(temporalPrecision.getValue())
                .build();
    }

    public void setKeySchema(final TemporalRangeKeySchema keySchema) {
        final TemporalRangeKeySchema schema = new TemporalRangeKeySchema.Builder(keySchema).build();
        rangeType.setValue(schema.getRangeType());
        temporalPrecision.setValue(schema.getTemporalPrecision());
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        rangeType.setReadOnly(readOnly);
        temporalPrecision.setReadOnly(readOnly);
    }

    @UiHandler("rangeType")
    public void onRangeType(final ValueChangeEvent<RangeType> event) {
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


    interface Binder extends UiBinder<Widget, TemporalRangeKeySchemaSettingsWidget> {

    }
}
