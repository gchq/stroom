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
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.ByField;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Count;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Overlap;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Several;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Shape;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.ShapeCount;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.TellingField;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Uncatalogued;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult.Unparsed;
import stroom.ai.impl.tools.catalogue.CatalogueValidation.Problem;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.XPathText;
import stroom.ai.impl.tools.sample.KindFields;
import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.Shapes;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/// The catalogue's check (`catalogue_event_types` `proposals=`): the whole catalogue against every record of the
/// scope, read as processing will read them. Pure: the records in, the check's JSON out ([CatalogueCheckResult]).
///
/// * **Counts** per event type and per way of recognising it.
/// * **Overlaps** between different event types, refused: the record, both types and the fields whose values tell
///   their other records apart ([TellingFields]). Two ways of recognising one type may overlap.
/// * **Uncatalogued records** (no event type recognises them), by shape ([Shapes]' signatures), with examples that
///   differ most; not refused.
/// * **Unparsed**: records whose held values, read by a condition, don't parse (counted, not uncatalogued).
/// * **May be several event types**: a type whose own records split by a field of their own ([KindFields] over
///   them, its conditions' fields aside) or come in clearly different shapes; blocking for the draft's seed unless
///   it says why it is one.
/// * **Problems**: the catalogue's own ([CatalogueValidation]), and what its records show: a field no record has; a
///   regex over its time budget (timed in Saxon's engine, as processing runs it); a regex or `contains` over values
///   longer than [#LONG_VALUE] where `starts_with` or `contains` recognises the same records; a condition that
///   depends on processing's string cut; an event type recognised by every record of a line format that shares it;
///   an `equals` or `one_of` naming a value that differs from at least [#UNIQUE_WRITTEN_VALUES] of the field's
///   values only in a part that varies (an id, a number, an address, a quoted string: [Shapes#mask]), with
///   `starts_with` or a `matches` with a named group suggested.
/// * **Order**: the catalogue's ([CatalogueOrder]).
///
/// Each tested field is read once a record and each condition tested once a distinct value ([RecordIndex]), so
/// hundreds of event types over thousands of records take a time near the records' reading.
public final class CatalogueCheck {

    /// A value longer than this is long: a regex over it reads it all (processing's JSONParser cuts strings here by
    /// default).
    public static final int LONG_VALUE = LocalCheck.JSON_PARSER_CUT;

    /// The fewest of a field's values written alike, differing only in a part that varies, for a value of them named
    /// by an `equals` or `one_of` to be refused ([#uniqueValues]): two may be two event types' own messages.
    static final int UNIQUE_WRITTEN_VALUES = 3;

    // The most records of an event type read for whether it may be several, and of a shape for its examples.
    private static final int SEVERAL_READ = 2_000;
    private static final int EXAMPLES_READ = 200;
    // The most examples a shape keeps.
    private static final int EXAMPLES = 5;
    // The most characters of a value an example gives.
    private static final int EXAMPLE_VALUE = 100;
    // The most overlaps given their telling fields.
    private static final int TOLD_OVERLAPS = 50;
    // Two shapes whose fields share less than this of their union are clearly different.
    private static final double DIFFERENT_SHAPES = 0.5;
    // A shape of an event type's records counts when it has at least this share of them (and two records).
    private static final double SHAPE_SHARE = 0.1;
    // The characters an XPath regex gives a meaning; escaped, each is itself.
    private static final String META = ".\\?*+{}()[]|^$";
    // A part Shapes.mask puts in place of a variable one.
    private static final Pattern MASKED_PART = ToolText.re("<(?:s|email|ip|uuid|hex|n)>");
    private static final String ESCAPABLE = ".\\?*+{}()[]|^$-";

    private CatalogueCheck() {
        // Static only.
    }

    /// @param catalogue The catalogue.
    /// @param sample    The scope's records, as processing reads them.
    /// @param schema    The event-logging schema the event types' meanings are checked against, or null to skip
    ///                  those checks.
    /// @return What the check found.
    public static CatalogueCheckResult check(final Catalogue catalogue,
                                             final CatalogueSample sample,
                                             final EventSchema schema) {
        return check(catalogue, sample, schema, Drafted.NONE);
    }

    /// As [#check(Catalogue, CatalogueSample, EventSchema)], knowing what the draft writes ([Drafted]): its common
    /// fields aren't counted as an event type's Data, its common user is Authenticate's where an event type names no
    /// actor, and what its own rule taking most of a type's records writes below the action element counts for the
    /// elements the schema requires, as the translation writes them all.
    ///
    /// @param catalogue The catalogue.
    /// @param sample    The scope's records, as processing reads them.
    /// @param schema    The event-logging schema, or null to skip the schema's checks.
    /// @param drafted   What the draft writes: in common, and its own rules.
    /// @return What the check found.
    public static CatalogueCheckResult check(final Catalogue catalogue,
                                             final CatalogueSample sample,
                                             final EventSchema schema,
                                             final Drafted drafted) {
        return new Run(catalogue, new RecordIndex(sample), schema, drafted).result();
    }

