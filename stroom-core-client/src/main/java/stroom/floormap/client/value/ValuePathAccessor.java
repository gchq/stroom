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

/**
 * GWT-compatible utility for reading and writing values in a {@link JSONObject}
 * using simple dot-prefixed key paths (e.g. {@code ".type"}, {@code ".coords"}).
 *
 * <p>Paths are of the form {@code ".key"} where {@code key} is the name of a
 * top-level JSON property. The accessor reads or writes the <b>entire value</b>
 * at that key — arrays and objects are returned as opaque {@link JSONValue}
 * instances that the caller is responsible for interpreting.</p>
 *
 * <p>This replaces the scattered {@code json.get(FloorMapJsonKeys.XYZ)} calls
 * with a schema-driven approach where the key name comes from a
 * {@link stroom.floormap.shared.FloorMapFieldMapping}.</p>
 */
public final class ValuePathAccessor {

    private ValuePathAccessor() {
        // Utility class
    }

    /**
     * Parses a raw JSON string into a mutable {@link JSONObject}.
     *
     * <p><strong>Throws on malformed JSON; it does not return {@code null}.</strong> The
     * delegate is {@link JSONParser#parseStrict(String)}, which raises an unchecked
     * exception. {@code null} is returned only for null, empty, or valid-but-non-object
     * input. Note the difference from the similarly-named {@code JsonValueAccessor.parse},
     * which does catch - two entry points with the same name and opposite failure
     * contracts, so check which one you are holding.</p>
     *
     * @param raw the raw JSON string; must start with {@code {}
     * @return the parsed object, or {@code null} if {@code raw} is null/empty or does not
     *         parse to an object
     * @throws com.google.gwt.json.client.JSONException if {@code raw} is malformed.
     *         {@code JSONParser.parseStrict} wraps the underlying
     *         {@code JavaScriptException}, so catching that instead will not catch this.
     */
    public static JSONObject parse(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        final JSONValue val = JSONParser.parseStrict(raw);
        return val != null ? val.isObject() : null;
    }

    /**
     * Reads the value at the given path from a JSON object.
     *
     * <p>The path must be a dot-prefixed key, e.g. {@code ".type"}.
     * The leading dot is stripped to obtain the JSON property name.</p>
     *
     * @param json the JSON object to read from; may be {@code null}
     * @param path the dot-prefixed key path (e.g. {@code ".coords"})
     * @return the value at the key, or {@code null} if not found
     */
    public static JSONValue get(final JSONObject json, final String path) {
        if (json == null || path == null) {
            return null;
        }
        return json.get(toKey(path));
    }

    /**
     * Writes a value at the given path in a JSON object.
     *
     * <p>The path must be a dot-prefixed key, e.g. {@code ".type"}.
     * The leading dot is stripped to obtain the JSON property name.</p>
     *
     * @param json  the JSON object to write to; must not be {@code null}
     * @param path  the dot-prefixed key path (e.g. {@code ".coords"})
     * @param value the value to set; if {@code null}, the key is removed
     */
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

    /**
     * Converts a dot-prefixed path to a JSON property key by stripping the
     * leading dot. If the path does not start with a dot, it is returned
     * unchanged.
     *
     * @param path the dot-prefixed path (e.g. {@code ".type"})
     * @return the JSON property key (e.g. {@code "type"})
     */
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
