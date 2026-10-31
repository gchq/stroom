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

/// A reference mapping: the maps a reference feed (a user list, an asset register) provides, from which
/// [ReferenceGenerator] writes the reference-data XSLT. Its JSON is kept with the XSLT, so its form must stay
/// readable: a mapping kept by any earlier save still reads.
///
/// @param input      How the reference feed is parsed: `data_splitter` or `json`.
/// @param jsonLayout `json_layout`: json input only, `array` or `lines`.
/// @param root       Root to match; defaults as for build_translation_xslt.
/// @param record     Record elements; defaults as for build_translation_xslt.
/// @param maps       The maps (at least one).
/// @param imports    Shared XSLTs imported (xsl:import), e.g. those an AGENTS doc's Required imports name: added
///                   for you.
/// @param dropWhen   `drop_when`: records to leave out of the reference data (disabled accounts, say); each
///                   entry's conditions must all hold.
public record ReferenceMapping(String input,
                               String jsonLayout,
                               String root,
                               String record,
                               List<ReferenceMap> maps,
                               List<String> imports,
                               List<DropRule> dropWhen) {

    /// The kinds of input.
    public static final List<String> INPUTS = List.of("data_splitter", "json");

    /// Fills in the defaults for nulls.
    public ReferenceMapping {
        jsonLayout = Objects.requireNonNullElse(jsonLayout, "array");
        maps = copy(maps);
        imports = copy(imports);
        dropWhen = copy(dropWhen);
    }

    /// @param json A reference mapping as JSON-like values.
    /// @return It validated, with the model's own checks.
    /// @throws MappingValidationException if it doesn't validate, with every error found.
    public static ReferenceMapping fromJson(final Map<String, Object> json) {
        return parse(json);
    }

    /// @param json A reference mapping as JSON-like values, which may be anything (an error says what it should be).
    /// @return It validated, with the model's own checks.
    /// @throws MappingValidationException if it doesn't validate, with every error found.
    public static ReferenceMapping parse(final Object json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("ReferenceMapping", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static ReferenceMapping read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "ReferenceMapping");
        if (fields == null) {
            return null;
        }
        final ReferenceMapping mapping = new ReferenceMapping(
                fields.requiredLiteral("input", INPUTS),
                fields.literal("json_layout", "array", List.of("array", "lines")),
                fields.optionalString("root"),
                fields.optionalString("record"),
                fields.requiredModels("maps", (value, at) -> ReferenceMap.read(reader, value, at), 1),
                fields.strings("imports"),
                fields.models("drop_when", (value, at) -> DropRule.read(reader, value, at)));
        return fields.ok()
                ? mapping
                : null;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("input", input, null)
                .put("json_layout", jsonLayout, "array")
                .put("root", root, null)
                .put("record", record, null)
                .models("maps", maps, map -> map.toJson(mode))
                .put("imports", imports, List.of())
                .models("drop_when", dropWhen, rule -> rule.toJson(mode))
                .build();
    }

    private static <T> List<T> copy(final List<T> list) {
        return List.copyOf(Objects.requireNonNullElse(list, List.of()));
    }
}
