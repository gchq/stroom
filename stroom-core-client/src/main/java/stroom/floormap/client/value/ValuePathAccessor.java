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

import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;

/// GWT-compatible utility for reading and writing values in a [JSONObject]
/// using simple dot-prefixed key paths (e.g. `".type"`, `".coords"`).
///
/// Paths are of the form `".key"` where `key` is the name of a
/// top-level JSON property. The accessor reads or writes the **entire value**
/// at that key — arrays and objects are returned as opaque [JSONValue]
/// instances that the caller is responsible for interpreting.
///
/// This replaces the scattered `json.get(FloorMapJsonKeys.XYZ)` calls
/// with a schema-driven approach where the key name comes from a
/// [stroom.floormap.shared.FloorMapFieldMapping].
public final class ValuePathAccessor {

    private ValuePathAccessor() {
        // Utility class
    }

    /// Parses a raw JSON string into a mutable [JSONObject].
    ///
    /// **Throws on malformed JSON; it does not return `null`.** The
    /// delegate is [JSONParser#parseStrict(String)], which raises an unchecked
    /// exception. `null` is returned only for null, empty, or valid-but-non-object
    /// input. Note the difference from the similarly-named `JsonValueAccessor.parse`,
    /// which does catch - two entry points with the same name and opposite failure
    /// contracts, so check which one you are holding.
    ///
    /// @param raw the raw JSON string; must start with `{`
    /// @return the parsed object, or `null` if `raw` is null/empty or does not
    ///         parse to an object
    /// @throws com.google.gwt.json.client.JSONException if `raw` is malformed.
    ///         `JSONParser.parseStrict` wraps the underlying
    ///         `JavaScriptException`, so catching that instead will not catch this.
    public static JSONObject parse(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        final JSONValue val = JSONParser.parseStrict(raw);
        return val != null ? val.isObject() : null;
    }

    /// Reads the value at the given path from a JSON object.
    ///
    /// The path must be a dot-prefixed key, e.g. `".type"`.
    /// The leading dot is stripped to obtain the JSON property name.
    ///
    /// @param json the JSON object to read from; may be `null`
    /// @param path the dot-prefixed key path (e.g. `".coords"`)
    /// @return the value at the key, or `null` if not found
    public static JSONValue get(final JSONObject json, final String path) {
        if (json == null || path == null) {
            return null;
        }
        return json.get(toKey(path));
    }

    /// Writes a value at the given path in a JSON object.
    ///
    /// The path must be a dot-prefixed key, e.g. `".type"`.
    /// The leading dot is stripped to obtain the JSON property name.
    ///
    /// @param json  the JSON object to write to; must not be `null`
    /// @param path  the dot-prefixed key path (e.g. `".coords"`)
    /// @param value the value to set; if `null`, the key is removed
    public static void set(final JSONObject json, final String path, final JSONValue value) {
        if (json == null || path == null) {
            return;
        }
        final String key = toKey(path);
        if (value != null) {
            json.put(key, value);
        } else {
            // Remove the key entirely when setting to null. GWT's JSONObject.put maps a
            // null value onto a native `delete`, so this really does remove the property
            // rather than storing a JSONNull - which is the opposite of what the name
            // suggests, and the opposite of what a JVM Map would do.
            if (json.containsKey(key)) {
                json.put(key, null);
            }
        }
    }

    /// Converts a dot-prefixed path to a JSON property key by stripping the
    /// leading dot. If the path does not start with a dot, it is returned
    /// unchanged.
    ///
    /// @param path the dot-prefixed path (e.g. `".type"`)
    /// @return the JSON property key (e.g. `"type"`)
    public static String toKey(final String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith(".")) {
            return path.substring(1);
        }
        return path;
    }
}
