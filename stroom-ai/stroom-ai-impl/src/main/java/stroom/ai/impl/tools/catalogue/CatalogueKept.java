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

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

/// How a mapping translates the catalogue's event types, by the records they take: each event type's sampled records
/// (those one of its ways of recognising it recognises, by the one evaluator, [LocalCheck#holds]) followed to the rule
/// that takes each ([LocalCheck#ruleOf]), and what that rule writes (its action element, fixed Action and TypeId).
/// The build-time checks compare rules with event types this way, never by condition text: a rule may test other
/// fields, in another order, or a variant's condition first, and still take the type's records.
public final class CatalogueKept {

    /// What [LocalCheck#ruleOf] names a record no rule takes.
    public static final String NO_RULE = "";
    /// How [LocalCheck#ruleOf] names a drop rule: this, then its reason.
    public static final String DROP = "drop: ";
    /// The action element of an event the schema has none for.
    public static final String UNKNOWN = "Unknown";

    private static final String EVENT_DETAIL = "EventDetail/";
    private static final String TYPE_ID = "EventDetail/TypeId";
    private static final int PATH_PARTS = 3;

    private CatalogueKept() {
        // Static only.
    }

    /// Each event type's sampled records, by the rule that takes them.
    ///
    /// @param mapping   The mapping.
    /// @param catalogue The catalogue.
    /// @param records   The sample's records, as [LocalCheck#sampleRecords] reads them for the mapping.
    /// @param inScope   Whether the record at an index is the build's to compare (its source type in the build's
    ///                  scope), or null for every record.
    /// @return One per event type, in the catalogue's order; none when the mapping reads repeated groups
    /// (`for_each`), whose item rules this can't follow ([#notChecked]).
    public static List<Taken> taken(final TranslationMapping mapping,
                                    final Catalogue catalogue,
                                    final List<Object> records,
                                    final IntPredicate inScope) {
        if (catalogue == null || NullSafe.isNonBlankString(mapping.forEach())) {
            return List.of();
        }
        final List<Map<String, String>> extracted = new ArrayList<>();
        final List<String> rules = new ArrayList<>();
        for (final Object record : records) {
            final Map<String, String> values = mapping.extract().isEmpty()
                    ? null
                    : LocalCheck.extractedValues(mapping, record);
            extracted.add(values);
            rules.add(null);
        }
        final List<Taken> out = new ArrayList<>();
        for (final EventType type : catalogue.eventTypes()) {
            final Map<String, Integer> byRule = new LinkedHashMap<>();
            int notChecked = 0;
            int recognised = 0;
            for (int n = 0; n < records.size(); n++) {
                if (inScope != null && !inScope.test(n)) {
                    continue;
                }
                final Boolean ofType = recognises(mapping, type, records.get(n), extracted.get(n));
                if (ofType == null) {
                    notChecked++;
                    continue;
                }
                if (!ofType) {
                    continue;
                }
                recognised++;
                if (rules.get(n) == null) {
                    rules.set(n, Objects.requireNonNullElse(LocalCheck.ruleOf(mapping, records.get(n),
                            extracted.get(n) == null
                                    ? Map.of()
                                    : extracted.get(n)), "\u0000"));
                }
                if ("\u0000".equals(rules.get(n))) {
                    // A rule before it tests what can't be told here: which rule takes it isn't known.
                    notChecked++;
                    recognised--;
                    continue;
                }
                byRule.merge(rules.get(n), 1, Integer::sum);
            }
            out.add(new Taken(type, typeIdOf(type), recognised, byRule, notChecked));
        }
        return out;
    }

    /// Whether an event type recognises a record: one of its ways' conditions all hold; a sole event type (no way)
    /// recognises every record.
    ///
    /// @param mapping   The mapping the conditions are read by.
    /// @param type      The event type.
    /// @param record    The record.
    /// @param extracted Its extracted values, or null.
    /// @return Whether it does; null when it can't be told here (a condition the evaluator can't tell, no way
    /// holding).
    public static Boolean recognises(final TranslationMapping mapping,
                                     final EventType type,
                                     final Object record,
                                     final Map<String, String> extracted) {
        if (type.recognisedBy().isEmpty()) {
            return true;
        }
        boolean untold = false;
        for (final Way way : type.recognisedBy()) {
            final Boolean holds = holds(mapping, way.when(), record, extracted);
            if (Boolean.TRUE.equals(holds)) {
                return true;
            }
            untold |= holds == null;
        }
        return untold
                ? null
                : false;
    }

    /// @param mapping   The mapping the conditions are read by.
    /// @param when      Conditions, all of which must hold.
    /// @param record    A record.
    /// @param extracted Its extracted values, or null.
    /// @return Whether they all hold; null when one can't be told and none fails.
    public static Boolean holds(final TranslationMapping mapping,
                                final List<Condition> when,
                                final Object record,
                                final Map<String, String> extracted) {
        boolean untold = false;
        for (final Condition condition : when) {
            final Boolean one = LocalCheck.holds(mapping, condition, record, extracted);
            if (Boolean.FALSE.equals(one)) {
                return false;
            }
            untold |= one == null;
        }
        return untold
                ? null
                : true;
    }

