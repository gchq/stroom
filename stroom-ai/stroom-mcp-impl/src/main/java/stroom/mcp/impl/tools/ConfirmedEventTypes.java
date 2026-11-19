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

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueKept;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

/// The event types the user confirmed (the catalogue's, `status: confirmed`) that a mapping no longer translates as
/// confirmed: every sampled record a confirmed event type recognises (its ways, by the one evaluator) must be
/// translated by that type's rules, with its action element, its fixed Action and its TypeId; records taken by no
/// rule, dropped, or taken by a rule writing another action element (Unknown among them), Action or TypeId are lost.
/// Event types the user dropped must have their records dropped. Rules are compared with event types by the records
/// they take ([CatalogueKept]), never by condition text. Seen in the chat 163 review: 200 "Session N logged out"
/// records confirmed as Logoff went to Unknown, and a type confirmed as Action Other became Logon, without the user
/// being told.
final class ConfirmedEventTypes {

    private ConfirmedEventTypes() {
        // Static only.
    }

    /// The confirmed event types whose sampled records the mapping translates otherwise than confirmed.
    ///
    /// @param mapping   The mapping.
    /// @param records   The sample's records, as [LocalCheck#sampleRecords] reads them.
    /// @param catalogue The build's catalogue, or null when it has none.
    /// @return One per event type and rule its records go to otherwise than confirmed, in the catalogue's order;
    /// none without a catalogue or records, or when the mapping reads for_each ([#notChecked] says so).
    static List<Lost> lost(final TranslationMapping mapping, final List<Object> records, final Catalogue catalogue) {
        return lost(mapping, records, catalogue, null, null);
    }

    /// As [#lost(TranslationMapping, List, Catalogue)], within a source type scope (the records of source types
    /// outside it are not this build's to compare; seen in eval 58, a group build asked to keep the event types
    /// of source types parked for later builds), each confirmed Action read against the schema: one the schema
    /// doesn't allow under its action element can't be written, so a rule writing an allowed Action there instead
    /// is not translating the type otherwise (seen in eval 60: "Add member to role" confirmed as an Authorise
    /// Action, which takes only Request or Modify).
    ///
    /// @param mapping   The mapping.
    /// @param records   The sample's records.
    /// @param catalogue The build's catalogue, or null.
    /// @param scope     The build's (or its group's) source type scope, or null for none.
    /// @param schema    The event-logging schema, or null when not known (each Action compared as confirmed).
    /// @return As the other.
    static List<Lost> lost(final TranslationMapping mapping,
                           final List<Object> records,
                           final Catalogue catalogue,
                           final SourceTypeScope scope,
                           final EventSchema schema) {
        if (catalogue == null || records.isEmpty()
            || catalogue.eventTypes().stream().noneMatch(type -> type.confirmed() && !type.drop())) {
            return List.of();
        }
        final Catalogue confirmed = catalogue.withEventTypes(catalogue.eventTypes().stream()
                .filter(type -> type.confirmed() && !type.drop())
                .toList());
        final List<Lost> lost = new ArrayList<>();
        for (final CatalogueKept.Taken taken : CatalogueKept.taken(mapping, confirmed, records,
                inScope(mapping, records, scope))) {
            taken.byRule().forEach((rule, count) -> otherwise(mapping, taken.type(), taken.typeId(), rule, schema)
                    .ifPresent(writes -> lost.add(new Lost(taken.typeId(), NullSafe.string(taken.type().title()),
                            CatalogueKept.recognisedBy(taken.type()), wanted(taken.type(), taken.typeId()), rule,
                            writes, count))));
        }
        return lost;
    }

    /// The event types the user said to leave untranslated whose sampled records a rule still translates (the
    /// mapping has no drop rule for them, or a rule before it takes them).
    ///
    /// @param mapping   The mapping.
    /// @param records   The sample's records.
    /// @param catalogue The build's catalogue, or null.
    /// @return A warning each, naming the event type, how many records and the rule; none when every one is dropped.
    static List<String> undropped(final TranslationMapping mapping,
                                  final List<Object> records,
                                  final Catalogue catalogue) {
        if (catalogue == null || records.isEmpty() || catalogue.eventTypes().stream().noneMatch(EventType::drop)) {
            return List.of();
        }
        final Catalogue dropped = catalogue.withEventTypes(catalogue.eventTypes().stream()
                .filter(EventType::drop)
                .toList());
        final List<String> warnings = new ArrayList<>();
        for (final CatalogueKept.Taken taken : CatalogueKept.taken(mapping, dropped, records, null)) {
            taken.byRule().forEach((rule, count) -> {
                if (!rule.startsWith(CatalogueKept.DROP)) {
                    warnings.add("dropped event type " + taken.typeId() + ": " + count + " sample records are "
                                 + "translated by " + (rule.isEmpty()
                            ? "no rule (taken by none)"
                            : "rule '" + rule + "'") + ", though the user said to leave them untranslated: give "
                                 + "them a drop rule (drop_when) before it");
                }
            });
        }
        return warnings;
    }

