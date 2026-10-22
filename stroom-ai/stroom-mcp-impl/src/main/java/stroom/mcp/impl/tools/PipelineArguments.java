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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.util.shared.NullSafe;

import java.util.LinkedHashMap;
import java.util.Map;

/// The pipeline tools' own arguments, read in the shapes models send them: a mapping of text given as an object,
/// or as JSON text or a Python literal (the lists and flags all the tools share are read by [ToolArgs]).
final class PipelineArguments {

    private PipelineArguments() {
        // Static methods only.
    }

    /// @param value An argument mapping text to text, e.g. `{"V1.2": "V1.3"}`, or that as JSON text.
    /// @param name  The argument's name, for a refusal.
    /// @return The mapping, in the order given; empty for none.
    /// @throws ToolRefusal if it isn't one.
    static Map<String, String> textMap(final Object value, final String name) {
        if (value == null || (value instanceof final String text && NullSafe.isBlankString(text))) {
            return Map.of();
        }
        final Object given = value instanceof final String text
                ? ToolArgs.literal(text.strip())
                : value;
        if (!(given instanceof final Map<?, ?> map)) {
            throw new ToolRefusal(name + " is an object of text replacements, e.g. {\"V1.2\": \"V1.3\"}");
        }
        final Map<String, String> read = new LinkedHashMap<>();
        map.forEach((from, to) -> read.put(String.valueOf(from), String.valueOf(to)));
        return read;
    }
}
