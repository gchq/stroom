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

import com.google.gwt.core.client.GWT;
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
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

/**
 * The {@link Spinner} provide two arrows for in- and decreasing values.
 */
public class Spinner implements HasHandlers {
    /**
     * Default resources for spinning arrows.
     */
    public interface SpinnerResources extends ClientBundle {
        ImageResource arrowDown();

        ImageResource arrowDownDisabled();

        ImageResource arrowDownHover();

        ImageResource arrowDownPressed();

        ImageResource arrowUp();

        ImageResource arrowUpDisabled();

        ImageResource arrowUpHover();

        ImageResource arrowUpPressed();
    }

    private static final int INITIAL_SPEED = 7;
    private static SpinnerResources images = GWT.create(SpinnerResources.class);

    private final Image decrementArrow = new Image();
    private final Image incrementArrow = new Image();

    private final EventBus eventBus = new SimpleEventBus();
    private int step = 1;
    private int minStep = 1;
    private int maxStep = 99;
    private int initialSpeed = INITIAL_SPEED;
    private long value = 0;
    private long min = 0;
    private long max = 100;
    private boolean increment;
    private final boolean constrained = true;
    private boolean enabled = true;

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

    private final MouseDownHandler mouseDownHandler = new MouseDownHandler() {
        @Override
        public void onMouseDown(final MouseDownEvent event) {
            if (enabled) {
                final Image sender = (Image) event.getSource();
                if (sender == incrementArrow) {
                    sender.setResource(images.arrowUpPressed());
                    increment = true;
                    increase();
                } else {
                    sender.setResource(images.arrowDownPressed());
                    increment = false;
                    decrease();
                }
                timer.scheduleRepeating(30);
            }
        }
    };

    private final MouseOverHandler mouseOverHandler = new MouseOverHandler() {
        @Override
        public void onMouseOver(final MouseOverEvent event) {
            if (enabled) {
                final Image sender = (Image) event.getSource();
                if (sender == incrementArrow) {
                    sender.setResource(images.arrowUpHover());
                } else {
                    sender.setResource(images.arrowDownHover());
                }
            }
        }
    };

    private final MouseOutHandler mouseOutHandler = new MouseOutHandler() {
        @Override
        public void onMouseOut(final MouseOutEvent event) {
            if (enabled) {
                cancelTimer((Widget) event.getSource());
            }
        }
    };

    private final MouseUpHandler mouseUpHandler = new MouseUpHandler() {
        @Override
        public void onMouseUp(final MouseUpEvent event) {
            if (enabled) {
                cancelTimer((Widget) event.getSource());
            }
        }
    };

    public Spinner() {
        this.initialSpeed = INITIAL_SPEED;
        incrementArrow.addMouseUpHandler(mouseUpHandler);
        incrementArrow.addMouseDownHandler(mouseDownHandler);
        incrementArrow.addMouseOverHandler(mouseOverHandler);
        incrementArrow.addMouseOutHandler(mouseOutHandler);
        incrementArrow.setResource(images.arrowUp());
        decrementArrow.addMouseUpHandler(mouseUpHandler);
        decrementArrow.addMouseDownHandler(mouseDownHandler);
        decrementArrow.addMouseOverHandler(mouseOverHandler);
        decrementArrow.addMouseOutHandler(mouseOutHandler);
        decrementArrow.setResource(images.arrowDown());

        SpinnerEvent.fire(this, value);
    }

    public HandlerRegistration addSpinnerHandler(final SpinnerEvent.Handler handler) {
        return eventBus.addHandler(SpinnerEvent.getType(), handler);
    }

    /**
     * @return the image representing the decreasing arrow
     */
    public Image getDecrementArrow() {
        return decrementArrow;
    }

    /**
     * @return the image representing the increasing arrow
     */
    public Image getIncrementArrow() {
        return incrementArrow;
    }

    /**
     * @return the maximum value
     */
    public long getMax() {
        return max;
    }

    /**
     * @return the maximum spinner step
     */
    public int getMaxStep() {
        return maxStep;
    }

    /**
     * @return the minimum value
     */
    public long getMin() {
        return min;
    }

    /**
     * @return the minimum spinner step
     */
    public int getMinStep() {
        return minStep;
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
     * @param enabled
     *            true to enable the widget, false to disable it
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            incrementArrow.setResource(images.arrowUp());
            decrementArrow.setResource(images.arrowDown());
        } else {
            incrementArrow.setResource(images.arrowUpDisabled());
            decrementArrow.setResource(images.arrowDownDisabled());
        }
        if (!enabled) {
            timer.cancel();
        }
    }

    /**
     * @param initialSpeed
     *            the initial speed of the spinner. Higher values mean lower
     *            speed, default value is 7
     */
    public void setInitialSpeed(final int initialSpeed) {
        this.initialSpeed = initialSpeed;
    }

    /**
     * @param max
     *            the maximum value. Will not have any effect if constrained is
     *            set to false
     */
    public void setMax(final long max) {
        this.max = max;
    }

    /**
     * @param maxStep
     *            the maximum step for this spinner
     */
    public void setMaxStep(final int maxStep) {
        this.maxStep = maxStep;
    }

    /**
     * @param min
     *            the minimum value. Will not have any effect if constrained is
     *            set to false
     */
    public void setMin(final long min) {
        this.min = min;
    }

    /**
     * @param minStep
     *            the minimum step for this spinner
     */
    public void setMinStep(final int minStep) {
        this.minStep = minStep;
    }

    /**
     * @param value
     *            sets the current value of this spinner
     * @param fireEvent
     *            fires value changed event if set to true
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
            ((Image) sender).setResource(images.arrowUp());
        } else {
            ((Image) sender).setResource(images.arrowDown());
        }
        timer.cancel();
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
