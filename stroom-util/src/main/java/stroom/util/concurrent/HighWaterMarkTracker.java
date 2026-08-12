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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Used for tracking the high-water-mark of concurrent operations.
 * Threads should call increment before doing some concurrent operation
 * then decrement when they have finished.
 */
public class HighWaterMarkTracker {

    private final AtomicReference<State> state = new AtomicReference<>(
            new State(0, 0));

    /**
     * Increment the number of concurrent things happening, i.e. call it before doing
     * the concurrent activity.
     */
    public void increment() {
        state.updateAndGet(currentState -> {
            final int newConcurrentCount = currentState.concurrentCount + 1;
            return new State(
                    newConcurrentCount,
                    Math.max(newConcurrentCount, currentState.highWaterMark));
        });
    }

    /**
     * Decrement the number of concurrent things happening, i.e. call it after completing the
     * concurrent activity
     */
    public void decrement() {
        state.updateAndGet(currentState ->
                new State(currentState.concurrentCount - 1, currentState.highWaterMark));
    }

    /**
     * Do concurrent work while keeping track of the maximum number of threads performing work
     * at once.
     */
    public <T> T getWithHighWaterMarkTracking(final Supplier<T> work) {
        increment();
        try {
            return work.get();
        } finally {
            decrement();
        }
    }

    /**
     * Do concurrent work while keeping track of the maximum number of threads performing work
     * at once.
     */
    public void doWithHighWaterMarkTracking(final Runnable work) {
        increment();
        try {
            work.run();
        } finally {
            decrement();
        }
    }

    /**
     * @return The highest number of concurrent things seen so far
     */
    public int getHighWaterMark() {
        return state.get().highWaterMark;
    }

    /**
     * @return Number of concurrent things now.
     */
    public int getCurrentCount() {
        return state.get().concurrentCount;
    }

    /**
     * Manually record a snapshot of the current count.
     *
     * @param concurrentCount The current count
     */
    public void setCurrentCount(final int concurrentCount) {
        state.updateAndGet(currentState ->
                new State(
                        concurrentCount,
                        Math.max(concurrentCount, currentState.highWaterMark)));
    }

    @Override
    public String toString() {
        final State currentState = state.get();
        return "HighWaterMarkTracker{" +
               "concurrentCount=" + currentState.concurrentCount +
               ", highWaterMark=" + currentState.highWaterMark +
               '}';
    }


    // --------------------------------------------------------------------------------


    private record State(int concurrentCount, int highWaterMark) {

    }
}
