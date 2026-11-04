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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.draft.DraftMapping.Records;
import stroom.ai.impl.tools.sample.SplitterSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/// The draft of records read by an `alternatives` splitter: each variant's records drafted on their own, as a
/// stream of that one format would be (its own time field and format, homes and kinds of event), then put together
/// as one mapping. What every variant writes alike stays common; the rest of a variant's common entries go into its
/// rules; and each rule is for its variant's records (`when` the variant equals it, before its own conditions).
/// Records no part read (kept by catch_all) are drafted the same way, `when` they have no variant.
///
/// Seen in testing: one draft over every variant's records took one time field and format for all of them, so the
/// records of the other formats were written with an empty time and failed the schema.
final class VariantDrafts {

    private static final String COMMON = "common";
    private static final String EVENTS = "events";
    private static final String FIELDS = "fields";
    private static final String WHEN = "when";
    private static final String NAME = "name";
    private static final String NOTES = "notes";
    // The name of the records no part read, as a rule's prefix.
    private static final String UNREAD = "unread";

    private VariantDrafts() {
        // Static only.
    }

    /// @param read    The records, read by an alternatives spec.
    /// @param drafter Drafts one variant's records as [DraftMapping#draft] would.
    /// @return What [DraftMapping#draft] returns, for every variant together.
    static Map<String, Object> draft(final Records read, final Function<Records, Map<String, Object>> drafter) {
        final SplitterSpec spec = read.spec();
        final String field = spec.variantField();
        final Map<String, List<Map<String, Object>>> byVariant = new LinkedHashMap<>();
        final Map<String, SplitterSpec> parts = new LinkedHashMap<>();
        spec.parts().forEach(part -> {
            byVariant.put(part.variant(), new ArrayList<>());
            parts.put(part.variant(), part);
        });
        final List<Map<String, Object>> unread = new ArrayList<>();
        for (final Map<String, Object> record : read.records()) {
            final Object variant = record.get(field);
            final Map<String, Object> own = new LinkedHashMap<>(record);
            own.remove(field);
            if (variant != null && byVariant.containsKey(String.valueOf(variant))) {
                byVariant.get(String.valueOf(variant)).add(own);
            } else {
                unread.add(own);
            }
        }
        final List<Drafted> drafts = new ArrayList<>();
        byVariant.forEach((variant, records) -> {
            if (!records.isEmpty()) {
                drafts.add(new Drafted(variant, drafter.apply(new Records(read.info(), records, parts.get(variant)))));
            }
        });
        if (!unread.isEmpty()) {
            drafts.add(new Drafted(null, drafter.apply(new Records(read.info(), unread, null))));
        }
        return merged(read, field, drafts);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> merged(final Records read, final String field, final List<Drafted> drafts) {
        final List<List<Map<String, Object>>> commons = drafts.stream()
                .map(drafted -> JsonValues.maps(drafted.mapping().get(COMMON)))
                .toList();
        // What every variant writes alike.
        final List<Map<String, Object>> common = commons.getFirst().stream()
                .filter(entry -> commons.stream().allMatch(entries -> entries.contains(entry)))
                .toList();
        final Map<String, Object> mapping = new LinkedHashMap<>(drafts.getFirst().mapping());
        mapping.put(COMMON, new ArrayList<>(common));
        final List<Map<String, Object>> events = new ArrayList<>();
        final List<Object> extract = new ArrayList<>();
        final List<Object> notes = new ArrayList<>();
        final Set<String> unmapped = new LinkedHashSet<>();
        final Set<String> seen = new LinkedHashSet<>(List.of(field));
        final List<Object> kinds = new ArrayList<>();
        Object sourceNotes = null;
        for (final Drafted drafted : drafts) {
            mergeRest(mapping, drafted.mapping());
            final String prefix = Objects.requireNonNullElse(drafted.variant(), UNREAD);
            final List<Map<String, Object>> own = JsonValues.maps(drafted.mapping().get(COMMON)).stream()
                    .filter(entry -> !common.contains(entry))
                    .toList();
            final Map<String, Object> condition = drafted.variant() == null
                    ? Map.of("field", field, "present", false)
                    : Map.of("field", field, "equals", drafted.variant());
            for (final Map<String, Object> rule : JsonValues.maps(drafted.mapping().get(EVENTS))) {
                events.add(forVariant(rule, prefix, condition, own));
            }
            for (final Object entry : JsonValues.maps(drafted.mapping().get("extract"))) {
                if (!extract.contains(entry)) {
                    extract.add(entry);
                }
            }
            for (final Object note : (List<Object>) drafted.result().getOrDefault(NOTES, List.of())) {
                final String said = "[" + prefix + "] " + note;
                if (!notes.contains(said)) {
                    notes.add(said);
                }
            }
            unmapped.addAll((List<String>) drafted.result().getOrDefault("unmapped_fields", List.of()));
            seen.addAll((List<String>) drafted.result().getOrDefault("fields_seen", List.of()));
            kinds.addAll((List<Object>) drafted.result().getOrDefault("kinds", List.of()));
            if (sourceNotes == null) {
                sourceNotes = drafted.result().get("source_notes");
            }
        }
        mapping.put(EVENTS, events);
        if (extract.isEmpty()) {
            mapping.remove("extract");
        } else {
            mapping.put("extract", extract);
        }
        notes.addFirst("Records in several formats: each format's records (its variant, in '" + field + "') are "
                       + "drafted on their own, so each has its own time and fields; a rule is for one variant's "
                       + "records (when " + field + " equals it), and what every variant writes alike is common.");
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("mapping", mapping);
        result.put("source_notes", sourceNotes);
        result.put("splitter", read.spec().toMap());
        result.put("format", read.info().get("format"));
        result.put("records", read.records().size());
        result.put("kinds", kinds);
        result.put(NOTES, notes);
        result.put("unmapped_fields", new ArrayList<>(unmapped));
        result.put("fields_seen", new ArrayList<>(seen));
        return result;
    }

    // The rest of a variant's mapping (nil_values, json_fields...): a list taken with its items once each, anything
    // else as the first variant has it.
    @SuppressWarnings("unchecked")
    private static void mergeRest(final Map<String, Object> mapping, final Map<String, Object> variant) {
        variant.forEach((key, value) -> {
            if (Set.of(COMMON, EVENTS, "extract").contains(key)) {
                return;
            }
            final Object had = mapping.get(key);
            if (had == null) {
                mapping.put(key, value);
            } else if (had instanceof final List<?> list && value instanceof final List<?> more) {
                final List<Object> union = new ArrayList<>(list);
                more.stream().filter(item -> !union.contains(item)).forEach(union::add);
                mapping.put(key, union);
            }
        });
    }

    // A variant's rule: named for its variant, for its records only, with the variant's own common entries it
    // doesn't write itself.
    private static Map<String, Object> forVariant(final Map<String, Object> rule,
                                                  final String prefix,
                                                  final Map<String, Object> condition,
                                                  final List<Map<String, Object>> own) {
        final Map<String, Object> out = new LinkedHashMap<>(rule);
        out.put(NAME, prefix + "_" + rule.get(NAME));
        final List<Object> when = new ArrayList<>(List.of(condition));
        if (rule.get(WHEN) instanceof final List<?> conditions) {
            when.addAll(conditions);
        }
        out.put(WHEN, when);
        final List<Map<String, Object>> fields = new ArrayList<>(JsonValues.maps(rule.get(FIELDS)));
        final Set<String> written = new LinkedHashSet<>();
        fields.forEach(entry -> written.add(entry.get("path") + "|" + entry.get("data_name")));
        final List<Map<String, Object>> withOwn = new ArrayList<>();
        own.stream().filter(entry -> !written.contains(entry.get("path") + "|" + entry.get("data_name")))
                .forEach(withOwn::add);
        withOwn.addAll(fields);
        out.put(FIELDS, withOwn);
        return out;
    }


    // --------------------------------------------------------------------------------


    // One variant's draft (null for the records no part read).
    private record Drafted(String variant, Map<String, Object> result) {

        @SuppressWarnings("unchecked")
        Map<String, Object> mapping() {
            return (Map<String, Object>) result.get("mapping");
        }
    }
}
