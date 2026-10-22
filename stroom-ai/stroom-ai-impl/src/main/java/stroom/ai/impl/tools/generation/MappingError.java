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
import java.util.stream.Collectors;

/// One reason a mapping (or part of one) doesn't validate, as pydantic reports it in the Stroom MCP server
/// (`ValidationError.errors()`): the agent is shown these, so the wording is pydantic's (and, for the models' own
/// checks, the server's).
///
/// @param loc     Where it is, from the top of what was read: keys and list positions, e.g.
///                `["events", 0, "fields", 1]`.
/// @param type    pydantic's error type, e.g. `missing`, `string_type`, `literal_error`, `value_error`.
/// @param message pydantic's message, e.g. `Field required`, or `Value error, ...` for a model's own check.
/// @param input   The value given there (for a missing key, the object it is missing from).
public record MappingError(List<Object> loc, String type, String message, Object input) {

    /// @return The location as pydantic writes it in a message: the parts joined with dots, e.g. `events.0.name`.
    public String dotted() {
        return loc.stream().map(String::valueOf).collect(Collectors.joining("."));
    }

    /// @return The location as the Stroom MCP server's `mapping_problems` writes it, e.g. `events[2].fields[0].path`
    /// (empty for the top).
    public String where() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < loc.size(); i++) {
            final Object part = loc.get(i);
            if (part instanceof Integer) {
                sb.append('[').append(part).append(']');
            } else if (i > 0) {
                sb.append('.').append(part);
            } else {
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
