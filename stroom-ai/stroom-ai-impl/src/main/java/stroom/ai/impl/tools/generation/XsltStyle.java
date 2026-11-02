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

/// How the XSLT is written: taken from a style guide in the standing instructions (AGENTS docs) when one says how
/// XSLT should look; otherwise the defaults. The indexing and CEF XSLTs follow it too.
///
/// @param naming           Names of variables and named templates: `snake_case` (default), `camelCase`,
///                         `PascalCase` or `kebab-case`.
/// @param variableMinReads `variable_min_reads`: read a field into a variable only when a template reads it at
///                         least this many times (at least 1; default 3).
/// @param inlineMapMaxKeys `inline_map_max_keys`: a value map used by one element with at most this many keys is
///                         written inline as an if (default 3; 0: always an xsl:map).
/// @param functionMinUses  `function_min_uses`: a conversion used by at least this many elements is declared once
///                         as an xsl:function (default 2; 0: always inline).
/// @param dataValues       `data_values`: how a computed Data value is written, `interpolated` (default) or
///                         `attribute`.
/// @param dataEntries      `data_entries`: how a Data entry read from the input is written, `function` (default)
///                         or `guarded`.
/// @param dataRunMin       `data_run_min`: Data entries several rules write the same way, this many or more in a
///                         row, are written once as a template of their own (at least 2; default 4).
/// @param variables        Where variables are declared: `just_in_time` (default) or `top`.
/// @param dataNames        `data_names`: Data element Names in this style, or `as_given` (default).
/// @param layout           How the templates are laid out: `modes` (default), `named` or `inline`.
public record XsltStyle(String naming,
                        long variableMinReads,
                        long inlineMapMaxKeys,
                        long functionMinUses,
                        String dataValues,
                        String dataEntries,
                        long dataRunMin,
                        String variables,
                        String dataNames,
                        String layout) {

    /// A field read fewer times than this in a template is written where it's used, not held in a variable: an
    /// element's own guard and value are two reads, so a variable needs a third, such as an enclosing guard.
    public static final int VARIABLE_MIN_READS = 3;
    /// A value map used by one element with at most this many keys is written inline, as an if.
    public static final int INLINE_MAP_KEYS = 3;
    /// A conversion used by at least this many elements is an xsl:function of its own.
    public static final int FUNCTION_MIN_USES = 2;
    /// The namings.
    public static final List<String> NAMINGS = List.of("snake_case", "camelCase", "PascalCase", "kebab-case");
    /// The defaults.
    public static final XsltStyle DEFAULT = new XsltStyle("snake_case", VARIABLE_MIN_READS, INLINE_MAP_KEYS,
            FUNCTION_MIN_USES, "interpolated", "function", 4, "just_in_time", "as_given", "modes");

    private static final List<String> DATA_NAMES = List.of("as_given", "snake_case", "camelCase", "PascalCase",
            "kebab-case");

    /// @param json A style as JSON-like values.
    /// @return It validated, with the model's own checks.
    /// @throws MappingValidationException if it doesn't validate.
    public static XsltStyle fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("XsltStyle", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    public static XsltStyle read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "XsltStyle");
        if (fields == null) {
            return null;
        }
        final XsltStyle style = new XsltStyle(
                fields.literal("naming", DEFAULT.naming, NAMINGS),
                fields.integer("variable_min_reads", DEFAULT.variableMinReads, 1),
                fields.integer("inline_map_max_keys", DEFAULT.inlineMapMaxKeys, 0),
                fields.integer("function_min_uses", DEFAULT.functionMinUses, 0),
                fields.literal("data_values", DEFAULT.dataValues, List.of("attribute", "interpolated")),
                fields.literal("data_entries", DEFAULT.dataEntries, List.of("function", "guarded")),
                fields.integer("data_run_min", DEFAULT.dataRunMin, 2),
                fields.literal("variables", DEFAULT.variables, List.of("just_in_time", "top")),
                fields.literal("data_names", DEFAULT.dataNames, DATA_NAMES),
                fields.literal("layout", DEFAULT.layout, List.of("modes", "named", "inline")));
        return fields.ok()
                ? style
                : null;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("naming", naming, DEFAULT.naming)
                .put("variable_min_reads", variableMinReads, DEFAULT.variableMinReads)
                .put("inline_map_max_keys", inlineMapMaxKeys, DEFAULT.inlineMapMaxKeys)
                .put("function_min_uses", functionMinUses, DEFAULT.functionMinUses)
                .put("data_values", dataValues, DEFAULT.dataValues)
                .put("data_entries", dataEntries, DEFAULT.dataEntries)
                .put("data_run_min", dataRunMin, DEFAULT.dataRunMin)
                .put("variables", variables, DEFAULT.variables)
                .put("data_names", dataNames, DEFAULT.dataNames)
                .put("layout", layout, DEFAULT.layout)
                .build();
    }
}
