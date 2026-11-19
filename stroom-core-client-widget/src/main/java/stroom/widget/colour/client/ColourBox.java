/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.colour.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/**
 * A form-field colour picker: a thin widget wrapper around a native HTML
 * {@code <input type="color">}, the BEM block {@code colour-box} (colour-box.css), its markup
 * {@code ColourBox.ui.xml}.
 *
 * <p>Values are 7-character hex strings (e.g. {@code "#1e88e5"}); anything
 * else is normalised to black, since the native control cannot represent an
 * empty value. Pair with a separate "default" checkbox when "no explicit
 * colour" must be expressible.</p>
 */
public class ColourBox extends Widget implements HasValue<String> {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String DEFAULT_COLOUR = "#000000";

    private final InputElement input;
    private Consumer<String> onChange;

    public ColourBox() {
        input = BINDER.createAndBindUi(this);
        setElement(input);
        sinkEvents(Event.ONCHANGE);
    }

    /// @param ariaLabel The picker's name for screen readers, where no label names it.
    public void setAriaLabel(final String ariaLabel) {
        input.setAttribute("aria-label", ariaLabel);
    }

    /// @param onChange Told the new colour when the user picks one (or `setValue(value, true)` sets
    ///                 one), or null for nothing.
    public void setOnChange(final Consumer<String> onChange) {
        this.onChange = onChange;
    }

    @Override
    public void onBrowserEvent(final Event event) {
        super.onBrowserEvent(event);
        if (Event.ONCHANGE == event.getTypeInt()) {
            fireChange();
        }
    }

    private void fireChange() {
        ValueChangeEvent.fire(this, getValue());
        if (onChange != null) {
            onChange.accept(getValue());
        }
    }

    /** The current colour as a 7-character hex string. */
    @Override
    public String getValue() {
        return normalise(input.getValue());
    }

    @Override
    public void setValue(final String value) {
        setValue(value, false);
    }

    @Override
    public void setValue(final String value, final boolean fireEvents) {
        input.setValue(normalise(value));
        if (fireEvents) {
            fireChange();
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    public void setEnabled(final boolean enabled) {
        input.setDisabled(!enabled);
    }

    private static String normalise(final String value) {
        // The native control requires a 7-char hex value
        if (value == null || !value.matches("^#[0-9a-fA-F]{6}$")) {
            return DEFAULT_COLOUR;
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<InputElement, ColourBox> {

    }
}
