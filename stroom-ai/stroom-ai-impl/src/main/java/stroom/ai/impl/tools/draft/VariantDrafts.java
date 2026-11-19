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
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.TimePatterns;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

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
    // The field the splitter writes a header's date in on each record after it.
    private static final String HEADER_DATE = SplitterDryRun.CARRIED_DATE;
    private static final String TIME = "time";
    private static final String TIME_CREATED = "EventTime/TimeCreated";
    // A time of day with no date (a fraction of a second at most).
    private static final Pattern DATELESS = Pattern.compile("\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?");
    // The name of the records no part read, as a rule's prefix.
    private static final String UNREAD = "unread";

    private VariantDrafts() {
        // Static only.
    }

    /// @param read    The records, read by an alternatives spec.
    /// @param drafter Drafts one variant's records as [DraftMapping#draft] would, given the variant (null for the
    ///                records no part read) and its records.
    /// @return What [DraftMapping#draft] returns, for every variant together.
    static Map<String, Object> draft(final Records read,
                                     final BiFunction<String, Records, Map<String, Object>> drafter) {
        final SplitterSpec spec = read.spec();
        final Map<String, SplitterSpec> parts = new LinkedHashMap<>();
        spec.parts().forEach(part -> parts.put(part.variant(), part));
        return draft(read, spec.variantField(), parts, drafter);
    }

    /// As [#draft(Records, Function)], for records a saved converter read: each variant's records drafted with the
    /// converter's spec.
    ///
    /// @param read     The records, read by a saved converter.
    /// @param variants The formats it reads.
    /// @param drafter  Drafts one variant's records as [DraftMapping#draft] would, given the variant and its
    ///                 records.
    /// @return What [DraftMapping#draft] returns, for every variant together.
    static Map<String, Object> draft(final Records read,
                                     final SplitterSpec.ConverterVariants variants,
                                     final BiFunction<String, Records, Map<String, Object>> drafter) {
        final Map<String, SplitterSpec> parts = new LinkedHashMap<>();
        variants.variants().forEach(variant -> parts.put(variant, read.spec()));
        return draft(read, variants.field(), parts, drafter);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> draft(final Records read,
                                             final String field,
                                             final Map<String, SplitterSpec> parts,
                                             final BiFunction<String, Records, Map<String, Object>> drafter) {
        final Map<String, List<Map<String, Object>>> byVariant = new LinkedHashMap<>();
        parts.keySet().forEach(variant -> byVariant.put(variant, new ArrayList<>()));
        final List<Map<String, Object>> unread = new ArrayList<>();
        for (final Map<String, Object> record : read.records()) {
            final Object variant = record.get(field);
            final Map<String, Object> own = new LinkedHashMap<>(record);
            own.remove(field);
            // The header's date, read only by the time it is joined with.
            own.remove(HEADER_DATE);
            if (variant != null && byVariant.containsKey(String.valueOf(variant))) {
                byVariant.get(String.valueOf(variant)).add(own);
            } else {
                unread.add(own);
            }
        }
        // A header line giving the date of the lines after it is no kind of event: its date is kept for them.
        final List<String> headerDates = byVariant.getOrDefault(SplitterDryRun.HEADER_VARIANT, List.of()).stream()
                .map(record -> record.get(SplitterDryRun.HEADER_DATE))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        byVariant.remove(SplitterDryRun.HEADER_VARIANT);
        final List<Drafted> drafts = new ArrayList<>();
        byVariant.forEach((variant, records) -> {
            if (!records.isEmpty()) {
                drafts.add(new Drafted(variant, drafter.apply(variant, new Records(read.info(), records,
                        parts.get(variant))),
                        records));
            }
        });
        if (!unread.isEmpty()) {
            drafts.add(new Drafted(null, drafter.apply(null, new Records(read.info(), unread, null)), unread));
        }
        final Map<String, Object> result = merged(read, field, drafts, headerDates);
        if (!headerDates.isEmpty()) {
            headed((Map<String, Object>) result.get("mapping"), field, (List<Object>) result.get(NOTES));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> merged(final Records read,
                                              final String field,
                                              final List<Drafted> drafts,
                                              final List<String> headerDates) {
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
        final List<Map<String, Object>> timeCandidates = new ArrayList<>();
        final Map<String, List<String>> timeExamples = new LinkedHashMap<>();
        final Set<String> zoneNames = new LinkedHashSet<>();
        // A variant's field of zone names (the first found).
        final String[] zoneField = new String[1];
        // The variants whose times have no date and no header to give it, with their time's format.
        final Map<String, String> datelessRules = new LinkedHashMap<>();
        Object sourceNotes = null;
        for (final Drafted drafted : drafts) {
            mergeRest(mapping, drafted.mapping());
            final String prefix = Objects.requireNonNullElse(drafted.variant(), UNREAD);
            final List<Map<String, Object>> own = new ArrayList<>(JsonValues.maps(drafted.mapping().get(COMMON))
                    .stream()
                    .filter(entry -> !common.contains(entry))
                    .toList());
            final Optional<Map<String, Object>> dateless = headerDates.isEmpty()
                    ? Optional.empty()
                    : datelessTime(drafted, headerDates.getFirst());
            if (headerDates.isEmpty()) {
                timeOfDay(drafted).ifPresent(format -> datelessRules.put(prefix, format));
            }
            dateless.ifPresent(time -> {
                own.removeIf(entry -> TIME_CREATED.equals(entry.get("path")));
                own.addFirst(time);
            });
            final Map<String, Object> condition = drafted.variant() == null
                    ? Map.of("field", field, "present", false)
                    : Map.of("field", field, "equals", drafted.variant());
            for (final Map<String, Object> rule : JsonValues.maps(drafted.mapping().get(EVENTS))) {
                if (belongsTo(rule, field, drafted, drafts)) {
                    events.add(forVariant(rule, prefix, condition, own));
                }
            }
            for (final Map<String, Object> entry : JsonValues.maps(drafted.mapping().get("extract"))) {
                if (!extract.contains(entry)) {
                    extract.add(entry);
                }
            }
            for (final Object note : (List<Object>) drafted.result().getOrDefault(NOTES, List.of())) {
                if (dateless.isPresent() && String.valueOf(note).startsWith("No timestamp field was recognised")) {
                    // Its time is the header's date joined with its own.
                    continue;
                }
                final String said = "[" + prefix + "] " + note;
                if (!notes.contains(said)) {
                    notes.add(said);
                }
            }
            unmapped.addAll((List<String>) drafted.result().getOrDefault("unmapped_fields", List.of()));
            if (drafted.result().get(DraftMapping.TIME_ZONES) instanceof final Map<?, ?> zones) {
                timeExamples.putAll((Map<String, List<String>>) zones.get("examples"));
                zoneNames.addAll((List<String>) zones.get("zone_names"));
                if (zoneField[0] == null && zones.get("zone_field") instanceof final String named) {
                    zoneField[0] = named;
                }
            }
            seen.addAll((List<String>) drafted.result().getOrDefault("fields_seen", List.of()));
            kinds.addAll((List<Object>) drafted.result().getOrDefault("kinds", List.of()));
            // A choice of time put to the agent: for the variant's rule, named as merged, or for its records.
            for (final Map<String, Object> candidate : JsonValues.maps(drafted.result().get(
                    DraftMapping.TIME_CANDIDATES))) {
                final Map<String, Object> named = new LinkedHashMap<>();
                if (candidate.get("rule") != null) {
                    named.put("rule", prefix + "_" + candidate.get("rule"));
                } else {
                    named.put("variant", prefix);
                }
                candidate.forEach((key, value) -> {
                    if (!"rule".equals(key)) {
                        named.put(key, value);
                    }
                });
                timeCandidates.add(named);
            }
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
        if (!timeCandidates.isEmpty()) {
            result.put(DraftMapping.TIME_CANDIDATES, timeCandidates);
        }
        if (!timeExamples.isEmpty() || !zoneNames.isEmpty()) {
            result.put(DraftMapping.TIME_ZONES, DraftMapping.timeZones(timeExamples, List.copyOf(zoneNames),
                    zoneField[0]));
        }
        if (!datelessRules.isEmpty()) {
            // For the user to say where the date comes from (draft_translation_mapping asks).
            // In a fixed order (Map.of's differs from one run to the next).
            final Map<String, Object> dateless = new LinkedHashMap<>();
            dateless.put("field", TIME);
            dateless.put("variant_field", field);
            dateless.put("variants", datelessRules);
            result.put(DraftMapping.DATELESS_TIMES, dateless);
        }
        result.put("fields_seen", new ArrayList<>(seen));
        return result;
    }

    // Whether a variant keeps a rule its draft wrote (a way of recognising an event type, from the catalogue every
    // variant's draft follows): the variant's whose sampled records it recognises, evaluated as processing tests
    // them (not guessed from one condition); one no variant's records match (an event type from documentation, not
    // in the sample) is the first variant's whose records hold the fields it tests, else the first variant's. A rule
    // with no conditions (the catch-all, or a sole event type) is every variant's. Seen in chat 163: the confirmed
    // kinds, applied to every variant with an 'event' field, gave a rule for each kind in each variant
    // (linux_icastart, netscaler_history), failing the schema checks; the agent wrote the mapping by hand.
    private static boolean belongsTo(final Map<String, Object> rule,
                                     final String field,
                                     final Drafted drafted,
                                     final List<Drafted> drafts) {
        final List<Condition> when = WayCheck.conditions(rule.get(WHEN));
        if (when == null || when.isEmpty()) {
            return true;
        }
        if (recognisesAny(drafted, field, when)) {
            return true;
        }
        if (drafts.stream().anyMatch(other -> other != drafted && recognisesAny(other, field, when))) {
            return false;
        }
        final List<String> fields = when.stream()
                .filter(condition -> condition.field() != null && !condition.not()
                                     && !condition.field().equals(field))
                .map(Condition::field)
                .toList();
        return drafts.stream()
                .filter(other -> other.records().stream()
                        .anyMatch(record -> fields.stream().allMatch(tested -> record.get(tested) != null)))
                .findFirst()
                .orElse(drafts.getFirst()) == drafted;
    }

    // Whether a variant's records (their variant field given back: a way may test it) are recognised.
    private static boolean recognisesAny(final Drafted drafted, final String field, final List<Condition> when) {
        final WayCheck check = WayCheck.of(drafted.mapping());
        return drafted.records().stream().anyMatch(record -> {
            final Map<String, Object> whole = new LinkedHashMap<>(record);
            if (drafted.variant() != null) {
                whole.put(field, drafted.variant());
            }
            return check.recognises(when, whole);
        });
    }

    // The header lines left untranslated; the splitter writes their date on the records after them, read by each
    // dateless time.
    @SuppressWarnings("unchecked")
    private static void headed(final Map<String, Object> mapping, final String field, final List<Object> notes) {
        final List<Object> drops = new ArrayList<>((List<Object>) mapping.getOrDefault("drop_when", List.of()));
        drops.add(Map.of("when", List.of(Map.of("field", field, "equals", SplitterDryRun.HEADER_VARIANT)),
                "reason", "a header line: its date is the date of the lines after it"));
        mapping.put("drop_when", drops);
        notes.add(1, "A header line gives the date of the lines after it, whose times have none: it writes no Event "
                     + "(drop_when), the splitter writes its date on each record after it ('" + HEADER_DATE + "'), "
                     + "and each time is joined with it, a day added when a time goes back past midnight (rollover: "
                     + "in processing; stepping reads a record alone, so it shows a time after midnight on the day "
                     + "before). Check the header is where the date is.");
    }

    // The format of a variant's times when they have no date (`HH:mm:ss.SSS`), read as a full time with a date
    // before it.
    private static Optional<String> timeOfDay(final Drafted drafted) {
        final List<String> times = drafted.records().stream()
                .map(record -> record.get(TIME))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        if (times.isEmpty() || !times.stream().allMatch(time -> DATELESS.matcher(time).matches())) {
            return Optional.empty();
        }
        final String pattern = TimePatterns.inferTimePattern("2026-01-01 " + times.getFirst());
        return pattern != null && pattern.startsWith("yyyy-MM-dd ")
                ? Optional.of(pattern.substring("yyyy-MM-dd ".length()))
                : Optional.empty();
    }

    // A variant whose records' times have no date: its time joined with the header's date, rolled over midnight.
    private static Optional<Map<String, Object>> datelessTime(final Drafted drafted, final String headerDate) {
        final List<String> times = drafted.records().stream()
                .map(record -> record.get(TIME))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        if (times.isEmpty() || !times.stream().allMatch(time -> DATELESS.matcher(time).matches())) {
            return Optional.empty();
        }
        final String pattern = TimePatterns.inferTimePattern(headerDate + " " + times.getFirst());
        if (pattern == null) {
            return Optional.empty();
        }
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", TIME_CREATED);
        entry.put("xpath", "concat(data[@name='" + HEADER_DATE + "']/@value, ' ', data[@name='" + TIME
                           + "']/@value)");
        entry.put("time_format", pattern);
        entry.put("rollover", true);
        return Optional.of(entry);
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
        // The catch-all's rule is the variant's uncatalogued records', for them only: the rule marked catch_all has
        // no conditions, so each variant's is left unmarked, and the generator takes each as a catch-all, as it
        // takes what its variant's rules before it leave.
        out.remove("catch_all");
        final List<Object> when = new ArrayList<>(List.of(condition));
        if (rule.get(WHEN) instanceof final List<?> conditions) {
            // A way testing the variant itself has it once.
            conditions.stream().filter(test -> !condition.equals(test)).forEach(when::add);
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
    private record Drafted(String variant, Map<String, Object> result, List<Map<String, Object>> records) {

        @SuppressWarnings("unchecked")
        Map<String, Object> mapping() {
            return (Map<String, Object>) result.get("mapping");
        }
    }
}