    /// A few records of each event type given, from the scope's records as processing reads them, for the user who
    /// asks what an event type's records look like while confirming it (`catalogue_event_types` `explain=`): each
    /// record's text shortened, and the values the type's conditions test as they were read. Pure, as the check is.
    ///
    /// @param sample The scope's records, as processing reads them.
    /// @param types  The event types.
    /// @param most   The most records given an event type.
    /// @return The records of each, by TypeId (an event type without one by its place, `#0`), in the records' order.
    public static Map<String, List<Example>> examples(final CatalogueSample sample,
                                                      final List<EventType> types,
                                                      final int most) {
        final RecordIndex index = new RecordIndex(sample);
        final Run run = new Run(new Catalogue(types, null, null), index, null, Drafted.NONE);
        final Map<String, List<Example>> out = new LinkedHashMap<>();
        for (int t = 0; t < types.size(); t++) {
            final EventType type = types.get(t);
            final BitSet recognised = run.recognised(type);
            final Set<String> tested = new LinkedHashSet<>();
            type.recognisedBy().forEach(way -> way.when().forEach(c -> {
                if (c.field() != null) {
                    tested.add(c.field());
                }
            }));
            final List<Example> examples = new ArrayList<>();
            for (int n = recognised.nextSetBit(0);
                 n >= 0 && examples.size() < most;
                 n = recognised.nextSetBit(n + 1)) {
                final Map<String, String> values = new LinkedHashMap<>();
                for (final String field : tested) {
                    final String value = index.text(n, field);
                    if (value != null) {
                        values.put(field, shortened(value, EXAMPLE_VALUE));
                    }
                }
                examples.add(new Example(n, CatalogueCheckResult.excerpt(index.recordText(n), false), values));
            }
            out.put(NullSafe.isBlankString(type.typeId())
                    ? "#" + t
                    : type.typeId(), List.copyOf(examples));
        }
        return out;
    }

    private static String shortened(final String text, final int most) {
        return text.length() <= most
                ? text
                : text.substring(0, most) + "…";
    }

    // The literal text an anchored regex starts with (`^Request:` gives `Request:`), or null.
    static String anchoredPrefix(final String regex) {
        if (regex == null || !regex.startsWith("^") || regex.indexOf('|') >= 0) {
            return null;
        }
        final String text = literalRun(regex, 1)[0];
        return text.isEmpty()
                ? null
                : text;
    }

    // The longest literal text a regex holds wherever it matches (`Fault>` of `<\w+:Fault>`), or null.
    static String longestLiteral(final String regex) {
        if (regex == null || regex.indexOf('|') >= 0) {
            return null;
        }
        String best = "";
        int at = 0;
        while (at < regex.length()) {
            final String[] run = literalRun(regex, at);
            if (run[0].length() > best.length()) {
                best = run[0];
            }
            at = Math.max(at + 1, skipToken(regex, Integer.parseInt(run[1])));
        }
        return best.isEmpty()
                ? null
                : best;
    }

    // The literal characters from a place in a regex, and where they end: a character followed by ?, * or { is
    // optional, so not among them.
    private static String[] literalRun(final String regex, final int from) {
        final StringBuilder out = new StringBuilder();
        int at = from;
        while (at < regex.length()) {
            final char c = regex.charAt(at);
            final char literal;
            final int next;
            if (c == '\\') {
                if (at + 1 >= regex.length() || ESCAPABLE.indexOf(regex.charAt(at + 1)) < 0) {
                    break;
                }
                literal = regex.charAt(at + 1);
                next = at + 2;
            } else if (META.indexOf(c) >= 0) {
                break;
            } else {
                literal = c;
                next = at + 1;
            }
            if (next < regex.length() && "?*{".indexOf(regex.charAt(next)) >= 0) {
                break;
            }
            out.append(literal);
            at = next;
            if (at < regex.length() && regex.charAt(at) == '+') {
                break;
            }
        }
        return new String[]{out.toString(), String.valueOf(at)};
    }

    // Past the regex token at a place: a character class, a count, an escape, or one character.
    private static int skipToken(final String regex, final int at) {
        if (at >= regex.length()) {
            return at;
        }
        final char c = regex.charAt(at);
        if (c == '\\') {
            return at + 2;
        }
        final char close = c == '['
                ? ']'
                : c == '{'
                        ? '}'
                        : 0;
        if (close == 0) {
            return at + 1;
        }
        for (int i = at + 1; i < regex.length(); i++) {
            if (regex.charAt(i) == '\\') {
                i++;
            } else if (regex.charAt(i) == close) {
                return i + 1;
            }
        }
        return regex.length();
    }


