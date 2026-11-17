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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// Source types told by a **combination** of readings ([SourceTypeReading#isCombination]): where only two fields
/// together say a record's source (`system` Teams or Skype, and inside `event`, `logger_name` System or
/// Application), each perhaps at its own depth.
///
/// A pair is inferred only where the records' shapes justify it, never past two parts (three or more only as the user
/// names them):
///
/// * **No single reading separates the shapes well enough**: every single candidate offered has a value whose records
///   fall into **families** another reading tells apart, a family being the records of one of its values with shapes
///   of their own (at least [#MIN_FAMILY] records, several shapes: a source holds kinds of event; a value of one shape
///   is a kind, never a family, so a kind-like field never makes a pair), most of them ([#DISTINCT]) shapes no other
///   value's records have.
/// * **Each part splits the other**: the second's values change record shape within some value of the first, and the
///   first's within some value of the second (where the second's values each sit within one of the first's, the
///   second alone tells the sources, and no pair is needed).
/// * Neither part is named for kinds of event (an event type, action, operation) or a level (a severity says how bad,
///   never which source), nor free text, a time or an address ([SourceTypeFields#mayName]): kinds within a source are
///   classify_kinds' and the translation rules' job.
/// * The feed looks aggregated already: some single reading was offered (each split), or the user said it is;
///   otherwise two fields of a single source's records (a component and its error records) could pass for a pair.
///
/// The rows proposed for a combination ([#propose]) take each of the first part's values, then, within it, a row each
/// for the next part's values that are families of their own, and `*` for the rest: where the next part's values all
/// have the same shapes they collapse to one `*` row.
public final class SourceTypeCombinations {

    /// The fewest records of a value, within another part's value, to be a family of its own.
    static final int MIN_FAMILY = 3;
    /// The share of a value's records whose shapes no other value's records have (within the other part's value) for
    /// them to be a family of their own.
    static final double DISTINCT = 0.8;
    // The most readings judged as parts of a pair, those holding most records.
    private static final int MAX_JUDGED = 12;
    // The most values of a family said in the evidence.
    private static final int VALUES_SAID = 3;

    private SourceTypeCombinations() {
        // Static only.
    }

