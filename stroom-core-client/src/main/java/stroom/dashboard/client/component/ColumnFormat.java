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

package stroom.dashboard.client.component;

import stroom.query.api.Format.Type;
import stroom.query.api.UserTimeZone.Use;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;

/// The Format dialog's content, for a table column: the type of its values and, for numbers and
/// dates, how they are shown, and whether long values wrap. Only the settings of the type chosen
/// are shown.
public class ColumnFormat extends Component {

    /// The date and time formats offered, the first being the default.
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

    private static final Binder BINDER = GWT.create(Binder.class);

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
    SelectionBox<Type> type;
    @UiField
    ValueSpinner decimalPlaces;
    @UiField
    TickBox separate;
    @UiField
    TickBox usePreferences;
    @UiField
    SelectionBox<String> format;
    @UiField
    TickBox custom;
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
    @UiField
    TickBox wrap;

    /// Makes the content.
    public ColumnFormat() {
        initWidget(BINDER.createAndBindUi(this));
        custom.setOnChange(value -> onCustom());
        type.addValueChangeHandler(this::onType);
        format.addValueChangeHandler(this::onFormat);
        timeZoneUse.addValueChangeHandler(this::onTimeZoneUse);
        format.addItems(STANDARD_FORMATS);
        timeZoneUse.addItem(Use.LOCAL);
        timeZoneUse.addItem(Use.UTC);
        timeZoneUse.addItem(Use.ID);
        timeZoneUse.addItem(Use.OFFSET);
    }

    /// Puts the focus on the type.
    public void focus() {
        type.focus();
    }

    /// @param types The types offered.
    public void setTypes(final List<Type> types) {
        this.type.addItems(types);
    }

    /// @return The type chosen.
    public Type getType() {
        return type.getValue();
    }

    /// Chooses a type, showing its settings.
    ///
    /// @param type The type.
    public void setType(final Type type) {
        this.type.setValue(type);
        showSettings();
    }

    /// @return The number of decimal places.
    public int getDecimalPlaces() {
        return decimalPlaces.getIntValue();
    }

    /// @param decimalPlaces The number of decimal places.
    public void setDecimalPlaces(final int decimalPlaces) {
        this.decimalPlaces.setValue(decimalPlaces);
    }

    /// @return Whether digits are grouped.
    public boolean isUseSeparator() {
        return separate.getValue();
    }

    /// @param useSeparator Whether digits are grouped.
    public void setUseSeparator(final boolean useSeparator) {
        separate.setValue(useSeparator);
    }

    /// @return Whether the user's preferences say how dates are shown.
    public boolean isUsePreferences() {
        return usePreferences.getValue();
    }

    /// @param usePreferences Whether the user's preferences say how dates are shown.
    public void setUsePreferences(final boolean usePreferences) {
        this.usePreferences.setValue(usePreferences);
    }

    /// @return The date and time pattern: the custom one if Custom is ticked, otherwise the
    /// standard one chosen.
    public String getPattern() {
        if (custom.getValue()) {
            return text.getText();
        }
        return format.getValue();
    }

    /// Shows a date and time pattern: chosen from the standard formats if it is one, otherwise as
    /// a custom format.
    ///
    /// @param pattern The pattern, or null or blank for the first standard format.
    public void setPattern(final String pattern) {
        String text = pattern;
        if (text == null || text.trim().isEmpty()) {
            text = STANDARD_FORMATS.get(0);
        }
        final int formatIndex = STANDARD_FORMATS.indexOf(text);
        if (formatIndex != -1) {
            this.format.setValue(text);
        }
        final boolean custom = formatIndex == -1;
        this.custom.setValue(custom);
        this.text.setEnabled(custom);
        this.text.setText(text);
    }

    /// @param timeZoneIds The named time zones offered.
    public void setTimeZoneIds(final List<String> timeZoneIds) {
        timeZoneId.addItems(timeZoneIds);
    }

    /// @return How the time zone is given.
    public Use getTimeZoneUse() {
        return timeZoneUse.getValue();
    }

    /// @param use How the time zone is given.
    public void setTimeZoneUse(final Use use) {
        timeZoneUse.setValue(use);
    }

    /// @return The named time zone chosen.
    public String getTimeZoneId() {
        return timeZoneId.getValue();
    }

    /// @param timeZoneId The named time zone.
    public void setTimeZoneId(final String timeZoneId) {
        this.timeZoneId.setValue(timeZoneId);
    }

    /// @return The offset's hours, or null for none.
    public Integer getTimeZoneOffsetHours() {
        return nullIfZero(timeZoneOffsetHours.getIntValue());
    }

    /// @param timeZoneOffsetHours The offset's hours, or null for none.
    public void setTimeZoneOffsetHours(final Integer timeZoneOffsetHours) {
        this.timeZoneOffsetHours.setValue(timeZoneOffsetHours == null
                ? 0
                : timeZoneOffsetHours);
    }

    /// @return The offset's minutes, or null for none.
    public Integer getTimeZoneOffsetMinutes() {
        return nullIfZero(timeZoneOffsetMinutes.getIntValue());
    }

    /// @param timeZoneOffsetMinutes The offset's minutes, or null for none.
    public void setTimeZoneOffsetMinutes(final Integer timeZoneOffsetMinutes) {
        this.timeZoneOffsetMinutes.setValue(timeZoneOffsetMinutes == null
                ? 0
                : timeZoneOffsetMinutes);
    }

    /// @return Whether long values wrap.
    public boolean isWrap() {
        return wrap.getValue();
    }

    /// @param wrap Whether long values wrap.
    public void setWrap(final boolean wrap) {
        this.wrap.setValue(wrap);
    }

    private static Integer nullIfZero(final int value) {
        return value == 0
                ? null
                : value;
    }

    // Shows the settings of the type chosen, and the time zone's id or offset as it is given
    private void showSettings() {
        final Type type = this.type.getValue();
        final boolean number = Type.NUMBER.equals(type);
        final boolean dateTime = Type.DATE_TIME.equals(type);
        formatDecimalPlaces.setVisible(number);
        formatUseSeparator.setVisible(number);
        formatUsePreferences.setVisible(dateTime);
        formatFormat.setVisible(dateTime);
        formatCustom.setVisible(dateTime);
        formatCustomFormat.setVisible(dateTime);
        formatTimeZone.setVisible(dateTime);
        formatTimeZoneId.setVisible(dateTime && Use.ID.equals(timeZoneUse.getValue()));
        formatTimeZoneOffset.setVisible(dateTime && Use.OFFSET.equals(timeZoneUse.getValue()));
    }

    private void onCustom() {
        text.setEnabled(custom.getValue());
    }

    private void onType(final ValueChangeEvent<Type> event) {
        showSettings();
    }

    private void onFormat(final ValueChangeEvent<String> event) {
        setPattern(format.getValue());
    }

    private void onTimeZoneUse(final ValueChangeEvent<Use> event) {
        showSettings();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ColumnFormat> {

    }
}
