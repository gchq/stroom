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

package stroom.preferences.client.component;

import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.MomentJs;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;

/// The Date and Time tab of the user's preferences: the format dates and times are shown in, a
/// standard one or the user's own, and the time zone they are shown in. It reads and writes the
/// preferences itself, as nothing it shows comes from the server.
public class TimePreferences extends Component {

    /// The formats offered; another is the user's own.
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
    FormGroup userPreferencesTimeZoneId;
    @UiField
    FormGroup userPreferencesTimeZoneOffset;
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

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public TimePreferences() {
        initWidget(BINDER.createAndBindUi(this));
        format.addItems(STANDARD_FORMATS);

        timeZoneUse.addItem(Use.LOCAL);
        timeZoneUse.addItem(Use.UTC);
        timeZoneUse.addItem(Use.ID);
        timeZoneUse.addItem(Use.OFFSET);

        for (final String tz : MomentJs.getTimeZoneIds()) {
            timeZoneId.addItem(tz);
        }

        timeZoneOffsetHours.setValue(0);
        timeZoneOffsetMinutes.setValue(0);

        custom.addValueChangeHandler(event -> {
            text.setEnabled(custom.getValue());
            onChange.run();
        });
        format.addValueChangeHandler(event -> {
            setPattern(format.getValue());
            onChange.run();
        });
        timeZoneUse.addValueChangeHandler(event -> {
            changeVisible();
            onChange.run();
        });
    }

    /// Shows the user's date and time preferences.
    ///
    /// @param userPreferences The preferences.
    public void read(final UserPreferences userPreferences) {
        setPattern(userPreferences.getDateTimePattern());
        final UserTimeZone timeZone = userPreferences.getTimeZone();
        if (timeZone != null) {
            timeZoneUse.setValue(timeZone.getUse());
            changeVisible();
            timeZoneId.setValue(timeZone.getId());
            timeZoneOffsetHours.setValue(timeZone.getOffsetHours() == null
                    ? 0
                    : timeZone.getOffsetHours());
            timeZoneOffsetMinutes.setValue(timeZone.getOffsetMinutes() == null
                    ? 0
                    : timeZone.getOffsetMinutes());
        }
    }

    /// Writes the preferences shown into the user's preferences' builder.
    ///
    /// @param builder The builder.
    public void write(final UserPreferences.Builder builder) {
        final UserTimeZone timeZone = UserTimeZone.builder()
                .use(timeZoneUse.getValue())
                .id(timeZoneId.getValue())
                .offsetHours(nullIfZero(timeZoneOffsetHours.getIntValue()))
                .offsetMinutes(nullIfZero(timeZoneOffsetMinutes.getIntValue()))
                .build();
        builder
                .dateTimePattern(custom.getValue()
                        ? text.getText()
                        : format.getValue())
                .timeZone(timeZone);
    }

    /// @param onChange What to run when the user changes a preference.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    private void setPattern(final String pattern) {
        String patternText = pattern;
        if (patternText == null || patternText.trim().isEmpty()) {
            //noinspection SequencedCollectionMethodCanBeUsed
            patternText = STANDARD_FORMATS.get(0);
        }

        if (!patternText.equals(format.getValue())) {
            format.setValue(patternText);
        }

        final boolean isCustom = format.getValue() == null;
        custom.setValue(isCustom);
        text.setEnabled(isCustom);
        text.setText(patternText);
    }

    private void changeVisible() {
        userPreferencesTimeZoneId.setVisible(Use.ID.equals(timeZoneUse.getValue()));
        userPreferencesTimeZoneOffset.setVisible(Use.OFFSET.equals(timeZoneUse.getValue()));
    }

    private static Integer nullIfZero(final int value) {
        return value == 0
                ? null
                : value;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TimePreferences> {

    }
}
