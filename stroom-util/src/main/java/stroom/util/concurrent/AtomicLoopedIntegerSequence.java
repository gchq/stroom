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

package stroom.util.concurrent;

import com.google.common.base.Preconditions;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread safe
 */
public class AtomicLoopedIntegerSequence {

    private final int startInc;
    private final int endExc;
    private final AtomicInteger lastVal = new AtomicInteger();

    public AtomicLoopedIntegerSequence(final int endExc) {
        this(0, endExc);
    }

    public AtomicLoopedIntegerSequence(final int startInc, final int endExc) {
        Preconditions.checkArgument(endExc > startInc);

        this.startInc = startInc;
        this.endExc = endExc;
        this.lastVal.set(startInc - 1);
    }

    public int getNext() {
        return lastVal.updateAndGet(val -> {
            int newVal = val + 1;

            if (newVal >= endExc) {
                newVal = startInc;
            }
            return newVal;
        });
    }

    @Override
    public String toString() {
        return "AtomicLoopedIntegerSequence{" +
                "startInc=" + startInc +
                ", endExc=" + endExc +
                ", lastVal=" + lastVal +
                '}';
    }
}