    /// A pair of readings to offer as the source type field, where the records' shapes justify one.
    ///
    /// @param records   The records read, parsed (none null).
    /// @param offered   The single readings offered (none where none separates well enough).
    /// @param threshold The separation a candidate needs.
    /// @param said      True when the user said the feed aggregates sources.
    /// @return The pair (best first part first), with its rows proposed and the evidence; empty where a single
    /// reading separates the shapes, no single reading was offered and the user didn't say the feed is aggregated, or
    /// no two readings split each other's values into families.
    public static Optional<SourceTypeFields.Candidate> pair(final List<Map<String, Object>> records,
                                                           final List<SourceTypeFields.Candidate> offered,
                                                           final double threshold,
                                                           final boolean said) {
        if (records.size() < SourceTypeFields.MIN_RECORDS || offered.isEmpty() && !said) {
            return Optional.empty();
        }
        final List<String> shapes = shapes(records);
        final List<Part> parts = eligible(records);
        final List<Part> singles = new ArrayList<>();
        for (final SourceTypeFields.Candidate candidate : offered) {
            singles.add(part(candidate.field(), records));
        }
        // A single reading no other reading splits tells the sources well enough.
        for (final Part single : singles) {
            if (parts.stream().noneMatch(other -> !other.spec().equals(single.spec())
                                                  && !splits(single, other, shapes).isEmpty())) {
                return Optional.empty();
            }
        }
        SourceTypeFields.Candidate best = null;
        for (int i = 0; i < parts.size(); i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                final Optional<SourceTypeFields.Candidate> found = judged(records, shapes, parts.get(i),
                        parts.get(j), threshold);
                if (found.isPresent() && (best == null || better(found.get(), best))) {
                    best = found.get();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /// Rows proposed for a combination from the records: a row (or rows) for each of the first part's values,
    /// commonest first; within a value, a row each for the next part's values whose records are families of their
    /// own, then `*` for the rest; `*` alone where the next part's values all share their shapes; and so on for
    /// each part. Each row covers the keys seen it matches first.
    ///
    /// @param records The records read, parsed.
    /// @param spec    The combination, as written.
    /// @return The rows, in order, each with the keys it covers and their records.
    public static List<SourceTypeRows.Proposed> propose(final List<Map<String, Object>> records, final String spec) {
        final SourceTypeReading reading = SourceTypeReading.of(spec);
        final List<Map<String, Object>> held = records.stream().filter(record -> record != null).toList();
        final List<String> shapes = shapes(held);
        final List<Part> parts = reading.parts().stream().map(part -> part(part.spec(), held)).toList();
        final List<SourceTypeRows.Row> rows = new ArrayList<>();
        final Set<String> names = new LinkedHashSet<>();
        final Map<String, List<Integer>> byFirst = byValue(indices(held.size()), parts.getFirst());
        byFirst.forEach((value, at) -> branch(parts, shapes, 1, at, new ArrayList<>(List.of(exactly(value))),
                new ArrayList<>(List.of(value)), rows, names));
        final Map<String, Long> keys = new LinkedHashMap<>();
        held.forEach(record -> {
            final String key = reading.valueOf(record);
            if (key != null) {
                keys.merge(key, 1L, Long::sum);
            }
        });
        final Map<String, List<String>> covered = new LinkedHashMap<>();
        final Map<String, Long> coveredRecords = new LinkedHashMap<>();
        keys.forEach((key, count) -> SourceTypeRows.first(rows, key).ifPresent(row -> {
            covered.computeIfAbsent(row.name(), k -> new ArrayList<>()).add(key);
            coveredRecords.merge(row.name(), count, Long::sum);
        }));
        return rows.stream()
                .map(row -> new SourceTypeRows.Proposed(row, covered.getOrDefault(row.name(), List.of()),
                        coveredRecords.getOrDefault(row.name(), 0L)))
                .toList();
    }

    /// A record's shape as a combination is judged by: its own shape ([SourceTypeFields#shapeKey]) with the
    /// structure of every held value (its format and the names in it), so records differing only inside a held
    /// field are told apart.
    ///
    /// @param record A record, parsed.
    /// @return Its shape.
    static String shape(final Map<String, Object> record) {
        final HeldText.Expansion expansion = HeldText.expand(record);
        final Set<String> held = new TreeSet<>(ToolText::compare);
        expansion.formats().forEach((parent, format) -> held.add(parent + ":" + format));
        held.addAll(expansion.values().keySet());
        return SourceTypeFields.shapeKey(record, null) + " | " + String.join(",", held);
    }

    // The pair of two readings judged, best part first; empty where they don't split each other into families or
    // the pair doesn't separate the shapes.
    private static Optional<SourceTypeFields.Candidate> judged(final List<Map<String, Object>> records,
                                                               final List<String> shapes,
                                                               final Part one,
                                                               final Part two,
                                                               final double threshold) {
        final Map<String, Families> twoInOne = splits(one, two, shapes);
        final Map<String, Families> oneInTwo = splits(two, one, shapes);
        if (twoInOne.isEmpty() || oneInTwo.isEmpty()) {
            return Optional.empty();
        }
        final double oneSeparation = separation(one, shapes);
        final double twoSeparation = separation(two, shapes);
        // The part separating better first; a record's own field before a held one; then the fewer values.
        final boolean oneFirst = oneSeparation != twoSeparation
                ? oneSeparation > twoSeparation
                : one.held() != two.held()
                        ? !one.held()
                        : one.counts().size() <= two.counts().size();
        final Part first = oneFirst
                ? one
                : two;
        final Part second = oneFirst
                ? two
                : one;
        final String spec = first.spec() + SourceTypeReading.AND + second.spec();
        final SourceTypeReading reading = SourceTypeReading.of(spec);
        final List<String> keys = records.stream().map(reading::valueOf).toList();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        keys.stream().filter(key -> key != null).forEach(key -> counts.merge(key, 1, Integer::sum));
        final int present = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (counts.size() < 2 || counts.size() > SourceTypeFields.MAX_VALUES) {
            return Optional.empty();
        }
        final SourceTypeFields.Candidate judged = SourceTypeFields.judge(spec, keys, shapes::get, i -> "", counts,
                present, Map.of());
        if (judged.separation() < threshold) {
            return Optional.empty();
        }
        final long noValue = keys.stream()
                .filter(key -> key == null || SourceTypeReading.keyParts(key).contains(""))
                .count();
        final List<String> evidence = new ArrayList<>();
        evidence.add(evidence(first, second, oneFirst
                ? twoInOne
                : oneInTwo));
        evidence.add(evidence(second, first, oneFirst
                ? oneInTwo
                : twoInOne));
        evidence.add("so neither alone tells the sources: together they do");
        return Optional.of(new SourceTypeFields.Candidate(spec, judged.values(), counts, present,
                judged.separation(), true, Map.of(), (int) noValue,
                new Combined(propose(records, spec), evidence)));
    }

    // One reading's values said as splitting another's: "within system = Teams, event>logger_name System and
    // Application have shapes of their own".
    private static String evidence(final Part outer, final Part inner, final Map<String, Families> split) {
        final Map.Entry<String, Families> first = split.entrySet().iterator().next();
        final List<String> some = first.getValue().distinct().subList(0, Math.min(VALUES_SAID,
                first.getValue().distinct().size()));
        return "within " + outer.spec() + " = " + first.getKey() + ", " + inner.spec() + " " + String.join(" and ",
                some) + (first.getValue().remainder() > 0
                ? " (and the rest)"
                : "") + " have shapes of their own" + (split.size() > 1
                ? " (so in " + split.size() + " of its values)"
                : "");
    }

    // Whether one pair is better than another: separates more, then fewer keys.
    private static boolean better(final SourceTypeFields.Candidate one, final SourceTypeFields.Candidate other) {
        return one.separation() > other.separation()
               || one.separation() == other.separation() && one.values().size() < other.values().size();
    }

    // The values of an outer reading whose records an inner reading's values split into families.
    private static Map<String, Families> splits(final Part outer, final Part inner, final List<String> shapes) {
        final Map<String, Families> out = new LinkedHashMap<>();
        byValue(indices(shapes.size()), outer).forEach((value, at) -> {
            final Families families = families(at, inner, shapes);
            if (families.split()) {
                out.put(value, families);
            }
        });
        return out;
    }

    // The families a reading's values make of some records: its values whose records (enough of them, of several
    // shapes) mostly have shapes no other value's records have; the rest's records counted.
    private static Families families(final List<Integer> at, final Part part, final List<String> shapes) {
        final Map<String, List<Integer>> byValue = byValue(at, part);
        final Map<String, Set<String>> valuesByShape = new HashMap<>();
        byValue.forEach((value, records) -> records.forEach(i -> valuesByShape
                .computeIfAbsent(shapes.get(i), k -> new HashSet<>()).add(value)));
        final List<String> distinct = new ArrayList<>();
        long rest = 0;
        for (final Map.Entry<String, List<Integer>> entry : byValue.entrySet()) {
            final List<Integer> records = entry.getValue();
            final Set<String> own = new HashSet<>();
            records.forEach(i -> own.add(shapes.get(i)));
            final long alone = records.stream().filter(i -> valuesByShape.get(shapes.get(i)).size() == 1).count();
            if (records.size() >= MIN_FAMILY && own.size() >= 2 && alone >= DISTINCT * records.size()) {
                distinct.add(entry.getKey());
            } else {
                rest += records.size();
            }
        }
        return new Families(distinct, rest);
    }

    // The next part of a combination: a row a family of its values (then * for the rest), or * where its values
    // make no families; a row once every part is placed.
    private static void branch(final List<Part> parts,
                               final List<String> shapes,
                               final int level,
                               final List<Integer> at,
                               final List<String> prefix,
                               final List<String> named,
                               final List<SourceTypeRows.Row> rows,
                               final Set<String> names) {
        if (level == parts.size()) {
            rows.add(new SourceTypeRows.Row(SourceTypeRows.uniqueName(String.join(" ", named), names),
                    SourceTypeRows.TUPLE, String.join(SourceTypeRows.AND, prefix)));
            return;
        }
        final Part part = parts.get(level);
        final Families families = families(at, part, shapes);
        if (!families.split()) {
            branch(parts, shapes, level + 1, at, plus(prefix, SourceTypeRows.ANY), named, rows, names);
            return;
        }
        final Map<String, List<Integer>> byValue = byValue(at, part);
        final List<Integer> rest = new ArrayList<>(at);
        for (final String value : families.distinct()) {
            final List<Integer> of = byValue.get(value);
            rest.removeAll(of);
            branch(parts, shapes, level + 1, of, plus(prefix, exactly(value)), plus(named, value), rows, names);
        }
        // The rest of the values, as one: never for records with no value alone (the (no value) row's, unless a
        // row with * takes them).
        if (families.remainder() > 0) {
            branch(parts, shapes, level + 1, rest, plus(prefix, SourceTypeRows.ANY), named, rows, names);
        }
    }

    // A part matching a value exactly; one holding ' + ' (which joins parts) by an anchored pattern of it.
    private static String exactly(final String value) {
        return SourceTypeReading.isCombination(value)
                ? "~ ^" + SourceTypeRows.escape(value) + "$"
                : "= " + value;
    }

    private static List<String> plus(final List<String> list, final String item) {
        final List<String> out = new ArrayList<>(list);
        out.add(item);
        return out;
    }

    // How far a reading's values alone tell the records' shapes apart.
    private static double separation(final Part part, final List<String> shapes) {
        final int present = part.counts().values().stream().mapToInt(Integer::intValue).sum();
        return SourceTypeFields.judge(part.spec(), part.values(), shapes::get, i -> "", part.counts(), present,
                Map.of()).separation();
    }

    // The readings that may be a part of a pair: the records' own fields and the names inside their held values,
    // each with a value in most records (a held name: in a quarter of them, as its held formats allow), a few
    // short names, not named for kinds of event or a level, free text, an address or a time; those holding most
    // records first.
    private static List<Part> eligible(final List<Map<String, Object>> records) {
        final Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        final Set<String> heldNames = new LinkedHashSet<>();
        for (final Map<String, Object> record : records) {
            for (final String field : record.keySet()) {
                if (SourceTypeMeta.isMeta(field)) {
                    continue;
                }
                final String value = SourceTypeFields.valueOf(record, field);
                if (value != null) {
                    counts.computeIfAbsent(field, k -> new LinkedHashMap<>()).merge(value, 1, Integer::sum);
                }
            }
            HeldText.expand(record).values().forEach((path, value) -> {
                heldNames.add(path);
                counts.computeIfAbsent(path, k -> new LinkedHashMap<>()).merge(value, 1, Integer::sum);
            });
        }
        final List<Part> out = new ArrayList<>();
        counts.forEach((spec, byValue) -> {
            final int present = byValue.values().stream().mapToInt(Integer::intValue).sum();
            final double least = heldNames.contains(spec)
                    ? HeldCandidates.MIN_SHARE
                    : SourceTypeFields.PRESENT;
            if (present >= least * records.size() && byValue.size() >= 2
                && byValue.size() <= SourceTypeFields.MAX_VALUES && byValue.size() * 2L <= present
                && SourceTypeFields.mayName(spec, byValue.keySet())
                && !SourceTypeProfiles.LEVEL_FIELD.matcher(SourceTypeReading.lastName(spec)).find()) {
                out.add(part(spec, records));
            }
        });
        out.sort(Comparator.comparingInt((Part part) -> part.counts().values().stream().mapToInt(Integer::intValue)
                .sum()).reversed());
        return List.copyOf(out.subList(0, Math.min(MAX_JUDGED, out.size())));
    }

    // A reading's value in each record.
    private static Part part(final String spec, final List<Map<String, Object>> records) {
        final List<String> values = records.stream().map(record -> SourceTypeFields.valueOf(record, spec)).toList();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        values.stream().filter(value -> value != null).forEach(value -> counts.merge(value, 1, Integer::sum));
        return new Part(spec, values, counts, SourceTypeReading.isReading(spec));
    }

    private static List<String> shapes(final List<Map<String, Object>> records) {
        return records.stream().map(SourceTypeCombinations::shape).toList();
    }

    private static List<Integer> indices(final int size) {
        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            out.add(i);
        }
        return out;
    }

    // The records given, by a reading's value (none left out), commonest value first.
    private static Map<String, List<Integer>> byValue(final List<Integer> at, final Part part) {
        final Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        at.forEach(i -> {
            final String value = part.values().get(i);
            if (value != null) {
                grouped.computeIfAbsent(value, k -> new ArrayList<>()).add(i);
            }
        });
        final Map<String, List<Integer>> out = new LinkedHashMap<>();
        grouped.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<Integer>> entry) -> entry.getValue().size())
                        .reversed().thenComparing(Map.Entry::getKey))
                .forEach(entry -> out.put(entry.getKey(), entry.getValue()));
        return out;
    }


    // --------------------------------------------------------------------------------


    /// A pair inferred: the rows proposed for it and why it is offered.
    ///
    /// @param rows     The rows proposed ([#propose]), each with the keys it covers.
    /// @param evidence What it rests on, said in plain words: which values of each part the other splits.
    public record Combined(List<SourceTypeRows.Proposed> rows, List<String> evidence) {

        /// @param rows     The rows.
        /// @param evidence The evidence.
        public Combined {
            rows = List.copyOf(rows);
            evidence = List.copyOf(evidence);
        }
    }

    // A reading judged as a part: its value in each record (null for none), the records of each value.
    private record Part(String spec, List<String> values, Map<String, Integer> counts, boolean held) {

    }

    // The families a reading's values make of some records: its values that are families of their own, and the
    // records of the rest.
    private record Families(List<String> distinct, long remainder) {

        // Whether the records fall into more than one family.
        boolean split() {
            return distinct.size() >= 2 || distinct.size() == 1 && remainder >= MIN_FAMILY;
        }
    }
}
