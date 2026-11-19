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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XPathRegex;
import stroom.ai.impl.tools.generation.XPathText;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.sample.XmlTree;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// The scope's records indexed for the catalogue's check: each tested field read once a record (as the one evaluator
/// reads it, [LocalCheck#conditionValues]), the records grouped by the field's values, and each condition tested
/// once a distinct value ([LocalCheck#holdsOver]), so hundreds of event types over thousands of records are checked
/// in a time near the records' reading, not types times records.
final class RecordIndex {

    // A record's values of a field that can't be told here (a field an extraction produces, not run here).
    private static final List<String> UNTOLD = List.of("\u0000untold");
    // The held JSON the evaluator reads again: text that starts an object or array.
    private static final Pattern JSON_TEXT = Pattern.compile("^[ \\t\\n\\r]*[\\[{]");
    // A regex is timed first over the longest values, and the rest to this many characters.
    private static final int LONGEST_TIMED = 20;
    private static final long PART_TIMED = 100_000;

    private final CatalogueSample sample;
    private final TranslationMapping reading;
    private final List<Object> records;
    private final int size;
    private final List<Map<String, String>> extracted;
    private final Map<String, List<List<String>>> values = new HashMap<>();
    private final Map<String, Map<List<String>, int[]>> groups = new HashMap<>();
    private final Map<Condition, Verdicts> verdicts = new HashMap<>();
    private final Map<Condition, Map<List<String>, Optional<Boolean>>> memo = new HashMap<>();
    private final Map<Condition, BitSet> unknownSeen = new HashMap<>();
    private final Map<Condition, Boolean> overBudget = new HashMap<>();
    private final Map<String, Map<String, List<List<String>>>> byValue = new HashMap<>();
    private final Map<String, String[]> unparsed = new HashMap<>();
    private List<Map<String, Object>> flat;
    private Set<String> fieldNames;
    private BitSet differing;
    private List<Map<String, String>> extractedWhole;

    RecordIndex(final CatalogueSample sample) {
        this.sample = sample;
        this.reading = sample.reading();
        this.records = sample.records();
        this.size = records.size();
        if (reading.extract().isEmpty()) {
            this.extracted = null;
        } else {
            this.extracted = new ArrayList<>(size);
            records.forEach(record -> extracted.add(LocalCheck.extractedValues(reading, record)));
        }
    }

    int size() {
        return size;
    }

    CatalogueSample sample() {
        return sample;
    }

    // The values of a field, a record's each, as the evaluator reads them; UNTOLD where they can't be told.
    List<List<String>> values(final String field) {
        return values.computeIfAbsent(field, name -> {
            final List<List<String>> out = new ArrayList<>(size);
            for (int n = 0; n < size; n++) {
                final List<String> found = LocalCheck.conditionValues(reading, name, records.get(n), extractedAt(n));
                out.add(found == null
                        ? UNTOLD
                        : found);
            }
            return out;
        });
    }

    // True if some record has a value of the field with text in it.
    boolean anyHas(final String field) {
        for (final List<String> held : values(field)) {
            if (held != UNTOLD && held.stream().anyMatch(value -> !value.isBlank())) {
                return true;
            }
        }
        return false;
    }

    // True if the field's values can't be told for some record (an extraction's, not run here).
    boolean untold(final String field) {
        return values(field).stream().anyMatch(held -> held == UNTOLD);
    }

    // The records by their values of a field, in the order first seen.
    Map<List<String>, int[]> groups(final String field) {
        return groups.computeIfAbsent(field, name -> {
            final Map<List<String>, List<Integer>> grouped = new LinkedHashMap<>();
            final List<List<String>> all = values(name);
            for (int n = 0; n < size; n++) {
                grouped.computeIfAbsent(all.get(n), key -> new ArrayList<>()).add(n);
            }
            final Map<List<String>, int[]> out = new LinkedHashMap<>();
            grouped.forEach((key, at) -> out.put(key, at.stream().mapToInt(Integer::intValue).toArray()));
            return out;
        });
    }

    // Where a condition holds over every record, and where it can't be told (a regex's only once it is known to run
    // within its budget, [#overBudget]).
    Verdicts verdicts(final Condition c) {
        return verdicts.computeIfAbsent(c, this::evaluate);
    }

