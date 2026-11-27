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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.ModelReader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// One way a record of an event type is recognised (`recognised_by`'s entries): conditions that must all hold, and
/// the names of a `matches` condition's capture groups, fields the translation may read.
///
/// @param when  The conditions, all of which must hold (an AND; never empty but for a sole event type's).
/// @param names The capture groups' names, in order; empty when none are named.
public record Way(List<Condition> when, List<String> names) {

    /// Fills in empty lists for null ones.
    public Way {
        when = List.copyOf(Objects.requireNonNullElse(when, List.of()));
        names = List.copyOf(Objects.requireNonNullElse(names, List.of()));
    }

    /// Reads one from JSON-like values, laxly, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't read.
    public static Way read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "Way");
        if (fields == null) {
            return null;
        }
        final List<Condition> when = fields.models("when", (value, at) -> Condition.read(reader, value, at));
        final List<String> names = fields.strings("names");
        return fields.ok()
                ? new Way(when, names)
                : null;
    }

    /// @return It as JSON, the conditions written compactly.
    public Map<String, Object> toJson() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("when", when.stream().map(condition -> condition.toJson(DumpMode.COMPACT)).toList());
        out.put("names", names);
        return out;
    }
}
