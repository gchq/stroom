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

import stroom.event.client.StaticEventBus;
import stroom.item.client.EventBinder;
import stroom.svg.client.SvgIconBox;
import stroom.svg.shared.SvgImage;
import stroom.widget.form.client.FieldValidity;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Provider;

import java.util.Objects;

/// A text field for a date and time, in ISO 8601 (e.g. `2024-01-15T09:50:00.000Z`), with a
/// calendar icon at its end. Enter or Alt+Down in the field, or a click on the icon, opens the Set
/// Date And Time dialog ([DateTimePopup]) on the field's time; choosing OK puts the time chosen in
/// the field, in the user's time zone. It never opens on focus.
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
/// The BEM block is `date-time-box` (`date-time-box.css`); its field is a text field.
public class DateTimeBox
        extends Composite
        implements Focus, HasValueChangeHandlers<String> {

    private static final String BLOCK = "date-time-box";
    private static final DateTimeModel FORMAT_MODEL = new DateTimeModel();

    private Provider<DateTimePopup> popupProvider;
    private final TextBox textBox;
    private boolean readOnly;
    private final SvgIconBox svgIconBox;
    private String stringValue;
    private Long longValue;
    private DateTimePopup popup;
    // Allows the box to be empty
    private boolean optional = false;
    // Allows text that isn't a plain time, e.g. now()-1d
    private boolean expressionsAllowed = false;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(svgIconBox.addClickHandler(event -> showPopup()));
            registerHandler(textBox.addKeyDownHandler(DateTimeBox.this::onKeyDown));
            registerHandler(textBox.addValueChangeHandler(event -> {
                stringValue = textBox.getValue();
                ValueChangeEvent.fire(DateTimeBox.this, stringValue);
            }));
            registerHandler(textBox.addBlurHandler(event -> onBlur()));
            registerHandler(textBox.addFocusHandler(event -> onFocus()));
        }
    };

    /// Makes an empty box.
    public DateTimeBox() {
        textBox = new TextBox();
        textBox.addStyleName(BLOCK + "__input stroom-control allow-focus");
        textBox.getElement().setAttribute("aria-haspopup", "dialog");
        stringValue = textBox.getText();

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName(BLOCK);
        svgIconBox.setWidget(textBox, SvgImage.CALENDAR);

        initWidget(svgIconBox);
    }

    @Override
    protected void onLoad() {
        eventBinder.bind();
    }

    @Override
    protected void onUnload() {
        eventBinder.unbind();
    }

    private void onKeyDown(final KeyDownEvent event) {
        final int keyCode = event.getNativeKeyCode();
        if (KeyCodes.KEY_ENTER == keyCode && !event.isAnyModifierKeyDown() && !expressionsAllowed) {
            event.preventDefault();
            showPopup();
        } else if (KeyCodes.KEY_DOWN == keyCode && event.isAltKeyDown()) {
            event.preventDefault();
            showPopup();
        }
    }

    private DateTimePopup getPopup() {
        if (popup == null) {
            popup = popupProvider != null
                    ? popupProvider.get()
                    : new DateTimePopup(StaticEventBus.getEventBus(),
                            new DateTimeViewImpl(GWT.create(DateTimeViewImpl.Binder.class)),
                            new DateTimeModel());
            popup.setTime(System.currentTimeMillis());
        }
        return popup;
    }

    private void showPopup() {
        if (readOnly || !isEnabled()) {
            return;
        }
        final DateTimePopup popup = getPopup();
        final Long time = parse(textBox.getValue());
        if (time != null) {
            popup.setTime(time);
        } else if (longValue != null && !expressionsAllowed) {
            popup.setTime(longValue);
        } else {
            popup.setTime(System.currentTimeMillis());
        }
        popup.show(newValue -> {
            if (!Objects.equals(time, newValue)) {
                setValue(newValue, true);
            }
        });
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
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
        svgIconBox.setReadonly(!enabled || readOnly);
        updateInvalidState();
    }

    /// Makes the value read only: it can be read, selected and copied, and stays in the tab
    /// order, but can't be changed, and its dialog doesn't open. It looks like the normal field
    /// with its value greyed.
    ///
    /// @param readOnly Whether the value is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        textBox.setReadOnly(readOnly);
        svgIconBox.setReadonly(!textBox.isEnabled() || readOnly);
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
        if (fireEvents) {
            ValueChangeEvent.fire(this, textBox.getValue());
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

    /// Sets how the box makes its dialog, e.g. from GIN. Without one, it makes its own, shown on
    /// the app's event bus ([StaticEventBus]).
    ///
    /// @param popupProvider The dialog's provider.
    public void setPopupProvider(final Provider<DateTimePopup> popupProvider) {
        this.popupProvider = popupProvider;
    }
}
