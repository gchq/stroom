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

import com.google.gwt.event.dom.client.*;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.TimeZone;
import com.google.gwt.i18n.client.impl.cldr.DateTimeFormatInfoImpl_en_GB;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.datepicker.client.DatePicker;

import java.util.Date;

public class MyDateBox extends Composite implements HasText, HasValue<String> {
    private static final String DEFAULT_TIME = "T00:00:00.000Z";

    private final PopupPanel popup;
    private final DatePicker datePicker;
    private final TextBox textBox;
    private final DateTimeFormat dateTimeFormat = new MyDateTimeFormat();

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

//      datePicker.addValueChangeHandler(new ValueChangeHandler<Date>() {
//          @Override
//          public void onValueChange(ValueChangeEvent<Date> event) {
//
//          }
//      });
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

    @Override
    public String getText() {
        return textBox.getText();
    }

    @Override
    public void setText(final String text) {
        textBox.setText(text);
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
        Date current = parseDate();
        if (current == null) {
            current = new Date();
        }
        datePicker.setCurrentMonth(current);
        popup.showRelativeTo(this);
    }

    private Date parseDate() {
        try {
            String text = textBox.getText().trim();
            int index = text.indexOf('T');
            if (index != -1) {
                text = text.substring(0, index);
            }

            return dateTimeFormat.parse(text);
        } catch (final Exception e) {
            // Ignore if we couldn't parse.
        }

        return null;
    }

    public void hideDatePicker() {
        popup.hide();
    }

//    private void setValue(Date oldDate, Date date, boolean fireEvents, boolean updateText) {
//        if (date != null) {
//            datePicker.setCurrentMonth(date);
//        }
//        datePicker.setValue(date, false);
//
//        if (updateText) {
//            format.reset(this, false);
//            textBox.setText(getFormat().format(this, date));
//        }
//
//        if (fireEvents) {
//            DateChangeEvent.fireIfNotEqualDates(this, oldDate, date);
//        }
//    }
//
//    private void updateDateFromTextBox() {
//        Date parsedDate = parseDate(true);
//        if (fireNullValues || (parsedDate != null)) {
//            setValue(picker.getValue(), parsedDate, true, false);
//        }
//    }

    private class DateBoxHandler implements ValueChangeHandler<Date>,
            FocusHandler, BlurHandler, ClickHandler, KeyDownHandler,
            CloseHandler<PopupPanel> {

        public void onValueChange(ValueChangeEvent<Date> event) {
            //setValue(parseDate(false), normalize(event.getValue()), true, true);

            String date = dateTimeFormat.format(event.getValue());
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

    public class MyDateTimeFormat extends DateTimeFormat {
        private static final String PATTERN = "yyyy-MM-dd";
        private static final String INNER_PATTERN = "yyyy-MM-dd";
        private DateTimeFormat inner = DateTimeFormat.getFormat(INNER_PATTERN);
        private TimeZone timeZone = TimeZone.createTimeZone(0);

        public MyDateTimeFormat() {
            super(PATTERN, new DateTimeFormatInfoImpl_en_GB());
        }

        @Override
        public Date parse(final String text) throws IllegalArgumentException {
            return inner.parse(text);
        }

        @Override
        public Date parseStrict(final String text) throws IllegalArgumentException {
            return inner.parseStrict(text);
        }

        @Override
        public int parseStrict(final String text, final int start, final Date date) {
            return inner.parseStrict(text, start, date);
        }

        @Override
        public String format(final Date date) {
            return super.format(date, timeZone);
        }
    }
}