    // A value as it is written, its variable parts masked (Shapes.mask), when it has both a variable part and fixed
    // text; else null: a code (all fixed text, as "Login", or all variable, as "4624") is no pattern.
    private static String writtenForm(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final List<String> tokens = Shapes.mask(value);
        boolean variable = false;
        boolean fixed = false;
        for (final String token : tokens) {
            final String rest = MASKED_PART.matcher(token).replaceAll("");
            variable |= rest.length() < token.length();
            fixed |= rest.chars().anyMatch(Character::isLetter);
        }
        return variable && fixed
                ? String.join(" ", tokens)
                : null;
    }


    // --------------------------------------------------------------------------------


    // One check of a catalogue over the records.
    private static final class Run {

        private final Catalogue catalogue;
        private final RecordIndex index;
        private final EventSchema schema;
        private final List<EventType> types;
        private final int size;
        private final List<List<BitSet>> byWay = new ArrayList<>();
        private final List<BitSet> byType = new ArrayList<>();
        private final List<String> keys = new ArrayList<>();
        private final Map<Condition, List<String>> conditionProblems = new HashMap<>();
        // By field: how many of its values are written each way, their variable parts masked (uniqueValues).
        private final Map<String, Map<String, Integer>> writtenAlike = new HashMap<>();
        private final Predicate<String> onSourceType;
        private final Drafted drafted;

        private Run(final Catalogue catalogue,
                    final RecordIndex index,
                    final EventSchema schema,
                    final Drafted drafted) {
            this.catalogue = catalogue;
            this.drafted = drafted;
            this.index = index;
            this.schema = schema;
            this.types = catalogue.eventTypes();
            this.size = index.size();
            final String sourceTypeField = catalogue.scope().sourceTypeField();
            this.onSourceType = field -> CatalogueValidation.onSourceTypeField(sourceTypeField, field);
        }

        private CatalogueCheckResult result() {
            final List<Problem> problems = new ArrayList<>(CatalogueValidation.problems(catalogue, schema));
            final List<Problem> notChecked = new ArrayList<>();
            final Map<String, Count> counts = new LinkedHashMap<>();
            for (int t = 0; t < types.size(); t++) {
                final EventType type = types.get(t);
                final List<BitSet> ways = new ArrayList<>();
                final BitSet all = new BitSet(size);
                if (type.recognisedBy().isEmpty()) {
                    all.set(0, size);
                }
                for (int w = 0; w < type.recognisedBy().size(); w++) {
                    final BitSet way = way(type.recognisedBy().get(w));
                    ways.add(way);
                    all.or(way);
                    for (final Condition c : type.recognisedBy().get(w).when()) {
                        for (final String problem : conditionProblems(c)) {
                            problems.add(new Problem(type.typeId(), w, problem));
                        }
                        final Optional<String> unchecked = notChecked(c);
                        if (unchecked.isPresent()) {
                            notChecked.add(new Problem(type.typeId(), w, unchecked.get()));
                        }
                    }
                }
                byWay.add(ways);
                byType.add(all);
                final String key = key(type, t, counts);
                keys.add(key);
                counts.put(key, new Count(all.cardinality(), ways.stream().map(BitSet::cardinality).toList()));
            }
            problems.addAll(soleOfLineFormat());
            final List<Problem> unplaced = new ArrayList<>();
            final Map<String, CatalogueCheckResult.Typing> typing = new LinkedHashMap<>();
            if (schema != null) {
                placements(problems, unplaced, typing);
            }
            final BitSet unparsedRecords = new BitSet(size);
            final Unparsed unparsed = unparsed(unparsedRecords);
            return new CatalogueCheckResult(counts, overlaps(), uncatalogued(unparsedRecords), unparsed, several(),
                    distinct(problems), distinct(notChecked),
                    CatalogueOrder.ordered(types).stream().map(EventType::typeId).toList(), distinct(unplaced),
                    typing);
        }

        // Each event type's placements checked against the schema and its records, the elements the schema
        // requires that nothing writes, and how many of its fields are typed ([PlacementCheck]).
        private void placements(final List<Problem> problems,
                                final List<Problem> unplaced,
                                final Map<String, CatalogueCheckResult.Typing> typing) {
            final PlacementCheck check = new PlacementCheck(schema, index, this::missing, catalogue, drafted,
                    when -> way(new Way(when, List.of())));
            for (int t = 0; t < types.size(); t++) {
                final EventType type = types.get(t);
                final PlacementCheck.Checked checked = check.check(type, byType.get(t));
                checked.problems().forEach(problem -> problems.add(new Problem(type.typeId(), -1, problem)));
                checked.unplaced().forEach(problem -> unplaced.add(new Problem(type.typeId(), -1, problem)));
                if (checked.typing() != null) {
                    typing.put(keys.get(t), checked.typing());
                }
            }
        }

