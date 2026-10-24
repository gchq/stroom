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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A kind of event a translation mapping writes: the records it is for (its conditions) and the fields its events
/// get. Ported from the Stroom MCP server's `EventRule` model.
///
/// @param name         Short name for this kind of event, e.g. `logon`.
/// @param when         All must hold. Empty: every record (such a rule goes last).
/// @param fields       Fields for this kind of event; they override common fields with the same path.
/// @param drop         True: records matching this rule are left untranslated on purpose (no Event, no warning).
/// @param allowUnknown `allow_unknown`: only when no action element describes this kind of event, so
///                     EventDetail/Unknown is right: the reason, in the user's words. `true` reads as no reason
///                     (`""`), `false` as none.
/// @param keepUnknown  `keep_unknown`: with allow_unknown, only when the user still wants Unknown after being shown
///                     the action elements the records' values suggest.
/// @param data         Input fields carried as Data of this rule's action element, each named after its field;
///                     always empty once validated, as each becomes a Data entry of fields.
public record EventRule(String name,
                        List<Condition> when,
                        List<FieldMapping> fields,
                        boolean drop,
                        String allowUnknown,
                        boolean keepUnknown,
                        List<String> data) {

    /// Fills in empty lists for null ones.
    public EventRule {
        when = List.copyOf(Objects.requireNonNullElse(when, List.of()));
        fields = List.copyOf(Objects.requireNonNullElse(fields, List.of()));
        data = List.copyOf(Objects.requireNonNullElse(data, List.of()));
    }

    /// @param name   The rule's name.
    /// @param when   Its conditions.
    /// @param drop   True to leave its records untranslated.
    /// @param fields Its fields.
    /// @return The rule, without allow_unknown, keep_unknown or data.
    public static EventRule of(final String name,
                               final List<Condition> when,
                               final boolean drop,
                               final List<FieldMapping> fields) {
        return new EventRule(name, when, fields, drop, null, false, List.of());
    }

    /// @param json A rule as JSON-like values.
    /// @return It validated, as the Python model validates it.
    /// @throws MappingValidationException if it doesn't validate.
    public static EventRule fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("EventRule", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static EventRule read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "EventRule");
        if (fields == null) {
            return null;
        }
        final String name = fields.requiredString("name");
        final List<Condition> when = fields.models("when", (value, at) -> Condition.read(reader, value, at));
        final List<FieldMapping> entries = fields.models("fields", (value, at) -> FieldMapping.read(reader, value,
                at));
        final boolean drop = fields.bool("drop", false);
        // allow_unknown: true has no reason, which the generator reports; false is no allowance.
        final Object given = fields.input().get("allow_unknown");
        final String allowUnknown = Boolean.TRUE.equals(given)
                ? ""
                : Boolean.FALSE.equals(given)
                        ? null
                        : fields.optionalString("allow_unknown");
        final boolean keepUnknown = fields.bool("keep_unknown", false);
        final List<String> data = fields.strings("data");
        if (!fields.ok()) {
            return null;
        }
        if (data.isEmpty()) {
            return new EventRule(name, when, entries, drop, allowUnknown, keepUnknown, List.of());
        }
        // Expanded here, so every reader of the mapping (generator, checks, documentation) sees ordinary Data
        // entries.
        final String home = MappingFacts.actionHome(entries.stream().map(FieldMapping::path).toList());
        if (home == null) {
            // Seen in testing: a rule of fields [] and a data list, for Network events with no typed child.
            fields.valueError("[" + name + "] data needs the rule's action element, which none of its fields names: "
                              + "map one field there first (EventDetail/Alert/Type, say; for an element whose only "
                              + "fit is Data, as Network's is for a DNS lookup, a Data entry of it: {\"path\": "
                              + "\"EventDetail/Network/Data\", \"data_name\": \"action\", \"field\": \"action\"}), "
                              + "and data carries the rest beside it");
            return null;
        }
        final Set<List<String>> have = new HashSet<>();
        for (final FieldMapping entry : entries) {
            // With the Data it is in (data_parent): one of the name in another Data isn't this one.
            have.add(Arrays.asList(entry.strippedPath(), entry.dataKey()));
        }
        final List<FieldMapping> expanded = new ArrayList<>(entries);
        for (final String field : data) {
            if (!have.contains(Arrays.asList(home + "/Data", field))) {
                final FieldMapping entry = FieldMapping.data(home + "/Data", field, field);
                // Made as the Python model makes it, so checked as it is: a field that is a function call, say.
                final String problem = entry.problem();
                if (problem != null) {
                    // As pydantic reports the entry's own error: at the rule, with the entry as the input.
                    final Map<String, Object> made = new LinkedHashMap<>();
                    made.put("path", home + "/Data");
                    made.put("data_name", field);
                    made.put("field", field);
                    reader.valueError(loc, problem, made);
                    return null;
                }
                expanded.add(entry);
            }
        }
        return new EventRule(name, when, expanded, drop, allowUnknown, keepUnknown, List.of());
    }

    /// @param fields The rule's fields.
    /// @return This rule with those fields.
    public EventRule withFields(final List<FieldMapping> fields) {
        return new EventRule(name, when, fields, drop, allowUnknown, keepUnknown, data);
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the Python model's `model_dump` writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("name", name, null)
                .models("when", when, condition -> condition.toJson(mode))
                .models("fields", fields, entry -> entry.toJson(mode))
                .put("drop", drop, false)
                .put("allow_unknown", allowUnknown, null)
                .put("keep_unknown", keepUnknown, false)
                .put("data", data, List.of())
                .build();
    }
}
