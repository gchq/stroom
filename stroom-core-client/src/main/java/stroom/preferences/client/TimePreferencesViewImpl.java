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

package stroom.preferences.client;

import stroom.document.client.event.DirtyUiHandlers;
import stroom.item.client.SelectionBox;
import stroom.preferences.client.TimePreferencesPresenter.TimePreferencesView;
import stroom.query.api.UserTimeZone.Use;
import stroom.widget.customdatebox.client.MomentJs;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.List;

public final class TimePreferencesViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements TimePreferencesView {

    public static final List<String> STANDARD_FORMATS = Arrays
            .asList("yyyy-MM-dd'T'HH:mm:ss.SSSXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS xx",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS xxx",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS VV",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "dd/MM/yyyy HH:mm:ss",
                    "dd/MM/yy HH:mm:ss",
                    "MM/dd/yyyy HH:mm:ss",
                    "d MMM yyyy HH:mm:ss",
                    "yyyy-MM-dd",
                    "dd/MM/yyyy",
                    "dd/MM/yy",
                    "MM/dd/yyyy",
                    "d MMM yyyy");

    private final Widget widget;

    @UiField
    FormGroup userPreferencesTimeZoneId;
    @UiField
    FormGroup userPreferencesTimeZoneOffset;
    @UiField
    SelectionBox<String> format;
    @UiField
    CustomCheckBox custom;
    @UiField
    TextBox text;
    @UiField
    SelectionBox<Use> timeZoneUse;
    @UiField
    SelectionBox<String> timeZoneId;
    @UiField
    ValueSpinner timeZoneOffsetHours;
    @UiField
    ValueSpinner timeZoneOffsetMinutes;

    @Inject
    public TimePreferencesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        format.addItems(STANDARD_FORMATS);

        timeZoneUse.addItem(Use.LOCAL);
        timeZoneUse.addItem(Use.UTC);
        timeZoneUse.addItem(Use.ID);
        timeZoneUse.addItem(Use.OFFSET);

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
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        format.focus();
    }

    @Override
    public String getPattern() {
        if (custom.getValue()) {
            return text.getText();
        }

        return format.getValue();
    }

    @Override
    public void setPattern(final String pattern) {
        String text = pattern;
        if (text == null || text.trim().length() == 0) {
            text = STANDARD_FORMATS.get(0);
        }

        if (!text.equals(this.format.getValue())) {
            this.format.setValue(text);
        }

        final boolean custom = this.format.getValue() == null;
        this.custom.setValue(custom);
        this.text.setEnabled(custom);
        this.text.setText(text);
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

    @UiHandler("custom")
    public void onTickBoxClick(final ValueChangeEvent<Boolean> event) {
        text.setEnabled(custom.getValue());
        getUiHandlers().onDirty();
    }

    @UiHandler("format")
    public void onFormatChange(final ValueChangeEvent<String> event) {
        setPattern(this.format.getValue());
        getUiHandlers().onDirty();
    }

    @UiHandler("timeZoneUse")
    public void onTimeZoneUseValueChange(final ValueChangeEvent<Use> event) {
        changeVisible();
        getUiHandlers().onDirty();
    }

    public interface Binder extends UiBinder<Widget, TimePreferencesViewImpl> {

    }
}