    private Verdicts evaluate(final Condition c) {
        final BitSet holds = new BitSet(size);
        final BitSet unknown = new BitSet(size);
        if (!LocalCheck.evaluable(c)) {
            unknown.set(0, size);
            return new Verdicts(holds, unknown);
        }
        for (final Map.Entry<List<String>, int[]> group : candidateGroups(c)) {
            final Boolean verdict = group.getKey() == UNTOLD
                    ? null
                    : LocalCheck.holdsOver(reading, c, group.getKey());
            final BitSet into = verdict == null
                    ? unknown
                    : verdict
                            ? holds
                            : null;
            if (into != null) {
                for (final int n : group.getValue()) {
                    into.set(n);
                }
            }
        }
        return new Verdicts(holds, unknown);
    }

    /// Whether a condition can hold only where one of its field's values is one of its own: `equals` or `one_of`,
    /// not negated (XPath's `f = 'x'` holds only where some value is 'x').
    static boolean looksUp(final Condition c) {
        return LocalCheck.evaluable(c) && !c.not() && (c.equalTo() != null || c.oneOf() != null);
    }

    // The groups of records a condition is tested on: those holding one of its values for one that looks them up
    // (each still tested by the evaluator, which has the last word), else every group.
    private List<Map.Entry<List<String>, int[]>> candidateGroups(final Condition c) {
        final Map<List<String>, int[]> all = groups(c.field());
        if (!looksUp(c)) {
            return new ArrayList<>(all.entrySet());
        }
        final Map<String, List<List<String>>> byValue = byValue(c.field());
        final Set<List<String>> keys = new LinkedHashSet<>();
        final List<String> wanted = c.equalTo() != null
                ? List.of(c.equalTo())
                : c.oneOf();
        for (final String value : wanted) {
            keys.addAll(byValue.getOrDefault(XPathText.literalText(value), List.of()));
        }
        keys.addAll(byValue.getOrDefault(null, List.of()));
        final List<Map.Entry<List<String>, int[]>> out = new ArrayList<>();
        keys.forEach(key -> out.add(Map.entry(key, all.get(key))));
        return out;
    }

    // A field's groups by each value they hold; the groups that can't be told under null.
    private Map<String, List<List<String>>> byValue(final String field) {
        return byValue.computeIfAbsent(field, name -> {
            final Map<String, List<List<String>>> out = new HashMap<>();
            for (final List<String> key : groups(name).keySet()) {
                if (key == UNTOLD) {
                    out.computeIfAbsent(null, k -> new ArrayList<>()).add(key);
                } else {
                    new LinkedHashSet<>(key).forEach(value -> out.computeIfAbsent(value, k -> new ArrayList<>())
                            .add(key));
                }
            }
            return out;
        });
    }

    // Where a condition holds among some records only (a way's records so far): a regex is run over no more records
    // than it must.
    BitSet holdsWithin(final Condition c, final BitSet candidates) {
        final Verdicts all = verdicts.get(c);
        final BitSet out = new BitSet(size);
        if (all != null) {
            out.or(all.holds());
            out.and(candidates);
            return out;
        }
        for (int n = candidates.nextSetBit(0); n >= 0; n = candidates.nextSetBit(n + 1)) {
            if (Boolean.TRUE.equals(holdsAt(c, n))) {
                out.set(n);
            }
        }
        return out;
    }

    // Whether a condition holds on one record; null when it can't be told.
    Boolean holdsAt(final Condition c, final int n) {
        final Verdicts all = verdicts.get(c);
        if (all != null) {
            return all.unknown().get(n)
                    ? null
                    : all.holds().get(n);
        }
        if (!LocalCheck.evaluable(c)) {
            return null;
        }
        final Boolean verdict = holdsOver(c, values(c.field()).get(n));
        if (verdict == null) {
            unknownSeen.computeIfAbsent(c, key -> new BitSet(size)).set(n);
        }
        return verdict;
    }

    // The records a condition was found not to be told on, of those it was tested on.
    BitSet unknown(final Condition c) {
        final Verdicts all = verdicts.get(c);
        return all != null
                ? all.unknown()
                : unknownSeen.getOrDefault(c, new BitSet());
    }

    private Boolean holdsOver(final Condition c, final List<String> held) {
        if (held == UNTOLD) {
            return null;
        }
        return memo.computeIfAbsent(c, key -> new HashMap<>())
                .computeIfAbsent(held, key -> Optional.ofNullable(LocalCheck.holdsOver(reading, c, key)))
                .orElse(null);
    }

