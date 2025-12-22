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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * This class provides an object and updates the object asynchronously in the background periodically.
 */
public class AsyncReference<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncReference.class);

    private final Function<T, T> updateFunction;
    private final Duration updateInterval;
    private final Executor executor;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile T object;
    private volatile Instant lastRefresh;

    public AsyncReference(final Function<T, T> updateFunction,
                          final Duration updateInterval,
                          final Executor executor) {
        this.updateFunction = updateFunction;
        this.updateInterval = updateInterval;
        this.executor = executor;
    }

    public T get() {
        T object = this.object;
        if (object == null) {
            // If object is currently null then we will lock so that all threads requiring the object are blocked until
            // the first one creates it.
            synchronized (this) {
                object = this.object;
                if (object == null) {
                    object = update();
                    this.object = object;
                }
            }
        } else {
            // Check to see if the referenced object needs to be refreshed.
            final Instant lastRefresh = this.lastRefresh;
            if (lastRefresh == null || Instant.now().isAfter(lastRefresh.plus(updateInterval))) {
                updateAsync();
            }
        }

        return object;
    }

    public void clear() {
        object = null;
        lastRefresh = null;
    }

    private void updateAsync() {
        if (refreshing.compareAndSet(false, true)) {
            executor.execute(() -> {
                try {
                    object = update();
                } finally {
                    refreshing.set(false);
                }
            });
        }
    }

    private synchronized T update() {
        try {
            // Refresh the object.
            return updateFunction.apply(object);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        } finally {
            lastRefresh = Instant.now();
        }
    }
}