        // An event type's key in the counts: its TypeId, or its place where it has none (or shares one).
        private static String key(final EventType type, final int at, final Map<String, Count> counts) {
            final String typeId = type.typeId() == null || type.typeId().isBlank()
                    ? "#" + at
                    : type.typeId();
            return counts.containsKey(typeId)
                    ? typeId + "#" + at
                    : typeId;
        }

        private static List<Problem> distinct(final List<Problem> problems) {
            return List.copyOf(new LinkedHashSet<>(problems));
        }

        // The records an event type recognises, by any of its ways (every record for one with none).
        private BitSet recognised(final EventType type) {
            final BitSet all = new BitSet(size);
            if (type.recognisedBy().isEmpty()) {
                all.set(0, size);
            }
            type.recognisedBy().forEach(way -> all.or(way(way)));
            return all;
        }

        // The records a way recognises: every condition holds (none can't be told). Its conditions that look values
        // up come first; the others are tested over the records those leave, its regexes last, and not at all when
        // one runs past its budget.
        private BitSet way(final Way way) {
            final BitSet out = new BitSet(size);
            out.set(0, size);
            final List<Condition> rest = new ArrayList<>();
            final List<Condition> regexes = new ArrayList<>();
            for (final Condition c : way.when()) {
                if (RecordIndex.looksUp(c) || !LocalCheck.evaluable(c)) {
                    out.and(index.verdicts(c).holds());
                } else if (c.matches() != null) {
                    regexes.add(c);
                } else {
                    rest.add(c);
                }
            }
            // Those that read every value tested over the records the values looked up leave.
            for (final Condition c : rest) {
                out.and(index.holdsWithin(c, out));
            }
            for (final Condition c : regexes) {
                if (index.overBudget(c)) {
                    out.clear();
                } else {
                    out.and(index.holdsWithin(c, out));
                }
            }
            return out;
        }

        // What the records show of a condition, said once however many ways have it.
        private List<String> conditionProblems(final Condition c) {
            final List<String> known = conditionProblems.get(c);
            if (known != null) {
                return known;
            }
            final List<String> out = new ArrayList<>();
            final String field = c.field();
            if (notChecked(c).isPresent()) {
                // Said as not checked ([#notChecked]), not refused.
                conditionProblems.put(c, out);
                return out;
            }
            if (index.overBudget(c)) {
                out.add("the regex of " + field + " ran past " + index.sample().regexBudget().toMillis() + " ms over "
                        + "the sample's records and was stopped: it backtracks, and would take as long in processing "
                        + "for every stream; tell the records apart by a short anchored pattern, or by starts_with or "
                        + "contains");
            } else {
                if (!index.unknown(c).isEmpty()) {
                    out.add("the regex of " + field + " can't be run over " + index.unknown(c).cardinality()
                            + " record(s): it overflows the stack, as it would in processing; tell the records apart "
                            + "by a simpler pattern, or by starts_with or contains");
                }
                if (!index.anyHas(field)) {
                    out.add(missing(field));
                }
                longValues(c).ifPresent(out::add);
                cutProblem(c).ifPresent(out::add);
                uniqueValues(c).ifPresent(out::add);
            }
            conditionProblems.put(c, out);
            return out;
        }

        // Why a condition can't be checked against the records here (an xpath, a dictionary, a record-scope test, a
        // field an extraction gives), so the records it recognises aren't counted: said, not refused (settled with the
        // check and translation lanes, 2026-11-19), never silently skipped. Empty when it can be.
        private Optional<String> notChecked(final Condition c) {
            final String field = c.field();
            if (!LocalCheck.evaluable(c)) {
                return Optional.of("the condition on " + (field == null
                        ? c.xpath()
                        : field) + " can't be checked against the records here (an xpath, a dictionary or a "
                                   + "record-scope field), so the records it recognises aren't counted");
            }
            if (index.untold(field)) {
                return Optional.of("the condition on " + field + ", a field an extraction gives, can't be checked "
                                   + "against the records here, so the records it recognises aren't counted");
            }
            return Optional.empty();
        }

        // A field no record has, with the record fields its name could have meant.
        private String missing(final String field) {
            final String last = lastName(field);
            final List<String> close = index.fieldNames().stream()
                    .filter(name -> !name.equals(field) && lastName(name).equalsIgnoreCase(last))
                    .limit(3)
                    .toList();
            return "no record has the field " + field + (close.isEmpty()
                    ? ""
                    : " (the records have " + String.join(", ", close) + ")") + ": name a field as the records "
                   + "spell it";
        }

        private static String lastName(final String field) {
            final String[] parts = field.split("[./>@\\[\\]']+");
            return parts.length == 0
                    ? field
                    : parts[parts.length - 1];
        }

