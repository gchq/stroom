/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.FormatPresenter.FormatView;
import stroom.item.client.ItemListBox;
import stroom.item.client.StringListBox;
import stroom.query.api.v2.Format.Type;
import stroom.query.api.v2.TimeZone;
import stroom.query.api.v2.TimeZone.Use;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.logical.shared.SelectionEvent;
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

public class FormatViewImpl extends ViewWithUiHandlers<FormatUihandlers> implements FormatView {

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

    private static final int ROW_COUNT = 9;
    private final Widget widget;

    @UiField
    FormGroup formatDecimalPlaces;
    @UiField
    FormGroup formatUseSeparator;

    @UiField
    FormGroup formatUsePreferences;
    @UiField
    FormGroup formatFormat;
    @UiField
    FormGroup formatCustom;
    @UiField
    FormGroup formatCustomFormat;
    @UiField
    FormGroup formatTimeZone;
    @UiField
    FormGroup formatTimeZoneId;
    @UiField
    FormGroup formatTimeZoneOffset;

    @UiField
    ItemListBox<Type> type;
    @UiField
    ValueSpinner decimalPlaces;
    @UiField
    CustomCheckBox separate;
    @UiField
    CustomCheckBox usePreferences;
    @UiField
    StringListBox format;
    @UiField
    CustomCheckBox custom;
    @UiField
    TextBox text;
    @UiField
    ItemListBox<Use> timeZoneUse;
    @UiField
    StringListBox timeZoneId;
    @UiField
    ValueSpinner timeZoneOffsetHours;
    @UiField
    ValueSpinner timeZoneOffsetMinutes;
    @UiField
    CustomCheckBox wrap;

    @Inject
    public FormatViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        decimalPlaces.setValue(0);
        decimalPlaces.setMin(0);
        decimalPlaces.setMax(10);

        format.addItems(STANDARD_FORMATS);

        timeZoneUse.addItem(TimeZone.Use.LOCAL);
        timeZoneUse.addItem(TimeZone.Use.UTC);
        timeZoneUse.addItem(TimeZone.Use.ID);
        timeZoneUse.addItem(TimeZone.Use.OFFSET);

        timeZoneOffsetHours.setMin(-12);
        timeZoneOffsetHours.setMax(12);
        timeZoneOffsetHours.setValue(0);
        timeZoneOffsetHours.setMinStep(1);
        timeZoneOffsetHours.setMaxStep(1);

        timeZoneOffsetMinutes.setMin(0);
        timeZoneOffsetMinutes.setMax(59);
        timeZoneOffsetMinutes.setValue(0);
        timeZoneOffsetMinutes.setMinStep(30);
        timeZoneOffsetMinutes.setMaxStep(30);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        type.focus();
    }

    @Override
    public void setTypes(final List<Type> types) {
        this.type.addItems(types);
    }

    @Override
    public void setType(final Type type) {
        this.type.setSelectedItem(type);

        formatDecimalPlaces.setVisible(Type.NUMBER.equals(type));
        formatUseSeparator.setVisible(Type.NUMBER.equals(type));

        formatUsePreferences.setVisible(Type.DATE_TIME.equals(type));
        formatFormat.setVisible(Type.DATE_TIME.equals(type));
        formatCustom.setVisible(Type.DATE_TIME.equals(type));
        formatCustomFormat.setVisible(Type.DATE_TIME.equals(type));
        formatTimeZone.setVisible(Type.DATE_TIME.equals(type));
        formatTimeZoneId.setVisible(Type.DATE_TIME.equals(type) &&
                TimeZone.Use.ID.equals(this.timeZoneUse.getSelectedItem()));
        formatTimeZoneOffset.setVisible(Type.DATE_TIME.equals(type) &&
                TimeZone.Use.OFFSET.equals(this.timeZoneUse.getSelectedItem()));
    }

    @Override
    public int getDecimalPlaces() {
        return this.decimalPlaces.getValue();
    }

    @Override
    public void setDecimalPlaces(final int decimalPlaces) {
        this.decimalPlaces.setValue(decimalPlaces);
    }

    @Override
    public boolean isUseSeparator() {
        return this.separate.getValue();
    }

    @Override
    public void setUseSeparator(final boolean useSeparator) {
        this.separate.setValue(useSeparator);
    }

    @Override
    public boolean isUsePreferences() {
        return this.usePreferences.getValue();
    }

    @Override
    public void setUsePreferences(final boolean usePreferences) {
        this.usePreferences.setValue(usePreferences);
    }

    @Override
    public String getPattern() {
        if (custom.getValue()) {
            return text.getText();
        }

        return format.getSelected();
    }

    @Override
    public void setPattern(final String pattern) {
        String text = pattern;
        if (text == null || text.trim().length() == 0) {
            text = STANDARD_FORMATS.get(0);
        }

        if (!text.equals(this.format.getSelected())) {
            this.format.setSelected(text);
        }

        final boolean custom = this.format.getSelectedIndex() == -1;
        this.custom.setValue(custom);
        this.text.setEnabled(custom);
        this.text.setText(text);
    }

    @Override
    public void setTimeZoneIds(final List<String> timeZoneIds) {
        this.timeZoneId.addItems(timeZoneIds);
    }

    @Override
    public Use getTimeZoneUse() {
        return this.timeZoneUse.getSelectedItem();
    }

    @Override
    public void setTimeZoneUse(final Use use) {
        this.timeZoneUse.setSelectedItem(use);
    }

    @Override
    public String getTimeZoneId() {
        return this.timeZoneId.getSelected();
    }

    @Override
    public void setTimeZoneId(final String timeZoneId) {
        this.timeZoneId.setSelected(timeZoneId);
    }

    @Override
    public Integer getTimeZoneOffsetHours() {
        final int val = this.timeZoneOffsetHours.getValue();
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
        final int val = this.timeZoneOffsetMinutes.getValue();
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

    @Override
    public boolean isWrap() {
        return wrap.getValue();
    }

    @Override
    public void setWrap(final boolean wrap) {
        this.wrap.setValue(wrap);
    }

    @UiHandler("custom")
    public void onCustomCheckBoxClick(final ValueChangeEvent<Boolean> event) {
        text.setEnabled(custom.getValue());
    }

    @UiHandler("type")
    public void onTypeChange(final SelectionEvent<Type> event) {
        getUiHandlers().onTypeChange(type.getSelectedItem());
    }

    @UiHandler("format")
    public void onFormatChange(final ChangeEvent event) {
        setPattern(this.format.getSelected());
    }

    @UiHandler("timeZoneUse")
    public void onTimeZoneUseChange(final SelectionEvent<TimeZone.Use> event) {
        setType(this.type.getSelectedItem());
    }

    public interface Binder extends UiBinder<Widget, FormatViewImpl> {

    }
}
