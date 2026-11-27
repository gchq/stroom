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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// Source type field candidates inside held values: the names in the structured data a record's string fields hold
/// ([HeldText#expand]), judged as the records' own fields are ([SourceTypeFields#judge]: how far their values tell
/// the records' shapes apart, and whether each value holds kinds of its own). Structured data only: a held value of
/// plain text gives no value, so its records are the `(no value)` source type, never a value of their words.
///
/// A held name is judged over the records whose held field is in a format the name is seen in (a JSON key over the
/// JSON records), most of which must hold it. Where the held field's formats name the source differently, the best
/// name is joined by the names that read the formats it misses (`event>logger_name | event>xml:@logger`), a reading
/// offered before its parts, for the user to confirm, as they confirm any source type field.
public final class HeldCandidates {

    // The least share of all records a held name must have a value in to be offered on its own.
    static final double MIN_SHARE = 0.25;

    private HeldCandidates() {
        // Static only.
    }

    /// @param records   The records read, parsed (none null).
    /// @param threshold The separation a candidate needs.
    /// @param said      True when the user said the feed aggregates sources (holding kinds isn't needed).
    /// @return The held candidates, and the readings joining a held field's formats, unordered.
    public static List<SourceTypeFields.Candidate> of(final List<Map<String, Object>> records,
                                                      final double threshold,
                                                      final boolean said) {
        final List<HeldText.Expansion> expansions = records.stream().map(HeldText::expand).toList();
        final Map<String, Map<String, Integer>> valuesByPath = new LinkedHashMap<>();
        expansions.forEach(expansion -> expansion.values().forEach((path, value) ->
                valuesByPath.computeIfAbsent(path, k -> new LinkedHashMap<>()).merge(value, 1, Integer::sum)));
        final List<SourceTypeFields.Candidate> offered = new ArrayList<>();
        final List<SourceTypeFields.Candidate> parts = new ArrayList<>();
        for (final Map.Entry<String, Map<String, Integer>> entry : valuesByPath.entrySet()) {
            final String path = entry.getKey();
            final Map<String, Integer> counts = entry.getValue();
            final int present = counts.values().stream().mapToInt(Integer::intValue).sum();
            final Map<String, Integer> formats = formatsOf(path, expansions);
            final long eligible = eligible(parent(path), formats.keySet(), expansions);
            if (present < SourceTypeFields.PRESENT * eligible || counts.size() > SourceTypeFields.MAX_VALUES
                || counts.size() * 2L > present || !SourceTypeFields.mayName(path, counts.keySet())) {
                continue;
            }
            final List<String> values = expansions.stream().map(expansion -> expansion.values().get(path)).toList();
            final SourceTypeFields.Candidate candidate = judge(path, records, expansions, values, counts, present,
                    formats);
            // A name of one format of a held field may join a reading of another's, whatever its own separation.
            parts.add(candidate);
            if (counts.size() >= 2 && present >= MIN_SHARE * records.size() && candidate.separation() >= threshold
                && (said || candidate.holdsKinds())) {
                offered.add(candidate);
            }
        }
        final List<SourceTypeFields.Candidate> out = new ArrayList<>(offered);
        for (final SourceTypeFields.Candidate best : bestByParent(offered)) {
            final SourceTypeFields.Candidate joined = joined(best, parts, records, expansions);
            if (joined != null && joined.separation() >= threshold && (said || joined.holdsKinds())) {
                out.add(joined);
            }
        }
        return out;
    }

    /// Candidates in order, each reading joining a held field's formats moved before the best of its parts.
    ///
    /// @param sorted Candidates, best first.
    /// @return Them, joined readings first of their parts.
    public static List<SourceTypeFields.Candidate> joinedFirst(final List<SourceTypeFields.Candidate> sorted) {
        final List<SourceTypeFields.Candidate> out = new ArrayList<>(sorted);
        for (final SourceTypeFields.Candidate candidate : sorted) {
            if (!candidate.field().contains(SourceTypeReading.OR.strip())) {
                continue;
            }
            final String first = candidate.field().substring(0, candidate.field().indexOf(
                    SourceTypeReading.OR.strip())).strip();
            final int at = indexOf(out, first);
            if (at >= 0 && at < out.indexOf(candidate)) {
                out.remove(candidate);
                out.add(at, candidate);
            }
        }
        return out;
    }

