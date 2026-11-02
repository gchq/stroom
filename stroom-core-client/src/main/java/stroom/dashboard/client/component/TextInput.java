/*
 * Copyright 2018 Crown Copyright
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

import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;
import java.util.function.Function;

/// A dashboard's Text Input pane: one text box, whose value is a query parameter, or, for a date
/// and time ([#setDateTime(boolean)]), a date-time box, whose calendar a controller attached to it
/// opens ([#getDateTimeBox()]). Text that fails the pane's check ([#setMarkInvalid(Function)]) is
/// marked invalid, with the reason as its tooltip, and kept.
public class TextInput extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox value;
    @UiField
    DateTimeBox dateTimeValue;

    private boolean dateTime;
    private Function<String, String> markInvalid = text -> null;
    private Consumer<String> onChange = text -> {
    };

    /// Makes the pane, empty.
    public TextInput() {
        initWidget(BINDER.createAndBindUi(this));
        // On each key, as the old pane did, so the dashboard is changed as the user types
        value.addKeyUpHandler(event -> {
            showTextValidity();
            onChange.accept(value.getText());
        });
        // When the user leaves the box or uses the calendar
        dateTimeValue.setOnChange(() -> onChange.accept(dateTimeValue.getText()));
    }

    /// Shows a date-time box with a calendar in place of the text box, keeping the text.
    ///
    /// @param dateTime Whether the value is a date and time.
    public void setDateTime(final boolean dateTime) {
        if (this.dateTime != dateTime) {
            final String text = getValue();
            this.dateTime = dateTime;
            value.setVisible(!dateTime);
            dateTimeValue.setVisible(dateTime);
            setValue(text);
        }
    }

    /// @return The date-time box, for a controller to attach to, to open its calendar.
    public DateTimeBox getDateTimeBox() {
        return dateTimeValue;
    }

    /// @param value The text to show.
    public void setValue(final String value) {
        if (dateTime) {
            dateTimeValue.setText(value);
            dateTimeValue.showValidity();
        } else {
            this.value.setValue(value);
            showTextValidity();
        }
    }

    /// @return The text typed.
    public String getValue() {
        return dateTime
                ? dateTimeValue.getText()
                : value.getValue();
    }

    /// Checks the text, only to mark it: while it fails the check the box is marked invalid, with
    /// the reason as its tooltip; the text is kept, and nothing refuses to save it.
    ///
    /// @param markInvalid Given the text, says what is wrong with it, or gives null if nothing is.
    public void setMarkInvalid(final Function<String, String> markInvalid) {
        this.markInvalid = markInvalid;
        dateTimeValue.setMarkInvalid(markInvalid);
        if (dateTime) {
            dateTimeValue.showValidity();
        } else {
            showTextValidity();
        }
    }

    /// Names the box for screen readers.
    ///
    /// @param name The box's name: the pane's name.
    public void setAccessibleName(final String name) {
        AccessibleName.set(value, name);
        AccessibleName.set(dateTimeValue, name);
    }

    /// @param onChange Given the text when the user types, or, for a date and time, leaves the box
    ///                 or uses the calendar.
    public void setOnChange(final Consumer<String> onChange) {
        this.onChange = onChange;
    }

    // Marks the text box invalid while its text fails the check, with the reason as its tooltip
    private void showTextValidity() {
        final String reason = markInvalid.apply(value.getText());
        FieldValidity.setInvalid(value.getElement(), reason != null);
        value.setTitle(reason);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TextInput> {

    }
}