        // A regex or contains over long values where starts_with or contains recognises the same records.
        private Optional<String> longValues(final Condition c) {
            if (c.matches() == null && c.contains() == null) {
                return Optional.empty();
            }
            final int longRecords = recordsWithLongValues(c.field());
            if (longRecords == 0) {
                return Optional.empty();
            }
            final String what = c.matches() != null
                    ? "the regex of " + c.field()
                    : "contains over " + c.field();
            final List<Map.Entry<String, String>> instead = new ArrayList<>();
            if (c.matches() != null) {
                final String prefix = anchoredPrefix(c.matches());
                if (prefix != null) {
                    instead.add(Map.entry("starts_with", prefix));
                }
                final String literal = longestLiteral(c.matches());
                if (literal != null) {
                    instead.add(Map.entry("contains", literal));
                }
            } else {
                instead.add(Map.entry("starts_with", c.contains()));
            }
            for (final Map.Entry<String, String> test : instead) {
                final Map<String, Object> json = new LinkedHashMap<>();
                json.put("field", c.field());
                json.put(test.getKey(), test.getValue());
                if (c.not()) {
                    json.put("not", true);
                }
                if (index.verdicts(Condition.fromJson(json)).holds().equals(index.verdicts(c).holds())) {
                    return Optional.of(what + " reads values longer than " + String.format("%,d",
                            LONG_VALUE) + " characters (" + longRecords + " record(s)), and " + test.getKey() + " '"
                                                 + test.getValue() + "' recognises the same records: use "
                                                 + test.getKey());
                }
            }
            return Optional.empty();
        }

        private int recordsWithLongValues(final String field) {
            int out = 0;
            for (final List<String> values : index.values(field)) {
                // Processing's cut leaves a long value at the cut's length: still a long value.
                if (values.stream().anyMatch(value -> value.length() >= LONG_VALUE)) {
                    out++;
                }
            }
            return out;
        }

        // An equals or one_of naming a value that differs from others of the field only in a part that varies (a
        // message holding an object's id, a different one each record): at least UNIQUE_WRITTEN_VALUES of the
        // field's values are written alike once their variable parts are masked (Shapes.mask: quoted strings,
        // addresses, UUIDs, long hex numbers, words with a digit), keeping some fixed text. Such an event type
        // recognises next to no records, and in processing every new value is uncatalogued. A code is never
        // refused, however rare and however varied its field (seen in the full e2e run: eval cases 32 and 35, a
        // sample of one record an event type, each its own EventID or action): it is all fixed text, or all
        // variable part.
        private Optional<String> uniqueValues(final Condition c) {
            final List<String> named = c.equalTo() != null
                    ? List.of(c.equalTo())
                    : c.oneOf();
            if (named == null || named.isEmpty()) {
                return Optional.empty();
            }
            final Map<String, Integer> written = writtenAlike.computeIfAbsent(c.field(), field -> {
                final Map<String, Integer> byWritten = new HashMap<>();
                for (final String value : index.variety(field).recordsByValue().keySet()) {
                    final String form = writtenForm(value);
                    if (form != null) {
                        byWritten.merge(form, 1, Integer::sum);
                    }
                }
                return byWritten;
            });
            for (final String value : named) {
                final String form = writtenForm(XPathText.literalText(value));
                final int alike = form == null
                        ? 0
                        : written.getOrDefault(form, 0);
                if (alike >= UNIQUE_WRITTEN_VALUES) {
                    return Optional.of("'" + value + "' is one of " + alike + " values of the field " + c.field()
                            + " that differ only in a part that varies (written '" + form + "'): an event type "
                            + "recognised by one of them recognises next to no records, and in processing every "
                            + "record with a new value would be uncatalogued; recognise the event type by what its "
                            + "values share: starts_with their fixed start, or matches with a regex whose named "
                            + "group (names) keeps the part that varies");
                }
            }
            return Optional.empty();
        }

        // A condition whose verdict on some record read whole differs from processing's, which cuts strings first.
        private Optional<String> cutProblem(final Condition c) {
            final BitSet differing = index.differing();
            int changed = 0;
            Boolean wholeHolds = null;
            for (int n = differing.nextSetBit(0); n >= 0; n = differing.nextSetBit(n + 1)) {
                final Boolean cut = index.holdsAt(c, n);
                final Boolean whole = cut == null
                        ? null
                        : index.holdsWhole(c, n);
                if (whole != null && !whole.equals(cut)) {
                    changed++;
                    if (wholeHolds == null) {
                        wholeHolds = whole;
                    }
                }
            }
            if (changed == 0) {
                return Optional.empty();
            }
            final int cut = index.sample().stringTruncateLength();
            return Optional.of("whether the condition on " + c.field() + " holds depends on processing's "
                                         + "string cut: on " + changed + " record(s) it " + (wholeHolds
                    ? "holds read whole but not"
                    : "doesn't hold read whole but does") + " as processing reads them, its strings cut at "
                                         + "stringTruncateLength (" + String.format("%,d", cut) + " characters): "
                                         + "test what comes before the cut, or raise the pipeline's "
                                         + "stringTruncateLength");
        }