    // The best candidate of each held field.
    private static List<SourceTypeFields.Candidate> bestByParent(final List<SourceTypeFields.Candidate> offered) {
        final Map<String, SourceTypeFields.Candidate> best = new LinkedHashMap<>();
        offered.stream()
                .sorted(Comparator.comparingDouble(SourceTypeFields.Candidate::separation).reversed()
                        .thenComparing(Comparator.comparingInt(SourceTypeFields.Candidate::records).reversed()))
                .forEach(candidate -> best.putIfAbsent(parent(candidate.field()), candidate));
        return List.copyOf(best.values());
    }

    // The best candidate joined by the names reading the held field's formats it misses, each of one format only
    // where it is seen in one; null when no other name reads a format it misses.
    private static SourceTypeFields.Candidate joined(final SourceTypeFields.Candidate best,
                                                     final List<SourceTypeFields.Candidate> parts,
                                                     final List<Map<String, Object>> records,
                                                     final List<HeldText.Expansion> expansions) {
        final String parent = parent(best.field());
        final Set<String> covered = new LinkedHashSet<>(best.formats().keySet());
        final List<String> readings = new ArrayList<>(List.of(best.field()));
        final List<SourceTypeFields.Candidate> others = new ArrayList<>(parts.stream()
                .filter(part -> parent.equals(parent(part.field())) && !part.field().equals(best.field()))
                .toList());
        others.sort(Comparator.comparingInt((SourceTypeFields.Candidate part) -> likeness(part, best)).reversed()
                .thenComparing(Comparator.comparingInt(SourceTypeFields.Candidate::records).reversed())
                .thenComparingInt(part -> part.values().size()));
        for (final SourceTypeFields.Candidate part : others) {
            if (readings.size() >= SourceTypeReading.MAX_ALTERNATIVES
                || part.formats().keySet().stream().anyMatch(covered::contains) || !alike(part, best)) {
                continue;
            }
            final String steps = part.field().substring(parent.length() + SourceTypeReading.HELD.length());
            readings.add(part.formats().size() == 1
                    ? parent + SourceTypeReading.HELD + part.formats().keySet().iterator().next() + ":" + steps
                    : part.field());
            covered.addAll(part.formats().keySet());
        }
        if (readings.size() < 2) {
            return null;
        }
        final SourceTypeReading reading = SourceTypeReading.of(String.join(SourceTypeReading.OR, readings));
        final List<String> values = records.stream().map(reading::valueOf).toList();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        values.stream().filter(value -> value != null).forEach(value -> counts.merge(value, 1, Integer::sum));
        final int present = counts.values().stream().mapToInt(Integer::intValue).sum();
        final Map<String, Integer> formats = new LinkedHashMap<>();
        for (int i = 0; i < records.size(); i++) {
            if (values.get(i) != null) {
                formats.merge(expansions.get(i).formats().getOrDefault(parent, HeldText.TEXT), 1, Integer::sum);
            }
        }
        if (counts.size() < 2 || counts.size() > SourceTypeFields.MAX_VALUES || counts.size() * 2L > present) {
            return null;
        }
        return judge(reading.spec(), records, expansions, values, counts, present, formats);
    }

    // Whether a part's values are like enough the best candidate's to read the same thing in another format: a
    // value in common, or both structured names (dotted, paths). Seen in an e2e walk: a syslog relay's `app`
    // joined by key=value's `mailbox`, user names, before the record's own LogFilePath.
    private static boolean alike(final SourceTypeFields.Candidate part, final SourceTypeFields.Candidate best) {
        return part.values().stream().anyMatch(best.counts()::containsKey)
               || structuredShare(part) >= 0.5 && structuredShare(best) >= 0.5;
    }

