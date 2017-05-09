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

package stroom.widget.customdatebox.client;

import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.datepicker.client.DatePicker;

import java.util.Date;

public class MyDateBox extends Composite implements DateBoxView {
    private static final String DEFAULT_TIME = "T00:00:00.000Z";

    private final PopupPanel popup;
    private final DatePicker datePicker;
    private final TextBox textBox;

    public MyDateBox() {
        datePicker = new CustomDatePicker();
        textBox = new TextBox();

        this.popup = new PopupPanel(true);
        popup.addAutoHidePartner(textBox.getElement());
        popup.setWidget(datePicker);
        popup.setStyleName("dateBoxPopup");

        initWidget(textBox);

        DateBoxHandler handler = new DateBoxHandler();
        datePicker.addValueChangeHandler(handler);
        textBox.addFocusHandler(handler);
        textBox.addBlurHandler(handler);
        textBox.addClickHandler(handler);
        textBox.addKeyDownHandler(handler);
        textBox.setDirectionEstimator(false);
        popup.addCloseHandler(handler);
    }

    @Override
    public Long getMilliseconds() {
        return ClientDateUtil.fromISOString(getValue());
    }

    @Override
    public void setMilliseconds(final Long milliseconds) {
        setValue(ClientDateUtil.toISOString(milliseconds));
    }

    @Override
    public String getValue() {
        return textBox.getText();
    }

    public void setValue(final String value) {
        textBox.setValue(value);
    }

    public void setValue(final String value, final boolean fireEvents) {
        textBox.setValue(value, fireEvents);
    }

    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return textBox.addKeyDownHandler(handler);
    }

    public HandlerRegistration addKeyPressHandler(final KeyPressHandler handler) {
        return textBox.addKeyPressHandler(handler);
    }

    public HandlerRegistration addKeyUpHandler(final KeyUpHandler handler) {
        return textBox.addKeyUpHandler(handler);
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return textBox.addValueChangeHandler(handler);
    }

    public void showDatePicker() {
        if (!popup.isShowing()) {
            Date current = parseDate();
            if (current == null) {
                current = new Date();
            }
            datePicker.setCurrentMonth(current);
            datePicker.setValue(current, false);
            popup.showRelativeTo(this);
        }
    }

    private Date parseDate() {
        try {
            String text = textBox.getText().trim();
            int index = text.indexOf('T');
            if (index != -1) {
                text = text.substring(0, index) + "T12:00:00.000Z";
            }

            final Long millis = ClientDateUtil.fromISOString(text);
            if (millis != null) {
                return new Date(millis);
            }
        } catch (final Exception e) {
            // Ignore if we couldn't parse.
        }

        return null;
    }

    public void hideDatePicker() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    private class DateBoxHandler implements ValueChangeHandler<Date>,
            FocusHandler, BlurHandler, ClickHandler, KeyDownHandler,
            CloseHandler<PopupPanel> {

        public void onValueChange(ValueChangeEvent<Date> event) {
            // Trim down the date to be just the date part.
            String date = ClientDateUtil.toDateString(event.getValue().getTime());

            String time = DEFAULT_TIME;
            String expression = "";

            String text = textBox.getText().trim();
            int tIndex = text.indexOf('T');
            if (tIndex != -1) {
                int zIndex = text.indexOf('Z', tIndex);
                if (zIndex != -1) {
                    time = text.substring(tIndex, zIndex + 1);
                    expression = text.substring(zIndex + 1).trim();
                    if (expression.length() > 0) {
                        expression = " " + expression;
                    }
                }
            }

            textBox.setText(date + time + expression);
            ValueChangeEvent.fire(textBox, textBox.getText());

//            hideDatePicker();
//            preventDatePickerPopup();
            textBox.setFocus(true);

            if (tIndex != -1) {
                textBox.setCursorPos(tIndex);
            }

            hideDatePicker();
        }

        public void onBlur(BlurEvent event) {
//            if (isDatePickerShowing() == false) {
//                updateDateFromTextBox();
//            }
        }

        public void onClick(ClickEvent event) {
            showDatePicker();
        }

        public void onClose(CloseEvent<PopupPanel> event) {
//            // If we are not closing because we have picked a new value, make sure the
//            // current value is updated.
//            if (allowDPShow) {
//                updateDateFromTextBox();
//            }
        }

        public void onFocus(FocusEvent event) {
//            if (allowDPShow && isDatePickerShowing() == false) {
            showDatePicker();
//            }
        }

        public void onKeyDown(KeyDownEvent event) {
            switch (event.getNativeKeyCode()) {
                case KeyCodes.KEY_ENTER:
                case KeyCodes.KEY_TAB:
//                    updateDateFromTextBox();
                    // Deliberate fall through
                case KeyCodes.KEY_ESCAPE:
                case KeyCodes.KEY_UP:
                    hideDatePicker();
                    break;
                case KeyCodes.KEY_DOWN:
                    showDatePicker();
                    break;
            }
        }
    }
}