        // An event type recognised by every record of a line format (its conditions only on the variant field) that
        // another event type recognises records of: only the line format's only event type is recognised that way.
        private List<Problem> soleOfLineFormat() {
            final String variantField = index.sample().variantField();
            final List<Problem> out = new ArrayList<>();
            if (variantField == null) {
                return out;
            }
            for (int t = 0; t < types.size(); t++) {
                final EventType type = types.get(t);
                for (int w = 0; w < type.recognisedBy().size(); w++) {
                    final List<Condition> when = type.recognisedBy().get(w).when();
                    if (when.isEmpty() || !when.stream().allMatch(c -> variantField.equals(c.field()))) {
                        continue;
                    }
                    final BitSet format = byWay.get(t).get(w);
                    for (int u = 0; u < types.size(); u++) {
                        final BitSet shared = (BitSet) byType.get(u).clone();
                        shared.and(format);
                        if (u != t && !shared.isEmpty()) {
                            final int first = shared.nextSetBit(0);
                            out.add(new Problem(type.typeId(), w, "it is recognised by every record of its line "
                                    + "format (" + variantField + " is " + index.variant(first) + "), which only "
                                    + "the line format's only event type is: " + types.get(u).typeId()
                                    + " recognises " + shared.cardinality() + " of its records; tell them apart"));
                        }
                    }
                }
            }
            return out;
        }

        // Records two event types recognise, one a pair of types (its first record), in the records' order.
        private List<Overlap> overlaps() {
            final int[] first = new int[size];
            Arrays.fill(first, -1);
            final Map<Integer, List<Integer>> shared = new TreeMap<>();
            for (int t = 0; t < types.size(); t++) {
                final BitSet own = byType.get(t);
                for (int n = own.nextSetBit(0); n >= 0; n = own.nextSetBit(n + 1)) {
                    if (first[n] < 0) {
                        first[n] = t;
                    } else {
                        final int at = n;
                        shared.computeIfAbsent(n, key -> new ArrayList<>(List.of(first[at]))).add(t);
                    }
                }
            }
            final Map<List<Integer>, int[]> pairs = new LinkedHashMap<>();
            shared.forEach((record, sharing) -> {
                for (int i = 0; i < sharing.size(); i++) {
                    for (int j = i + 1; j < sharing.size(); j++) {
                        final int[] seen = pairs.computeIfAbsent(List.of(sharing.get(i), sharing.get(j)),
                                pair -> new int[]{record, 0});
                        seen[1]++;
                    }
                }
            });
            final List<Overlap> out = new ArrayList<>();
            for (final Map.Entry<List<Integer>, int[]> pair : pairs.entrySet()) {
                final int a = pair.getKey().get(0);
                final int b = pair.getKey().get(1);
                final List<TellingField> telling = out.size() < TOLD_OVERLAPS
                        ? TellingFields.between(index, keys.get(a), byType.get(a), keys.get(b), byType.get(b),
                        this::notToTell)
                        : List.of();
                out.add(new Overlap(pair.getValue()[0], index.recordText(pair.getValue()[0]),
                        List.of(keys.get(a), keys.get(b)), telling, pair.getValue()[1]));
            }
            return out;
        }

        // A field never offered to tell event types apart: the build's source type field.
        private boolean notToTell(final String field) {
            return onSourceType.test(field);
        }

        // The records whose held values, read by a condition, don't parse, and why.
        private Unparsed unparsed(final BitSet into) {
            final Set<String> fields = new LinkedHashSet<>();
            types.forEach(type -> type.recognisedBy().forEach(way -> way.when().stream()
                    .filter(LocalCheck::evaluable)
                    .forEach(c -> fields.add(c.field()))));
            final Set<String> why = new LinkedHashSet<>();
            for (final String field : fields) {
                for (int n = 0; n < size; n++) {
                    final String problem = index.unparsed(field, n);
                    if (problem != null) {
                        into.set(n);
                        why.add(problem);
                    }
                }
            }
            return into.isEmpty()
                    ? Unparsed.NONE
                    : new Unparsed(into.cardinality(), List.copyOf(why));
        }

        // The records no event type recognises (and whose held values parse), by shape, commonest first.
        private Uncatalogued uncatalogued(final BitSet unparsedRecords) {
            final BitSet left = new BitSet(size);
            left.set(0, size);
            byType.forEach(left::andNot);
            left.andNot(unparsedRecords);
            if (left.isEmpty()) {
                return Uncatalogued.NONE;
            }
            final List<Integer> at = new ArrayList<>();
            final List<String> texts = new ArrayList<>();
            final List<Map<String, Object>> parsed = new ArrayList<>();
            for (int n = left.nextSetBit(0); n >= 0; n = left.nextSetBit(n + 1)) {
                at.add(n);
                texts.add(index.recordText(n));
                parsed.add(index.shapeView(n));
            }
            final RecordSplitter.Chunk chunk = new RecordSplitter.Chunk(SampleProfiler.JSON_LINES, texts, parsed,
                    null, null, List.of());
            final Shapes signer = new Shapes();
            final Map<String, List<Integer>> bySignature = new LinkedHashMap<>();
            for (int k = 0; k < at.size(); k++) {
                bySignature.computeIfAbsent(signer.signature(chunk, k), signature -> new ArrayList<>()).add(at.get(k));
            }
            final List<Shape> shapes = new ArrayList<>();
            bySignature.forEach((signature, members) -> shapes.add(new Shape(signature, members.size(),
                    examples(members).stream().map(index::recordText).toList())));
            shapes.sort(Comparator.comparingInt(Shape::records).reversed());
            return new Uncatalogued(left.cardinality(), shapes);
        }