    // How like the best candidate's a part's values are: values in common, and whether both are structured names
    // (dotted, paths), so `@logger`'s `com.x.Auth` joins `logger_name`'s rather than an id's.
    private static int likeness(final SourceTypeFields.Candidate part, final SourceTypeFields.Candidate best) {
        final long common = part.values().stream().filter(best.counts()::containsKey).count();
        final boolean bothStructured = structuredShare(part) >= 0.5 == structuredShare(best) >= 0.5;
        final boolean root = part.field().endsWith(SourceTypeReading.ROOT_STEP);
        return (int) Math.min(common, 50) * 10 + (bothStructured
                ? 5
                : 0) - (root
                ? 1
                : 0);
    }

    private static double structuredShare(final SourceTypeFields.Candidate candidate) {
        return candidate.values().isEmpty()
                ? 0.0
                : (double) candidate.values().stream().filter(SourceTypeRows::structured).count()
                  / candidate.values().size();
    }

    // A held candidate judged: the records' shapes taken with their held field's structure, so records differing
    // only inside the held field (all one wrapper) are told apart.
    private static SourceTypeFields.Candidate judge(final String path,
                                                    final List<Map<String, Object>> records,
                                                    final List<HeldText.Expansion> expansions,
                                                    final List<String> values,
                                                    final Map<String, Integer> counts,
                                                    final int present,
                                                    final Map<String, Integer> formats) {
        final String parent = parent(path);
        return SourceTypeFields.judge(path, values,
                i -> SourceTypeFields.shapeKey(records.get(i), null) + " | " + heldShape(parent, path,
                        expansions.get(i)),
                i -> SourceTypeFields.naming(records.get(i), null) + " | " + heldNaming(path, expansions.get(i)),
                counts, present, formats);
    }

    // A record's held field's structure: its format and the names in it (the candidate's own aside).
    private static String heldShape(final String parent, final String path, final HeldText.Expansion expansion) {
        final String prefix = parent + SourceTypeReading.HELD;
        final Set<String> names = new TreeSet<>(ToolText::compare);
        expansion.values().keySet().stream()
                .filter(name -> name.startsWith(prefix) && !name.equals(path) && !path.startsWith(name + ">"))
                .forEach(name -> names.add(name.substring(prefix.length())));
        return expansion.formats().getOrDefault(parent, "missing") + " " + String.join(",", names);
    }

    // A record's held values named like kinds of event, the candidate's own aside.
    private static String heldNaming(final String path, final HeldText.Expansion expansion) {
        final List<String> pairs = new ArrayList<>();
        expansion.values().forEach((name, value) -> {
            if (!name.equals(path) && KindFields.namedLikeKinds(SourceTypeReading.lastName(name))
                && value.length() <= 60) {
                pairs.add(name + "=" + value);
            }
        });
        pairs.sort(ToolText::compare);
        return String.join(",", pairs);
    }

    // The records with a value for a held name, by its held field's format.
    private static Map<String, Integer> formatsOf(final String path, final List<HeldText.Expansion> expansions) {
        final String parent = parent(path);
        final Map<String, Integer> out = new LinkedHashMap<>();
        expansions.stream()
                .filter(expansion -> expansion.values().containsKey(path))
                .forEach(expansion -> out.merge(expansion.formats().getOrDefault(parent, HeldText.TEXT), 1,
                        Integer::sum));
        return out;
    }

    // The records whose held field is in one of the formats given.
    private static long eligible(final String parent,
                                 final Set<String> formats,
                                 final List<HeldText.Expansion> expansions) {
        return expansions.stream()
                .filter(expansion -> formats.contains(expansion.formats().get(parent)))
                .count();
    }

    // The held field a held name is in: `event` of `event>logger_name`, `event>payload` of
    // `event>payload>logger_name`.
    private static String parent(final String path) {
        final String first = path.contains(SourceTypeReading.OR.strip())
                ? path.substring(0, path.indexOf(SourceTypeReading.OR.strip())).strip()
                : path;
        final int at = first.lastIndexOf(SourceTypeReading.HELD);
        return at < 0
                ? first
                : first.substring(0, at);
    }

    private static int indexOf(final List<SourceTypeFields.Candidate> candidates, final String field) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).field().equals(field)) {
                return i;
            }
        }
        return -1;
    }
}
