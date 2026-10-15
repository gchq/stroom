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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import java.math.BigDecimal;
import java.util.Map;

/// Builds the JSON of the data resource's replies (`DataResource.fetch()`) for the
/// `Widgets/Editors & Viewers/*` stories.
///
/// Plain Java, so the fixtures work both in GWT and on the JVM.
final class DataFixtures {

    /// The path of `DataResource.fetch()`.
    static final String FETCH_PATH = "/data/v1/fetch";

    private DataFixtures() {
        // Static utility
    }

    /// Quotes text as a JSON string.
    ///
    /// @param text The text, or null.
    /// @return The JSON string, or `null`.
    static String quote(final String text) {
        if (text == null) {
            return "null";
        }
        final StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        final String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        sb.append("0000", 0, 4 - hex.length());
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /// The JSON of a `DefaultLocation`.
    ///
    /// @param lineNo The one based line number.
    /// @param colNo  The one based column number.
    /// @return The JSON.
    static String location(final int lineNo, final int colNo) {
        return "{\"type\": \"default\", \"lineNo\": " + lineNo + ", \"colNo\": " + colNo + "}";
    }

    /// The JSON of a `StreamLocation`, as markers have.
    ///
    /// @param partIndex The zero based part index.
    /// @param lineNo    The one based line number.
    /// @param colNo     The one based column number.
    /// @return The JSON.
    static String streamLocation(final int partIndex, final int lineNo, final int colNo) {
        return "{\"type\": \"stream\", \"partIndex\": " + partIndex + ", \"lineNo\": " + lineNo
               + ", \"colNo\": " + colNo + "}";
    }

    /// Gets a number from a request's JSON body, e.g. the requested data range's first character.
    ///
    /// @param body         The request's body.
    /// @param defaultValue The value if there is no such number.
    /// @param path         The names of the nested members, e.g. `sourceLocation`, `dataRange`,
    ///                     `charOffsetFrom`.
    /// @return The number, or the default.
    static long getLong(final String body, final long defaultValue, final String... path) {
        final Object value = get(body, path);
        return value instanceof BigDecimal
                ? ((BigDecimal) value).longValue()
                : defaultValue;
    }

    /// Gets a value from a request's JSON body.
    ///
    /// @param body The request's body.
    /// @param path The names of the nested members.
    /// @return The value ([Map], [java.util.List], [BigDecimal], [String], [Boolean]), or null if
    /// there is none.
    static Object get(final String body, final String... path) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        Object value = JsonValues.parse(body);
        for (final String name : path) {
            if (value instanceof Map) {
                value = ((Map<?, ?>) value).get(name);
            } else {
                return null;
            }
        }
        return value;
    }
}
