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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Added sequencer that handles wrapping so AtomicSequence(3) would give you
 * back 0,1,2,0,1,2 etc. or you can a variable limit as = new AtomicSequence();
 * as.next(3); as.next(3);
 */
public class AtomicSequence {

    private final AtomicLong sequence = new AtomicLong();
    private final int limit;

    public AtomicSequence() {
        this(Integer.MAX_VALUE);
    }

    /**
     * @param limit Exclusive
     */
    public AtomicSequence(final int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be greater than zero ");
        }
        this.limit = limit;
    }

    /**
     * @return the next sequence within the default limit
     */
    public int next() {
        return next(limit);
    }

    /**
     * @return return a sequence within the limit
     */
    public int next(final int limit) {
        for (; ; ) {
            final long current = sequence.get();

            if (current > limit) {
                final long next = current - limit + 1;
                if (sequence.compareAndSet(current, next)) {
                    return (int) current % limit;
                }
            } else {
                final long next = current + 1;
                if (sequence.compareAndSet(current, next)) {
                    return (int) current % limit;
                }
            }
        }
    }

    /**
     * Reset the sequence
     */
    public void reset() {
        sequence.set(0);
    }
}
