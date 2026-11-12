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

package stroom.dataconnection.client.presenter;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/// Turns JDBC connection properties into text for editing, one `name=value` per line, and back.
public final class ConnectionPropertiesText {

    private ConnectionPropertiesText() {
        // Static methods only.
    }

    /// Writes the properties one per line, sorted by name.
    ///
    /// @param properties The properties, or `null`.
    /// @return The text, empty for none.
    public static String format(final Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return "";
        }
        return new TreeMap<>(properties).entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    /// Reads properties written one per line as `name=value`. Blank lines are skipped, names and
    /// values are trimmed, and a line with no `=` is a name with an empty value. A value may hold
    /// `=`, as only the first one separates it from the name.
    ///
    /// @param text The text, or `null`.
    /// @return The properties, in name order.
    public static Map<String, String> parse(final String text) {
        final Map<String, String> properties = new TreeMap<>();
        if (text == null) {
            return properties;
        }
        for (final String line : text.split("\n")) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                final int equals = trimmed.indexOf('=');
                final String name = equals < 0
                        ? trimmed
                        : trimmed.substring(0, equals).trim();
                final String value = equals < 0
                        ? ""
                        : trimmed.substring(equals + 1).trim();
                if (!name.isEmpty()) {
                    properties.put(name, value);
                }
            }
        }
        return properties;
    }
}
