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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.generation.XPathRegex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// The rows of a source type registry: each a source type's name and how its records are matched on the source type
/// field's value, exactly, by an anchored pattern, or by an item of a value that is a list (`DNS Server, File Server`,
/// split on its separator, each item stripped of white space and compared exactly, case and all). Rows are tried in
/// order and the first match wins, so a narrower pattern goes before a wider one (`^/var/log/mail/audit/` before
/// `^/var/log/mail/`), and of a list holding the items of several rows, the first row's takes it; a row no record could
/// reach, because an earlier row always matches first, is refused. Patterns are matched on the one field's value, never
/// on a message, and must be anchored and short, so a long value costs one scan, never backtracking.
///
/// Where the source type is read by a combination of readings ([SourceTypeReading#isCombination]), a row matches its
/// key part by part ([#TUPLE]): each part `= value`, `~ ^pattern`, `∈ item` or `*` (any value, none too), written
/// `name = Teams + System` (a bare part is `= part`) or `name ~ ^Sk + *`. A key with a part of no value is matched
/// only by a row with `*` for that part, else by the `(no value)` row. A row of `*` alone is refused: it would take
/// every record, leaving no later row reachable.
public final class SourceTypeRows {

    /// A row matching one value exactly.
    public static final String VALUE = "value";
    /// A row matching values by an anchored regular expression.
    public static final String PATTERN = "pattern";
    /// A row matching a value that is a list holding its item (`Mail ∈ Mail Server`).
    public static final String MEMBER = "member";
    /// A row matching the records with no value: those where the reading finds nothing (a held value of plain
    /// text, a field missing), offered as a source type of their own, never a value of theirs.
    public static final String NONE = "none";
    /// The name of the row matching the records with no value, as it is written.
    public static final String NO_VALUE = "(no value)";
    /// How a list-member row is written: `name ∈ item`.
    public static final String MEMBER_SIGN = "∈";
    /// How a list-member row is written in plain ASCII: `name in item`.
    public static final String MEMBER_WORD = "in";
    /// A row matching a combination's key part by part ([TuplePart]).
    public static final String TUPLE = "tuple";
    /// A combination row's part matching any value, none too.
    public static final String ANY = "*";
    /// What joins a combination row's parts.
    public static final String AND = SourceTypeReading.AND;
    /// The separators a list value's items may be split on.
    public static final List<String> SEPARATORS = List.of(",", ";", "|");
    /// The separator a list-member row splits on where none is kept.
    public static final String DEFAULT_SEPARATOR = ",";
    /// The most items given a list-member row each when rows are proposed; past them, a row a value.
    public static final int MAX_ITEMS = 40;
    /// The longest pattern accepted.
    public static final int MAX_PATTERN = 200;
    /// The most values given a row each before the tool proposes patterns, where the values are structured.
    public static final int FEW_VALUES = 12;
    // The share of values that must be structured (paths, URLs, dotted names) for patterns to be proposed.
    private static final double STRUCTURED_SHARE = 0.8;
    private static final Pattern PATH = Pattern.compile("^(/[^/\\s]+){2,}/?$|^[A-Za-z]:\\\\");
    private static final Pattern URL = Pattern.compile("^[A-Za-z][\\w+.-]*://[^/\\s]+/");
    private static final Pattern DOTTED = Pattern.compile("^[A-Za-z_$][\\w$-]*(\\.[A-Za-z_$][\\w$-]*){2,}$");
    // A group with a quantifier inside it, itself repeated: `(a+)+`, which can backtrack without end.
    private static final Pattern NESTED_QUANTIFIER = Pattern.compile("\\([^()]*[+*][^()]*\\)[+*{]");
    private static final Pattern LITERAL_PREFIX = Pattern.compile("^\\^((?:\\\\.|[^\\\\.\\[\\]{}()*+?^$|])*)$");
    private static final String SPECIAL = "\\.[]{}()*+?^$|";
    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();
    private static final int MAX_COMPILED = 1000;
    // The share of values that must hold a separator for list-member rows to be proposed.
    private static final double LISTED_SHARE = 0.3;
    // The longest item proposed a row: past it, the value isn't a list of names.
    private static final int MAX_ITEM = 80;
    // XML's white space, as XPath's replace() strips it from an item, so both sides read the same items.
    private static final Pattern AROUND = Pattern.compile("^[ \t\n\r]+|[ \t\n\r]+$");
    // A combination row's part that starts with an operator none of `=`, `~`, `∈`, `in`, `*` (`!= x`, `> x`).
    private static final Pattern OTHER_OPERATOR = Pattern.compile("^(!=|==|=~|!~|<>|<=|>=|[<>!≠≈∉]) ");
    private static final Map<String, List<TuplePart>> TUPLES = new ConcurrentHashMap<>();

    private SourceTypeRows() {
        // Static only.
    }

    /// @param rows  The rows, in order.
    /// @param value A source type field's value.
    /// @return The first row that matches it (for no value, the [#NONE] row, if any); empty for a value no row
    /// matches (a `new` source type).
    public static Optional<Row> first(final List<Row> rows, final String value) {
        return rows.stream().filter(row -> row.matches(value)).findFirst();
    }

