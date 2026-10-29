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

import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Timer;

/**
 * The value of a {@link ValueSpinner}, its bounds, and the stepping while a button is held.
 */
public class Spinner implements HasValueChangeHandlers<Long> {

    private static final int INITIAL_SPEED = 7;
    private final HandlerManager handlerManager = new HandlerManager(this);
    private final boolean constrained = true;
    private int step = 1;
    private int minStep = 1;
    private int maxStep = 99;
    private int delta = 1;
    private long value = 0;
    private long min = 0;
    private long max = 100;
    private boolean wrapValues;
    private boolean increment;

    private final Timer timer = new Timer() {
        private int counter = 0;
        private int speed = 7;

        @Override
        public void cancel() {
            super.cancel();
            step = minStep;
            speed = INITIAL_SPEED;
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
                step += delta;
            }
        }
    };
    private boolean enabled = true;

    public Spinner() {
        ValueChangeEvent.fire(this, value);
    }

    /// Steps the value once, then keeps stepping, faster and faster, until [#stopRepeating()] is
    /// called, e.g. while a button is held down. Does nothing while the spinner is disabled.
    ///
    /// @param increment Whether to step up (true) or down (false).
    public void startRepeating(final boolean increment) {
        if (enabled) {
            this.increment = increment;
            if (increment) {
                increase();
            } else {
                decrease();
            }
            timer.scheduleRepeating(30);
        }
    }

    /// Stops the stepping started by [#startRepeating(boolean)].
    public void stopRepeating() {
        timer.cancel();
    }

    /// Takes several steps at once (e.g. for Page Up), stopping at the bounds rather than wrapping.
    ///
    /// @param steps The number of steps, negative to step down.
    public void stepBy(final int steps) {
        moveTo(value + (long) steps * minStep);
    }

    /// Changes the value, kept within the bounds, telling its handlers if it changed.
    ///
    /// @param newValue The value to change it to.
    public void moveTo(final long newValue) {
        final long bounded = Math.max(min, Math.min(max, newValue));
        if (bounded != value) {
            setValue(bounded, true);
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<Long> handler) {
        return handlerManager.addHandler(ValueChangeEvent.getType(), handler);
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
     * @param minStep the minimum step for this spinner
     */
    public void setMinStep(final int minStep) {
        this.minStep = minStep;
        this.step = minStep;
    }

    public void setDelta(final int delta) {
        this.delta = delta;
    }

    public int getDelta() {
        return delta;
    }

    public void setWrapValues(final boolean wrapValues) {
        this.wrapValues = wrapValues;
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
        if (!enabled) {
            timer.cancel();
        }
    }

    /**
     * @param value     sets the current value of this spinner
     * @param fireEvent fires value changed event if set to true
     */
    public void setValue(final long value, final boolean fireEvent) {
        this.value = value;
        if (fireEvent) {
            ValueChangeEvent.fire(this, value);
        }
    }

    /**
     * Increases the current value of the spinner by adding current step
     */
    protected void increase() {
        value += step;
        if (constrained && value > max) {
            if (wrapValues) {
                value = min;
            } else {
                value = max;
                timer.cancel();
            }
        }
        ValueChangeEvent.fire(this, value);
    }

    /**
     * Decreases the current value of the spinner by subtracting current step
     */
    protected void decrease() {
        value -= step;
        if (constrained && value < min) {
            if (wrapValues) {
                value = max;
            } else {
                value = min;
                timer.cancel();
            }
        }
        ValueChangeEvent.fire(this, value);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        handlerManager.fireEvent(event);
    }
}
