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

package stroom.pipeline.stepping.store;

/**
 * A claim on a set of {@code (element, fingerprint)} versions in a {@link StepDataStore}, held for as long as
 * something is <b>using</b> them - a producer writing them, or a read serving a step from them. While a
 * version is pinned the retention LRU will not evict it; see {@link StepDataStore#pin}.
 * <p>
 * Always release it in a {@code finally} (or use try-with-resources): a leaked pin does not corrupt anything,
 * but it holds a version past the point where it should have been reclaimed, so the store's footprint grows
 * with every edit rather than staying at the retention limit.
 * <p>
 * {@link #close()} is idempotent and declares no checked exception, so try-with-resources over one adds no
 * catch clause.
 */
public final class StorePin implements AutoCloseable {

    private final Runnable release;
    private boolean closed;

    StorePin(final Runnable release) {
        this.release = release;
    }

    @Override
    public void close() {
        // Idempotent: closing twice must not decrement a reference count twice and free a version somebody
        // else is still using.
        if (!closed) {
            closed = true;
            release.run();
        }
    }
}
