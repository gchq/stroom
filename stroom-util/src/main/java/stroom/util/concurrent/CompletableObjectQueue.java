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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

public class CompletableObjectQueue<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CompletableObjectQueue.class);
    private final CompletableQueue<T> queue;

    public CompletableObjectQueue(final int capacity) {
        queue = new CompletableQueue<>(capacity);
    }

    public void put(final T value) {
        try {
            queue.put(value);
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    public T take() {
        try {
            return queue.take();
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        } catch (final CompleteException e) {
            LOGGER.trace("Complete");
        }
        return null;
    }

    public void complete() {
        queue.complete();
    }

    public void terminate() {
        queue.terminate();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
