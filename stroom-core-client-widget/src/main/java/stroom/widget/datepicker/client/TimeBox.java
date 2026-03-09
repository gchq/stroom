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
import stroom.util.shared.time.Time;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Provider;

import java.util.Objects;

public class TimeBox
        extends Composite
        implements Focus, HasValueChangeHandlers<String> {

    private Provider<TimePopup> popupProvider;
    private final TextBox textBox;
    private final SvgIconBox svgIconBox;
    private Time value;
    private TimePopup popup;

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
                value = Time.parse(textBox.getValue());
                ValueChangeEvent.fire(TimeBox.this, textBox.getValue());
            }));
            registerHandler(textBox.addBlurHandler(event -> onBlur()));
            registerHandler(textBox.addFocusHandler(event -> onFocus()));
        }
    };

    private TimePopup getPopup() {
        if (popup == null && popupProvider != null) {
            popup = popupProvider.get();
            popup.setTime(Time.ZERO);
        }
        return popup;
    }

    public TimeBox() {
        textBox = new TextBox();
        textBox.addStyleName("ScheduleBox-textBox stroom-control allow-focus");

        svgIconBox = new SvgIconBox();
        svgIconBox.addStyleName("ScheduleBox");
        svgIconBox.setWidget(textBox, SvgImage.HISTORY);

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
        final TimePopup popup = getPopup();
        if (popup != null) {
            final Time time = Time.parse(textBox.getValue());
            popup.setTime(time);
            popup.show(newValue -> {
                if (!Objects.equals(time, newValue)) {
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

    public Time getValue() {
        return Time.parse(textBox.getValue());
    }

    public void setValue(final Time value) {
        setValue(value, false);
    }

    public void setValue(final Time value, final boolean fireEvents) {
        if (value != null) {
            this.value = value;
        } else {
            this.value = Time.ZERO;
        }
        textBox.setValue(this.value.toString());
        textBox.getElement().removeClassName("invalid");
        if (fireEvents) {
            ValueChangeEvent.fire(this, null);
        }
    }

    public boolean isValid() {
        return Time.isValid(textBox.getValue());
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

    public void setHourVisible(final boolean visible) {
        getPopup().getView().setHourVisible(visible);
    }

    public void setMinuteVisible(final boolean visible) {
        getPopup().getView().setMinuteVisible(visible);
    }

    public void setSecondVisible(final boolean visible) {
        getPopup().getView().setSecondVisible(visible);
    }

    @Override
    public com.google.gwt.event.shared.HandlerRegistration addValueChangeHandler(
            final ValueChangeHandler<String> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    public void setPopupProvider(final Provider<TimePopup> popupProvider) {
        this.popupProvider = popupProvider;
    }
}
