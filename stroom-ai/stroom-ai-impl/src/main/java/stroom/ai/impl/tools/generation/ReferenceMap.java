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

/// One map a reference feed provides: the name `stroom:lookup()` uses, the key, and what the key maps to. Ported
/// from the Stroom MCP server's `ReferenceMap` model (`utils/refgen.py`).
///
/// @param name     The map name stroom:lookup() uses, e.g. `USER_TO_DEPARTMENT`.
/// @param key      Input field holding the key.
/// @param keyXpath `key_xpath`: or an XPath for the key, e.g. `lower-case(data[@name='user']/@value)`.
/// @param values   What the key maps to (at least one).
public record ReferenceMap(String name,
                           String key,
                           String keyXpath,
                           List<ReferenceValue> values) {

    /// Fills in an empty list for a null one.
    public ReferenceMap {
        values = List.copyOf(Objects.requireNonNullElse(values, List.of()));
    }

    /// @param json A map as JSON-like values.
    /// @return It validated, as the Python model validates it.
    /// @throws MappingValidationException if it doesn't validate.
    public static ReferenceMap fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("ReferenceMap", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
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
                fields.requiredModels("values", (value, at) -> ReferenceValue.read(reader, value, at), 1));
        if (!fields.ok()) {
            return null;
        }
        if ((map.key == null) == (map.keyXpath == null)) {
            reader.valueError(loc, "map '" + map.name + "': give exactly one of key or key_xpath", input);
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
    /// @return It as JSON, as the Python model's `model_dump` writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("name", name, null)
                .put("key", key, null)
                .put("key_xpath", keyXpath, null)
                .models("values", values, part -> part.toJson(mode))
                .build();
    }
}