    // Whether a condition's regex takes longer than the budget over the field's values in the sample, run as the
    // evaluator runs it (Saxon's engine): timed over the longest values and a part of the rest first, and over every
    // value only when that part, as a share of the whole, would run past the budget, so a regex is refused only on
    // its time over the whole sample.
    boolean overBudget(final Condition c) {
        if (c.matches() == null || !LocalCheck.evaluable(c)) {
            return false;
        }
        return overBudget.computeIfAbsent(c, key -> {
            final List<String> distinct = distinctValues(c.field());
            long total = 0;
            for (final String value : distinct) {
                total += value.length();
            }
            final List<String> part = part(distinct);
            long partLength = 0;
            for (final String value : part) {
                partLength += value.length();
            }
            final long budget = sample.regexBudget().toNanos();
            final String regex = XPathText.literalText(c.matches());
            final long started = System.nanoTime();
            if (!runsWithin(regex, part, started + budget)) {
                return true;
            }
            if (part.size() == distinct.size()) {
                return false;
            }
            final long took = System.nanoTime() - started;
            final double projected = partLength == 0
                    ? took
                    : (double) took * total / partLength;
            return projected > budget && !runsWithin(regex, distinct, System.nanoTime() + budget);
        });
    }

    // Every distinct value of a field in the records.
    private List<String> distinctValues(final String field) {
        final Set<String> out = new LinkedHashSet<>();
        for (final List<String> held : groups(field).keySet()) {
            if (held != UNTOLD) {
                out.addAll(held);
            }
        }
        return new ArrayList<>(out);
    }

    // The values a regex is timed over first: the longest, then the rest in order to a share of the sample.
    private static List<String> part(final List<String> distinct) {
        final List<String> longest = distinct.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(LONGEST_TIMED)
                .toList();
        final Set<String> out = new LinkedHashSet<>(longest);
        long length = 0;
        for (final String value : out) {
            length += value.length();
        }
        for (final String value : distinct) {
            if (length >= PART_TIMED) {
                break;
            }
            if (out.add(value)) {
                length += value.length();
            }
        }
        return new ArrayList<>(out);
    }

    // Whether a regex runs over every value given before the deadline.
    private static boolean runsWithin(final String regex, final List<String> values, final long deadline) {
        try {
            for (final String value : values) {
                XPathRegex.findsWithin(regex, value, deadline);
                if (System.nanoTime() - deadline > 0) {
                    return false;
                }
            }
            return true;
        } catch (final XPathRegex.TimedOut e) {
            return false;
        } catch (final XPathRegex.XPathRegexException | StackOverflowError e) {
            // Said by the catalogue's validation, or by the evaluator's verdict that it can't be told.
            return true;
        }
    }

    // Why a record's held values that a field is read through don't parse, each held field's in turn from the
    // outermost; null when they parse (or aren't held).
    String unparsed(final String field, final int n) {
        final String held = heldField(field);
        if (held == null) {
            return null;
        }
        final String outer = unparsed(held, n);
        if (outer != null) {
            return outer;
        }
        return unparsed.computeIfAbsent(held, this::whyUnparsed)[n];
    }

    private String heldField(final String field) {
        final String xml = MappingFacts.heldXmlField(reading.xmlFields(), field);
        return xml != null
                ? xml
                : MappingFacts.heldJsonField(reading.jsonFields(), field);
    }

    private String[] whyUnparsed(final String held) {
        final boolean xml = reading.xmlFields().contains(held);
        final String[] out = new String[size];
        final List<List<String>> all = values(held);
        for (int n = 0; n < size; n++) {
            final List<String> found = all.get(n);
            if (found != UNTOLD && !found.isEmpty()) {
                out[n] = xml
                        ? xmlProblem(held, found.getFirst())
                        : jsonProblem(held, found.getFirst());
            }
        }
        return out;
    }

    // Why held JSON doesn't parse, as the evaluator reads it (a JSON string holding JSON read again); null when it
    // does, or isn't JSON at all.
    private static String jsonProblem(final String held, final String text) {
        if (!JSON_TEXT.matcher(text).find()) {
            return null;
        }
        try {
            Object parsed = ToolJson.loads(text);
            while (parsed instanceof final String inner && JSON_TEXT.matcher(inner).find()) {
                parsed = ToolJson.loads(inner);
            }
            return null;
        } catch (final ToolJson.JsonDecodeException e) {
            return held + ": " + e.msg() + " at " + e.pos();
        } catch (final StackOverflowError e) {
            return held + ": nested too deeply to read";
        }
    }

    private static String xmlProblem(final String held, final String text) {
        if (!text.strip().startsWith("<")) {
            return null;
        }
        try {
            XmlTree.parse(text);
            return null;
        } catch (final XmlTree.XmlException e) {
            return held + ": " + e.getMessage();
        }
    }

