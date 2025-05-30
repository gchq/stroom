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
import stroom.planb.shared.HistogramKeySchema;
import stroom.planb.shared.HistogramKeyType;
import stroom.planb.shared.HistogramPeriod;
import stroom.query.api.UserTimeZone;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class HistogramKeySchemaSettingsWidget
        extends AbstractSettingsWidget
        implements HistogramKeySchemaSettingsView {

    private final Widget widget;

    private final TimeZoneWidget timeZoneWidget;

    @UiField
    SelectionBox<HistogramKeyType> keyType;
    @UiField
    SelectionBox<HistogramPeriod> period;
    @UiField
    SimplePanel timeZone;

    @Inject
    public HistogramKeySchemaSettingsWidget(final Binder binder,
                                            final TimeZoneWidget timeZoneWidget) {
        this.timeZoneWidget = timeZoneWidget;
        widget = binder.createAndBindUi(this);
        keyType.addItems(HistogramKeyType.ORDERED_LIST);
        keyType.setValue(HistogramKeyType.TAGS);
        period.addItems(HistogramPeriod.ORDERED_LIST);
        period.setValue(HistogramPeriod.SECOND);
        timeZone.setWidget(timeZoneWidget.asWidget());
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
    public HistogramKeySchema getKeySchema() {
        final UserTimeZone userTimeZone = timeZoneWidget.getUserTimeZone();
        return new HistogramKeySchema(keyType.getValue(), period.getValue(), userTimeZone);
    }

    @Override
    public void setKeySchema(final HistogramKeySchema keySchema) {
        if (keySchema != null) {
            keyType.setValue(keySchema.getKeyType());
            period.setValue(keySchema.getPeriod());
            timeZoneWidget.setUserTimeZone(keySchema.getTimeZone());
        }
    }

    public void onReadOnly(final boolean readOnly) {
        keyType.setEnabled(!readOnly);
        period.setEnabled(!readOnly);
        timeZoneWidget.onReadOnly(readOnly);
    }

    @UiHandler("keyType")
    public void onKeyType(final ValueChangeEvent<HistogramKeyType> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("period")
    public void onPeriod(final ValueChangeEvent<HistogramPeriod> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, HistogramKeySchemaSettingsWidget> {

    }
}