    /// What of the confirmed and dropped event types couldn't be checked against the mapping's rules, said rather
    /// than skipped: a mapping of repeated groups (for_each), or records told by what the check can't evaluate.
    ///
    /// @param mapping   The mapping.
    /// @param records   The sample's records.
    /// @param catalogue The build's catalogue, or null.
    /// @return The notes; empty when everything was checked, or nothing was confirmed.
    static List<String> notChecked(final TranslationMapping mapping,
                                   final List<Object> records,
                                   final Catalogue catalogue) {
        if (catalogue == null || records.isEmpty()) {
            return List.of();
        }
        final Set<String> confirmed = new LinkedHashSet<>();
        catalogue.eventTypes().stream()
                .filter(type -> type.confirmed() || type.drop())
                .forEach(type -> confirmed.add(CatalogueKept.typeIdOf(type)));
        if (confirmed.isEmpty()) {
            return List.of();
        }
        return CatalogueKept.notChecked(mapping, CatalogueKept.taken(mapping, catalogue, records, null), confirmed);
    }

    /// @param lost The confirmed event types translated otherwise.
    /// @return Each one's key, for what the user agreed to keep with the XSLT: event type and rule to what the rule
    /// writes.
    static Map<String, String> keys(final List<Lost> lost) {
        final Map<String, String> keys = new LinkedHashMap<>();
        lost.forEach(one -> keys.put(one.key(), one.writes()));
        return keys;
    }

    /// The confirmed event types translated otherwise, for the chat: a table, one row an event type and the rule its
    /// records go to.
    ///
    /// @param lost    The confirmed event types translated otherwise.
    /// @param records The sample's records, counted.
    /// @return The markdown.
    static String markdown(final List<Lost> lost, final Object records) {
        final List<String> lines = new ArrayList<>(List.of("**Event types you confirmed that this mapping "
                                                           + "translates otherwise**", "",
                "| Event type | Recognised by | You confirmed | Records (of " + records + ") | Now | Rule |",
                "| --- | --- | --- | --- | --- | --- |"));
        lost.forEach(one -> lines.add("| " + cell(one.label()) + " | " + cell(one.recognisedBy()) + " | "
                                      + cell(one.wanted()) + " | " + one.records() + " | " + cell(one.writes())
                                      + " | " + cell(one.rule().isEmpty()
                ? "none"
                : one.rule()) + " |"));
        return String.join("\n", lines) + "\n";
    }

    /// @param lost The confirmed event types translated otherwise.
    /// @param rule A rule's name.
    /// @return What of them the rule takes, for the Unknown review: each event type and what the user confirmed it
    /// as.
    static List<String> takenBy(final List<Lost> lost, final String rule) {
        return lost.stream()
                .filter(one -> one.rule().equals(rule))
                .map(one -> one.label() + " (" + one.records() + " records), confirmed as " + one.wanted())
                .toList();
    }

    /// @param mapping The mapping.
    /// @param records The sample's records.
    /// @param scope   The build's (or its group's) source type scope, or null for none.
    /// @return Which records (by index) are of the scope's source types: every record without a scope, and a record
    /// not telling its source type; null for every record.
    static IntPredicate inScope(final TranslationMapping mapping,
                                        final List<Object> records,
                                        final SourceTypeScope scope) {
        if (scope == null || scope.names() == null || NullSafe.isBlankString(scope.field())) {
            return null;
        }
        return n -> {
            final Object record = records.get(n);
            final Map<String, String> extracted = mapping.extract().isEmpty()
                    ? Map.of()
                    : NullSafe.map(LocalCheck.extractedValues(mapping, record));
            final String value = valueOf(record, extracted, scope.field());
            if (NullSafe.isBlankString(value)) {
                return true;
            }
            return SourceTypeRows.first(scope.rows(), value)
                    .map(SourceTypeRows.Row::name)
                    .filter(scope.names()::contains)
                    .isPresent();
        };
    }

