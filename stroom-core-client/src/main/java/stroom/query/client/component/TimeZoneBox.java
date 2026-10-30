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

package stroom.query.client.component;

import stroom.item.client.SelectionBox;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.widget.customdatebox.client.MomentJs;
import stroom.widget.form.client.FormGroup;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

/// A time zone: how it is given (local, UTC, an id or an offset), then the id or the offset as
/// chosen. Its markup is `TimeZoneBox.ui.xml`.
public final class TimeZoneBox extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup timeZoneIdGroup;
    @UiField
    FormGroup timeZoneOffsetGroup;
    @UiField
    SelectionBox<Use> timeZoneUse;
    @UiField
    SelectionBox<String> timeZoneId;
    @UiField
    ValueSpinner timeZoneOffsetHours;

    // The offset's minutes, which can't be set here, as browsers don't support minute offsets: kept
    // as they were set, so a time zone that has them keeps them
    private int timeZoneOffsetMinutes;
    private Runnable onChange;

    public TimeZoneBox() {
        final Widget widget = BINDER.createAndBindUi(this);
        timeZoneUse.setOnChange(use -> onPartChange());
        timeZoneId.setOnChange(id -> onPartChange());
        timeZoneOffsetHours.setOnChange(hours -> onPartChange());
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

        changeVisible();

        initWidget(widget);
    }

    public UserTimeZone getUserTimeZone() {
        return UserTimeZone.builder()
                .use(timeZoneUse.getValue())
                .id(timeZoneId.getValue())
                .offsetHours(timeZoneOffsetHours.getIntValue())
                .offsetMinutes(timeZoneOffsetMinutes)
                .build();
    }

    public void setUserTimeZone(final UserTimeZone userTimeZone) {
        if (userTimeZone != null) {
            timeZoneUse.setValue(userTimeZone.getUse());
            timeZoneId.setValue(userTimeZone.getId());
            timeZoneOffsetHours.setValue(userTimeZone.getOffsetHours());
            timeZoneOffsetMinutes = userTimeZone.getOffsetMinutes() != null
                    ? userTimeZone.getOffsetMinutes()
                    : 0;
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
        return timeZoneOffsetMinutes == 0
                ? null
                : timeZoneOffsetMinutes;
    }

    public void setTimeZoneOffsetMinutes(final Integer timeZoneOffsetMinutes) {
        this.timeZoneOffsetMinutes = timeZoneOffsetMinutes != null
                ? timeZoneOffsetMinutes
                : 0;
    }

    public void changeVisible() {
        timeZoneIdGroup.setVisible(Use.ID.equals(this.timeZoneUse.getValue()));
        timeZoneOffsetGroup.setVisible(Use.OFFSET.equals(this.timeZoneUse.getValue()));
    }

    /// Makes the time zone read only: it can be read and stays in the tab order, but none of its
    /// parts can be changed. It looks like the normal fields with their values greyed.
    ///
    /// @param readOnly Whether the time zone is read only.
    public void setReadOnly(final boolean readOnly) {
        timeZoneUse.setReadOnly(readOnly);
        timeZoneId.setReadOnly(readOnly);
        timeZoneOffsetHours.setReadOnly(readOnly);
    }

    /// Enables or disables every part of the time zone.
    ///
    /// @param enabled Whether the time zone can be changed.
    public void setEnabled(final boolean enabled) {
        timeZoneUse.setEnabled(enabled);
        timeZoneId.setEnabled(enabled);
        timeZoneOffsetHours.setEnabled(enabled);
    }

    // A part changed by the user: Use may show or hide the id or the offset
    private void onPartChange() {
        changeVisible();
        fireChange();
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    /// @param onChange Run when the user changes any part of the time zone, or null for nothing.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    interface Binder extends UiBinder<Widget, TimeZoneBox> {

    }
}