    /// @return The row matching the records with no value: `(no value)`.
    public static Row noValue() {
        return new Row(NO_VALUE, NONE, "");
    }

    /// Rows proposed from the values the survey saw: a row a value where the values are few or not structured;
    /// where they are many or share containers and are structured (file paths, URLs, dotted names), a pattern a
    /// container (a directory, a URL's path, a package), each covering its values. A value in a container that holds
    /// several other containers (`/var/log/secure` beside `/var/log/mail/` and `/var/log/httpd/`) keeps a row of its
    /// own, so a pattern of that common root doesn't take every new file in it. Narrower patterns are ordered before
    /// wider ones (`/var/log/mail/audit/` before `/var/log/mail/`).
    ///
    /// @param counts The records seen with each value.
    /// @return The rows proposed, each with the values it covers and their records.
    public static Proposal propose(final Map<String, Long> counts) {
        final List<Map.Entry<String, Long>> byCount = counts.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();
        final Optional<Proposal> lists = proposeItems(byCount);
        if (lists.isPresent()) {
            return lists.get();
        }
        final long structured = byCount.stream().filter(entry -> structured(entry.getKey())).count();
        final Map<String, List<String>> byContainer = new LinkedHashMap<>();
        byCount.forEach(entry -> byContainer.computeIfAbsent(container(entry.getKey()), k -> new ArrayList<>())
                .add(entry.getKey()));
        final boolean shared = byContainer.values().stream().anyMatch(values -> values.size() >= 2);
        final boolean patterns = !byCount.isEmpty() && structured >= STRUCTURED_SHARE * byCount.size()
                                 && (byCount.size() > FEW_VALUES || shared);
        final List<Proposed> rows = new ArrayList<>();
        if (!patterns) {
            byCount.forEach(entry -> rows.add(new Proposed(new Row(entry.getKey(), VALUE, entry.getKey()),
                    List.of(entry.getKey()), entry.getValue())));
            return new Proposal(rows, false);
        }
        final List<Proposed> exact = new ArrayList<>();
        final List<Proposed> wide = new ArrayList<>();
        byContainer.forEach((container, values) -> {
            final long records = values.stream().mapToLong(counts::get).sum();
            // A container holding several others is a common root (`/var/log/` over `mail/` and `httpd/`): its
            // pattern would take any new file there, so its own values get rows of their own.
            final long inside = byContainer.keySet().stream()
                    .filter(other -> !other.equals(container) && other.startsWith(container))
                    .count();
            if (container.isEmpty() || inside >= 2) {
                values.forEach(value -> exact.add(new Proposed(new Row(value, VALUE, value), List.of(value),
                        counts.get(value))));
            } else {
                final String name = container.length() > 1
                        ? container.substring(0, container.length() - 1)
                        : container;
                wide.add(new Proposed(new Row(name, PATTERN, "^" + escape(container)), values, records));
            }
        });
        wide.sort((a, b) -> Long.compare(b.records(), a.records()));
        rows.addAll(exact);
        rows.addAll(narrowestFirst(wide));
        return new Proposal(rows, true);
    }

    /// A row as the user writes it: `name = value`, `name ~ ^pattern`, or a list's item, `name ∈ item` (in plain
    /// ASCII, `name in item`). The first of ` = `, ` ~ ` and ` ∈ ` divides the name from the match; ` in ` only where
    /// none is there, so a name holding ` in ` takes one of the others.
    ///
    /// @param given The row as written.
    /// @return The row (a list-member row without a separator, given it from the values seen); empty when it is
    /// none of these.
    public static Optional<Row> parse(final String given) {
        if (given == null) {
            return Optional.empty();
        }
        if (NO_VALUE.equalsIgnoreCase(given.strip())) {
            return Optional.of(noValue());
        }
        int at = -1;
        String match = null;
        for (final Map.Entry<String, String> sign : List.of(Map.entry(" = ", VALUE), Map.entry(" ~ ", PATTERN),
                Map.entry(" " + MEMBER_SIGN + " ", MEMBER))) {
            final int found = given.indexOf(sign.getKey());
            if (found > 0 && (at < 0 || found < at)) {
                at = found;
                match = sign.getValue();
            }
        }
        if (at < 0) {
            final int word = given.indexOf(" " + MEMBER_WORD + " ");
            if (word <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Row(given.substring(0, word).strip(), MEMBER,
                    given.substring(word + MEMBER_WORD.length() + 2).strip()));
        }
        return Optional.of(new Row(given.substring(0, at).strip(), match, given.substring(at + 3).strip()));
    }

