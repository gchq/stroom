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
import stroom.ai.impl.tools.catalogue.Way;
import stroom.ai.impl.tools.draft.CheckedRecords;
import stroom.ai.impl.tools.draft.VendorCode;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.SourceTypeScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/// The confirmed event types whose rules no longer write the vendor's code as Data ([VendorCode]): the build still
/// builds, and is warned, so the agent can put it back. Seen in eval cases 67 and 70 on Haiku: mappings saved whose
/// rules for an event type had lost the Data the draft wrote for its code. A rule is an event type's by the sampled
/// records it takes of each way of recognising the type ([CatalogueKept]), never by its name or conditions; the
/// catch-all is not an event type, and records going to it, to no rule or to a drop rule are
/// [ConfirmedEventTypes]' to say. An event type read back from an existing pipeline ([EventType#EXISTING_PIPELINE])
/// is held to nothing here: its rules write what that pipeline wrote.
final class VendorCodeKept {

    private VendorCodeKept() {
        // Static only.
    }

    /// @param mapping   The mapping.
    /// @param records   The sample's records.
    /// @param catalogue The build's catalogue, or null when it has none.
    /// @param scope     The build's (or its group's) source type scope, or null for none.
    /// @return A warning per confirmed event type and rule of it that doesn't write a code field as Data, naming
    /// the event type, the rule, the fields and the Data each is written as; none without a catalogue.
    static List<String> missing(final TranslationMapping mapping,
                                final List<Object> records,
                                final Catalogue catalogue,
                                final SourceTypeScope scope) {
        return missing(CheckedRecords.of(mapping, records), catalogue, scope);
    }

    /// As [#missing(TranslationMapping, List, Catalogue, SourceTypeScope)], over records whose extracted values,
    /// rules and ways' verdicts are worked out once for the check: each way followed on its own reads them once
    /// between them.
    ///
    /// @param checked   The sample's records, as the mapping reads them.
    /// @param catalogue The build's catalogue, or null when it has none.
    /// @param scope     The build's (or its group's) source type scope, or null for none.
    /// @return As the other.
    static List<String> missing(final CheckedRecords checked,
                                final Catalogue catalogue,
                                final SourceTypeScope scope) {
        final TranslationMapping mapping = checked.mapping();
        if (catalogue == null || checked.size() == 0) {
            return List.of();
        }
        final IntPredicate inScope = ConfirmedEventTypes.inScope(checked, scope);
        // Each way of each confirmed event type on its own, so a rule is held to its own way's code.
        final Map<String, Missing> missing = new LinkedHashMap<>();
        for (final EventType type : catalogue.eventTypes()) {
            // One read back from an existing pipeline writes what that pipeline wrote: its vendor's code was never
            // written as Data, so nothing was lost (seen in eval cases 102 and 105 on Haiku: warned of, the code was
            // added, and the new version's Events differed from the original's).
            if (!type.confirmed() || type.drop() || EventType.EXISTING_PIPELINE.equals(type.basis())) {
                continue;
            }
            final String typeId = CatalogueKept.typeIdOf(type);
            for (final Way way : type.recognisedBy()) {
                final List<String> codes = VendorCode.fields(way, catalogue);
                if (codes.isEmpty()) {
                    continue;
                }
                final EventType ofWay = oneWay(type, typeId, way);
                for (final CatalogueKept.Taken taken : CatalogueKept.taken(checked,
                        catalogue.withEventTypes(List.of(ofWay)), inScope)) {
                    for (final String ruleName : taken.byRule().keySet()) {
                        final EventRule rule = CatalogueKept.rule(mapping, ruleName);
                        if (rule == null || rule.catchAll() || rule.drop()) {
                            continue;
                        }
                        for (final String code : codes) {
                            if (!writes(mapping, rule, code)) {
                                missing.computeIfAbsent(typeId + "\u0000" + ruleName,
                                        key -> new Missing(typeId, ruleName, new LinkedHashSet<>())).fields()
                                        .add(code);
                            }
                        }
                    }
                }
            }
        }
        final List<String> warnings = new ArrayList<>();
        missing.values().forEach(one -> warnings.add(warning(mapping, one)));
        return warnings;
    }

    // The event type recognised by one of its ways alone, its TypeId as the whole type's (a default TypeId reads
    // the first way).
    private static EventType oneWay(final EventType type, final String typeId, final Way way) {
        return type.withTypeId(typeId).withRecognisedBy(List.of(way));
    }

    // True when the rule (or common) carries the field as Data: an entry reading it as Data, one of the Data name
    // its code is written under, or the rule's data list naming it.
    private static boolean writes(final TranslationMapping mapping, final EventRule rule, final String code) {
        if (rule.data() != null && rule.data().contains(code)) {
            return true;
        }
        final String name = VendorCode.dataName(code, jsonKeys(mapping, code));
        final List<FieldMapping> entries = new ArrayList<>();
        if (rule.fields() != null) {
            entries.addAll(rule.fields());
        }
        if (mapping.common() != null) {
            entries.addAll(mapping.common());
        }
        return entries.stream().anyMatch(entry -> entry.dataName() != null
                                                  && (code.equals(entry.field()) || name.equals(entry.dataName())));
    }

    private static boolean jsonKeys(final TranslationMapping mapping, final String code) {
        return "json".equals(mapping.input()) || MappingFacts.heldJsonField(mapping.jsonFields(), code) != null;
    }

    private static String warning(final TranslationMapping mapping, final Missing one) {
        final List<String> said = one.fields().stream()
                .map(code -> code + " (as Data named '" + VendorCode.dataName(code, jsonKeys(mapping, code)) + "')")
                .toList();
        return "confirmed event type " + one.typeId() + ": rule '" + one.rule() + "' no longer writes the vendor's "
               + "code of its records, " + String.join(", ", said) + ", so searching by that code won't find "
               + "them: put it back (changes= a fields entry {\"path\": \"EventDetail/<action element>/Data\", "
               + "\"data_name\": ..., \"field\": ...} in that rule)";
    }


    // --------------------------------------------------------------------------------


    // A confirmed event type's rule, and the code fields it doesn't write as Data.
    private record Missing(String typeId, String rule, Set<String> fields) {

    }
}
