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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.sample.HeldText;
import stroom.ai.impl.tools.sample.SourceTypeMeta;
import stroom.ai.impl.tools.sample.SourceTypeReading;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// EventSource/System/Name per source type, for a build whose scope is a source type group of several source types
/// (seen in chat 182: the user answered "as per source type", and only one name could be given). `system_name` is
/// then a JSON object keyed by the scope's source types; the draft writes the name from the source type field's
/// value (an entry with `field` and `map`), so each record gets its source type's own. A JSON object keyed otherwise
/// is each variant's ([SystemNames]).
///
/// Only where each of the scope's source types is told by exact values of a record's own field (registry rows
/// `= value`): a pattern, a list's item, no value, a combination, a held reading or a part's meta attribute can't
/// be written as a map of values, so the group is given one name.
final class SourceTypeSystemNames {

    private static final String SYSTEM_NAME = "EventSource/System/Name";
    // The transform the entry reads the field's value with: control characters taken out, then white space
    // stripped from around it (and runs within it made one space), so a value its source type's row matches with
    // control characters or padding (the group's source-type function strips both) gets its name too, and a value
    // written as it is carries no control character into the Event (user, 2026-11-22).
    private static final String CLEAN = "clean";

    private SourceTypeSystemNames() {
        // Static only.
    }

    /// @param given The `system_name` argument, as the build's record gives it (a name, or a JSON object).
    /// @param scope The build's scope, or null for a build without one.
    /// @return Each source type's name, where the object's keys are the scope's source types; empty for one name,
    /// none, or an object of each variant's (none of its keys a source type of the scope).
    /// @throws ToolRefusal if the object names some of the scope's source types but leaves one out or names
    /// another, or the scope's source types aren't told by exact values of a record's field.
    static Map<String, String> of(final Object given, final SourceTypeScope scope) {
        if (!(given instanceof final Map<?, ?> map) || !severalTypes(scope)) {
            return Map.of();
        }
        final Set<String> names = scope.names();
        final List<String> keys = map.keySet().stream().map(String::valueOf).toList();
        if (keys.stream().noneMatch(names::contains)) {
            return Map.of();
        }
        final List<String> unknown = keys.stream().filter(key -> !names.contains(key)).toList();
        if (!unknown.isEmpty()) {
            throw new ToolRefusal("system_name names " + ToolText.list(unknown) + ", which aren't the build's "
                                  + "source types: give a name for each of " + scope.said() + ", or one name");
        }
        final Map<String, String> out = new LinkedHashMap<>();
        map.forEach((type, name) -> {
            // A name as it goes into the Event: no control characters (user, 2026-11-22).
            final String cleaned = name == null
                    ? null
                    : HeldText.clean(String.valueOf(name));
            if (cleaned != null && !cleaned.isEmpty()) {
                out.put(String.valueOf(type), cleaned);
            }
        });
        final List<String> missing = names.stream()
                .filter(type -> !out.containsKey(type))
                .sorted(ToolText::compare)
                .toList();
        if (!missing.isEmpty()) {
            throw new ToolRefusal("system_name has no name for " + ToolText.list(missing) + ": give a name for "
                                  + "each of " + scope.said() + ", or one name");
        }
        final Optional<String> why = unsupported(scope);
        if (why.isPresent()) {
            throw new ToolRefusal("A System/Name per source type is written from " + scope.field() + "'s values, "
                                  + "but " + why.get() + ": give the group one name");
        }
        return out;
    }

    /// @param scope The build's scope, or null.
    /// @return What the System/Name note offers a group of several source types given no name: one name, or each
    /// source type's own; empty where the scope isn't such a group, or its source types can't be named each.
    static Optional<String> unnamedOffer(final SourceTypeScope scope) {
        if (!severalTypes(scope) || unsupported(scope).isPresent()) {
            return Optional.empty();
        }
        return Optional.of("The build's source type group holds " + scope.said() + ": its EventSource/System/Name "
                           + "can be one name for the group or each source type's own. Ask the user which; give "
                           + "system_name=\"<name>\", or system_name={\"<source type>\": \"<name>\"} for each.");
    }

