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
import stroom.planb.shared.RangeType;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalRangeKeySchema;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class TemporalRangeKeySchemaSettingsWidget
        extends AbstractSettingsWidget
        implements TemporalRangeKeySchemaSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<RangeType> rangeType;
    @UiField
    SelectionBox<TemporalPrecision> temporalPrecision;

    @Inject
    public TemporalRangeKeySchemaSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
        rangeType.addItems(RangeType.ORDERED_LIST);
        temporalPrecision.addItems(TemporalPrecision.ORDERED_LIST);
        setKeySchema(new TemporalRangeKeySchema.Builder().build());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TemporalRangeKeySchema getKeySchema() {
        return new TemporalRangeKeySchema.Builder()
                .rangeType(rangeType.getValue())
                .temporalPrecision(temporalPrecision.getValue())
                .build();
    }

    @Override
    public void setKeySchema(final TemporalRangeKeySchema keySchema) {
        final TemporalRangeKeySchema schema = new TemporalRangeKeySchema.Builder(keySchema).build();
        rangeType.setValue(schema.getRangeType());
        temporalPrecision.setValue(schema.getTemporalPrecision());
    }

    public void onReadOnly(final boolean readOnly) {
        rangeType.setEnabled(!readOnly);
        temporalPrecision.setEnabled(!readOnly);
    }

    @UiHandler("rangeType")
    public void onRangeType(final ValueChangeEvent<RangeType> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("temporalPrecision")
    public void onTemporalPrecision(final ValueChangeEvent<TemporalPrecision> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, TemporalRangeKeySchemaSettingsWidget> {

    }
}
