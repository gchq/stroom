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

package stroom.widget.linecolinput.client;

import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TextBox;

import java.util.Optional;

public class LineColInput extends Composite {

    private final TextBox valueBox = new TextBox();
    private final RegExp regExp;

    public LineColInput() {

        valueBox.addKeyPressHandler(LineColInput::onKeyPress);
        valueBox.addKeyUpHandler(this::validateEntry);
        valueBox.addStyleName("allow-focus");

        final FlowPanel layout = new FlowPanel();
        layout.setStylePrimaryName("lineColInput");
        layout.add(valueBox);

        initWidget(layout);

        regExp = RegExp.compile("^[0-9]+(:[0-9]+)?$");
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

        if (value != null && !value.isEmpty() && !regExp.test(value)) {
            valueBox.addStyleName("highlight");
        } else {
            valueBox.removeStyleName("highlight");
        }
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

    /**
     * Sets whether this widget is enabled.
     *
     * @param enabled true to enable the widget, false to disable it
     */
    public void setEnabled(final boolean enabled) {
        valueBox.setEnabled(enabled);
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
}