    /// Offers a group of several source types given no name one name or each source type's own, in the draft's
    /// System/Name note itself, not a note of its own (seen in chat 184: a client's cut list of notes dropped the
    /// offer, which came last, and the agent asked for one name); a note of its own only where the draft has no
    /// System/Name note.
    ///
    /// @param scope The build's scope, or null.
    /// @param notes The draft's notes, changed in place.
    static void offerInNote(final SourceTypeScope scope, final List<Object> notes) {
        final Optional<String> offer = unnamedOffer(scope);
        if (offer.isEmpty()) {
            return;
        }
        final int at = notes.indexOf(DraftMapping.systemNote(null));
        if (at < 0) {
            notes.add(offer.get());
        } else {
            notes.set(at, DraftMapping.systemNote(offer.get()));
        }
    }

    /// Writes EventSource/System/Name from the source type field's value: the common entry made a `field` and `map`
    /// entry of each value the scope's rows tell its source types by, to the source type's name, the value read
    /// cleaned (`transform` `clean`: control characters taken out, then trimmed), as the group's XSLT strips it to
    /// tell its source type.
    ///
    /// @param mapping The draft's mapping.
    /// @param scope   The build's scope.
    /// @param names   Each source type's name ([#of]).
    static void apply(final Map<String, Object> mapping,
                      final SourceTypeScope scope,
                      final Map<String, String> names) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", SYSTEM_NAME);
        entry.put("field", scope.field());
        entry.put("transform", CLEAN);
        entry.put("map", new LinkedHashMap<>(byValue(scope, names)));
        final List<Object> common = new ArrayList<>(JsonValues.listOrEmpty(mapping.get("common")));
        boolean replaced = false;
        for (int i = 0; i < common.size(); i++) {
            if (common.get(i) instanceof final Map<?, ?> was && SYSTEM_NAME.equals(was.get("path"))) {
                common.set(i, entry);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            common.addFirst(entry);
        }
        mapping.put("common", common);
    }

    /// @param scope The build's scope.
    /// @param names Each source type's name.
    /// @return Each value the scope's rows tell the named source types by (as `clean` gives it, [#key]), to its
    /// name: the rows in order, a value the source type of its first row (as the tools and the group's XSLT read
    /// it).
    static Map<String, String> byValue(final SourceTypeScope scope, final Map<String, String> names) {
        final Map<String, String> out = new LinkedHashMap<>();
        final Set<String> seen = new HashSet<>();
        for (final SourceTypeRows.Row row : scope.rows()) {
            if (!SourceTypeRows.VALUE.equals(row.match()) || row.value() == null) {
                continue;
            }
            final String key = key(row.value());
            if (seen.add(key) && names.containsKey(row.name())) {
                out.put(key, names.get(row.name()));
            }
        }
        return out;
    }

    /// @param value A source type field's value, or a registry row's.
    /// @return It as the System/Name entry looks it up in its map: as `clean` gives it (control characters taken
    /// out, white space stripped from around it, runs within it one space).
    static String key(final String value) {
        return HeldText.clean(value);
    }

    // Why the scope's source types can't each be named from the source type field's values; empty when they can.
    private static Optional<String> unsupported(final SourceTypeScope scope) {
        final String field = scope.field();
        if (SourceTypeMeta.isMeta(field)) {
            return Optional.of("it is a part's meta attribute, not a field of the records");
        }
        if (SourceTypeReading.isReading(field)) {
            return Optional.of("it is read into the records' held values or combined with another field");
        }
        final List<String> inexact = new ArrayList<>();
        for (final String type : scope.names()) {
            final List<SourceTypeRows.Row> rows = scope.rows().stream()
                    .filter(row -> type.equals(row.name()))
                    .toList();
            if (rows.isEmpty() || rows.stream().anyMatch(row -> !SourceTypeRows.VALUE.equals(row.match())
                                                                || row.value() == null || row.value().isEmpty())) {
                inexact.add(type);
            }
        }
        if (!inexact.isEmpty()) {
            return Optional.of(String.join(", ", inexact.stream().sorted(ToolText::compare).toList()) + " "
                               + (inexact.size() == 1
                    ? "isn't"
                    : "aren't") + " told by exact values (a registry row by a pattern, a list's item or no value)");
        }
        return Optional.empty();
    }

    private static boolean severalTypes(final SourceTypeScope scope) {
        return scope != null && scope.names() != null && scope.names().size() >= 2;
    }
}
