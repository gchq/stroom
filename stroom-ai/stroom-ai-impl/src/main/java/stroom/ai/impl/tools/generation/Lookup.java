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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

/// Reference data: what `stroom:lookup()` finds for a key in a map a reference loader provides
/// (`find_reference_data` lists the maps). The pipeline must name the loader as a pipeline reference
/// (`create_pipeline` references, or `update_pipeline (references=...)`); a key the map lacks gives no value, so the
/// element is left out (or `default`). Ported from the Stroom MCP server's `Lookup` model.
///
/// @param map   The map name, e.g. `USER_TO_DEPARTMENT`.
/// @param field Input field holding the key (or a name from extract).
/// @param xpath Or an XPath giving the key.
/// @param path  Path below the map's value when it holds elements, e.g. `department`; null for a text value.
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"map", "field", "xpath", "path"})
public record Lookup(@JsonProperty("map") String map,
                     @JsonProperty("field") String field,
                     @JsonProperty("xpath") String xpath,
                     @JsonProperty("path") String path) {

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static Lookup read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "Lookup");
        if (fields == null) {
            return null;
        }
        final Lookup lookup = new Lookup(fields.requiredString("map"), fields.optionalString("field"),
                fields.optionalString("xpath"), fields.optionalString("path"));
        if (!fields.ok()) {
            return null;
        }
        if ((lookup.field == null) == (lookup.xpath == null)) {
            fields.valueError("lookup '" + lookup.map + "': give exactly one of field or xpath for the key");
            return null;
        }
        return lookup;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the Python model's `model_dump` writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("map", map, null)
                .put("field", field, null)
                .put("xpath", xpath, null)
                .put("path", path, null)
                .build();
    }
}
