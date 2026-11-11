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

import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.draft.SourceNotes;
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
import java.util.Optional;
import java.util.Set;

/// The kinds of event the user confirmed (classify_kinds' `user_confirmed` catalogue events) that a mapping no longer
/// translates as confirmed: their sample records taken by no rule, dropped, or taken by a rule that writes another
/// action element (Unknown among them) or another Action. Seen in the chat 163 review: 200 "Session N logged out"
/// records confirmed as Logoff went to Unknown, and a kind confirmed as Action Other became Logon, without the user
/// being told.
final class ConfirmedKinds {

    static final String UNKNOWN = "Unknown";
    private static final String EVENT_DETAIL = "EventDetail/";
    private static final String DROP = "drop: ";
    private static final String NO_RULE = "";
    private static final int PATH_PARTS = 3;

    private ConfirmedKinds() {
        // Static only.
    }

    /// The confirmed kinds whose sample records the mapping translates otherwise than confirmed.
    ///
    /// @param mapping The mapping.
    /// @param records The sample's records, as [LocalCheck#sampleRecords] reads them.
    /// @param events  The catalogue events of the build's source notes; only those the user confirmed are checked.
    /// @return One per kind and rule its records go to otherwise than confirmed, in the catalogue's order; none
    /// when no records are held, or the mapping reads for_each (its records aren't the kinds' records).
    static List<Lost> lost(final TranslationMapping mapping,
                           final List<Object> records,
                           final List<Map<String, Object>> events) {
        final List<Map<String, Object>> confirmed = events.stream()
                .filter(event -> RecordSourceNotesTool.USER_CONFIRMED.equals(event.get("basis")))
                .filter(event -> NullSafe.isNonBlankString(text(event.get("field")))
                                 && (event.get("value") != null || SourceNotes.isPattern(event)))
                .toList();
        // Each record's rule is worked out only when there is something to check it against.
        if (confirmed.isEmpty() || records.isEmpty() || NullSafe.isNonBlankString(mapping.forEach())) {
            return List.of();
        }
        final List<Map<String, String>> extracted = new ArrayList<>();
        final List<String> rules = new ArrayList<>();
        for (final Object record : records) {
            final Map<String, String> values = mapping.extract().isEmpty()
                    ? Map.of()
                    : NullSafe.map(LocalCheck.extractedValues(mapping, record));
            extracted.add(values);
            rules.add(LocalCheck.ruleOf(mapping, record, values));
        }
        final List<Lost> lost = new ArrayList<>();
        for (final Map<String, Object> event : confirmed) {
            final String field = text(event.get("field"));
            final boolean pattern = SourceNotes.isPattern(event);
            final String value = pattern
                    ? "~" + text(event.get(SourceNotes.MATCHES))
                    : text(event.get("value"));
            // The rule each of the kind's records goes to, counted.
            final Map<String, Integer> byRule = new LinkedHashMap<>();
            for (int n = 0; n < records.size(); n++) {
                final String held = valueOf(records.get(n), extracted.get(n), field);
                final boolean ofKind = pattern
                        ? SourceNotes.matches(event, held)
                        : value.equals(held);
                if (ofKind && rules.get(n) != null) {
                    byRule.merge(rules.get(n), 1, Integer::sum);
                }
            }
            byRule.forEach((rule, count) -> otherwise(mapping, event, rule).ifPresent(writes ->
                    lost.add(new Lost(kindName(event), field, value, wanted(event), rule, writes, count))));
        }
        return lost;
    }

    /// The kinds the user said to leave untranslated whose sample records a rule still translates (the mapping has no
    /// drop rule for them, or a rule before it takes them).
    ///
    /// @param mapping The mapping.
    /// @param records The sample's records.
    /// @param dropped The dropped kinds' keys ([SourceNotes#DROPPED]).
    /// @return A warning each, naming the kind, how many records and the rule; none when every one is dropped.
    static List<String> undropped(final TranslationMapping mapping,
                                  final List<Object> records,
                                  final List<String> dropped) {
        if (dropped.isEmpty() || records.isEmpty() || NullSafe.isNonBlankString(mapping.forEach())) {
            return List.of();
        }
        final List<String> warnings = new ArrayList<>();
        for (final String key : dropped) {
            final List<Map<String, Object>> when = SourceNotes.dropConditions(key);
            if (when.isEmpty()) {
                continue;
            }
            final Map<String, Integer> byRule = new LinkedHashMap<>();
            for (final Object record : records) {
                final Map<String, String> values = mapping.extract().isEmpty()
                        ? Map.of()
                        : NullSafe.map(LocalCheck.extractedValues(mapping, record));
                final boolean ofKind = when.stream().allMatch(condition -> {
                    final String held = valueOf(record, values, text(condition.get("field")));
                    return condition.get(SourceNotes.MATCHES) != null
                            ? SourceNotes.matches(condition, held)
                            : held != null && held.equals(text(condition.get("equals")));
                });
                final String rule = ofKind
                        ? LocalCheck.ruleOf(mapping, record, values)
                        : null;
                if (rule != null && !rule.startsWith(DROP)) {
                    byRule.merge(rule.isEmpty()
                            ? "(no rule)"
                            : rule, 1, Integer::sum);
                }
            }
            byRule.forEach((rule, count) -> warnings.add("dropped kind " + key + ": " + count + " sample records are "
                                                         + "translated by rule '" + rule + "', though the user said "
                                                         + "to leave them untranslated: give them a drop rule "
                                                         + "(drop_when) before it"));
        }
        return warnings;
    }

