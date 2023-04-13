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

package stroom.widget.valuespinner.client;

import stroom.svg.client.SvgImages;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

/**
 * The {@link Spinner} provide two arrows for in- and decreasing values.
 */
public class Spinner implements HasHandlers {

    private static final int INITIAL_SPEED = 7;
    private final SimplePanel decrementArrow = new SimplePanel();
    private final SimplePanel incrementArrow = new SimplePanel();
    private final EventBus eventBus = new SimpleEventBus();
    private final boolean constrained = true;
    private int step = 1;
    private int minStep = 1;
    private int maxStep = 99;
    private int initialSpeed = INITIAL_SPEED;
    private long value = 0;
    private long min = 0;
    private long max = 100;
    private boolean increment;

    private final Timer timer = new Timer() {
        private int counter = 0;
        private int speed = 7;

        @Override
        public void cancel() {
            super.cancel();
            speed = initialSpeed;
            counter = 0;
        }

        @Override
        public void run() {
            counter++;
            if (speed <= 0 || counter % speed == 0) {
                speed--;
                counter = 0;
                if (increment) {
                    increase();
                } else {
                    decrease();
                }
            }
            if (speed < 0 && step < maxStep) {
                step += 1;
            }
        }
    };
    private boolean enabled = true;
    private final MouseDownHandler mouseDownHandler = new MouseDownHandler() {
        @Override
        public void onMouseDown(final MouseDownEvent event) {
            if (enabled) {
                final Widget sender = (Widget) event.getSource();
                if (sender == incrementArrow) {
                    sender.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowUpPressed");
                    increment = true;
                    increase();
                } else {
                    sender.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowDownPressed");
                    increment = false;
                    decrease();
                }
                timer.scheduleRepeating(30);
            }
        }
    };
    private final MouseOverHandler mouseOverHandler = event -> {
        if (enabled) {
            final Widget sender = (Widget) event.getSource();
            if (sender == incrementArrow) {
                sender.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowUpHover");
            } else {
                sender.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowDownHover");
            }
        }
    };
    private final MouseOutHandler mouseOutHandler = event -> {
        if (enabled) {
            cancelTimer((Widget) event.getSource());
        }
    };
    private final MouseUpHandler mouseUpHandler = event -> {
        if (enabled) {
            cancelTimer((Widget) event.getSource());
        }
    };

    public Spinner() {
        this.initialSpeed = INITIAL_SPEED;
        incrementArrow.getElement().setInnerHTML(SvgImages.MONO_ARROW_UP);
        incrementArrow.addDomHandler(mouseUpHandler, MouseUpEvent.getType());
        incrementArrow.addDomHandler(mouseDownHandler, MouseDownEvent.getType());
        incrementArrow.addDomHandler(mouseOverHandler, MouseOverEvent.getType());
        incrementArrow.addDomHandler(mouseOutHandler, MouseOutEvent.getType());
        incrementArrow.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowUp");
        decrementArrow.getElement().setInnerHTML(SvgImages.MONO_ARROW_DOWN);
        decrementArrow.addDomHandler(mouseUpHandler, MouseUpEvent.getType());
        decrementArrow.addDomHandler(mouseDownHandler, MouseDownEvent.getType());
        decrementArrow.addDomHandler(mouseOverHandler, MouseOverEvent.getType());
        decrementArrow.addDomHandler(mouseOutHandler, MouseOutEvent.getType());
        decrementArrow.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowDown");

        SpinnerEvent.fire(this, value);
    }

    public HandlerRegistration addSpinnerHandler(final SpinnerEvent.Handler handler) {
        return eventBus.addHandler(SpinnerEvent.getType(), handler);
    }

    /**
     * @return the image representing the decreasing arrow
     */
    public SimplePanel getDecrementArrow() {
        return decrementArrow;
    }

    /**
     * @return the image representing the increasing arrow
     */
    public SimplePanel getIncrementArrow() {
        return incrementArrow;
    }

    /**
     * @return the maximum value
     */
    public long getMax() {
        return max;
    }

    /**
     * @param max the maximum value. Will not have any effect if constrained is
     *            set to false
     */
    public void setMax(final long max) {
        this.max = max;
    }

    /**
     * @return the maximum spinner step
     */
    public int getMaxStep() {
        return maxStep;
    }

    /**
     * @param maxStep the maximum step for this spinner
     */
    public void setMaxStep(final int maxStep) {
        this.maxStep = maxStep;
    }

    /**
     * @return the minimum value
     */
    public long getMin() {
        return min;
    }

    /**
     * @param min the minimum value. Will not have any effect if constrained is
     *            set to false
     */
    public void setMin(final long min) {
        this.min = min;
    }

    /**
     * @return the minimum spinner step
     */
    public int getMinStep() {
        return minStep;
    }

    /**
     * @param minStep the minimum step for this spinner
     */
    public void setMinStep(final int minStep) {
        this.minStep = minStep;
    }

    /**
     * @return the current value
     */
    public long getValue() {
        return value;
    }

    /**
     * @return true is min and max values are active, false if not
     */
    public boolean isConstrained() {
        return constrained;
    }

    /**
     * @return Gets whether this widget is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this widget is enabled.
     *
     * @param enabled true to enable the widget, false to disable it
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            incrementArrow.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowUp");
            decrementArrow.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowDown");
        } else {
            incrementArrow.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowUpDisabled");
            decrementArrow.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowDownDisabled");
        }
        if (!enabled) {
            timer.cancel();
        }
    }

    /**
     * @param initialSpeed the initial speed of the spinner. Higher values mean lower
     *                     speed, default value is 7
     */
    public void setInitialSpeed(final int initialSpeed) {
        this.initialSpeed = initialSpeed;
    }

    /**
     * @param value     sets the current value of this spinner
     * @param fireEvent fires value changed event if set to true
     */
    public void setValue(final long value, final boolean fireEvent) {
        this.value = value;
        if (fireEvent) {
            SpinnerEvent.fire(this, value);
        }
    }

    /**
     * Decreases the current value of the spinner by subtracting current step
     */
    protected void decrease() {
        value -= step;
        if (constrained && value < min) {
            value = min;
            timer.cancel();
        }
        SpinnerEvent.fire(this, value);
    }

    /**
     * Increases the current value of the spinner by adding current step
     */
    protected void increase() {
        value += step;
        if (constrained && value > max) {
            value = max;
            timer.cancel();
        }
        SpinnerEvent.fire(this, value);
    }

    private void cancelTimer(final Widget sender) {
        step = minStep;
        if (sender == incrementArrow) {
            sender.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowUp");
        } else {
            sender.getElement().setClassName("valueSpinner-arrow valueSpinner-arrowDown");
        }
        timer.cancel();
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
