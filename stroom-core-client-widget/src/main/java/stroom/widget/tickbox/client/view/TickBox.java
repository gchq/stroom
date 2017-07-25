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

package stroom.widget.tickbox.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.util.shared.HasBooleanValue;

public class TickBox extends Composite
        implements HasValue<TickBoxState>, HasValueChangeHandlers<TickBoxState>, HasBooleanValue {
    private static volatile Binder binder;
    private static volatile Resources resources;
    @UiField
    FlowPanel layout;
    @UiField
    Image image;
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
                    resources = GWT.create(Resources.class);
                    resources.style().ensureInjected();
                }
            }
        }

        initWidget(binder.createAndBindUi(this));
        sinkEvents(Event.MOUSEEVENTS);

        setValue(state);

        if (text != null) {
            lblText = new Label(text, false);
            lblText.setStyleName(resources.style().text());
            layout.add(lblText);
        }
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

            switch (state) {
                case TICK:
                    image.setResource(resources.tick());
                    break;
                case HALF_TICK:
                    image.setResource(resources.halfTick());
                    break;
                case UNTICK:
                    image.setResource(resources.untick());
                    break;
            }

            if (fireEvents) {
                ValueChangeEvent.fire(this, state);
            }
        }
    }

    @Override
    public void onBrowserEvent(final Event event) {
        final Element target = DOM.eventGetTarget(event);

        if (enabled && event.getTypeInt() == Event.ONMOUSEDOWN && event.getButton() == Event.BUTTON_LEFT
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
                lblText.setStyleName(resources.style().text());
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
    }

    public interface Binder extends UiBinder<Widget, TickBox> {
    }

    public interface Style extends CssResource {
        String outer();

        String tickBox();

        String text();
    }

    public interface Resources extends ClientBundle {
        ImageResource tick();

        ImageResource halfTick();

        ImageResource untick();

        @Source("tickbox.css")
        Style style();
    }
}