    /// A row as the user writes it, for a source type field of the parts given: one reading's row as [#parse(String)]
    /// reads it; a combination's, `name = part + part` (a bare part is `= part`), `name ~ ^pattern + *`, each part
    /// `= value`, `~ ^pattern`, `∈ item` (`in item`) or `*`. The first of ` = `, ` ~ `, ` ∈ ` and ` in ` divides the
    /// name from the parts, and is the first part's operator unless it is ` = ` (`name = ~ ^Sk + *`).
    ///
    /// @param given The row as written.
    /// @param parts How many parts the source type field's values have (1 for one reading).
    /// @return The row (a combination's checked by [#problems(List, Collection, int)]); empty when it is none of
    /// these.
    public static Optional<Row> parse(final String given, final int parts) {
        if (parts <= 1 || given == null || NO_VALUE.equalsIgnoreCase(given.strip())) {
            return parse(given);
        }
        int at = -1;
        String sign = null;
        for (final String each : List.of(" = ", " ~ ", " " + MEMBER_SIGN + " ", " " + MEMBER_WORD + " ")) {
            final int found = given.indexOf(each);
            if (found > 0 && (at < 0 || found < at)) {
                at = found;
                sign = each;
            }
        }
        if (at < 0) {
            return Optional.empty();
        }
        final String name = given.substring(0, at).strip();
        final String rest = given.substring(at + sign.length()).strip();
        final String first = " = ".equals(sign)
                ? rest
                : sign.strip() + " " + rest;
        return Optional.of(new Row(name, TUPLE, canonical(first)));
    }

    /// @param parts A combination row's parts as written: each `value`, `= value`, `~ ^pattern`, `∈ item`, `in item`
    ///              or `*`, joined by ` + `.
    /// @return Them as the registry keeps them: each `= value`, `~ ^pattern`, `∈ item` or `*` (a part with another
    /// operator kept as written, for [#problems(List, Collection, int)] to refuse).
    public static String canonical(final String parts) {
        final List<String> out = new ArrayList<>();
        for (final String each : SourceTypeReading.splitParts(parts)) {
            out.add(canonicalPart(each));
        }
        return String.join(AND, out);
    }

