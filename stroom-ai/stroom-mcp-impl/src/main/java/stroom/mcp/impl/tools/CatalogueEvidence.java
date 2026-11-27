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

import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.CatalogueValidation;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.sample.KindFields;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The evidence `catalogue_event_types` gives the agent to propose a source's event types from, over the build's
/// scope (its source type group): no proposals of its own (product knowledge is the agent's; exact checking the
/// tools').
///
/// * **Fields that would tell records apart** ([KindFields]' value checks, with their exclusions: hosts, nodes,
///   severities, shippers, times), each with its values and their counts; never the build's source type field,
///   whose value is one source type's in every record of it.
/// * **Record shapes**: the distinct sets of fields present together, with counts (the check's shapes, over a
///   catalogue with no event types).
/// * **Example records**: per shape, those that differ most; rare records (a telling field's value seen once or
///   twice) always among them.
/// * **The seed** ([stroom.ai.impl.tools.catalogue.CatalogueSeed]): the draft's own rules as event types, marked as
///   the draft's (basis `draft`), for the agent to correct rather than start from nothing: nothing of it enters the
///   catalogue by itself.
final class CatalogueEvidence {

    // The most values said of a field.
    private static final int VALUES_SHOWN = 10;
    // A value seen this often or less is rare: its record is always an example.
    private static final int RARE = 2;
    // The most rare records given as examples.
    private static final int RARE_SHOWN = 8;
    // The most of a record's fields, and of a value's characters, an example shows.
    private static final int EXAMPLE_FIELDS = 20;
    private static final int EXAMPLE_VALUE = 100;

    private CatalogueEvidence() {
        // Static only.
    }

    /// @param records         The scope's records as the draft reads them, the fields inside held values among them.
    /// @param asIs            The check of a catalogue with no event types over the scope's records: every record
    ///                        uncatalogued, by shape.
    /// @param seed            The draft's seed.
    /// @param sourceTypeField The build's source type field, or null.
    /// @return The evidence: `fields`, `shapes`, `examples` and `seed`.
    @SuppressWarnings("unchecked")
    static Map<String, Object> of(final List<Map<String, Object>> records,
                                  final CatalogueCheckResult asIs,
                                  final List<EventType> seed,
                                  final String sourceTypeField) {
        final List<Map<String, Object>> telling = withoutSourceType(records, sourceTypeField);
        final List<KindFields.Candidate> candidates = KindFields.candidates(telling).candidates();
        final List<Map<String, Object>> fields = new ArrayList<>();
        final Set<Integer> rare = new LinkedHashSet<>();
        for (final KindFields.Candidate candidate : candidates) {
            final Map<String, Integer> counts = counts(telling, candidate.field());
            final Map<String, Object> field = new LinkedHashMap<>();
            field.put("field", candidate.field());
            field.put("records", candidate.records());
            final Map<String, Integer> shown = new LinkedHashMap<>();
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(VALUES_SHOWN)
                    .forEach(entry -> shown.put(entry.getKey(), entry.getValue()));
            field.put("values", shown);
            if (counts.size() > VALUES_SHOWN) {
                field.put("more_values", counts.size() - VALUES_SHOWN);
            }
            fields.add(field);
            counts.forEach((value, count) -> {
                if (count <= RARE) {
                    firstWith(telling, candidate.field(), value).ifPresent(rare::add);
                }
            });
        }
        final Map<String, Object> uncatalogued = (Map<String, Object>) asIs.toJson().get("uncatalogued");
        final List<Map<String, Object>> shapes = new ArrayList<>();
        final List<String> examples = new ArrayList<>();
        for (final Object item : (List<Object>) uncatalogued.getOrDefault("shapes", List.of())) {
            final Map<String, Object> shape = (Map<String, Object>) item;
            final Map<String, Object> kept = new LinkedHashMap<>();
            kept.put("signature", shape.get("signature"));
            kept.put("records", shape.get("records"));
            shapes.add(kept);
            for (final Object example : (List<Object>) shape.getOrDefault("examples", List.of())) {
                if (!examples.contains(String.valueOf(example))) {
                    examples.add(String.valueOf(example));
                }
            }
        }
        rare.stream().limit(RARE_SHOWN).forEach(index -> {
            final String said = said(records.get(index));
            if (!examples.contains(said)) {
                examples.add(said);
            }
        });
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("fields", fields);
        out.put("shapes", shapes);
        out.put("examples", examples);
        out.put("seed", seed.stream().map(EventType::toJson).toList());
        return out;
    }

    // The records without the build's source type field, as the records spell it.
    private static List<Map<String, Object>> withoutSourceType(final List<Map<String, Object>> records,
                                                               final String sourceTypeField) {
        if (sourceTypeField == null) {
            return records;
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map<String, Object> record : records) {
            final Map<String, Object> copy = new LinkedHashMap<>(record);
            copy.keySet().removeIf(field -> CatalogueValidation.onSourceTypeField(sourceTypeField, field));
            out.add(copy);
        }
        return out;
    }

    // Each value of the field, with the records holding it.
    private static Map<String, Integer> counts(final List<Map<String, Object>> records, final String field) {
        final Map<String, Integer> out = new LinkedHashMap<>();
        for (final Map<String, Object> record : records) {
            final Object value = record.get(field);
            if (value != null && !String.valueOf(value).isBlank()) {
                out.merge(String.valueOf(value), 1, Integer::sum);
            }
        }
        return out;
    }

    private static Optional<Integer> firstWith(final List<Map<String, Object>> records,
                                                         final String field,
                                                         final String value) {
        for (int i = 0; i < records.size(); i++) {
            if (value.equals(String.valueOf(records.get(i).get(field)))) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    // A record as `field=value` pairs, its first fields, each value cut short.
    private static String said(final Map<String, Object> record) {
        final List<String> pairs = new ArrayList<>();
        for (final Map.Entry<String, Object> entry : record.entrySet()) {
            final String value = entry.getValue() == null
                    ? ""
                    : String.valueOf(entry.getValue());
            if (value.isBlank()) {
                continue;
            }
            pairs.add(entry.getKey() + "=" + (value.length() <= EXAMPLE_VALUE
                    ? value
                    : value.substring(0, EXAMPLE_VALUE) + "…"));
            if (pairs.size() == EXAMPLE_FIELDS) {
                break;
            }
        }
        return String.join(" ", pairs);
    }
}
