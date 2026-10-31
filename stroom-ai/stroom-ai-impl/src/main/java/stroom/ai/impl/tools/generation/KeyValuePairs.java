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

import stroom.ai.impl.tools.ToolText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/// Key/value pairs in one text field, each key a field of its own: an extract entry a key, written as one. A
/// [TranslationMapping] turns each into its extract entries when it is validated, so it always holds none.
///
/// @param field The input field holding the text.
/// @param style How the text writes each pair: one of [Pairs#STYLES].
/// @param keys  The keys, as the text writes them (at least one): each value is a field named after its key in
///              snake case (`Source IP` -> `source_ip`).
/// @param names Field names for keys named otherwise: key -> name.
public record KeyValuePairs(String field,
                            String style,
                            List<String> keys,
                            Map<String, String> names) {

    /// Fills in empty collections for null ones.
    public KeyValuePairs {
        keys = List.copyOf(Objects.requireNonNullElse(keys, List.of()));
        names = Objects.requireNonNullElse(names, Map.of());
    }

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static KeyValuePairs read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "KeyValuePairs");
        if (fields == null) {
            return null;
        }
        final KeyValuePairs pairs = new KeyValuePairs(fields.requiredString("field"),
                fields.requiredLiteral("style", Pairs.STYLES), fields.requiredStrings("keys", 1),
                fields.stringMap("names"));
        if (!fields.ok()) {
            return null;
        }
        final Set<String> unknown = new TreeSet<>(pairs.names.keySet());
        pairs.keys.forEach(unknown::remove);
        if (!unknown.isEmpty()) {
            fields.valueError("pairs names " + ToolText.list(unknown) + ", which aren't among its keys");
            return null;
        }
        return pairs;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("field", field, null)
                .put("style", style, null)
                .put("keys", keys, null)
                .put("names", new LinkedHashMap<>(names), Map.of())
                .build();
    }
}