    /// Rows proposed for a combination from the keys the survey saw, where nothing better is known: a row a key
    /// whose parts all have a value, each part matched exactly, named by its values; a key with a part of no value
    /// is left to the `(no value)` row.
    ///
    /// @param counts The records seen with each key.
    /// @return The rows, commonest first.
    public static List<Proposed> proposeKeys(final Map<String, Long> counts) {
        final List<Proposed> out = new ArrayList<>();
        final Set<String> names = new LinkedHashSet<>();
        counts.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> exactParts(entry.getKey()).ifPresent(parts -> out.add(new Proposed(
                        new Row(uniqueName(String.join(" ", SourceTypeReading.keyParts(entry.getKey())), names),
                                TUPLE, parts), List.of(entry.getKey()), entry.getValue()))));
        return out;
    }

    /// @param key A combination's key.
    /// @return The parts of a row matching it exactly (`= Teams + = System`; a part holding ` + ` by an anchored
    /// pattern of it); empty for a key with a part of no value (the `(no value)` row's) or of one part.
    public static Optional<String> exactParts(final String key) {
        final List<String> values = SourceTypeReading.keyParts(key);
        if (values.size() < 2 || values.contains("")) {
            return Optional.empty();
        }
        return Optional.of(String.join(AND, values.stream()
                .map(value -> SourceTypeReading.isCombination(value)
                        ? "~ ^" + escape(value) + "$"
                        : "= " + value)
                .toList()));
    }

    /// @param name  A name proposed.
    /// @param taken The names given already (this one added).
    /// @return The name, or with a number after it where it is taken.
    public static String uniqueName(final String name, final Set<String> taken) {
        String out = name;
        int n = 2;
        while (!taken.add(out)) {
            out = name + " " + n++;
        }
        return out;
    }

    // A combination row's part as the registry keeps it.
    private static String canonicalPart(final String part) {
        if (part.isEmpty() || ANY.equals(part) || OTHER_OPERATOR.matcher(part).find()) {
            return part;
        }
        for (final String sign : List.of("= ", "~ ", MEMBER_SIGN + " ")) {
            if (part.startsWith(sign)) {
                return sign + part.substring(sign.length()).strip();
            }
        }
        if (part.startsWith(MEMBER_WORD + " ")) {
            return MEMBER_SIGN + " " + part.substring(MEMBER_WORD.length() + 1).strip();
        }
        return "= " + part;
    }

    /// The separator a field's values are lists by: of [#SEPARATORS], the one most values hold.
    ///
    /// @param values The values seen.
    /// @return The separator; [#DEFAULT_SEPARATOR] when no value holds one.
    public static String separator(final Collection<String> values) {
        String best = DEFAULT_SEPARATOR;
        long most = 0;
        for (final String separator : SEPARATORS) {
            final long holding = values.stream().filter(value -> value != null && value.contains(separator)).count();
            if (holding > most) {
                most = holding;
                best = separator;
            }
        }
        return best;
    }

    /// @param value     A value that may be a list.
    /// @param separator Its separator.
    /// @return Its items, each stripped of XML's white space, empty ones left out; the value itself, stripped, when it
    /// holds no separator.
    public static List<String> items(final String value, final String separator) {
        if (value == null) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        for (final String item : value.split(Pattern.quote(separator == null
                ? DEFAULT_SEPARATOR
                : separator), -1)) {
            final String stripped = AROUND.matcher(item).replaceAll("");
            if (!stripped.isEmpty()) {
                out.add(stripped);
            }
        }
        return out;
    }

    /// @param rows      Rows.
    /// @param separator The separator their values are lists by.
    /// @return The rows, each list-member row without a separator given this one.
    public static List<Row> withSeparator(final List<Row> rows, final String separator) {
        return rows.stream()
                .map(row -> (MEMBER.equals(row.match()) || TUPLE.equals(row.match())) && row.separator() == null
                        ? new Row(row.name(), row.match(), row.value(), separator)
                        : row)
                .toList();
    }

    // Rows proposed a list item each, where the values are lists (a share of them hold a separator, their items
    // repeat across values and are few and short): the rarest item first, as it says most of a record's source (a
    // list holding a rare item and a common one is the rare one's), each covering the values it matches first; an
    // item no value reaches first is left out. Empty where the values aren't lists.
    private static Optional<Proposal> proposeItems(final List<Map.Entry<String, Long>> byCount) {
        if (byCount.size() < 2) {
            return Optional.empty();
        }
        final List<String> values = byCount.stream().map(Map.Entry::getKey).toList();
        final String separator = separator(values);
        final long listed = values.stream().filter(value -> value.contains(separator)).count();
        if (listed < 2 || listed < LISTED_SHARE * values.size()
            || values.stream().filter(SourceTypeRows::structured).count() >= STRUCTURED_SHARE * values.size()) {
            return Optional.empty();
        }
        final Map<String, Long> records = new LinkedHashMap<>();
        int held = 0;
        for (final Map.Entry<String, Long> entry : byCount) {
            final List<String> items = items(entry.getKey(), separator);
            held += items.size();
            items.forEach(item -> records.merge(item, entry.getValue(), Long::sum));
        }
        if (records.isEmpty() || records.size() > MAX_ITEMS || held <= records.size()
            || records.keySet().stream().anyMatch(item -> item.length() > MAX_ITEM)) {
            return Optional.empty();
        }
        final List<Row> ordered = records.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
                .map(entry -> new Row(entry.getKey(), MEMBER, entry.getKey(), separator))
                .toList();
        final Map<String, List<String>> covered = new LinkedHashMap<>();
        final Map<String, Long> coveredRecords = new LinkedHashMap<>();
        for (final Map.Entry<String, Long> entry : byCount) {
            first(ordered, entry.getKey()).ifPresent(row -> {
                covered.computeIfAbsent(row.name(), k -> new ArrayList<>()).add(entry.getKey());
                coveredRecords.merge(row.name(), entry.getValue(), Long::sum);
            });
        }
        final List<Proposed> rows = ordered.stream()
                .filter(row -> covered.containsKey(row.name()))
                .map(row -> new Proposed(row, covered.get(row.name()), coveredRecords.get(row.name())))
                .toList();
        return Optional.of(new Proposal(rows, false, true));
    }

    /// What is wrong with rows: a pattern not anchored at the value's start, too long, not a regular expression,
    /// or with a repeated group of repeats; two rows of one name; a row no value could reach as an earlier row
    /// always matches first (by the values seen, and for patterns that are a literal start, by the starts).
    ///
    /// @param rows   The rows, in order.
    /// @param values The values seen.
    /// @return The problems, said for the agent and the user; none when the rows are fine.
    public static List<String> problems(final List<Row> rows, final Collection<String> values) {
        return problems(rows, values, rows.stream()
                .filter(row -> TUPLE.equals(row.match()))
                .mapToInt(row -> row.parts().size())
                .findFirst()
                .orElse(1));
    }

    /// What is wrong with rows for a source type field of the parts given: as [#problems(List, Collection)], and for
    /// a combination, a row whose parts aren't the field's (too few or many, an operator none of `=`, `~`, `∈` and
    /// `*`, an empty part), a row of `*` alone (it takes every record), a row of one value on a combination or of
    /// parts on one reading; a combination row no key could reach (an earlier row's parts cover its parts, or every
    /// key seen it matches an earlier row takes first, unless it has a `*` part: a catch-all for keys still to come).
    ///
    /// @param rows   The rows, in order.
    /// @param values The values seen (a combination's keys).
    /// @param parts  How many parts the source type field's values have (1 for one reading).
    /// @return The problems, said for the agent and the user; none when the rows are fine.
    public static List<String> problems(final List<Row> rows, final Collection<String> values, final int parts) {
        final List<String> out = new ArrayList<>();
        final Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            final Row row = rows.get(i);
            if (row.name() == null || row.name().isBlank()) {
                out.add("row " + (i + 1) + " has no name");
                continue;
            }
            if (!names.add(row.name())) {
                out.add("two rows are named '" + row.name() + "'");
            }
            if (PATTERN.equals(row.match())) {
                final Optional<String> bad = patternProblem(row.value())
                        .or(() -> dialectProblem(row.value(), values));
                if (bad.isPresent()) {
                    out.add("'" + row.name() + "': " + bad.get());
                    continue;
                }
            } else if (MEMBER.equals(row.match())) {
                final Optional<String> bad = memberProblem(row);
                if (bad.isPresent()) {
                    out.add("'" + row.name() + "': " + bad.get());
                    continue;
                }
            } else if (NONE.equals(row.match())) {
                if (rows.subList(0, i).stream().anyMatch(before -> NONE.equals(before.match()))) {
                    out.add("two rows match the records with no value: keep one " + NO_VALUE + " row");
                }
                continue;
            } else if (TUPLE.equals(row.match())) {
                final Optional<String> bad = tupleProblem(row, parts, values);
                if (bad.isPresent()) {
                    out.add("'" + row.name() + "': " + bad.get());
                    continue;
                }
            } else if (!VALUE.equals(row.match()) || row.value() == null || row.value().isEmpty()) {
                out.add("'" + row.name() + "' matches no value: give a value, an anchored pattern, or a list's item");
                continue;
            }
            if (parts > 1 && !TUPLE.equals(row.match())) {
                out.add("'" + row.name() + "' matches one value, but the source type is read by a combination of "
                        + parts + " readings: give each part, joined by ' + ' (* for any value), e.g. '"
                        + row.name() + " = " + String.join(AND, Collections.nCopies(parts, "value")) + "'");
                continue;
            }
            final List<Row> earlier = rows.subList(0, i).stream()
                    .filter(before -> TUPLE.equals(before.match()) && tupleProblem(before, parts, values).isEmpty()
                                      || VALUE.equals(before.match())
                                      || MEMBER.equals(before.match()) && memberProblem(before).isEmpty()
                                      || PATTERN.equals(before.match()) && patternProblem(before.value()).isEmpty())
                    .toList();
            unreachable(row, earlier, values).ifPresent(before -> out.add("'" + row.name() + "' can never match: "
                    + "'" + before.name() + "', before it, always matches first; put it before '" + before.name()
                    + "'"));
        }
        return out;
    }

    /// @param literal Text.
    /// @return It as a regular expression matching it exactly, its special characters escaped one by one.
    public static String escape(final String literal) {
        final StringBuilder out = new StringBuilder();
        for (final char c : literal.toCharArray()) {
            if (SPECIAL.indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    /// @param value A value.
    /// @return True if it is structured so a pattern on its container makes sense: a file path, a URL, or a
    /// dotted name.
    public static boolean structured(final String value) {
        return PATH.matcher(value).find() || URL.matcher(value).find() || DOTTED.matcher(value).matches();
    }

    /// @param value A structured value.
    /// @return Its container: a path's directory or a URL's (with its last `/`), a dotted name's package (with its
    /// last `.`); empty for a value with none.
    public static String container(final String value) {
        if (PATH.matcher(value).find() || URL.matcher(value).find()) {
            final int slash = Math.max(value.lastIndexOf('/', value.length() - 2), value.lastIndexOf('\\'));
            return slash > 0
                    ? value.substring(0, slash + 1)
                    : "";
        }
        if (DOTTED.matcher(value).matches()) {
            return value.substring(0, value.lastIndexOf('.') + 1);
        }
        return "";
    }

    // Why a list-member row isn't accepted: no item, a separator not one of those read, an item holding it.
    private static Optional<String> memberProblem(final Row row) {
        if (row.value() == null || row.value().isBlank()) {
            return Optional.of("a list-member row needs an item, e.g. 'name " + MEMBER_SIGN + " item'");
        }
        final String separator = row.separatorOrDefault();
        if (!SEPARATORS.contains(separator)) {
            return Optional.of("its list separator '" + separator + "' isn't one of " + SEPARATORS);
        }
        if (row.value().contains(separator)) {
            return Optional.of("its item holds the list's separator '" + separator + "': give one item a row");
        }
        if (!AROUND.matcher(row.value()).replaceAll("").equals(row.value())) {
            return Optional.of("its item starts or ends with white space, which is stripped from each item read");
        }
        return Optional.empty();
    }

    // Why a pattern isn't accepted; empty when it is.
    private static Optional<String> patternProblem(final String regex) {
        if (regex == null || !regex.startsWith("^")) {
            return Optional.of("a pattern must be anchored at the value's start (begin with ^), e.g. ^/var/log/mail/");
        }
        if (regex.length() > MAX_PATTERN) {
            return Optional.of("its pattern is over " + MAX_PATTERN + " characters: match a shorter start");
        }
        if (NESTED_QUANTIFIER.matcher(regex).find()) {
            return Optional.of("its pattern repeats a group that itself repeats, which can take without end on "
                               + "a long value: match a plain start instead");
        }
        try {
            compiled(regex);
        } catch (final PatternSyntaxException e) {
            return Optional.of("its pattern is not a regular expression: " + e.getDescription());
        }
        // A source type group matches it with XPath's matches() (Saxon), the tools with Java's: it must compile in
        // both.
        return XPathRegex.problem(regex)
                .map(why -> "its pattern isn't one XPath reads as the tools do, and a source type group matches it "
                            + "with XPath's matches(): " + why);
    }

    /// A pattern is matched by the tools in Java's dialect and by a source type group in XPath's (Saxon's): where the
    /// two differ on a value seen, the survey's counts and processing would disagree, so it is refused.
    ///
    /// @param regex  An anchored pattern, one both dialects compile.
    /// @param values The values seen.
    /// @return Why it is refused (the first value the dialects disagree on); empty when they agree on every one.
    public static Optional<String> dialectProblem(final String regex, final Collection<String> values) {
        if (regex == null) {
            return Optional.empty();
        }
        for (final String value : values) {
            if (value == null) {
                continue;
            }
            final boolean java;
            final boolean xpath;
            try {
                java = compiled(regex).matcher(value).find();
                xpath = XPathRegex.finds(regex, value);
            } catch (final PatternSyntaxException | XPathRegex.XPathRegexException e) {
                return Optional.of("its pattern doesn't compile in both Java's and XPath's dialects: "
                                   + e.getMessage());
            }
            if (java != xpath) {
                return Optional.of("its pattern " + (java
                        ? "matches"
                        : "doesn't match") + " '" + value + "' in the tools' (Java's) dialect but "
                                   + (xpath
                        ? "matches"
                        : "doesn't match") + " it in XPath's, which a source type group runs: write one both read "
                                   + "alike (plain characters, classes and anchors)");
            }
        }
        return Optional.empty();
    }

    // Why a combination row isn't accepted: its parts not the field's, an operator unknown, an empty part, `*` alone,
    // a pattern or list item refused as one reading's would be.
    private static Optional<String> tupleProblem(final Row row, final int parts, final Collection<String> values) {
        if (parts <= 1) {
            return Optional.of("it gives " + row.parts().size() + " parts, but the source type is read by one reading: "
                               + "give 'name = value', 'name ~ ^pattern' or 'name " + MEMBER_SIGN + " item'");
        }
        final List<TuplePart> given = row.parts();
        if (given.size() != parts) {
            return Optional.of("it gives " + given.size() + " part(s), but the source type is read by a combination "
                               + "of " + parts + ": give one for each, joined by ' + ' (* for any value)");
        }
        for (int i = 0; i < given.size(); i++) {
            final TuplePart part = given.get(i);
            final String at = "part " + (i + 1) + " ('" + part.raw() + "')";
            if (part.raw().isEmpty()) {
                return Optional.of("part " + (i + 1) + " is empty: give a value, '~ ^pattern', '" + MEMBER_SIGN
                                   + " item' or '*'");
            }
            if (part.match() == null) {
                return Optional.of(at + " has an operator none of '=', '~', '" + MEMBER_SIGN + "' ('in') and '*'");
            }
            if (!ANY.equals(part.match()) && part.value().isEmpty()) {
                return Optional.of(at + " matches no value: give one after its operator");
            }
            if (PATTERN.equals(part.match())) {
                final int index = i;
                final List<String> seen = values.stream()
                        .map(SourceTypeReading::keyParts)
                        .filter(each -> each.size() > index)
                        .map(each -> each.get(index))
                        .toList();
                final Optional<String> bad = patternProblem(part.value()).or(() -> dialectProblem(part.value(), seen));
                if (bad.isPresent()) {
                    return Optional.of(at + ": " + bad.get());
                }
            }
            if (MEMBER.equals(part.match())) {
                final Optional<String> bad = memberProblem(new Row(row.name(), MEMBER, part.value(), row.separator()));
                if (bad.isPresent()) {
                    return Optional.of(at + ": " + bad.get());
                }
            }
        }
        if (given.stream().allMatch(part -> ANY.equals(part.match()))) {
            return Optional.of("every part is '*', so it takes every record with a value and no row after it could "
                               + "match: give at least one part a value, pattern or item");
        }
        return Optional.empty();
    }

    // Whether an earlier combination row takes every key this one could match: each of its parts covers this one's.
    private static boolean covers(final Row before, final Row row) {
        final List<TuplePart> outer = before.parts();
        final List<TuplePart> inner = row.parts();
        if (outer.size() != inner.size()) {
            return false;
        }
        for (int i = 0; i < outer.size(); i++) {
            if (!outer.get(i).covers(inner.get(i), before.separatorOrDefault(), row.separatorOrDefault())) {
                return false;
            }
        }
        return true;
    }

    // The earlier row that always matches first what this row would match; empty when it may match something.
    private static Optional<Row> unreachable(final Row row, final List<Row> earlier, final Collection<String> values) {
        if (TUPLE.equals(row.match())) {
            final Optional<Row> covering = earlier.stream()
                    .filter(before -> TUPLE.equals(before.match()) && covers(before, row))
                    .findFirst();
            // A row with a part of any value is a catch-all for keys still to come: the keys seen don't judge it.
            if (covering.isPresent() || row.parts().stream().anyMatch(part -> ANY.equals(part.match()))) {
                return covering;
            }
        }
        if (VALUE.equals(row.match())) {
            return earlier.stream().filter(before -> before.matches(row.value())).findFirst();
        }
        if (MEMBER.equals(row.match())) {
            // The same item of the same list, earlier: it takes every value this would.
            final Optional<Row> same = earlier.stream()
                    .filter(before -> MEMBER.equals(before.match()) && row.value().equals(before.value())
                                      && row.separatorOrDefault().equals(before.separatorOrDefault()))
                    .findFirst();
            if (same.isPresent()) {
                return same;
            }
        }
        final String start = PATTERN.equals(row.match())
                ? literalStart(row.value())
                : null;
        if (start != null) {
            for (final Row before : earlier) {
                final String beforeStart = PATTERN.equals(before.match())
                        ? literalStart(before.value())
                        : null;
                if (beforeStart != null && start.startsWith(beforeStart)) {
                    return Optional.of(before);
                }
            }
        }
        final List<String> matched = values.stream().filter(row::matches).toList();
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        final boolean allTaken = matched.stream().allMatch(value -> earlier.stream().anyMatch(b -> b.matches(value)));
        return allTaken
                ? Optional.of(earlier.stream().filter(b -> matched.stream().anyMatch(b::matches)).findFirst()
                .orElseThrow())
                : Optional.empty();
    }

    // The text a pattern that is only `^` and literal text matches values starting with; null for another.
    private static String literalStart(final String regex) {
        if (regex == null || !LITERAL_PREFIX.matcher(regex).matches()) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        for (int i = 1; i < regex.length(); i++) {
            final char c = regex.charAt(i);
            if (c == '\\' && i + 1 < regex.length()) {
                out.append(regex.charAt(++i));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // Patterns with a narrower one moved before every wider one it is inside.
    private static List<Proposed> narrowestFirst(final List<Proposed> patterns) {
        final List<Proposed> out = new ArrayList<>(patterns);
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int i = 0; i < out.size() && !moved; i++) {
                for (int j = 0; j < i; j++) {
                    final String inner = literalStart(out.get(i).row().value());
                    final String outer = literalStart(out.get(j).row().value());
                    if (inner != null && outer != null && inner.startsWith(outer)) {
                        out.add(j, out.remove(i));
                        moved = true;
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static Pattern compiled(final String regex) {
        if (COMPILED.size() > MAX_COMPILED) {
            COMPILED.clear();
        }
        return COMPILED.computeIfAbsent(regex, Pattern::compile);
    }


    // --------------------------------------------------------------------------------


    /// A source type's row.
    ///
    /// @param name      The source type's name.
    /// @param match     [#VALUE], [#PATTERN], [#MEMBER] or [#NONE].
    /// @param value     The value matched exactly, the anchored pattern, or the list's item.
    /// @param separator A list-member row's: what the list's items are split on (one of [#SEPARATORS]); null for
    ///                  another row, or [#DEFAULT_SEPARATOR].
    public record Row(String name, String match, String value, String separator) {

        /// A row matching a value exactly or by a pattern.
        ///
        /// @param name  The source type's name.
        /// @param match [#VALUE] or [#PATTERN] (or [#MEMBER], split on [#DEFAULT_SEPARATOR]).
        /// @param value The value matched exactly, the anchored pattern, or the list's item.
        public Row(final String name, final String match, final String value) {
            this(name, match, value, null);
        }

        /// @param row A row as the registry (or a scope) keeps it: `name`, `match`, `value` and, for a list-member
        ///            row, `separator`.
        /// @return The row.
        public static Row of(final Map<String, Object> row) {
            final String separator = JsonValues.text(row.get("separator"));
            final String match = JsonValues.text(row.get("match"));
            return new Row(JsonValues.text(row.get("name")), match, JsonValues.text(row.get("value")),
                    (MEMBER.equals(match) || TUPLE.equals(match)) && !separator.isEmpty()
                            ? separator
                            : null);
        }

        /// @return It as the registry (or a scope) keeps it: `name`, `match`, `value` and, for a list-member row,
        /// `separator`.
        public Map<String, Object> toMap() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("match", match);
            out.put("value", value);
            if (MEMBER.equals(match) || TUPLE.equals(match) && parts().stream()
                    .anyMatch(part -> MEMBER.equals(part.match()))) {
                out.put("separator", separatorOrDefault());
            }
            return out;
        }

        /// @return A combination row's parts, in order ([#TUPLE]); none for another row.
        public List<TuplePart> parts() {
            if (!TUPLE.equals(match) || value == null) {
                return List.of();
            }
            if (TUPLES.size() > MAX_COMPILED) {
                TUPLES.clear();
            }
            return TUPLES.computeIfAbsent(value, TuplePart::all);
        }

        /// @return What a list-member row splits a value on: its separator, else [#DEFAULT_SEPARATOR].
        public String separatorOrDefault() {
            return separator == null || separator.isEmpty()
                    ? DEFAULT_SEPARATOR
                    : separator;
        }

        /// @param fieldValue A source type field's value, or null for none.
        /// @return True if this row matches it: equal to the value, found by the pattern from its start, a list
        /// holding the item, or (the [#NONE] row) no value.
        public boolean matches(final String fieldValue) {
            if (NONE.equals(match)) {
                // A combination's key with a part of no value too: only a row with * for that part takes it first.
                return fieldValue == null || fieldValue.isEmpty()
                       || fieldValue.contains(SourceTypeReading.KEY_SEPARATOR.strip())
                          && SourceTypeReading.keyParts(fieldValue).contains("");
            }
            if (fieldValue == null || value == null) {
                return false;
            }
            if (TUPLE.equals(match)) {
                final List<TuplePart> parts = parts();
                final List<String> got = SourceTypeReading.keyParts(fieldValue);
                if (parts.isEmpty() || got.size() != parts.size()) {
                    return false;
                }
                for (int i = 0; i < parts.size(); i++) {
                    if (!parts.get(i).matches(got.get(i), separatorOrDefault())) {
                        return false;
                    }
                }
                return true;
            }
            if (PATTERN.equals(match)) {
                try {
                    return value.startsWith("^") && compiled(value).matcher(fieldValue).find();
                } catch (final PatternSyntaxException e) {
                    return false;
                }
            }
            if (MEMBER.equals(match)) {
                return !value.isEmpty() && items(fieldValue, separatorOrDefault()).contains(value);
            }
            return value.equals(fieldValue);
        }

        /// @return How it is shown: `= PegaDB`, `~ ^/var/log/mail/`, `∈ Mail Server`, or `no value`; a combination's
        /// parts, `= Teams + *`.
        public String said() {
            if (NONE.equals(match)) {
                return "no value";
            }
            if (TUPLE.equals(match)) {
                return value;
            }
            return (PATTERN.equals(match)
                    ? "~ "
                    : MEMBER.equals(match)
                            ? MEMBER_SIGN + " "
                            : "= ") + value;
        }
    }

    /// A combination row's part.
    ///
    /// @param match [#VALUE], [#PATTERN], [#MEMBER], [#ANY], or null for an operator none of these.
    /// @param value The value, the pattern or the item; empty for [#ANY].
    /// @param raw   The part as written.
    public record TuplePart(String match, String value, String raw) {

        /// @param parts A combination row's parts as the registry keeps them ([#canonical]).
        /// @return Each part.
        static List<TuplePart> all(final String parts) {
            final List<TuplePart> out = new ArrayList<>();
            for (final String each : SourceTypeReading.splitParts(parts)) {
                out.add(of(each));
            }
            return List.copyOf(out);
        }

        /// @param part A part as the registry keeps it: `= value`, `~ ^pattern`, `∈ item` or `*`.
        /// @return The part.
        static TuplePart of(final String part) {
            if (ANY.equals(part)) {
                return new TuplePart(ANY, "", part);
            }
            for (final Map.Entry<String, String> sign : List.of(Map.entry("= ", VALUE), Map.entry("~ ", PATTERN),
                    Map.entry(MEMBER_SIGN + " ", MEMBER))) {
                if (part.startsWith(sign.getKey())) {
                    return new TuplePart(sign.getValue(), part.substring(sign.getKey().length()).strip(), part);
                }
            }
            return new TuplePart(null, part, part);
        }

        /// @param partValue The part's value in a key ("" for none).
        /// @param separator What a list item's value is split on.
        /// @return True if this part matches it: any value (none too), equal, found by the pattern from its start,
        /// or a list holding the item; never a part with no value, but for `*`.
        public boolean matches(final String partValue, final String separator) {
            if (ANY.equals(match)) {
                return true;
            }
            if (match == null || partValue == null || partValue.isEmpty()) {
                return false;
            }
            return new Row("", match, value, separator).matches(partValue);
        }

        /// @param inner          A later row's part.
        /// @param separator      This part's row's list separator.
        /// @param innerSeparator The later row's.
        /// @return True if this part matches every value the later part could.
        boolean covers(final TuplePart inner, final String separator, final String innerSeparator) {
            if (ANY.equals(match)) {
                return true;
            }
            if (ANY.equals(inner.match()) || match == null || inner.match() == null) {
                return false;
            }
            if (VALUE.equals(inner.match())) {
                return matches(inner.value(), separator);
            }
            if (MEMBER.equals(match) && MEMBER.equals(inner.match())) {
                return value.equals(inner.value()) && separator.equals(innerSeparator);
            }
            if (PATTERN.equals(match) && PATTERN.equals(inner.match())) {
                final String outerStart = literalStart(value);
                final String innerStart = literalStart(inner.value());
                return outerStart != null && innerStart != null && innerStart.startsWith(outerStart);
            }
            return false;
        }
    }

    /// A row proposed, with what it covers.
    ///
    /// @param row     The row.
    /// @param values  The values seen that it covers.
    /// @param records The records seen with them.
    public record Proposed(Row row, List<String> values, long records) {

    }

    /// Rows proposed.
    ///
    /// @param rows     The rows, in order.
    /// @param patterns True when some are patterns, for the user to confirm.
    /// @param lists    True when they are a list's items (list-member rows), for the user to confirm.
    public record Proposal(List<Proposed> rows, boolean patterns, boolean lists) {

        /// Rows proposed, none of them a list's items.
        ///
        /// @param rows     The rows, in order.
        /// @param patterns True when some are patterns, for the user to confirm.
        public Proposal(final List<Proposed> rows, final boolean patterns) {
            this(rows, patterns, false);
        }
    }
}
