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

package stroom.cell.valuespinner.shared;

public class ConstrainedLong extends EditableLong implements Editable, HasSpinnerConstraints {

    private static final long serialVersionUID = -5468699731097143387L;

    private int step = 1;
    private int maxStep = 99;
    private long min = 0;
    private long max = 100;

    public ConstrainedLong() {
    }

    public ConstrainedLong(final int step, final int maxStep, final long value, final long min, final long max,
                           final boolean editable) {
        super(value);
        this.step = step;
        this.maxStep = maxStep;
        this.min = min;
        this.max = max;
        setEditable(editable);
    }

    public ConstrainedLong(final long value, final long min, final long max, final boolean editable) {
        super(value);
        this.min = min;
        this.max = max;
        setEditable(editable);
    }

    public ConstrainedLong(final long value, final long min, final long max) {
        this(value, min, max, true);
    }

    @Override
    public int getStep() {
        return step;
    }

    @Override
    public int getMaxStep() {
        return maxStep;
    }

    @Override
    public long getMin() {
        return min;
    }

    @Override
    public long getMax() {
        return max;
    }
}
