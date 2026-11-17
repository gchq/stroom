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
                .map(row -> MEMBER.equals(row.match()) && row.separator() == null
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
            } else if (!VALUE.equals(row.match()) || row.value() == null || row.value().isEmpty()) {
                out.add("'" + row.name() + "' matches no value: give a value, an anchored pattern, or a list's item");
                continue;
            }
            final List<Row> earlier = rows.subList(0, i).stream()
                    .filter(before -> VALUE.equals(before.match())
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

    // The earlier row that always matches first what this row would match; empty when it may match something.
    private static Optional<Row> unreachable(final Row row, final List<Row> earlier, final Collection<String> values) {
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
            return new Row(JsonValues.text(row.get("name")), JsonValues.text(row.get("match")),
                    JsonValues.text(row.get("value")), MEMBER.equals(JsonValues.text(row.get("match")))
                                                       && !separator.isEmpty()
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
            if (MEMBER.equals(match)) {
                out.put("separator", separatorOrDefault());
            }
            return out;
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
                return fieldValue == null || fieldValue.isEmpty();
            }
            if (fieldValue == null || value == null) {
                return false;
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

        /// @return How it is shown: `= PegaDB`, `~ ^/var/log/mail/`, `∈ Mail Server`, or `no value`.
        public String said() {
            if (NONE.equals(match)) {
                return "no value";
            }
            return (PATTERN.equals(match)
                    ? "~ "
                    : MEMBER.equals(match)
                            ? MEMBER_SIGN + " "
                            : "= ") + value;
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
