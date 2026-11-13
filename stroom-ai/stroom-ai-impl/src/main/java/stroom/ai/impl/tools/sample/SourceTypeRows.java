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

import stroom.ai.impl.tools.generation.XPathRegex;

import java.util.ArrayList;
import java.util.Collection;
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
/// field's value, exactly or by an anchored pattern. Rows are tried in order and the first match wins, so a narrower
/// pattern goes before a wider one (`^/var/log/mail/audit/` before `^/var/log/mail/`); a row no record could reach,
/// because an earlier row always matches first, is refused. Patterns are matched on the one field's value, never on a
/// message, and must be anchored and short, so a long value costs one scan, never backtracking.
public final class SourceTypeRows {

    /// A row matching one value exactly.
    public static final String VALUE = "value";
    /// A row matching values by an anchored regular expression.
    public static final String PATTERN = "pattern";
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

    private SourceTypeRows() {
        // Static only.
    }

    /// @param rows  The rows, in order.
    /// @param value A source type field's value.
    /// @return The first row that matches it; empty for a value no row matches (a `new` source type).
    public static Optional<Row> first(final List<Row> rows, final String value) {
        if (value == null) {
            return Optional.empty();
        }
        return rows.stream().filter(row -> row.matches(value)).findFirst();
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
            } else if (!VALUE.equals(row.match()) || row.value() == null || row.value().isEmpty()) {
                out.add("'" + row.name() + "' matches no value: give a value, or an anchored pattern");
                continue;
            }
            final List<Row> earlier = rows.subList(0, i).stream()
                    .filter(before -> patternProblem(before.value()).isEmpty() || VALUE.equals(before.match()))
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
        final String start = literalStart(row.value());
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
    /// @param name  The source type's name.
    /// @param match [#VALUE] or [#PATTERN].
    /// @param value The value matched exactly, or the anchored pattern.
    public record Row(String name, String match, String value) {

        /// @param fieldValue A source type field's value.
        /// @return True if this row matches it: equal to the value, or found by the pattern from its start.
        public boolean matches(final String fieldValue) {
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
            return value.equals(fieldValue);
        }

        /// @return How it is shown: `= PegaDB` or `~ ^/var/log/mail/`.
        public String said() {
            return (PATTERN.equals(match)
                    ? "~ "
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
    public record Proposal(List<Proposed> rows, boolean patterns) {

    }
}