    /// Why the catalogue's event types can't all be followed to the mapping's rules here, said rather than skipped:
    /// a mapping of repeated groups (`for_each`, whose item rules this can't evaluate), and records an event type's
    /// way, or a rule before the one taking them, tests by what can't be told here (an xpath, a dictionary, a
    /// record-scope field).
    ///
    /// @param mapping The mapping.
    /// @param taken   What [#taken] found.
    /// @param types   The event types checked (their TypeIds), or null for every one.
    /// @return What wasn't checked; empty when everything was.
    public static List<String> notChecked(final TranslationMapping mapping,
                                          final List<Taken> taken,
                                          final Set<String> types) {
        if (NullSafe.isNonBlankString(mapping.forEach())) {
            return List.of("event types not checked against the mapping's rules: it translates repeated groups "
                           + "(for_each '" + mapping.forEach() + "'), whose item rules this check can't evaluate; "
                           + "step records of each event type to see they are translated as confirmed");
        }
        final List<String> out = new ArrayList<>();
        for (final Taken one : taken) {
            if (one.notChecked() > 0 && (types == null || types.contains(one.typeId()))) {
                out.add("event type " + one.typeId() + ": " + one.notChecked() + " sample records not checked (a "
                        + "condition the check can't evaluate, an xpath, a dictionary or a record-scope field, "
                        + "recognises them or comes before the rule taking them)");
            }
        }
        return out;
    }

    /// What a rule writes, as the catalogue says an event type is translated.
    ///
    /// @param mapping The mapping.
    /// @param rule    One of its rules.
    /// @return Its action elements (Unknown among them when it writes one), fixed Action of each and fixed TypeId.
    public static Written written(final TranslationMapping mapping, final EventRule rule) {
        final Set<String> details = new LinkedHashSet<>();
        if (MappingFacts.writesUnknown(mapping, rule)) {
            details.add(UNKNOWN);
        }
        for (final FieldMapping entry : rule.fields()) {
            final String path = entry.strippedPath();
            if (path.startsWith(EVENT_DETAIL) && path.split("/").length >= PATH_PARTS) {
                details.add(path.split("/")[1]);
            }
        }
        final Map<String, String> actions = new LinkedHashMap<>();
        details.forEach(detail -> {
            final String action = fixedValue(mapping, rule, EVENT_DETAIL + detail + "/Action");
            if (action != null) {
                actions.put(detail, action);
            }
        });
        return new Written(List.copyOf(details), actions, fixedValue(mapping, rule, TYPE_ID));
    }

    /// @param mapping A mapping.
    /// @param name    A rule's name.
    /// @return The rule of that name, or null.
    public static EventRule rule(final TranslationMapping mapping, final String name) {
        return mapping.events().stream().filter(rule -> rule.name().equals(name)).findFirst().orElse(null);
    }

    /// @param type An event type.
    /// @return Its action element: the first part of its `event_detail` (Authenticate of `Authenticate/Logon`),
    /// Unknown when it gives none.
    public static String detailOf(final EventType type) {
        final String detail = ToolText.strip(NullSafe.string(type.eventDetail()).strip(), "/");
        final String first = detail.split("/")[0];
        return first.isEmpty()
                ? UNKNOWN
                : first;
    }

    /// @param type An event type.
    /// @return Its TypeId, or the default the translation writes when it has none ([TypeIds#defaultTypeId]).
    public static String typeIdOf(final EventType type) {
        return NullSafe.isNonBlankString(type.typeId())
                ? type.typeId()
                : TypeIds.defaultTypeId(type);
    }

    /// @param type An event type.
    /// @return How its records are recognised, in words: each way's conditions joined by "and", the ways by "or";
    /// "every record" for a sole event type.
    public static String recognisedBy(final EventType type) {
        if (type.recognisedBy().isEmpty()) {
            return "every record";
        }
        final List<String> ways = new ArrayList<>();
        for (final Way way : type.recognisedBy()) {
            ways.add(String.join(" and ", way.when().stream().map(MappingFacts::conditionWords).toList()));
        }
        return String.join("; or ", ways);
    }

    // The fixed value the rule (or the mapping's common entries) writes at the path, or null.
    private static String fixedValue(final TranslationMapping mapping, final EventRule rule, final String path) {
        for (final List<FieldMapping> entries : List.of(rule.fields(), mapping.common())) {
            for (final FieldMapping entry : entries) {
                if (path.equals(entry.strippedPath()) && entry.value() != null) {
                    return entry.value();
                }
            }
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    /// An event type's sampled records, by the rule that takes them.
    ///
    /// @param type       The event type.
    /// @param typeId     Its TypeId (or the default written for it).
    /// @param records    How many sampled records it recognises (and are followed to a rule).
    /// @param byRule     Their count by the rule taking them ([#NO_RULE] for none, [#DROP] and the reason for a
    ///                   drop rule), in the order met.
    /// @param notChecked How many records couldn't be told here.
    public record Taken(EventType type, String typeId, int records, Map<String, Integer> byRule, int notChecked) {

    }


    // --------------------------------------------------------------------------------


    /// What a rule writes.
    ///
    /// @param details The action elements it writes (Unknown among them when it writes one).
    /// @param actions The fixed Action each writes, by action element.
    /// @param typeId  The fixed TypeId it writes, or null when it writes none or one from a field.
    public record Written(List<String> details, Map<String, String> actions, String typeId) {

    }
}
