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

/// Records (or, with for_each, items) to leave untranslated on purpose: no Event, no warning. Ported from the
/// Stroom MCP server's `DropRule` model.
///
/// @param when   All must hold for the record to be dropped (at least one).
/// @param reason Why, in the user's words; kept as a comment in the XSLT and in the documentation.
public record DropRule(List<Condition> when,
                       String reason) {

    /// Fills in an empty list for a null one.
    public DropRule {
        when = List.copyOf(Objects.requireNonNullElse(when, List.of()));
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static DropRule read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "DropRule");
        if (fields == null) {
            return null;
        }
        final DropRule rule = new DropRule(fields.requiredModels("when", (value, at) -> Condition.read(reader, value,
                at), 1), fields.requiredString("reason"));
        return fields.ok()
                ? rule
                : null;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the Python model's `model_dump` writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .models("when", when, condition -> condition.toJson(mode))
                .put("reason", reason, null)
                .build();
    }
}
