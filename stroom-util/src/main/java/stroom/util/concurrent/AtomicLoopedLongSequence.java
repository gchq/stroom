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

import java.util.concurrent.atomic.AtomicLong;

public class AtomicLoopedLongSequence {

    private final long startInc;
    private final long endExc;
    private final AtomicLong lastVal = new AtomicLong();

    public AtomicLoopedLongSequence(final long endExc) {
        this(0, endExc);
    }

    public AtomicLoopedLongSequence(final long startInc, final long endExc) {
        Preconditions.checkArgument(endExc > startInc);

        this.startInc = startInc;
        this.endExc = endExc;
        this.lastVal.set(startInc - 1L);
    }

    public long getNext() {
        return lastVal.updateAndGet(val -> {
            long newVal = val + 1;

            if (newVal >= endExc) {
                newVal = startInc;
            }
            return newVal;
        });
    }

    @Override
    public String toString() {
        return "AtomicLoopedLongSequence{" +
                "startInc=" + startInc +
                ", endExc=" + endExc +
                ", lastVal=" + lastVal +
                '}';
    }
}