    /// @param lost The confirmed kinds translated otherwise.
    /// @return Each one's key, for what the user agreed to keep with the XSLT: kind and rule to what the rule writes.
    static Map<String, String> keys(final List<Lost> lost) {
        final Map<String, String> keys = new LinkedHashMap<>();
        lost.forEach(one -> keys.put(one.key(), one.writes()));
        return keys;
    }

    /// The confirmed kinds translated otherwise, for the chat: a table, one row a kind and the rule it goes to.
    ///
    /// @param lost    The confirmed kinds translated otherwise.
    /// @param records The sample's records, counted.
    /// @return The markdown.
    static String markdown(final List<Lost> lost, final Object records) {
        final List<String> lines = new ArrayList<>(List.of("**Kinds you confirmed that this mapping translates "
                                                           + "otherwise**", "",
                "| Kind | You confirmed | Records (of " + records + ") | Now | Rule |",
                "| --- | --- | --- | --- | --- |"));
        lost.forEach(one -> lines.add("| " + cell(one.kind() + " (" + one.field() + "=" + one.value() + ")") + " | "
                                      + cell(one.wanted()) + " | " + one.records() + " | " + cell(one.writes())
                                      + " | " + cell(one.rule().isEmpty()
                ? "none"
                : one.rule()) + " |"));
        return String.join("\n", lines) + "\n";
    }

    /// @param lost The confirmed kinds translated otherwise.
    /// @param rule A rule's name.
    /// @return What of them the rule takes, for the Unknown review: each kind and what the user confirmed it as.
    static List<String> takenBy(final List<Lost> lost, final String rule) {
        return lost.stream()
                .filter(one -> one.rule().equals(rule))
                .map(one -> one.kind() + " (" + one.records() + " records), confirmed as " + one.wanted())
                .toList();
    }

    // What the rule writes for the event's records, when not what the user confirmed; none when it is.
    private static Optional<String> otherwise(final TranslationMapping mapping,
                                                        final Map<String, Object> event,
                                                        final String ruleName) {
        if (NO_RULE.equals(ruleName)) {
            return Optional.of("taken by no rule");
        }
        if (ruleName.startsWith(DROP)) {
            return Optional.of("dropped (" + ruleName.substring(DROP.length()) + ")");
        }
        final EventRule rule = mapping.events().stream()
                .filter(candidate -> candidate.name().equals(ruleName))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            return Optional.empty();
        }
        final String want = wantedDetail(event);
        final Set<String> details = details(mapping, rule);
        if (!want.isEmpty() && !details.contains(want)) {
            return Optional.of(details.isEmpty()
                    ? "no action element"
                    : String.join(", ", details));
        }
        final String action = text(event.get("action"));
        if (!action.isEmpty() && !want.isEmpty() && !UNKNOWN.equals(want)) {
            final String written = fixedValue(mapping, rule, EVENT_DETAIL + want + "/Action");
            if (written != null && !written.equals(action)) {
                return Optional.of(want + ", Action " + written);
            }
        }
        return Optional.empty();
    }

    // The action elements the rule writes (Unknown when it writes EventDetail/Unknown).
    private static Set<String> details(final TranslationMapping mapping, final EventRule rule) {
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
        return details;
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

    private static String valueOf(final Object record, final Map<String, String> extracted, final String field) {
        if (record instanceof final Map<?, ?> map && map.get(field) != null) {
            return String.valueOf(map.get(field));
        }
        return extracted.get(field);
    }

    // The action element the user confirmed (its first part: Authenticate of Authenticate/Logon).
    private static String wantedDetail(final Map<String, Object> event) {
        final String detail = text(event.get("event_detail")).strip();
        final String trimmed = detail.startsWith("/")
                ? detail.substring(1)
                : detail;
        return trimmed.split("/")[0];
    }

    private static String wanted(final Map<String, Object> event) {
        final String detail = wantedDetail(event);
        final String action = text(event.get("action"));
        return action.isEmpty() || UNKNOWN.equals(detail)
                ? NullSafe.nonBlankStringElse(detail, "no action element")
                : detail + ", Action " + action;
    }

    private static String kindName(final Map<String, Object> event) {
        return NullSafe.nonBlankStringElse(text(event.get("event")), text(event.get("value")));
    }

    private static String cell(final String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }

    private static String text(final Object value) {
        return value == null
                ? ""
                : String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// A confirmed kind its records' rule translates otherwise.
    ///
    /// @param kind    The catalogue event's name.
    /// @param field   The field that tells the kind.
    /// @param value   Its value.
    /// @param wanted  What the user confirmed, e.g. `Authenticate, Action Logoff`.
    /// @param rule    The rule its records go to ("" when none).
    /// @param writes  What the rule does with them instead, e.g. `Unknown`.
    /// @param records How many of the sample's records.
    record Lost(String kind, String field, String value, String wanted, String rule, String writes, int records) {

        /// @return The kind and rule, as what the user agreed to is kept.
        String key() {
            return field + "=" + value + " in " + (rule.isEmpty()
                    ? "(no rule)"
                    : rule);
        }
    }
}