    // The records whose strings processing cuts: read whole, they differ.
    BitSet differing() {
        if (differing == null) {
            differing = new BitSet(size);
            if (sample.whole() != null) {
                for (int n = 0; n < size; n++) {
                    if (!Objects.equals(records.get(n), sample.whole().get(n))) {
                        differing.set(n);
                    }
                }
            }
        }
        return differing;
    }

    // Whether a condition holds on a record read whole (no string cut); null when it can't be told.
    Boolean holdsWhole(final Condition c, final int n) {
        if (extractedWhole == null && extracted != null) {
            extractedWhole = new ArrayList<>(size);
            sample.whole().forEach(record -> extractedWhole.add(LocalCheck.extractedValues(reading, record)));
        }
        final List<String> found = LocalCheck.conditionValues(reading, c.field(), sample.whole().get(n),
                extractedWhole == null
                        ? null
                        : extractedWhole.get(n));
        return found == null
                ? null
                : LocalCheck.holdsOver(reading, c, found);
    }

    // A record's fields as name to value, the fields its held values and extractions give it included, as the draft
    // names them.
    Map<String, Object> flat(final int n) {
        if (flat == null) {
            flat = new ArrayList<>(size);
            for (int at = 0; at < size; at++) {
                flat.add(null);
            }
        }
        Map<String, Object> view = flat.get(n);
        if (view == null) {
            view = new LinkedHashMap<>();
            final Object record = records.get(n);
            if (record instanceof final Map<?, ?> map) {
                for (final Map.Entry<?, ?> entry : map.entrySet()) {
                    view.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } else if (record instanceof final XmlTree.Element element) {
                view.putAll(LocalCheck.xmlFlat(element));
            }
            final Map<String, String> pairs = extractedAt(n);
            if (pairs != null) {
                final Map<String, Object> into = view;
                pairs.forEach((name, value) -> {
                    if (!value.isEmpty()) {
                        into.putIfAbsent(name, value);
                    }
                });
            }
            flat.set(n, view);
        }
        return view;
    }

    // A record's flat view as its shape is signed: a held field's own text left out where its fields are read from
    // it (they are signed, not the text they are read from).
    Map<String, Object> shapeView(final int n) {
        final Map<String, Object> view = flat(n);
        final Set<String> parents = new LinkedHashSet<>();
        for (final String key : view.keySet()) {
            final String held = heldField(key);
            if (held != null && !held.equals(key)) {
                parents.add(held);
            }
        }
        if (parents.isEmpty()) {
            return view;
        }
        final Map<String, Object> out = new LinkedHashMap<>(view);
        parents.forEach(out::remove);
        return out;
    }

    // A field's value in a record's flat view, as text; null when it has none.
    String text(final int n, final String field) {
        final Object value = flat(n).get(field);
        return value == null
                ? null
                : value instanceof final String text
                        ? text
                        : JsonValues.jsonText(value);
    }

    // Every field some record has, as the records spell them.
    Set<String> fieldNames() {
        if (fieldNames == null) {
            fieldNames = new LinkedHashSet<>();
            for (int n = 0; n < size; n++) {
                fieldNames.addAll(flat(n).keySet());
            }
        }
        return fieldNames;
    }

    // A record's text: given, or written from the record (a map without the fields its held values give it, as the
    // source wrote it; XML as XML).
    String recordText(final int n) {
        if (sample.texts() != null) {
            return sample.texts().get(n);
        }
        final Object record = records.get(n);
        if (record instanceof final XmlTree.Element element) {
            return XmlTree.toText(element);
        }
        if (record instanceof final Map<?, ?> map) {
            final Map<String, Object> own = new LinkedHashMap<>();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                final String key = String.valueOf(entry.getKey());
                if (!heldBelow(key)) {
                    own.put(key, entry.getValue());
                }
            }
            return ToolJson.dumps(own);
        }
        return String.valueOf(record);
    }

    // Whether a key is one a held value gives (read inside a json_fields or xml_fields field the record has).
    private boolean heldBelow(final String key) {
        final String held = heldField(key);
        return held != null && !held.equals(key);
    }

    // A record's line format (its variant), or null.
    String variant(final int n) {
        return sample.variantField() == null
                ? null
                : LocalCheck.valueOf(records.get(n), sample.variantField());
    }

    private Map<String, String> extractedAt(final int n) {
        return extracted == null
                ? null
                : extracted.get(n);
    }


    // --------------------------------------------------------------------------------


    // Where a condition holds and where it can't be told.
    record Verdicts(BitSet holds, BitSet unknown) {

    }
}
