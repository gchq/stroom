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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.generation.DumpMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A header field of a CEF plan: a constant, or an XPath on the Event (with an optional map of its values).
///
/// @param value        A constant.
/// @param source       Or an XPath from the Event, e.g. `EventSource/System/Name`.
/// @param map          Source value -> header value, e.g. severities.
/// @param defaultValue `default`: when the source is empty, or matches no map entry.
public record CefValue(String value,
                       String source,
                       Map<String, String> map,
                       String defaultValue) {

    /// The model's own check.
    public static final String ONE_OF = "A header field takes value (a constant) or source (an XPath on the Event)";

    /// Fills in an empty map for a null one.
    public CefValue {
        map = map == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /// @param value A constant.
    /// @return The header field.
    public static CefValue constant(final String value) {
        return new CefValue(value, null, Map.of(), null);
    }

    /// @param source An XPath from the Event.
    /// @return The header field.
    public static CefValue from(final String source) {
        return new CefValue(null, source, Map.of(), null);
    }

    /// @param json A header field as JSON-like values.
    /// @return It validated (lax types, defaults and its own checks).
    /// @throws stroom.ai.impl.tools.generation.MappingValidationException if it doesn't validate.
    public static CefValue fromJson(final Map<String, Object> json) {
        final CefJsonReader reader = new CefJsonReader();
        return reader.orThrow("CefValue", read(reader, json, List.of()));
    }

    /// @param reader The reader, noting what is wrong.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return The header field, or null if it doesn't validate.
    static CefValue read(final CefJsonReader reader, final Object input, final List<Object> loc) {
        final Map<String, Object> data = reader.model(input, loc, "CefValue");
        if (data == null) {
            return null;
        }
        final int before = reader.errorCount();
        final CefValue value = new CefValue(reader.optionalString(data, loc, "value"),
                reader.optionalString(data, loc, "source"),
                reader.stringMap(data.get("map"), CefJsonReader.at(loc, "map")),
                reader.optionalString(data, loc, "default"));
        if (reader.errorCount() > before) {
            return null;
        }
        if ((value.value == null) == (value.source == null)) {
            reader.valueError(loc, ONE_OF, data);
            return null;
        }
        return value;
    }

    /// @param mode How to write it.
    /// @return It as JSON, keys in field order: the form a plan is kept in.
    public Map<String, Object> toJson(final DumpMode mode) {
        final Map<String, Object> out = new LinkedHashMap<>();
        put(out, mode, "value", value, null);
        put(out, mode, "source", source, null);
        put(out, mode, "map", map, Map.of());
        put(out, mode, "default", defaultValue, null);
        return out;
    }

    /// Writes a field, unless the mode leaves it out.
    ///
    /// @param out          A model's JSON.
    /// @param mode         How it is written.
    /// @param key          The field.
    /// @param value        Its value.
    /// @param defaultValue Its default.
    static void put(final Map<String, Object> out, final DumpMode mode, final String key, final Object value,
                    final Object defaultValue) {
        if (value == null && mode != DumpMode.ALL) {
            return;
        }
        if (mode == DumpMode.COMPACT && Objects.equals(value, defaultValue)) {
            return;
        }
        out.put(key, value);
    }
}
