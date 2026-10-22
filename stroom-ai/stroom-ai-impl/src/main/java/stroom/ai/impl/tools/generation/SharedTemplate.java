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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A named template from a shared XSLT, called where the environment's other XSLTs call it. Ported from the Stroom
/// MCP server's `SharedTemplate` model.
///
/// @param href       The shared XSLT document, as the other pipelines import it, e.g. `Common-Event-V1`.
/// @param template   The named template to call, e.g. `eventSourceDevice`.
/// @param at         The element it writes, as a path below Event, e.g. `EventSource/Device` or `Meta`. Nothing
///                   below it is mapped.
/// @param withParams `with_params`: parameters to pass, name -> XPath read from the record (or item).
public record SharedTemplate(String href,
                             String template,
                             String at,
                             Map<String, String> withParams) {

    /// Fills in an empty map for a null one.
    public SharedTemplate {
        withParams = Objects.requireNonNullElse(withParams, Map.of());
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    public static SharedTemplate read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "SharedTemplate");
        if (fields == null) {
            return null;
        }
        final SharedTemplate shared = new SharedTemplate(fields.requiredString("href"),
                fields.requiredString("template"), fields.requiredString("at"), fields.stringMap("with_params"));
        return fields.ok()
                ? shared
                : null;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the Python model's `model_dump` writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("href", href, null)
                .put("template", template, null)
                .put("at", at, null)
                .put("with_params", new LinkedHashMap<>(withParams), Map.of())
                .build();
    }
}
