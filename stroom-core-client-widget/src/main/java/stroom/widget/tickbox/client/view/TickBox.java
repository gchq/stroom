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

package stroom.widget.tickbox.client.view;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.util.shared.HasBooleanValue;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class TickBox extends Composite
        implements HasValue<TickBoxState>, HasValueChangeHandlers<TickBoxState>, HasBooleanValue, Focus {

    private static volatile Binder binder;
    @UiField
    FlowPanel layout;
    @UiField
    Button image;
    Label lblText;
    private TickBoxState state = TickBoxState.TICK;
    private boolean enabled = true;

    public TickBox() {
        this(TickBoxState.UNTICK, null);
    }

    public TickBox(final String text) {
        this(TickBoxState.UNTICK, text);
    }

    public TickBox(final TickBoxState state, final String text) {
        if (binder == null) {
            synchronized (TickBox.class) {
                if (binder == null) {
                    binder = GWT.create(Binder.class);
                }
            }
        }

        initWidget(binder.createAndBindUi(this));
        sinkEvents(Event.MOUSEEVENTS);

        setValue(state);

        if (text != null) {
            lblText = new Label(text, false);
            lblText.setStyleName("tickBox-text");
            layout.add(lblText);
        }

        image.getElement().setAttribute("role", "tickbox");
    }

    @Override
    public void focus() {
        image.setFocus(true);
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<TickBoxState> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    @Override
    public Boolean getBooleanValue() {
        if (TickBoxState.TICK.equals(state)) {
            return true;
        }
        if (TickBoxState.UNTICK.equals(state)) {
            return false;
        }
        return null;
    }

    @Override
    public void setBooleanValue(final Boolean value) {
        if (value != null) {
            setBooleanValue(value, false);
        }
    }

    public void setBooleanValue(final boolean value, final boolean fireEvents) {
        if (value) {
            setValue(TickBoxState.TICK, fireEvents);
        } else {
            setValue(TickBoxState.UNTICK, fireEvents);
        }
    }

    @Override
    public TickBoxState getValue() {
        return state;
    }

    @Override
    public void setValue(final TickBoxState value) {
        setValue(value, false);
    }

    @Override
    public void setValue(final TickBoxState value, final boolean fireEvents) {
        if (this.state != value) {
            this.state = value;

            updateImage();

            if (fireEvents) {
                ValueChangeEvent.fire(this, state);
            }
        }
    }

    private void updateImage() {
        switch (state) {
            case TICK:
                if (enabled) {
                    image.getElement().setClassName("form-check-input flatButton tickBox-tick");
                } else {
                    image.getElement().setClassName("form-check-input flatButton tickBox-tick tickBox-disabled");
                }
                break;
            case HALF_TICK:
                if (enabled) {
                    image.getElement().setClassName("form-check-input flatButton tickBox-halfTick");
                } else {
                    image.getElement().setClassName("form-check-input flatButton tickBox-halfTick tickBox-dDisabled");
                }
                break;
            case UNTICK:
                if (enabled) {
                    image.getElement().setClassName("form-check-input flatButton tickBox-untick");
                } else {
                    image.getElement().setClassName("form-check-input flatButton tickBox-untick tickBox-disabled");
                }
                break;
        }
    }

    @Override
    public void onBrowserEvent(final Event event) {
        final Element target = DOM.eventGetTarget(event);

        if (enabled && event.getTypeInt() == Event.ONMOUSEDOWN
                && MouseUtil.isPrimary(event)
                && getElement().isOrHasChild(target)) {
            if (state == TickBoxState.TICK) {
                setValue(TickBoxState.UNTICK, true);
            } else {
                setValue(TickBoxState.TICK, true);
            }
        }
    }

    public String getText() {
        if (lblText == null) {
            return null;
        }

        return lblText.getText();
    }

    public void setText(final String text) {
        if (text != null) {
            if (lblText == null) {
                lblText = new Label(text, false);
                lblText.setStyleName("tickBox-text");
                layout.add(lblText);
            } else {
                lblText.setText(text);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        updateImage();
    }

    public interface Binder extends UiBinder<Widget, TickBox> {

    }
}
