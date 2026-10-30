/*
 * Copyright 2024 Crown Copyright
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

package stroom.widget.datepicker.client.component;

import stroom.util.shared.time.Time;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.IconBox;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Focus;

/// A text box for a time (`HH:MM:SS`), with a clock that opens a dialog to choose one: the clock,
/// Enter or Alt+Down asks to open it ([#setOnOpenPicker(Runnable)]), which a [TimeBoxController]
/// attached to it answers. Its markup is `TimeBox.ui.xml`, styled by `date-time-box.css`.
public class TimeBox extends Component implements Focus, HasValueChangeHandlers<String> {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    IconBox iconBox;
    @UiField
    TextBox textBox;

    private Runnable onOpenPicker;
    private Runnable onChange;

    /// Makes a box with no time.
    public TimeBox() {
        initWidget(BINDER.createAndBindUi(this));
        iconBox.setOnIconClick(this::openPicker);
        textBox.addKeyDownHandler(event -> {
            // As a date-time box: Enter or Alt+Down opens the dialog
            final int keyCode = event.getNativeKeyCode();
            if ((KeyCodes.KEY_ENTER == keyCode && !event.isAnyModifierKeyDown())
                || (KeyCodes.KEY_DOWN == keyCode && event.isAltKeyDown())) {
                event.preventDefault();
                openPicker();
            }
        });
        textBox.addValueChangeHandler(event -> changed(textBox.getValue()));
        textBox.addBlurHandler(event -> FieldValidity.setInvalid(textBox.getElement(), !isValid()));
        textBox.addFocusHandler(event -> FieldValidity.setInvalid(textBox.getElement(), false));
    }

    /// @param onOpenPicker Run when the user asks to choose a time in the dialog, unless the box
    ///                     is read only or disabled.
    public void setOnOpenPicker(final Runnable onOpenPicker) {
        this.onOpenPicker = onOpenPicker;
    }

    /// @param onChange Run when the user changes the time, by typing or with the dialog.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
    }

    /// @param name The text box's form name.
    public void setName(final String name) {
        textBox.setName(name);
    }

    /// @param enabled Whether the time can be changed, by typing or with the dialog.
    public void setEnabled(final boolean enabled) {
        textBox.setEnabled(enabled);
        // So the clock icon can't open the dialog either
        iconBox.setReadOnly(!enabled || isReadOnly());
    }

    /// Makes the value read only: it can be read, selected and copied, and stays in the tab
    /// order, but can't be changed, and its dialog doesn't open. It looks like the normal field
    /// with its value greyed.
    ///
    /// @param readOnly Whether the value is read only.
    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        textBox.setReadOnly(readOnly);
        iconBox.setReadOnly(!textBox.isEnabled() || readOnly);
    }

    /// @return The time typed or chosen; one that can't be read is midnight.
    public Time getValue() {
        return Time.parse(textBox.getValue());
    }

    /// @param value The time to show, or null for midnight.
    public void setValue(final Time value) {
        setValue(value, false);
    }

    /// @param value      The time to show, or null for midnight.
    /// @param fireEvents Whether to tell its listeners, as when the user changes it.
    public void setValue(final Time value, final boolean fireEvents) {
        textBox.setValue((value != null
                ? value
                : Time.ZERO).toString());
        FieldValidity.setInvalid(textBox.getElement(), false);
        if (fireEvents) {
            changed(null);
        }
    }

    /// @return Whether the text is a time.
    public boolean isValid() {
        return Time.isValid(textBox.getValue());
    }

    /// Kept beside [#setOnChange(Runnable)] until its callers have moved.
    ///
    /// @param handler Told when the user changes the time; the event's value is the text typed, or
    ///                null when the dialog set it.
    /// @return The handler's registration.
    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    private void openPicker() {
        if (!isReadOnly() && textBox.isEnabled() && onOpenPicker != null) {
            onOpenPicker.run();
        }
    }

    private void changed(final String text) {
        ValueChangeEvent.fire(this, text);
        if (onChange != null) {
            onChange.run();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<IconBox, TimeBox> {

    }
}