    // What the rule writes for the event type's records, when not what the user confirmed; none when it is.
    private static Optional<String> otherwise(final TranslationMapping mapping,
                                              final EventType type,
                                              final String typeId,
                                              final String ruleName,
                                              final EventSchema schema) {
        if (CatalogueKept.NO_RULE.equals(ruleName)) {
            return Optional.of("taken by no rule");
        }
        if (ruleName.startsWith(CatalogueKept.DROP)) {
            return Optional.of("dropped (" + ruleName.substring(CatalogueKept.DROP.length()) + ")");
        }
        final EventRule rule = CatalogueKept.rule(mapping, ruleName);
        if (rule == null) {
            return Optional.empty();
        }
        final CatalogueKept.Written written = CatalogueKept.written(mapping, rule);
        final String want = CatalogueKept.detailOf(type);
        if (!written.details().contains(want)) {
            return Optional.of(written.details().isEmpty()
                    ? "no action element"
                    : String.join(", ", written.details()));
        }
        final String action = NullSafe.string(type.action());
        if (!action.isEmpty() && !CatalogueKept.UNKNOWN.equals(want)) {
            final String writtenAction = written.actions().get(want);
            if (writtenAction != null && !writtenAction.equals(action)
                && !writableOtherwise(schema, want, action, writtenAction)) {
                return Optional.of(want + ", Action " + writtenAction);
            }
        }
        if (written.typeId() != null && !written.typeId().equals(typeId)) {
            return Optional.of("TypeId " + written.typeId());
        }
        return Optional.empty();
    }

    // True when the confirmed Action can't be written as the user confirmed it and the rule writes the schema's
    // spelling of it, or an Action the schema allows in place of one it doesn't: following what was confirmed would
    // fail validation, so the rule is not translating the type otherwise. False without a schema.
    private static boolean writableOtherwise(final EventSchema schema,
                                             final String detail,
                                             final String confirmed,
                                             final String written) {
        if (schema == null) {
            return false;
        }
        final Optional<String> allowedConfirmed = SourceNotes.allowedAction(schema, detail, confirmed);
        final Optional<String> allowedWritten = SourceNotes.allowedAction(schema, detail, written);
        if (allowedWritten.isEmpty()) {
            return false;
        }
        return allowedConfirmed.isEmpty() || allowedConfirmed.get().equals(written);
    }

    private static String valueOf(final Object record, final Map<String, String> extracted, final String field) {
        if (record instanceof final Map<?, ?> map && map.get(field) != null) {
            return String.valueOf(map.get(field));
        }
        return extracted.get(field);
    }

    // What the user confirmed: the action element, its Action, and the TypeId.
    private static String wanted(final EventType type, final String typeId) {
        final String detail = CatalogueKept.detailOf(type);
        final String action = NullSafe.string(type.action());
        return (action.isEmpty() || CatalogueKept.UNKNOWN.equals(detail)
                ? detail
                : detail + ", Action " + action) + ", TypeId " + typeId;
    }

    private static String cell(final String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }


    // --------------------------------------------------------------------------------


    /// A confirmed event type its records' rule translates otherwise.
    ///
    /// @param typeId       The event type's TypeId.
    /// @param title        Its title ("" when it has none).
    /// @param recognisedBy How its records are recognised, in words.
    /// @param wanted       What the user confirmed, e.g. `Authenticate, Action Logoff, TypeId Authenticate-Logoff`.
    /// @param rule         The rule its records go to ("" when none).
    /// @param writes       What the rule does with them instead, e.g. `Unknown`.
    /// @param records      How many of the sample's records.
    record Lost(String typeId,
                String title,
                String recognisedBy,
                String wanted,
                String rule,
                String writes,
                int records) {

        /// @return The event type and rule, as what the user agreed to is kept.
        String key() {
            return typeId + " in " + (rule.isEmpty()
                    ? "(no rule)"
                    : rule);
        }

        /// @return The event type as the user knows it: its TypeId, and its title.
        String label() {
            return title.isBlank()
                    ? typeId
                    : typeId + " (" + title + ")";
        }
    }
}
