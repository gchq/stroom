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
import stroom.ai.impl.workspace.SafePattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Records of several lines told apart by a key each line holds (auditd's `msg=audit(1791676800.123:4567)`
/// envelope): consecutive lines with the same key are one record, as the Data Splitter's record regex takes them,
/// the key's later lines matched by a backreference. Lines of one record that come between another's (interleaved)
/// are not put back together: the user's decision, as Stroom's own tools can't (its translation reads records in
/// chunks), so the profile only warns.
public final class KeyedRecords {

    // The fewest lines, records and lines joined to suggest a key from.
    private static final int MIN_LINES = 6;
    private static final int MIN_RECORDS = 3;
    // A key is on this share of the lines; a line name starts this share.
    private static final double PRESENT = 0.9;
    private static final double NAMED = 0.95;
    private static final int MOST_NAMES = 20;
    // Values that come back after another's lines, at most, as a share of the keys; runs whose lines differ in kind,
    // at least.
    private static final double COME_BACK = 0.1;
    private static final double KINDS_DIFFER = 0.8;
    private static final Pattern LINE_NAME = Pattern.compile("[A-Za-z_][\\w.-]*");
    // A marker line between sections: no white space, word tokens between punctuation, punctuation at both ends.
    private static final Pattern MARKER = Pattern.compile("[^\\w\\s]+\\w+(?:[^\\w\\s]+\\w+)+[^\\w\\s]+");
    private static final Pattern WORD = Pattern.compile("\\w+");
    /// The field a sectioned record's first section's name is written in.
    public static final String SECTION = "first_section";
    // A separator row (----, ====), and a line labelled with an arrow (time->...).
    private static final Pattern SEPARATOR_ROW = Pattern.compile("\\s*[-=*#~_]{3,}[ \\t]*(?=\\n|$)");
    private static final Pattern ARROW_LINE = Pattern.compile("\\s*([A-Za-z_]\\w*)->");

    /// A line with no key: a record of its own, as the converter's second regex takes it.
    public static final String LONE_LINE = "^(?=[^\\n]*\\S)([^\\n]*)\\n?";

    private KeyedRecords() {
        // Static only.
    }

    /// The Data Splitter regex taking one keyed record: a line holding the key, then each line after it holding the
    /// same key (with join_unkeyed, or holding no key). `$1` is the record; with line_name, `$2` is its first line's
    /// name; the key is [#keyGroup].
    ///
    /// @param spec A spec with record_key.
    /// @return The regex.
    public static String recordRegex(final SplitterSpec spec) {
        final String key = spec.recordKey();
        final int keyGroup = keyGroup(spec);
        final int[] span = groupSpan(key);
        final String back = key.substring(0, span[0]) + "\\" + keyGroup + key.substring(span[1]);
        final String lead = spec.lineName() == null
                ? ""
                : "(?=" + spec.lineName() + ")";
        final String later = spec.joinUnkeyed()
                ? "(?:[^\\n]*?" + back + "[^\\n]*|(?![^\\n]*?" + key + ")[^\\n]*)"
                : "[^\\n]*?" + back + "[^\\n]*";
        return "^(" + lead + "[^\\n]*?" + key + "[^\\n]*(?:\\n" + later + ")*)\\n?";
    }

    /// The Data Splitter regex taking one section of a keyed record (join_unkeyed with line_name): a named line and
    /// the unnamed lines after it. `$1` the name, `$2` the section, which the spec reads.
    ///
    /// @param spec A spec with join_unkeyed and line_name.
    /// @return The regex.
    public static String sectionRegex(final SplitterSpec spec) {
        return "^(?=" + spec.lineName() + ")([^\\n]*(?:\\n(?!" + spec.lineName() + ")[^\\n]*)*)\\n?";
    }

    /// The Data Splitter regex taking a line skip_lines passes over, writing nothing.
    ///
    /// @param spec A spec with skip_lines.
    /// @return The regex.
    public static String skippedLineRegex(final SplitterSpec spec) {
        return "^(?:" + spec.skipLines() + ")[^\\n]*\\n?";
    }

    /// Lines between keyed records that are no records: separator rows (`----`, `====`) and lines labelled with an
    /// arrow (ausearch's `time->Sun Oct 11 09:00:34 2026`), each seen at least as often as records need.
    ///
    /// @param lines The lines.
    /// @return A regex for them (skip_lines), or null when there are none.
    public static String separators(final List<String> lines) {
        final List<String> found = new ArrayList<>();
        if (lines.stream().filter(line -> SEPARATOR_ROW.matcher(line).matches()).count() >= MIN_RECORDS) {
            found.add(SEPARATOR_ROW.pattern());
        }
        final Map<String, Integer> labels = new LinkedHashMap<>();
        for (final String line : lines) {
            final Matcher m = ARROW_LINE.matcher(line);
            if (m.lookingAt()) {
                labels.merge(m.group(1), 1, Integer::sum);
            }
        }
        labels.forEach((label, count) -> {
            if (count >= MIN_RECORDS) {
                found.add("\\s*" + ToolText.escape(label) + "->");
            }
        });
        return found.isEmpty()
                ? null
                : String.join("|", found);
    }

    /// @param spec A spec with record_key.
    /// @return The group of [#recordRegex] holding the key.
    public static int keyGroup(final SplitterSpec spec) {
        return spec.lineName() == null
                ? 2
                : 3;
    }

    /// The Data Splitter regex taking one line of a keyed record that line_name names: `$1` the name, `$2` the whole
    /// line, which the spec reads (the name found by a lookahead, so fields before it, such as a forwarder's
    /// `node=web01`, are kept).
    ///
    /// @param spec A spec with line_name.
    /// @return The regex.
    public static String namedLineRegex(final SplitterSpec spec) {
        return "^(?=" + spec.lineName() + ")([^\\n]*)\\n?";
    }

    /// Lines joined into records as the converter joins them.
    ///
    /// @param lines The lines.
    /// @param spec  A spec with record_key.
    /// @return The records.
    public static List<String> group(final List<String> lines, final SplitterSpec spec) {
        final Pattern key = SafePattern.compile(spec.recordKey(), 0);
        final Pattern skip = spec.skipLines() == null
                ? null
                : SafePattern.compile(spec.skipLines(), 0);
        final List<String> records = new ArrayList<>();
        String last = null;
        // Whether the last record is keyed and open: with join_unkeyed, a line holding no key (a blank one too)
        // joins it.
        boolean open = false;
        for (final String line : lines) {
            if (skip != null && SafePattern.matcher(skip, line).lookingAt()) {
                // Passed over (a separator): the record before it ends.
                last = null;
                open = false;
                continue;
            }
            final boolean blank = ToolText.isBlank(line);
            final String found = blank
                    ? null
                    : keyOf(key, line);
            if (found != null && found.equals(last)
                || found == null && spec.joinUnkeyed() && open) {
                records.set(records.size() - 1, records.getLast() + "\n" + line);
                continue;
            }
            if (blank) {
                continue;
            }
            records.add(line);
            last = found;
            open = found != null;
        }
        return records;
    }

    /// One keyed record's fields, as the converter writes them: its key, its first line's name, and each line's
    /// fields under its name (`SYSCALL/exe`); a name given again (several PATH lines) keeps its first line's, as a
    /// field reads the first of several data elements.
    ///
    /// @param spec A spec with record_key.
    /// @param text The record.
    /// @return The fields, or null when a line can't be read.
    public static Map<String, String> apply(final SplitterSpec spec, final String text) {
        final SplitterSpec lineSpec = spec.lineSpec();
        final List<String> lines = List.of(text.split("\n", -1));
        final Map<String, String> fields = new LinkedHashMap<>();
        final String key = keyOf(SafePattern.compile(spec.recordKey(), 0), lines.getFirst());
        if (key != null) {
            fields.put(spec.keyField(), ToolText.strip(key));
        }
        final Pattern named = spec.lineName() == null
                ? null
                : SafePattern.compile(namedLineRegex(spec), 0);
        final boolean sections = spec.joinUnkeyed() && named != null;
        // Each part the spec reads, with its name (null for none): a line, or with sections a named line and the
        // unnamed lines after it.
        final List<String> names = new ArrayList<>();
        final List<String> parts = new ArrayList<>();
        for (final String line : lines) {
            final Matcher name = named == null
                    ? null
                    : SafePattern.matcher(named, line);
            final boolean isNamed = name != null && name.lookingAt();
            if (sections && !isNamed && !names.isEmpty() && names.getLast() != null) {
                parts.set(parts.size() - 1, parts.getLast() + "\n" + line);
            } else if (!ToolText.isBlank(line)) {
                names.add(isNamed
                        ? ToolText.strip(name.group(1))
                        : null);
                parts.add(line);
            }
        }
        for (int n = 0; n < parts.size(); n++) {
            if (n == 0 && names.getFirst() != null && key != null) {
                fields.put(spec.leadField(), names.getFirst());
            }
            final String part = parts.get(n);
            final Map<String, String> read = SplitterDryRun.applyRecord(lineSpec, part, null);
            final Map<String, String> partFields = read == null && lineSpec.catchAll()
                    ? Map.of(SplitterSpec.UNPARSED, ToolText.strip(part))
                    : read;
            if (partFields == null) {
                return null;
            }
            final String prefix = names.get(n) == null
                    ? ""
                    : names.get(n) + "/";
            partFields.forEach((field, value) -> fields.putIfAbsent(prefix + field, value));
        }
        return fields;
    }

    /// Records whose key is only on marker lines between sections (ModSecurity's serial audit log: `--5f005215-A--`,
    /// `--5f005215-B--` ... `--5f005215-Z--`, the lines between them part of the transaction): lines with no white
    /// space, word tokens between punctuation at both ends, one token holding over runs of such lines (the key) and
    /// another differing within a run (the section's name).
    ///
    /// @param lines The lines.
    /// @return `record_key`, `key_field`, `line_name`, `lead_field`, `join_unkeyed`, `records`, `sections_per_record`
    /// and any interleaving warning; or null when the lines have no such markers.
    public static Map<String, Object> sections(final List<String> lines) {
        final Map<String, List<String>> byShape = new LinkedHashMap<>();
        for (final String line : lines) {
            final String marker = ToolText.strip(line);
            if (MARKER.matcher(marker).matches()) {
                byShape.computeIfAbsent(WORD.matcher(marker).replaceAll("W"), shape -> new ArrayList<>()).add(marker);
            }
        }
        final List<String> markers = byShape.values().stream()
                .max(Comparator.comparingInt(List::size))
                .orElse(List.of());
        if (markers.size() < MIN_LINES) {
            return null;
        }
        // Each marker's word tokens as fields t1, t2, ...: the key and name found as for key=value lines.
        final List<Map<String, String>> tokens = new ArrayList<>();
        for (final String marker : markers) {
            final Map<String, String> found = new LinkedHashMap<>();
            final Matcher word = WORD.matcher(marker);
            while (word.find()) {
                found.put("t" + (found.size() + 1), word.group());
            }
            tokens.add(found);
        }
        final List<String> nameKeys = nameKeys(tokens, markers.size());
        Candidate best = null;
        for (final String column : tokens.getFirst().keySet()) {
            if (nameKeys.contains(column)) {
                continue;
            }
            final Candidate candidate = candidate(column, tokens, nameKeys);
            if (candidate != null && (best == null || candidate.joined().size() > best.joined().size())) {
                best = candidate;
            }
        }
        final String nameColumn = best == null
                ? null
                : nameKey(nameKeys, best.joined());
        if (nameColumn == null) {
            return null;
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        // At a line's start (whatever came before it in the text), to the line's end.
        out.put("record_key", "(?<![^\\n])" + markerRegex(markers.getFirst(), best.key()) + "[ \\t]*(?=\\n|$)");
        out.put("key_field", SplitterSpec.RECORD_KEY);
        out.put("line_name", markerRegex(markers.getFirst(), nameColumn) + "[ \\t]*(?=\\n|$)");
        out.put("lead_field", SECTION);
        out.put("join_unkeyed", true);
        out.put("records", best.runs());
        out.put("sections_per_record", Math.round(10.0 * markers.size() / best.runs()) / 10.0);
        if (best.interleaved() > 0) {
            out.put("interleaved", best.interleaved());
            out.put("interleaved_warning", best.interleaved() + " key(s) come back after another's sections: each "
                                           + "such record is read as several. Putting them back together is not "
                                           + "supported: say so to the user.");
        }
        return out;
    }

    // A marker as a regex: its punctuation as written, its word tokens any word, the one in `column` (t1, t2, ...)
    // the group.
    private static String markerRegex(final String marker, final String column) {
        final StringBuilder out = new StringBuilder();
        final Matcher word = WORD.matcher(marker);
        int at = 0;
        int n = 0;
        while (word.find()) {
            out.append(ToolText.escape(marker.substring(at, word.start())));
            n++;
            out.append(("t" + n).equals(column)
                    ? "(\\w+)"
                    : "\\w+");
            at = word.end();
        }
        return out.append(ToolText.escape(marker.substring(at))).toString();
    }

    /// The key that ties lines into records, when the lines have one: a key=value field on nearly every line whose
    /// value holds over runs of consecutive lines (and comes back rarely), where the lines of a run are of different
    /// kinds (the field naming each line, or their keys, differ), and that isn't a time (lines written in the same
    /// second share one). With it, the field naming each line: one of a few names on every line, differing between
    /// the lines of a record (auditd's type; not a forwarder's node=, the same on all of them).
    ///
    /// @param lines The lines.
    /// @return `record_key`, `key_field`, `line_name`, `lead_field`, `records`, `lines_per_record`, and
    /// `interleaved` (keys whose lines come back after another's) with a warning; or null when no field ties them.
    public static Map<String, Object> suggest(final List<String> lines) {
        if (lines.size() < MIN_LINES) {
            return null;
        }
        final List<Map<String, String>> pairs = new ArrayList<>();
        for (final String line : lines) {
            final Map<String, String> found = new LinkedHashMap<>();
            final Matcher m = SampleProfiler.KEY_VALUE.matcher(line);
            while (m.find()) {
                // A key given twice (auditd's own msg='...' after its envelope): the first.
                found.putIfAbsent(m.group(1), ToolText.strip(m.group(2), "\""));
            }
            pairs.add(found);
        }
        final List<String> nameKeys = nameKeys(pairs, lines.size());
        Candidate best = null;
        final Map<String, Integer> present = new LinkedHashMap<>();
        pairs.forEach(found -> found.keySet().forEach(key -> present.merge(key, 1, Integer::sum)));
        for (final Map.Entry<String, Integer> key : present.entrySet()) {
            if (nameKeys.contains(key.getKey()) || key.getValue() < PRESENT * lines.size()) {
                continue;
            }
            final Candidate candidate = candidate(key.getKey(), pairs, nameKeys);
            if (candidate != null && (best == null || candidate.joined().size() > best.joined().size())) {
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        final String nameKey = nameKey(nameKeys, best.joined());
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("record_key", "(?<!\\S)" + ToolText.escape(best.key()) + "=(\"[^\"]*\"|[^\\s\"]+)");
        out.put("key_field", best.key());
        if (nameKey != null) {
            // Found after any pairs before it (a forwarder's node=web01).
            out.put("line_name", "(?:[^\\s=]+=\\S*\\s+)*?" + ToolText.escape(nameKey)
                                 + "=\"?([A-Za-z_][\\w.-]*)\"?(?=\\s|$)");
            out.put("lead_field", nameKey);
        }
        out.put("records", best.runs());
        out.put("lines_per_record", Math.round(10.0 * lines.size() / best.runs()) / 10.0);
        if (best.interleaved() > 0) {
            out.put("interleaved", best.interleaved());
            out.put("interleaved_warning", best.interleaved() + " " + best.key() + " value(s) come back after "
                                           + "another's lines (events written interleaved): records are consecutive "
                                           + "lines with one key, so each such event is read as several records. "
                                           + "Putting them back together is not supported: say so to the user.");
        }
        return out;
    }

    // The keys on (nearly) every line whose values are a few names: what a line is (auditd's type), or where it
    // came from (a forwarder's node).
    private static List<String> nameKeys(final List<Map<String, String>> pairs, final int lines) {
        final Map<String, Integer> present = new LinkedHashMap<>();
        pairs.forEach(found -> found.keySet().forEach(key -> present.merge(key, 1, Integer::sum)));
        return present.entrySet().stream()
                .filter(key -> key.getValue() >= NAMED * lines)
                .map(Map.Entry::getKey)
                .filter(key -> {
                    final List<String> names = pairs.stream()
                            .map(found -> found.get(key))
                            .filter(name -> name != null)
                            .distinct()
                            .toList();
                    return names.size() >= 2 && names.size() <= Math.max(MOST_NAMES, lines / 4)
                           && names.stream().allMatch(name -> LINE_NAME.matcher(name).matches());
                })
                .toList();
    }

    // Of the name keys, the one whose value differs between the lines of most records (what each line is), when it
    // differs in at least half of them; else null.
    private static String nameKey(final List<String> nameKeys, final List<List<Map<String, String>>> joined) {
        String best = null;
        long bestCount = 0;
        for (final String key : nameKeys) {
            final long differs = joined.stream()
                    .filter(run -> run.stream().map(found -> found.get(key)).distinct().count() > 1)
                    .count();
            if (differs > bestCount) {
                best = key;
                bestCount = differs;
            }
        }
        return bestCount * 2 >= joined.size()
                ? best
                : null;
    }

    // A key's runs of consecutive lines with one value, when they tie lines into records; else null.
    private static Candidate candidate(final String key,
                                       final List<Map<String, String>> pairs,
                                       final List<String> nameKeys) {
        final List<List<Map<String, String>>> runs = new ArrayList<>();
        final List<String> runValues = new ArrayList<>();
        for (final Map<String, String> found : pairs) {
            final String value = found.get(key);
            if (value != null && !runValues.isEmpty() && value.equals(runValues.getLast())) {
                runs.getLast().add(found);
            } else {
                runs.add(new ArrayList<>(List.of(found)));
                runValues.add(value);
            }
        }
        final long distinct = runValues.stream().filter(value -> value != null).distinct().count();
        final List<List<Map<String, String>>> joined = runs.stream().filter(run -> run.size() > 1).toList();
        if (distinct < MIN_RECORDS || joined.size() < MIN_RECORDS
            || TimePatterns.inferTimePattern(runValues.getFirst() == null
                ? ""
                : runValues.getFirst()) != null) {
            return null;
        }
        final long interleaved = runValues.stream().filter(value -> value != null).count() - distinct;
        if (interleaved > COME_BACK * distinct) {
            return null;
        }
        // The lines of a run are of different kinds: the same kind written in the same moment is not one record.
        final long mixedKinds = joined.stream().filter(run -> run.stream()
                .map(found -> found.keySet() + String.valueOf(nameKeys.stream().map(found::get).toList()))
                .distinct()
                .count() > 1).count();
        if (mixedKinds < KINDS_DIFFER * joined.size()) {
            return null;
        }
        return new Candidate(key, runs.size(), joined, (int) interleaved);
    }

    /// @param key  The record_key regex.
    /// @param line A line.
    /// @return The line's key, or null when it holds none.
    static String keyOf(final Pattern key, final String line) {
        final Matcher m = SafePattern.matcher(key, line);
        return m.find()
                ? m.group(1)
                : null;
    }

    // Where a regex's one capturing group starts and ends (past its closing parenthesis), passing over escapes,
    // quoted text, character classes and the groups that don't capture.
    static int[] groupSpan(final String regex) {
        int depth = 0;
        int start = -1;
        boolean inClass = false;
        for (int i = 0; i < regex.length(); i++) {
            final char c = regex.charAt(i);
            if (c == '\\') {
                if (i + 1 < regex.length() && regex.charAt(i + 1) == 'Q') {
                    final int end = regex.indexOf("\\E", i + 2);
                    i = end < 0
                            ? regex.length()
                            : end + 1;
                } else {
                    i++;
                }
            } else if (inClass) {
                inClass = c != ']';
            } else if (c == '[') {
                inClass = true;
            } else if (c == '(') {
                if (start < 0 && capturing(regex, i)) {
                    start = i;
                    depth = 0;
                }
                if (start >= 0) {
                    depth++;
                }
            } else if (c == ')' && start >= 0) {
                depth--;
                if (depth == 0) {
                    return new int[]{start, i + 1};
                }
            }
        }
        throw new IllegalArgumentException("record_key needs a capturing group, the key");
    }

    // Whether the group opening at `at` captures: plain, or named (?<name>...), not (?:...), (?=...), (?<=...) and
    // the like.
    private static boolean capturing(final String regex, final int at) {
        if (at + 1 >= regex.length() || regex.charAt(at + 1) != '?') {
            return true;
        }
        return at + 3 < regex.length() && regex.charAt(at + 2) == '<' && regex.charAt(at + 3) != '='
               && regex.charAt(at + 3) != '!';
    }


    // --------------------------------------------------------------------------------


    // A key's runs: how many records, those of several lines, and how many values came back.
    private record Candidate(String key, int runs, List<List<Map<String, String>>> joined, int interleaved) {

    }
}
