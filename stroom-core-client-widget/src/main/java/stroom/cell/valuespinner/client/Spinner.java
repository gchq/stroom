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

package stroom.cell.valuespinner.client;

import stroom.cell.valuespinner.shared.HasSpinnerConstraints;

import com.google.gwt.dom.client.InputElement;
import com.google.gwt.user.client.Timer;

public class Spinner {

    private static final int INITIAL_SPEED = 7;
    private int step = 1;
    private int maxStep = 99;
    private int initialSpeed = INITIAL_SPEED;
    private long initialValue = 0;
    private long value = 0;
    private long min = 0;
    private long max = 100;
    private boolean increment;
    private final boolean constrained = true;
    private InputElement input;
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
    private boolean spinning;

    public void start(final HasSpinnerConstraints constraints, final boolean increment, final long value,
                      final InputElement input) {
        this.min = constraints.getMin();
        this.max = constraints.getMax();
        this.step = constraints.getStep();
        this.maxStep = constraints.getMaxStep();
        this.increment = increment;
        this.value = value;
        this.input = input;
        this.initialSpeed = INITIAL_SPEED;
        this.initialValue = value;

        spinning = true;
        if (increment) {
            increase();
        } else {
            decrease();
        }
        timer.scheduleRepeating(30);
    }

    public void stop() {
        timer.cancel();
        spinning = false;
    }

    /**
     * Decreases the current value of the spinner by subtracting current step
     */
    private void decrease() {
        value -= step;
        if (constrained && value < min) {
            value = min;
            timer.cancel();
        }
        update();
    }

    /**
     * Increases the current value of the spinner by adding current step
     */
    private void increase() {
        value += step;
        if (constrained && value > max) {
            value = max;
            timer.cancel();
        }
        update();
    }

    private void update() {
        input.setValue(String.valueOf(value));
    }

    public boolean hasChanged() {
        return initialValue != value;
    }

    public boolean isSpinning() {
        return spinning;
    }
}
