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

package stroom.floormap.client.value;

/// An opaque wrapper around a parsed value, which may be backed by either
/// a GWT `JSONObject` (for JSON format), a GWT XML `Document`
/// (for XML format), or any other implementation-specific object (e.g. a
/// `Map` in tests).
///
/// Consumers should never cast or inspect the underlying object directly.
/// Instead, all access should go through a [ValueAccessor], which
/// provides format-independent read/write operations.
///
/// Holds no GWT types itself, only an opaque reference, so it can be used in unit tests on the JVM.
///
/// @see ValueAccessor
public final class ParsedValue {

    private final Object backing;

    public ParsedValue(final Object backing) {
        this.backing = backing;
    }

    /// Returns the underlying backing object.
    ///
    /// Only [ValueAccessor] implementations should call this.
    ///
    /// @return the backing object (never `null`)
    public Object getBacking() {
        return backing;
    }
}
