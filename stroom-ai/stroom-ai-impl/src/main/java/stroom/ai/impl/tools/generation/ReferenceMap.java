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

package stroom.ai.impl.tools.generation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/// One map a reference feed provides: the name `stroom:lookup()` uses, the key, and what the key maps to.
///
/// @param name     The map name stroom:lookup() uses, e.g. `USER_TO_DEPARTMENT`.
/// @param key      Input field holding the key.
/// @param keyXpath `key_xpath`: or an XPath for the key, e.g. `lower-case(data[@name='user']/@value)`.
/// @param values   What the key maps to (at least one).
/// @param keyFields `key_fields`: or the input fields whose values make the key, in order, as a lookup's `key_fields`
///                  makes it ([LookupKeys]); one is the same as key. Null when the key is key or key_xpath.
public record ReferenceMap(String name,
                           String key,
                           String keyXpath,
                           List<ReferenceValue> values,
                           List<String> keyFields) {

    /// Fills in an empty list for a null one.
    public ReferenceMap {
        values = List.copyOf(Objects.requireNonNullElse(values, List.of()));
    }

    /// A map keyed by one field or xpath.
    ///
    /// @param name     The map name.
    /// @param key      Input field holding the key.
    /// @param keyXpath Or an XPath for the key.
    /// @param values   What the key maps to.
    public ReferenceMap(final String name, final String key, final String keyXpath, final List<ReferenceValue> values) {
        this(name, key, keyXpath, values, null);
    }

    /// @param json A map as JSON-like values.
    /// @return It validated, with the model's own checks.
    /// @throws MappingValidationException if it doesn't validate.
    public static ReferenceMap fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("ReferenceMap", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static ReferenceMap read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "ReferenceMap");
        if (fields == null) {
            return null;
        }
        final ReferenceMap map = new ReferenceMap(fields.requiredString("name"), fields.optionalString("key"),
                fields.optionalString("key_xpath"),
                fields.requiredModels("values", (value, at) -> ReferenceValue.read(reader, value, at), 1),
                fields.optionalStrings("key_fields"));
        if (!fields.ok()) {
            return null;
        }
        if (Stream.of(map.key, map.keyXpath, map.keyFields).filter(Objects::nonNull).count() != 1) {
            reader.valueError(loc, "map '" + map.name + "': give exactly one of key, key_xpath or key_fields", input);
            return null;
        }
        final String problem = LookupKeys.problem(map.keyFields, "map '" + map.name + "'");
        if (problem != null) {
            reader.valueError(loc, problem, input);
            return null;
        }
        if (map.values.stream().filter(part -> part.element().isEmpty()).count() > 1) {
            reader.valueError(loc, "map '" + map.name + "': at most one text value; name the others as elements",
                    input);
            return null;
        }
        return map;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("name", name, null)
                .put("key", key, null)
                .put("key_xpath", keyXpath, null)
                .models("values", values, part -> part.toJson(mode))
                .putIfSet("key_fields", keyFields)
                .build();
    }

    /// @return How many fields the key is made of: key_fields' count, else 1.
    public int keyCount() {
        return keyFields == null
                ? 1
                : keyFields.size();
    }
}
