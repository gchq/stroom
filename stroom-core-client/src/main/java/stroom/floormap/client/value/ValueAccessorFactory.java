/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.floormap.shared.ValueFormat;

/// GWT client-side factory for obtaining [ValueAccessor] implementations
/// based on the configured [ValueFormat].
///
/// A factory rather than a method on the [ValueAccessor] interface itself:
/// it lives in the client package because the concrete implementations
/// ([JsonValueAccessor], [XmlValueAccessor]) depend on GWT libraries,
/// which the shared interface must not.
public final class ValueAccessorFactory {

    private ValueAccessorFactory() {
        // Utility class
    }

    /// Returns the appropriate [ValueAccessor] for the given format.
    ///
    /// @param format the value format; must not be `null`
    /// @return the accessor instance (singleton)
    public static ValueAccessor forFormat(final ValueFormat format) {
        return switch (format) {
            case JSON -> JsonValueAccessor.INSTANCE;
            case XML -> XmlValueAccessor.INSTANCE;
        };
    }
}
