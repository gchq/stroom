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

package stroom.widget.datepicker.client;

import stroom.svg.client.IconBox;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.HasInvalidInput;
import stroom.widget.form.client.InvalidInputs;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Focus;

/// A text field for a date and time, in ISO 8601 (e.g. `2024-01-15T09:50:00.000Z`), with a
/// calendar icon at its end. Enter or Alt+Down in the field, or a click on the icon, asks to open
/// the Set Date And Time dialog ([#setOnOpenPicker(Runnable)]), which a [DateTimeBoxController]
/// attached to it opens on the field's time; choosing OK puts the time chosen in the field, in the
/// user's time zone. It never opens on focus.
///
/// The field says it opens a dialog (`aria-haspopup="dialog"`). The icon is a picture, hidden from
/// screen readers, as the keys open the same dialog.
///
/// A box that allows expressions ([#setExpressionsAllowed(boolean)]) takes any text as well, e.g.
/// `now()-1d` or `day()` in a query's time range, for the server to work out: such text is never
/// marked invalid, and the dialog, which only edits a plain time, opens on now and replaces it. In
/// such a box, which is typed in first, Enter is the screen's (e.g. to run a query), and only Alt+Down
/// or the icon opens the dialog.
///
/// The BEM block is `date-time-box` (`date-time-box.css`); its field is a text field. Its markup is
/// `DateTimeBox.ui.xml`.
public class DateTimeBox
        extends Component
        implements Focus, HasValueChangeHandlers<String>, HasInvalidInput {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final DateTimeModel FORMAT_MODEL = new DateTimeModel();
    private static final String NOT_A_TIME = "Must be a date and time";
    private static final String NO_TIME = "A date and time is needed";

    @UiField
    IconBox iconBox;
    @UiField
    TextBox textBox;

    private Runnable onOpenPicker;
    private Runnable onChange;
    private String stringValue;
    private Long longValue;
    // Allows the box to be empty
    private boolean optional = false;
    // Allows text that isn't a plain time, e.g. now()-1d
    private boolean expressionsAllowed = false;

    /// Makes an empty box.
    public DateTimeBox() {
        initWidget(BINDER.createAndBindUi(this));
        stringValue = textBox.getText();
        iconBox.setOnIconClick(this::openPicker);
        textBox.addKeyDownHandler(this::onKeyDown);
        textBox.addValueChangeHandler(event -> {
            stringValue = textBox.getValue();
            changed();
        });
        textBox.addBlurHandler(event -> onBlur());
        textBox.addFocusHandler(event -> onFocus());
    }

    /// @param onOpenPicker Run when the user asks to choose a time in the dialog, unless the box
    ///                     is read only or disabled.
    public void setOnOpenPicker(final Runnable onOpenPicker) {
        this.onOpenPicker = onOpenPicker;
    }

    /// @param onChange Run when the user changes the text, by typing or with the dialog.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    private void onKeyDown(final KeyDownEvent event) {
        final int keyCode = event.getNativeKeyCode();
        if (KeyCodes.KEY_ENTER == keyCode && !event.isAnyModifierKeyDown() && !expressionsAllowed) {
            event.preventDefault();
            openPicker();
        } else if (KeyCodes.KEY_DOWN == keyCode && event.isAltKeyDown()) {
            event.preventDefault();
            openPicker();
        }
    }

    private void openPicker() {
        if (!isReadOnly() && isEnabled() && onOpenPicker != null) {
            onOpenPicker.run();
        }
    }

    /// The time the dialog starts on, for its controller: the field's, else the last one set
    /// (unless any text is allowed, when the field's text may be an expression), else now.
    ///
    /// @return The time, in milliseconds since the epoch.
    long getPickerStartTime() {
        final Long time = parse(textBox.getValue());
        if (time != null) {
            return time;
        } else if (longValue != null && !expressionsAllowed) {
            return longValue;
        }
        return System.currentTimeMillis();
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
    }

    /// @param role The box's WAI-ARIA role, e.g. `group` to name its text box and button as one.
    public void setRole(final String role) {
        getElement().setAttribute("role", role);
    }

    /// @param ariaLabel The box's name for screen readers, where no label names it.
    public void setAriaLabel(final String ariaLabel) {
        getElement().setAttribute("aria-label", ariaLabel);
    }

    /// Sets the field's `name` attribute.
    ///
    /// @param name The name.
    public void setName(final String name) {
        textBox.setName(name);
    }

    /// Enables or disables the box: a disabled box's icon doesn't open the dialog either.
    ///
    /// @param enabled Whether the box is enabled.
    public void setEnabled(final boolean enabled) {
        textBox.setEnabled(enabled);
        iconBox.setReadOnly(!enabled || isReadOnly());
        updateInvalidState();
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

    /// @return Whether the box is enabled.
    public boolean isEnabled() {
        return textBox.isEnabled();
    }

    private static Long parse(final String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        final UTCDate date = UTCDate.create(text.trim());
        if (date != null) {
            return (long) date.getTime();
        }
        return null;
    }

    /// @return The time in the field, in milliseconds since the epoch, or null if it is empty or
    /// isn't a plain time (e.g. an expression).
    public Long getValue() {
        return parse(textBox.getValue());
    }

    /// Puts a time in the field, in ISO 8601 in the user's time zone, without firing a change.
    ///
    /// @param value The time, in milliseconds since the epoch, or null to empty the field.
    public void setValue(final Long value) {
        setValue(value, false);
    }

    /// Puts a time in the field, in ISO 8601 in the user's time zone.
    ///
    /// @param value      The time, in milliseconds since the epoch, or null to empty the field.
    /// @param fireEvents Whether to fire a change, with the field's text.
    public void setValue(final Long value, final boolean fireEvents) {
        longValue = value;
        setText(value != null
                ? FORMAT_MODEL.formatIso(UTCDate.create(value))
                : null, fireEvents);
    }

    /// @return The field's text, e.g. a time or, where allowed, an expression.
    public String getText() {
        return textBox.getValue();
    }

    /// Puts text in the field, without firing a change.
    ///
    /// @param text The text, e.g. a time or, where allowed, an expression; null to empty it.
    public void setText(final String text) {
        setText(text, false);
    }

    /// Puts text in the field.
    ///
    /// @param text       The text, e.g. a time or, where allowed, an expression; null to empty it.
    /// @param fireEvents Whether to fire a change, with the field's text.
    public void setText(final String text, final boolean fireEvents) {
        stringValue = text;
        textBox.setValue(text);
        FieldValidity.setInvalid(textBox.getElement(), false);
        // Text set by the screen replaces what was typed, so what was said about that goes
        InvalidInputs.showReason(this);
        if (fireEvents) {
            changed();
        }
    }

    /// @return Whether the field holds a time, or is empty where that is allowed, or holds any
    /// text where expressions are allowed.
    public boolean isValid() {
        if (stringValue == null || expressionsAllowed) {
            return true;
        }
        if (optional && stringValue.isEmpty()) {
            return true;
        }
        return parse(stringValue) != null;
    }

    private boolean isEmpty() {
        return stringValue == null || stringValue.isEmpty();
    }

    private void onFocus() {
        FieldValidity.setInvalid(textBox.getElement(), false);
    }

    private void onBlur() {
        updateInvalidState();
    }

    private void updateInvalidState() {
        final boolean valid = isEmpty() && optional
                              || !isEnabled()
                              || isValid();
        FieldValidity.setInvalid(textBox.getElement(), !valid);
        InvalidInputs.showReason(this);
    }

    /// @return Why the box's text can't be kept (it isn't a time, or the box is empty and may not
    /// be), or null if it can, or the box is disabled or read only.
    @Override
    public String getInvalidReason() {
        if (!isEnabled() || isReadOnly() || expressionsAllowed) {
            return null;
        } else if (isEmpty()) {
            return optional || stringValue == null
                    ? null
                    : NO_TIME;
        }
        return isValid()
                ? null
                : NOT_A_TIME;
    }

    @Override
    public void focusInvalidInput() {
        focus();
    }

    /// @param optional Whether the box may be left empty.
    public void setOptional(final boolean optional) {
        this.optional = optional;
    }

    /// @return Whether the box may be left empty.
    public boolean getOptional() {
        return optional;
    }

    /// Allows text that isn't a plain time, e.g. `now()-1d`, for the server to work out (as a
    /// query's time range and expression terms do). Such text is never marked invalid, and the
    /// dialog opens on now and replaces it. Enter is then left to the screen (e.g. to run a query),
    /// and only Alt+Down or the icon opens the dialog.
    ///
    /// @param expressionsAllowed Whether any text is allowed.
    public void setExpressionsAllowed(final boolean expressionsAllowed) {
        this.expressionsAllowed = expressionsAllowed;
        updateInvalidState();
    }

    @Override
    public HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<String> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    /// Adds a handler of the field's key presses, e.g. to commit an edit on Enter.
    ///
    /// @param handler The handler.
    /// @return Its registration.
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return textBox.addKeyDownHandler(handler);
    }

    private void changed() {
        ValueChangeEvent.fire(this, textBox.getValue());
        if (onChange != null) {
            onChange.run();
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<IconBox, DateTimeBox> {

    }
}