        // A shape's examples: its first record, then each time the one that differs most from those chosen.
        private List<Integer> examples(final List<Integer> members) {
            final List<Integer> pool = members.subList(0, Math.min(EXAMPLES_READ, members.size()));
            final List<Integer> out = new ArrayList<>(List.of(pool.getFirst()));
            while (out.size() < Math.min(EXAMPLES, pool.size())) {
                int best = -1;
                int bestDistance = -1;
                for (final int candidate : pool) {
                    if (out.contains(candidate)) {
                        continue;
                    }
                    int nearest = Integer.MAX_VALUE;
                    for (final int chosen : out) {
                        nearest = Math.min(nearest, distance(candidate, chosen));
                    }
                    if (nearest > bestDistance) {
                        bestDistance = nearest;
                        best = candidate;
                    }
                }
                out.add(best);
            }
            return out;
        }

        // How many fields two records differ in (one has it and the other not, or their values differ).
        private int distance(final int one, final int two) {
            final Set<String> fields = new LinkedHashSet<>(index.flat(one).keySet());
            fields.addAll(index.flat(two).keySet());
            int out = 0;
            for (final String field : fields) {
                if (!Objects.equals(index.text(one, field), index.text(two, field))) {
                    out++;
                }
            }
            return out;
        }

        // The event types whose records may be several: they split by a field of their own, or come in clearly
        // different shapes; not one said to be one (one_type_because).
        private List<Several> several() {
            final List<Several> out = new ArrayList<>();
            for (int t = 0; t < types.size(); t++) {
                final EventType type = types.get(t);
                final BitSet own = byType.get(t);
                if (type.oneTypeBecause() != null && !type.oneTypeBecause().isBlank() || own.cardinality() < 2) {
                    continue;
                }
                final List<Integer> members = new ArrayList<>();
                for (int n = own.nextSetBit(0); n >= 0 && members.size() < SEVERAL_READ; n = own.nextSetBit(n + 1)) {
                    members.add(n);
                }
                final ByField byField = byField(type, members);
                final List<ShapeCount> shapes = differentShapes(members);
                if (byField != null || !shapes.isEmpty()) {
                    out.add(new Several(keys.get(t), byField, shapes, EventType.DRAFT.equals(type.basis())));
                }
            }
            return out;
        }

        // The field of their own an event type's records split by: KindFields' first candidate over them alone, the
        // fields its conditions test (and the source type's, and the line format's) left out; named like a kind field
        // first.
        private ByField byField(final EventType type, final List<Integer> members) {
            final Set<String> tested = new LinkedHashSet<>();
            type.recognisedBy().forEach(way -> {
                way.when().forEach(c -> tested.add(c.field()));
                tested.addAll(way.names());
            });
            final String variantField = index.sample().variantField();
            final Predicate<String> left = field -> !tested.contains(field) && !field.equals(variantField)
                                                    && !onSourceType.test(field);
            final List<Map<String, Object>> views = new ArrayList<>();
            for (final int n : members) {
                final Map<String, Object> view = new LinkedHashMap<>();
                index.flat(n).forEach((field, value) -> {
                    if (left.test(field)) {
                        view.put(field, value);
                    }
                });
                views.add(view);
            }
            final List<KindFields.Candidate> candidates = KindFields.candidates(views).candidates().stream()
                    .filter(candidate -> candidate.values().size() >= 2)
                    .filter(candidate -> candidate.records() * 2L > members.size())
                    .sorted(Comparator.comparing((KindFields.Candidate candidate) -> !candidate.named()))
                    .toList();
            if (candidates.isEmpty()) {
                return null;
            }
            final String field = candidates.getFirst().field();
            final Map<String, Integer> counts = new HashMap<>();
            for (final int n : members) {
                final String value = index.text(n, field);
                if (value != null && !value.isBlank()) {
                    counts.merge(value.strip(), 1, Integer::sum);
                }
            }
            final Map<String, Integer> ordered = new LinkedHashMap<>();
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
            return new ByField(field, ordered);
        }

