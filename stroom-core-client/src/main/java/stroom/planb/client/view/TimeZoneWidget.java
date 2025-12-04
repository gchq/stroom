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
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.widget.customdatebox.client.MomentJs;
import stroom.widget.form.client.FormGroup;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public final class TimeZoneWidget
        extends AbstractSettingsWidget
        implements TimeZoneView {

    private final Widget widget;

    @UiField
    FormGroup userPreferencesTimeZoneId;
    @UiField
    FormGroup userPreferencesTimeZoneOffset;
    @UiField
    SelectionBox<Use> timeZoneUse;
    @UiField
    SelectionBox<String> timeZoneId;
    @UiField
    ValueSpinner timeZoneOffsetHours;
    @UiField
    ValueSpinner timeZoneOffsetMinutes;

    @Inject
    public TimeZoneWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);

        timeZoneUse.addItem(Use.LOCAL);
        timeZoneUse.addItem(Use.UTC);
        timeZoneUse.addItem(Use.ID);
        timeZoneUse.addItem(Use.OFFSET);
        timeZoneUse.setValue(Use.UTC);

        for (final String tz : MomentJs.getTimeZoneIds()) {
            timeZoneId.addItem(tz);
        }

        timeZoneOffsetHours.setMin(-12);
        timeZoneOffsetHours.setMax(12);
        timeZoneOffsetHours.setValue(0);
        timeZoneOffsetHours.setMinStep(1);
        timeZoneOffsetHours.setMaxStep(1);

        timeZoneOffsetMinutes.setMin(0);
        timeZoneOffsetMinutes.setMax(45);
        timeZoneOffsetMinutes.setValue(0);
        timeZoneOffsetMinutes.setMinStep(15);
        timeZoneOffsetMinutes.setMaxStep(15);
        timeZoneOffsetMinutes.setDelta(15);

        // FIXME:  Browsers don't support minute offsets so disable this for now.
        timeZoneOffsetMinutes.setVisible(false);

        changeVisible();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public UserTimeZone getUserTimeZone() {
        return UserTimeZone.builder()
                .use(timeZoneUse.getValue())
                .id(timeZoneId.getValue())
                .offsetHours(timeZoneOffsetHours.getIntValue())
                .offsetMinutes(timeZoneOffsetMinutes.getIntValue())
                .build();
    }

    public void setUserTimeZone(final UserTimeZone userTimeZone) {
        timeZoneUse.setValue(userTimeZone.getUse());
        timeZoneId.setValue(userTimeZone.getId());
        timeZoneOffsetHours.setValue(userTimeZone.getOffsetHours());
        timeZoneOffsetMinutes.setValue(userTimeZone.getOffsetMinutes());
        changeVisible();
    }

    @Override
    public Use getTimeZoneUse() {
        return this.timeZoneUse.getValue();
    }

    @Override
    public void setTimeZoneUse(final Use use) {
        this.timeZoneUse.setValue(use);
        changeVisible();
    }

    @Override
    public String getTimeZoneId() {
        return this.timeZoneId.getValue();
    }

    @Override
    public void setTimeZoneId(final String timeZoneId) {
        this.timeZoneId.setValue(timeZoneId);
    }

    @Override
    public Integer getTimeZoneOffsetHours() {
        final int val = this.timeZoneOffsetHours.getIntValue();
        if (val == 0) {
            return null;
        }
        return val;
    }

    @Override
    public void setTimeZoneOffsetHours(final Integer timeZoneOffsetHours) {
        if (timeZoneOffsetHours == null) {
            this.timeZoneOffsetHours.setValue(0);
        } else {
            this.timeZoneOffsetHours.setValue(timeZoneOffsetHours);
        }
    }

    @Override
    public Integer getTimeZoneOffsetMinutes() {
        final int val = this.timeZoneOffsetMinutes.getIntValue();
        if (val == 0) {
            return null;
        }
        return val;
    }

    @Override
    public void setTimeZoneOffsetMinutes(final Integer timeZoneOffsetMinutes) {
        if (timeZoneOffsetMinutes == null) {
            this.timeZoneOffsetMinutes.setValue(0);
        } else {
            this.timeZoneOffsetMinutes.setValue(timeZoneOffsetMinutes);
        }
    }

    public void changeVisible() {
        userPreferencesTimeZoneId.setVisible(Use.ID.equals(this.timeZoneUse.getValue()));
        userPreferencesTimeZoneOffset.setVisible(Use.OFFSET.equals(this.timeZoneUse.getValue()));
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        timeZoneUse.setEnabled(!readOnly);
        timeZoneId.setEnabled(!readOnly);
        timeZoneOffsetHours.setEnabled(!readOnly);
        timeZoneOffsetMinutes.setEnabled(!readOnly);
    }

    @UiHandler("timeZoneUse")
    public void onTimeZoneUse(final ValueChangeEvent<Use> event) {
        changeVisible();
        getUiHandlers().onChange();
    }

    @UiHandler("timeZoneId")
    public void onTimeZoneId(final ValueChangeEvent<String> event) {
        changeVisible();
        getUiHandlers().onChange();
    }

    @UiHandler("timeZoneOffsetHours")
    public void onTimeZoneOffsetHours(final ValueChangeEvent<Long> event) {
        changeVisible();
        getUiHandlers().onChange();
    }

    @UiHandler("timeZoneOffsetMinutes")
    public void onTimeZoneOffsetMinutes(final ValueChangeEvent<Long> event) {
        changeVisible();
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, TimeZoneWidget> {

    }
}
