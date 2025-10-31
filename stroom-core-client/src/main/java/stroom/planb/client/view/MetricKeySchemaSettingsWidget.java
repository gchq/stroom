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
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.MetricKeySchema;
import stroom.planb.shared.TemporalResolution;
import stroom.query.api.UserTimeZone;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.Objects;

public class MetricKeySchemaSettingsWidget
        extends AbstractSettingsWidget
        implements MetricKeySchemaSettingsView {

    private final Widget widget;

    private final TimeZoneWidget timeZoneWidget;

    @UiField
    SelectionBox<KeyType> keyType;
    @UiField
    SelectionBox<HashLength> hashLength;
    @UiField
    SelectionBox<TemporalResolution> temporalResolution;
    @UiField
    SimplePanel timeZone;

    private boolean readOnly;

    @Inject
    public MetricKeySchemaSettingsWidget(final Binder binder,
                                         final TimeZoneWidget timeZoneWidget) {
        this.timeZoneWidget = timeZoneWidget;
        widget = binder.createAndBindUi(this);
        keyType.addItems(KeyType.ORDERED_LIST);
        hashLength.addItems(HashLength.ORDERED_LIST);
        temporalResolution.addItems(TemporalResolution.ORDERED_LIST);
        timeZone.setWidget(timeZoneWidget.asWidget());
        setKeySchema(new MetricKeySchema.Builder().build());
        onKeyTypeChange();
    }

    @Override
    public void setUiHandlers(final PlanBSettingsUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
        timeZoneWidget.setUiHandlers(uiHandlers);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public MetricKeySchema getKeySchema() {
        final UserTimeZone userTimeZone = timeZoneWidget.getUserTimeZone();
        return new MetricKeySchema.Builder()
                .keyType(keyType.getValue())
                .hashLength(hashLength.getValue())
                .temporalResolution(temporalResolution.getValue())
                .timeZone(userTimeZone)
                .build();
    }

    @Override
    public void setKeySchema(final MetricKeySchema keySchema) {
        final MetricKeySchema schema = new MetricKeySchema.Builder(keySchema).build();
        keyType.setValue(schema.getKeyType());
        hashLength.setValue(schema.getHashLength());
        temporalResolution.setValue(schema.getTemporalResolution());
        timeZoneWidget.setUserTimeZone(schema.getTimeZone());
        onKeyTypeChange();
    }

    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        keyType.setEnabled(!readOnly);
        hashLength.setEnabled(!readOnly);
        temporalResolution.setEnabled(!readOnly);
        timeZoneWidget.onReadOnly(readOnly);
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

    @UiHandler("temporalResolution")
    public void onTemporalResolution(final ValueChangeEvent<TemporalResolution> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, MetricKeySchemaSettingsWidget> {

    }
}