        // An event type's records' shapes, by the fields they have, when two that count (a tenth of its records, and
        // two) share less than half their fields.
        private List<ShapeCount> differentShapes(final List<Integer> members) {
            final Map<Set<String>, Integer> byFields = new LinkedHashMap<>();
            for (final int n : members) {
                byFields.merge(new TreeSet<>(index.flat(n).keySet()), 1, Integer::sum);
            }
            final int least = Math.max(2, (int) Math.ceil(members.size() * SHAPE_SHARE));
            final List<Map.Entry<Set<String>, Integer>> counted = byFields.entrySet().stream()
                    .filter(entry -> entry.getValue() >= least)
                    .sorted(Map.Entry.<Set<String>, Integer>comparingByValue().reversed())
                    .toList();
            boolean different = false;
            for (int i = 0; i < counted.size() && !different; i++) {
                for (int j = i + 1; j < counted.size() && !different; j++) {
                    different = shared(counted.get(i).getKey(), counted.get(j).getKey()) < DIFFERENT_SHAPES;
                }
            }
            return different
                    ? counted.stream()
                    .map(entry -> new ShapeCount("fields:" + String.join(",", entry.getKey()), entry.getValue()))
                    .toList()
                    : List.of();
        }

        // The share of two sets of fields' union both have.
        private static double shared(final Set<String> one, final Set<String> two) {
            final Set<String> union = new LinkedHashSet<>(one);
            union.addAll(two);
            int both = 0;
            for (final String field : one) {
                if (two.contains(field)) {
                    both++;
                }
            }
            return union.isEmpty()
                    ? 1
                    : (double) both / union.size();
        }
    }


    // --------------------------------------------------------------------------------


    /// A record of an event type, as the user is shown it when they ask about the event type.
    ///
    /// @param record The record's index in the scope's records (from 0).
    /// @param text   The record's text, shortened.
    /// @param values The values the event type's conditions test, by field, as read (shortened).
    public record Example(int record, String text, Map<String, String> values) {

        /// Copies the values given.
        public Example {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        /// @return It as JSON: `record`, `text` and `values` (left out when none).
        public Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("record", record);
            out.put("text", text);
            if (!values.isEmpty()) {
                out.put("values", values);
            }
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// What the draft writes, as far as the catalogue's check needs it: what every event writes in common (those
    /// fields aren't an event type's Data, and its user is Authenticate's where an event type names no actor), and
    /// the draft's own rules, whose fields an event type's rule starts from (the one taking most of its records), as
    /// the translation writes them ([stroom.ai.impl.tools.draft.CatalogueTranslation]).
    ///
    /// @param commonFields The fields the common entries read.
    /// @param commonUser   The field EventSource/User/Id is read from, or null.
    /// @param rules        The draft's own rules with conditions, in order.
    public record Drafted(Set<String> commonFields, String commonUser, List<Rule> rules) {

        /// Nothing drafted.
        public static final Drafted NONE = new Drafted(Set.of(), null, List.of());

        /// Copies the parts.
        public Drafted {
            commonFields = Set.copyOf(Objects.requireNonNullElse(commonFields, Set.of()));
            rules = List.copyOf(Objects.requireNonNullElse(rules, List.of()));
        }

        /// @param mapping A draft's mapping (JSON): `common` and `events`, or null.
        /// @return What it writes.
        public static Drafted of(final Map<String, Object> mapping) {
            final Map<String, Object> json = NullSafe.map(mapping);
            final Set<String> fields = new LinkedHashSet<>();
            String user = null;
            for (final Map<String, Object> entry : JsonValues.maps(json.get("common"))) {
                if (entry.get("field") instanceof final String field && !field.isBlank()) {
                    fields.add(field);
                    if ("EventSource/User/Id".equals(ToolText.strip(String.valueOf(entry.get("path")), "/"))) {
                        user = field;
                    }
                }
                JsonValues.strings(entry.get("any_of")).forEach(fields::add);
            }
            final List<Rule> rules = new ArrayList<>();
            for (final Map<String, Object> rule : JsonValues.maps(json.get("events"))) {
                final List<Map<String, Object>> when = JsonValues.maps(rule.get("when"));
                if (when.isEmpty()) {
                    continue;
                }
                try {
                    final List<Condition> conditions = when.stream().map(Condition::fromJson).toList();
                    final List<String> paths = JsonValues.maps(rule.get("fields")).stream()
                            .map(entry -> ToolText.strip(String.valueOf(entry.get("path")), "/"))
                            .toList();
                    rules.add(new Rule(conditions, paths));
                } catch (final IllegalArgumentException e) {
                    // A rule whose conditions don't read takes no records here.
                }
            }
            return new Drafted(fields, user, rules);
        }


        // --------------------------------------------------------------------------------


        /// One of the draft's own rules.
        ///
        /// @param when  Its conditions.
        /// @param paths The paths its fields write, below Event.
        public record Rule(List<Condition> when, List<String> paths) {

            /// Copies the lists.
            public Rule {
                when = List.copyOf(when);
                paths = List.copyOf(paths);
            }
        }
    }
}
