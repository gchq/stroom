/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.language.functions.ref;

/// The first and last time windows that hold any real row of a query's results, by window key.
///
/// A function such as `lag` has rows copied into windows other than their own, which can make rows
/// for windows before the first one with data or after the last. Those windows are outside the range,
/// so their rows are not shown, and a function that reads one of them has no value to give.
///
/// A result store sets the range before it reads rows, and the functions compiled from the same
/// [ValueReferenceIndex] see it. Until it is set every window is in range.
public final class WindowRange {

    private static final Bounds UNBOUNDED = new Bounds(Long.MIN_VALUE, Long.MAX_VALUE);

    // Both ends are held in one value so that a reader never sees one end of an old range with the
    // other end of a new one.
    private volatile Bounds bounds = UNBOUNDED;

    /// Sets the range.
    ///
    /// @param firstWindowKey The key of the earliest window with a real row.
    /// @param lastWindowKey  The key of the latest window with a real row.
    public void set(final long firstWindowKey, final long lastWindowKey) {
        bounds = new Bounds(firstWindowKey, lastWindowKey);
    }

    /// Makes every window in range, as when nothing is known of the rows.
    public void clear() {
        bounds = UNBOUNDED;
    }

    /// Gets whether a window is in the range.
    ///
    /// @param windowKey The key of the window.
    /// @return True if it is no earlier than the first window and no later than the last.
    public boolean contains(final long windowKey) {
        final Bounds current = bounds;
        return windowKey >= current.first && windowKey <= current.last;
    }


    // --------------------------------------------------------------------------------


    private record Bounds(long first, long last) {

    }
}
