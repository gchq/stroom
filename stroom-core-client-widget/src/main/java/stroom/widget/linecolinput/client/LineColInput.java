/*
 * Copyright 2020 Crown Copyright
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

package stroom.widget.linecolinput.client;

import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.FieldValidity;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

import java.util.Optional;
import java.util.function.Consumer;

/// A text box for a position in a stream: a line number, optionally with a column number after a
/// colon, e.g. `12` or `12:5`. Only digits and a colon can be typed; anything else that makes it
/// no such position marks it invalid (`aria-invalid`, as `FieldValidity` does).
///
/// BEM block `line-col-input` (no rules of its own: its field looks as every text field does), its
/// text box `line-col-input__field`; its markup
/// is `LineColInput.ui.xml`.
public class LineColInput extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final RegExp LINE_COL = RegExp.compile("^[0-9]+(:[0-9]+)?$");

    @UiField
    TextBox valueBox;
    private HandlerRegistration onChangeRegistration;

    public LineColInput() {
        initWidget(BINDER.createAndBindUi(this));
        valueBox.addKeyPressHandler(LineColInput::onKeyPress);
        valueBox.addKeyUpHandler(this::validateEntry);
    }

    /// @param onChange Told the position typed, or empty if it is no position, when the user changes
    ///                 it and leaves the box (as a value change event is), or null for nothing.
    public void setOnChange(final Consumer<Optional<Location>> onChange) {
        if (onChangeRegistration != null) {
            onChangeRegistration.removeHandler();
            onChangeRegistration = null;
        }
        if (onChange != null) {
            onChangeRegistration = valueBox.addValueChangeHandler(event -> onChange.accept(getLocation()));
        }
    }

    private static void onKeyPress(final KeyPressEvent event) {
        // only allow numbers, colon, tab, backspace, del and arrow keys
        if (!Character.isDigit(event.getCharCode())
                && event.getCharCode() != ':'
                && !KeyCodes.isArrowKey(event.getNativeEvent().getKeyCode())
                && event.getNativeEvent().getKeyCode() != KeyCodes.KEY_TAB
                && event.getNativeEvent().getKeyCode() != KeyCodes.KEY_DELETE
                && event.getNativeEvent().getKeyCode() != KeyCodes.KEY_BACKSPACE) {
            ((TextBox) event.getSource()).cancelKey();
        }
    }

    private void validateEntry(final KeyUpEvent keyUpEvent) {

        final String value = ((TextBox) keyUpEvent.getSource()).getValue();

        FieldValidity.setInvalid(valueBox.getElement(), value != null && !value.isEmpty() && !LINE_COL.test(value));
    }

    /**
     * @return the TextBox used by this widget
     */
    public TextBox getTextBox() {
        return valueBox;
    }

    /**
     * @return whether this widget is enabled.
     */
    public boolean isEnabled() {
        return valueBox.isEnabled();
    }

    /// Names its text box for assistive technology, where no label is for it (see [AccessibleName]).
    ///
    /// @param name The name, e.g. `Line and column`.
    public void setAccessibleName(final String name) {
        AccessibleName.set(this, name);
    }

    /**
     * Sets whether this widget is enabled.
     *
     * @param enabled true to enable the widget, false to disable it
     */
    public void setEnabled(final boolean enabled) {
        valueBox.setEnabled(enabled);
    }

    /// Makes the value read only: it can be read, selected and copied, and stays in the tab
    /// order, but can't be changed. It looks like the normal field with its value greyed.
    ///
    /// @param readOnly Whether the value is read only.
    public void setReadOnly(final boolean readOnly) {
        valueBox.setReadOnly(readOnly);
    }

    public void setValue(final Integer lineNo, final Integer colNo) {
        final StringBuilder sb = new StringBuilder();
        if (lineNo == null) {
            // no value
        } else {
            sb.append(lineNo);
            if (colNo != null) {
                sb.append(":");
                sb.append(colNo);
            }
        }
        valueBox.setValue(sb.toString());
    }

    public void setValue(final Location location) {
        if (location != null) {
            setValue(location.getLineNo(), location.getColNo());
        }
    }

    public Optional<Location> getLocation() {
        return DefaultLocation.parse(valueBox.getValue());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, LineColInput> {

    }
}
