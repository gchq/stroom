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

package stroom.dashboard.client.input;

import stroom.dashboard.client.input.TextInputPresenter.TextInputView;
import stroom.dashboard.shared.TextInputFormat;
import stroom.svg.client.SvgIconBox;
import stroom.svg.shared.SvgImage;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.datepicker.client.UTCDate;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class TextInputViewImpl extends ViewWithUiHandlers<TextInputUiHandlers> implements TextInputView {

    private static final String INVALID_CLASS_NAME = "invalid";
    private static final String NO_ICON_CLASS_NAME = "TextInputView-noIcon";
    // Matches an offset without a colon at the end of a date-time, e.g. +1000
    private static final String OFFSET_WITHOUT_COLON_REGEX = "([+-]\\d{2})(\\d{2})$";

    private final Widget widget;
    private final Provider<DateTimePopup> dateTimePopupProvider;
    private final TextBox value;

    @UiField
    SvgIconBox iconBox;

    private boolean dateTime;

    @Inject
    public TextInputViewImpl(final Binder binder,
                             final Provider<DateTimePopup> dateTimePopupProvider) {
        this.dateTimePopupProvider = dateTimePopupProvider;
        widget = binder.createAndBindUi(this);

        value = new TextBox();
        value.addStyleName("w-100 allow-focus");
        value.addKeyUpHandler(event -> fireValueChanged());

        // The calendar icon is only shown for the date/time format.
        iconBox.setWidget(value, SvgImage.CALENDAR);
        iconBox.addClickHandler(event -> showDateTimePopup());
        setFormat(TextInputFormat.TEXT);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setValue(final String value) {
        this.value.setValue(value);
    }

    @Override
    public String getValue() {
        return value.getValue();
    }

    @Override
    public void setFormat(final TextInputFormat format) {
        dateTime = format == TextInputFormat.DATE_TIME;
        iconBox.setReadonly(!dateTime);
        if (dateTime) {
            iconBox.removeStyleName(NO_ICON_CLASS_NAME);
        } else {
            iconBox.addStyleName(NO_ICON_CLASS_NAME);
        }
    }

    @Override
    public void setValid(final boolean valid) {
        if (valid) {
            value.removeStyleName(INVALID_CLASS_NAME);
        } else {
            value.addStyleName(INVALID_CLASS_NAME);
        }
    }

    private void showDateTimePopup() {
        if (dateTime) {
            final DateTimePopup popup = dateTimePopupProvider.get();
            // Start the picker at the current absolute date if there is one, else at now.
            final UTCDate date = UTCDate.create(value.getValue());
            popup.setTime(date != null
                    ? (long) date.getTime()
                    : System.currentTimeMillis());
            popup.show(ms -> {
                // The picker writes offsets like +1000, but the server only parses +10:00.
                final String isoDateTime = popup.getDateTimeModel().formatIso(UTCDate.create(ms));
                value.setValue(isoDateTime.replaceFirst(OFFSET_WITHOUT_COLON_REGEX, "$1:$2"));
                fireValueChanged();
            });
        }
    }

    private void fireValueChanged() {
        if (getUiHandlers() != null) {
            getUiHandlers().onValueChanged(value.getText());
        }
    }

    public interface Binder extends UiBinder<Widget, TextInputViewImpl> {

    }
}
