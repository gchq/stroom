/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.query.client.view;

import stroom.document.client.event.ChangeUiHandlers;
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
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public final class TimeZoneWidget extends Composite {

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

    private ChangeUiHandlers uiHandlers;

    @Inject
    public TimeZoneWidget(final Binder binder) {
        final Widget widget = binder.createAndBindUi(this);

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

        initWidget(widget);
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
        if (userTimeZone != null) {
            timeZoneUse.setValue(userTimeZone.getUse());
            timeZoneId.setValue(userTimeZone.getId());
            timeZoneOffsetHours.setValue(userTimeZone.getOffsetHours());
            timeZoneOffsetMinutes.setValue(userTimeZone.getOffsetMinutes());
            changeVisible();
        }
    }

    public Use getTimeZoneUse() {
        return this.timeZoneUse.getValue();
    }

    public void setTimeZoneUse(final Use use) {
        this.timeZoneUse.setValue(use);
        changeVisible();
    }

    public String getTimeZoneId() {
        return this.timeZoneId.getValue();
    }

    public void setTimeZoneId(final String timeZoneId) {
        this.timeZoneId.setValue(timeZoneId);
    }

    public Integer getTimeZoneOffsetHours() {
        final int val = this.timeZoneOffsetHours.getIntValue();
        if (val == 0) {
            return null;
        }
        return val;
    }

    public void setTimeZoneOffsetHours(final Integer timeZoneOffsetHours) {
        if (timeZoneOffsetHours == null) {
            this.timeZoneOffsetHours.setValue(0);
        } else {
            this.timeZoneOffsetHours.setValue(timeZoneOffsetHours);
        }
    }

    public Integer getTimeZoneOffsetMinutes() {
        final int val = this.timeZoneOffsetMinutes.getIntValue();
        if (val == 0) {
            return null;
        }
        return val;
    }

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

    public void onReadOnly(final boolean readOnly) {
        timeZoneUse.setEnabled(!readOnly);
        timeZoneId.setEnabled(!readOnly);
        timeZoneOffsetHours.setEnabled(!readOnly);
        timeZoneOffsetMinutes.setEnabled(!readOnly);
    }

    @UiHandler("timeZoneUse")
    public void onTimeZoneUse(final ValueChangeEvent<Use> event) {
        changeVisible();
        fireChange();
    }

    @UiHandler("timeZoneId")
    public void onTimeZoneId(final ValueChangeEvent<String> event) {
        changeVisible();
        fireChange();
    }

    @UiHandler("timeZoneOffsetHours")
    public void onTimeZoneOffsetHours(final ValueChangeEvent<Long> event) {
        changeVisible();
        fireChange();
    }

    @UiHandler("timeZoneOffsetMinutes")
    public void onTimeZoneOffsetMinutes(final ValueChangeEvent<Long> event) {
        changeVisible();
        fireChange();
    }

    private void fireChange() {
        if (uiHandlers != null) {
            uiHandlers.onChange();
        }
    }

    public void setUiHandlers(final ChangeUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
    }

    public interface Binder extends UiBinder<Widget, TimeZoneWidget> {

    }
}
