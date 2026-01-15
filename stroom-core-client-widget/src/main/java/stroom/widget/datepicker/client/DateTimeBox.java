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

package stroom.widget.datepicker.client;

import stroom.item.client.EventBinder;
import stroom.svg.client.SvgIconBox;
import stroom.svg.shared.SvgImage;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Provider;

import java.util.Objects;

public class DateTimeBox
        extends Composite
        implements Focus, HasValueChangeHandlers<String> {

    private Provider<DateTimePopup> popupProvider;
    private final TextBox textBox;
    private final SvgIconBox svgIconBox;
    private String stringValue;
    private Long longValue;
    private DateTimePopup popup;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(svgIconBox.addClickHandler(event -> showPopup()));
            registerHandler(textBox.addKeyDownHandler(event -> {
                final int keyCode = event.getNativeKeyCode();
                if (KeyCodes.KEY_ENTER == keyCode) {
                    showPopup();
                }
            }));
            registerHandler(textBox.addValueChangeHandler(event -> {
                stringValue = textBox.getValue();
                ValueChangeEvent.fire(DateTimeBox.this, stringValue);
            }));
            registerHandler(textBox.addBlurHandler(event -> onBlur()));
            registerHandler(textBox.addFocusHandler(event -> onFocus()));
        }
    };

    private DateTimePopup getPopup() {
        if (popup == null && popupProvider != null) {
            popup = popupProvider.get();
            popup.setTime(System.currentTimeMillis());
        }
        return popup;
    }

    public DateTimeBox() {
        textBox = new TextBox();
        textBox.addStyleName("ScheduleBox-textBox stroom-control allow-focus");

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName("ScheduleBox");
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

    private void showPopup() {
        final DateTimePopup popup = getPopup();
        if (popup != null) {
            final UTCDate date = UTCDate.create(textBox.getValue());
            if (date != null) {
                popup.setTime((long) date.getTime());
            } else {
                if (longValue == null) {
                    popup.setTime(System.currentTimeMillis());
                } else {
                    popup.setTime(longValue);
                }
            }
            popup.show(newValue -> {
                if (!Objects.equals(longValue, newValue)) {
                    setValue(newValue, true);
                }
            });
        }
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
    }

    public void setName(final String name) {
        textBox.setName(name);
    }

    public void setEnabled(final boolean enabled) {
        textBox.setEnabled(enabled);
    }

    private Long parse(final String text) {
        final UTCDate date = UTCDate.create(text);
        if (date != null) {
            return (long) date.getTime();
        }
        return null;
    }

    public Long getValue() {
        return parse(textBox.getValue());
    }

    public void setValue(final Long value) {
        setValue(value, false);
    }

    public void setValue(final Long value, final boolean fireEvents) {
        if (value != null) {
            this.longValue = value;
            this.stringValue = getPopup().getDateTimeModel().formatIso(UTCDate.create(value));
        } else {
            this.longValue = null;
            this.stringValue = null;
        }
        textBox.setValue(stringValue);
        textBox.getElement().removeClassName("invalid");
        if (fireEvents) {
            ValueChangeEvent.fire(this, null);
        }
    }

    public boolean isValid() {
        if (stringValue == null) {
            return true;
        }
        final Long ms = parse(stringValue);
        return ms != null;
    }

    private void onFocus() {
        textBox.getElement().removeClassName("invalid");
    }

    private void onBlur() {
        if (isValid()) {
            textBox.getElement().removeClassName("invalid");
        } else {
            textBox.getElement().addClassName("invalid");
        }
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<String> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    public void setPopupProvider(final Provider<DateTimePopup> popupProvider) {
        this.popupProvider = popupProvider;
    }
}
