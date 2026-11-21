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

package stroom.ai.impl.tools;

import stroom.ai.api.ElasticEql;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Writes the EQL of an `sequence_query` call from its structured steps, and the ES|QL counts its pre-flight makes of
/// each step. Nothing here runs anything.
///
/// A step's conditions take the `search_events` `where` form (field, condition, value), plus `caseInsensitive`.
/// In EQL: EQUALS is `==` (`:` when case-insensitive), or `like`/`like~` with a `*` wildcard, or `cidrMatch` for a
/// CIDR on an IP field; IN is `in`/`in~`, or `cidrMatch` with CIDRs; CONTAINS, STARTS_WITH and ENDS_WITH are
/// `like`/`like~` patterns; MATCHES_REGEX is `regex`/`regex~`; ranges are `<`, `<=`, `>`, `>=` (BETWEEN both
/// ends, inclusive); IS_NULL and IS_NOT_NULL are `== null` and `!= null`. Numbers are written bare, the rest as
/// strings.
///
/// A condition may be negated (NOT_IN, `not field in (...)`, `in~` when case-insensitive, `not cidrMatch(...)` with
/// CIDRs), or on a string's length (`length(field) > N`). A step's `any_of` conditions are written as one group,
/// `(a or b ...)`, beside its other conditions.
///
/// In a `like` pattern and with `:`, `?` matches any one character, and Elasticsearch's EQL can't escape it (9.5.3:
/// `\?` matches nothing). So a value with a `?` and no `*` is matched exactly (`==`, or `in~` when
/// case-insensitive, which is exact); a pattern with both is refused, saying to use a regex.
final class EqlQueryWriter {

    /// The longest window a sequence may span.
    static final Duration MAX_MAXSPAN = Duration.ofHours(24);
    /// The most times a step may repeat (`with runs=N`), as Elasticsearch allows.
    static final int MAX_RUNS = 100;

    private static final Set<Condition> CONDITIONS = Set.copyOf(IndexEvents.CONDITIONS);
    private static final Set<FieldType> NUMBERS = Set.of(FieldType.INTEGER, FieldType.LONG, FieldType.FLOAT,
            FieldType.DOUBLE, FieldType.ID);
    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z_@][A-Za-z0-9_@.]*");
    // EQL's keywords, which a field name can't be unless in backquotes.
    private static final Set<String> EQL_KEYWORDS = Set.of("and", "any", "by", "false", "in", "join", "like",
            "maxspan", "not", "null", "of", "or", "regex", "sample", "sequence", "true", "until", "where", "with",
            "runs");
    private static final Pattern MAXSPAN = Pattern.compile(
            "(?i)^\\s*(\\d+)\\s*(ms|milliseconds?|s|secs?|seconds?|m|mins?|minutes?|h|hrs?|hours?|d|days?)\\s*$");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d+");
    // The conditions a length can be compared with.
    private static final Set<Condition> LENGTH_CONDITIONS = Set.of(Condition.EQUALS, Condition.NOT_EQUALS,
            Condition.GREATER_THAN, Condition.GREATER_THAN_OR_EQUAL_TO, Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO, Condition.BETWEEN);
    // What a literal ? in a pattern is refused with.
    private static final String QUESTION_MARK_ADVICE = "EQL reads ? in a pattern as any one character and can't "
                                                       + "escape it, so a literal ? can't be in a pattern: give the "
                                                       + "whole value (EQUALS, no *), or use MATCHES_REGEX with \\? "
                                                       + "for the ?.";

    private EqlQueryWriter() {
        // Static only.
    }

    /// The EQL of a sequence.
    ///
    /// @param steps    The steps, in order, two or more.
    /// @param until    The step that ends a sequence before it is complete, or null.
    /// @param maxspan  How far apart the first and last events may be.
    /// @param docCount How many documents the steps read: with more than one, a step that needs it
    ///                 ([Step#restricted()]) matches its own document's events only ([ElasticEql#DOC_FIELD]).
    /// @return The EQL.
    static String eql(final List<Step> steps, final Step until, final Duration maxspan, final int docCount) {
        final StringBuilder sb = new StringBuilder("sequence with maxspan=").append(maxspanText(maxspan));
        for (final Step step : steps) {
            sb.append("\n  ").append(step.missing()
                    ? "!"
                    : "").append(stepText(step, docCount));
            if (step.runs() > 1) {
                sb.append(" with runs=").append(step.runs());
            }
        }
        if (until != null) {
            sb.append("\nuntil ").append(stepText(until, docCount));
        }
        return sb.toString();
    }

    // `[any where ...] by a, b`.
    private static String stepText(final Step step, final int docCount) {
        final List<String> parts = new ArrayList<>();
        if (docCount > 1 && step.restricted()) {
            parts.add(ElasticEql.DOC_FIELD + " == \"" + step.doc() + "\"");
        }
        for (final Filter filter : step.filters()) {
            parts.add(eqlCondition(filter, step.types().get(filter.field())));
        }
        if (!step.anyOf().isEmpty()) {
            parts.add("(" + String.join(" or ", step.anyOf().stream()
                    .map(filter -> eqlCondition(filter, step.types().get(filter.field())))
                    .toList()) + ")");
        }
        if (parts.isEmpty()) {
            parts.add("true");
        }
        final List<String> by = step.joinFields().stream().map(EqlQueryWriter::eqlName).toList();
        return "[any where " + String.join(" and ", parts) + "]" + (by.isEmpty()
                ? ""
                : " by " + String.join(", ", by));
    }

    /// One condition in EQL.
    ///
    /// @param filter The condition.
    /// @param type   Its field's type, or null if not known (then written as a string).
    /// @return The EQL, e.g. `user.name : "alice"`.
    static String eqlCondition(final Filter filter, final FieldType type) {
        if (filter.length()) {
            return lengthCondition(filter, type, "length(" + eqlName(filter.field()) + ")", " and ");
        }
        if (filter.negated()) {
            return negatedIn(filter, type);
        }
        final String name = eqlName(filter.field());
        final boolean text = isText(type);
        final boolean insensitive = filter.caseInsensitive() && text;
        final String value = filter.value();
        return switch (filter.condition()) {
            case EQUALS -> {
                if (type == FieldType.IPV4_ADDRESS && value.contains("/")) {
                    yield "cidrMatch(" + name + ", " + eqlString(value.strip()) + ")";
                } else if (text && value.contains("*")) {
                    yield like(name, insensitive, pattern(filter, value));
                }
                yield exact(name, insensitive, eqlLiteral(filter, type, value));
            }
            case NOT_EQUALS -> text && value.contains("*")
                    ? "not " + like(name, insensitive, pattern(filter, value))
                    : insensitive
                            ? "not " + exact(name, true, eqlString(value))
                            : name + " != " + eqlLiteral(filter, type, value);
            case CONTAINS -> like(name, insensitive, pattern(filter, "*" + value + "*"));
            case STARTS_WITH -> like(name, insensitive, pattern(filter, value + "*"));
            case ENDS_WITH -> like(name, insensitive, pattern(filter, "*" + value));
            case MATCHES_REGEX -> name + (insensitive
                    ? " regex~ "
                    : " regex ") + eqlString(value);
            case GREATER_THAN -> name + " > " + eqlLiteral(filter, type, value);
            case GREATER_THAN_OR_EQUAL_TO -> name + " >= " + eqlLiteral(filter, type, value);
            case LESS_THAN -> name + " < " + eqlLiteral(filter, type, value);
            case LESS_THAN_OR_EQUAL_TO -> name + " <= " + eqlLiteral(filter, type, value);
            case BETWEEN -> {
                final List<String> ends = between(filter);
                yield "(" + name + " >= " + eqlLiteral(filter, type, ends.get(0)) + " and " + name + " <= "
                      + eqlLiteral(filter, type, ends.get(1)) + ")";
            }
            case IN -> {
                final List<String> values = values(filter);
                if (type == FieldType.IPV4_ADDRESS && values.stream().anyMatch(v -> v.contains("/"))) {
                    yield "cidrMatch(" + name + ", " + String.join(", ", values.stream()
                            .map(v -> eqlString(cidr(v)))
                            .toList()) + ")";
                }
                yield name + (insensitive
                        ? " in~ ("
                        : " in (") + String.join(", ", values.stream()
                        .map(v -> eqlLiteral(filter, type, v))
                        .toList()) + ")";
            }
            case IS_NULL -> name + " == null";
            case IS_NOT_NULL -> name + " != null";
            default -> throw new WorkspaceException("Condition " + filter.condition() + " can't be used in a step.");
        };
    }

    private static String like(final String name, final boolean insensitive, final String pattern) {
        return name + (insensitive
                ? " like~ "
                : " like ") + eqlString(pattern);
    }

    // An exact match: `==`, or, case-insensitive, `:` when that can't be read as a pattern (no ?), else `in~`, which
    // is exact.
    private static String exact(final String name, final boolean insensitive, final String literal) {
        if (!insensitive) {
            return name + " == " + literal;
        }
        return literal.contains("?") || literal.contains("*")
                ? name + " in~ (" + literal + ")"
                : name + " : " + literal;
    }

    // A pattern, refused if it holds a literal ?, which EQL can't escape.
    private static String pattern(final Filter filter, final String pattern) {
        if (pattern.contains("?")) {
            throw new WorkspaceException("The condition on '" + filter.field() + "' has a ? in a pattern ('"
                                         + filter.value() + "'). " + QUESTION_MARK_ADVICE);
        }
        return pattern;
    }

    // NOT_IN: `not field in (...)`, `in~` when case-insensitive, `not cidrMatch(...)` with CIDRs on an IP field.
    private static String negatedIn(final Filter filter, final FieldType type) {
        final String name = eqlName(filter.field());
        final List<String> values = values(filter);
        if (type == FieldType.IPV4_ADDRESS && values.stream().anyMatch(v -> v.contains("/"))) {
            return "not cidrMatch(" + name + ", " + String.join(", ", values.stream()
                    .map(v -> eqlString(cidr(v)))
                    .toList()) + ")";
        }
        final boolean insensitive = filter.caseInsensitive() && isText(type);
        return "not " + name + (insensitive
                ? " in~ ("
                : " in (") + String.join(", ", values.stream()
                .map(v -> eqlLiteral(filter, type, v))
                .toList()) + ")";
    }

    // A comparison of a string's length, in EQL (`length(f) > 10`) or ES|QL (`LENGTH(f) > 10`).
    private static String lengthCondition(final Filter filter,
                                          final FieldType type,
                                          final String length,
                                          final String and) {
        if (!isText(type)) {
            throw new WorkspaceException("length is for string fields; '" + filter.field() + "' isn't one.");
        }
        if (!LENGTH_CONDITIONS.contains(filter.condition())) {
            throw new WorkspaceException("length on '" + filter.field() + "' is compared with EQUALS, NOT_EQUALS, "
                                         + "GREATER_THAN, GREATER_THAN_OR_EQUAL_TO, LESS_THAN, "
                                         + "LESS_THAN_OR_EQUAL_TO or BETWEEN.");
        }
        final List<String> numbers = filter.condition() == Condition.BETWEEN
                ? between(filter)
                : List.of(filter.value().strip());
        for (final String number : numbers) {
            if (!WHOLE_NUMBER.matcher(number).matches()) {
                throw new WorkspaceException("length on '" + filter.field() + "' is compared with a whole number of "
                                             + "characters, not '" + number + "'.");
            }
        }
        return switch (filter.condition()) {
            case EQUALS -> length + " == " + numbers.getFirst();
            case NOT_EQUALS -> length + " != " + numbers.getFirst();
            case GREATER_THAN -> length + " > " + numbers.getFirst();
            case GREATER_THAN_OR_EQUAL_TO -> length + " >= " + numbers.getFirst();
            case LESS_THAN -> length + " < " + numbers.getFirst();
            case LESS_THAN_OR_EQUAL_TO -> length + " <= " + numbers.getFirst();
            default -> "(" + length + " >= " + numbers.get(0) + and + length + " <= " + numbers.get(1) + ")";
        };
    }

    /// The count of one step's events, up to a cap, as an ES|QL pipeline: its conditions in `WHERE`, then
    /// `LIMIT <cap + 1>`, then `STATS n = COUNT(*)`. The limit stops the read once the cap is passed (checked on
    /// 9.5.3, 3 million events: with a filter Elasticsearch can't push into Lucene, about 600,000 read for a cap of
    /// 100,000, 97 ms against 342 ms; a filter it pushes down is counted from the index either way), so a count of
    /// `cap + 1` means "more than the cap". A case-insensitive regex has no ES|QL form, so it is left out (the count
    /// is then at most what it would be).
    ///
    /// @param step The step.
    /// @param cap  The most events counted.
    /// @return The pipeline, e.g. `| WHERE action == "Copy" | LIMIT 1000001 | STATS n = COUNT(*)`.
    static String countPipeline(final Step step, final long cap) {
        final List<String> parts = new ArrayList<>();
        for (final Filter filter : step.filters()) {
            final String condition = esqlCondition(filter, step.types().get(filter.field()));
            if (condition != null) {
                parts.add(condition);
            }
        }
        final List<String> anyOf = new ArrayList<>();
        for (final Filter filter : step.anyOf()) {
            anyOf.add(esqlCondition(filter, step.types().get(filter.field())));
        }
        // A group with a condition that has no ES|QL form is left out whole, so the count is at most what it is.
        if (!anyOf.isEmpty() && !anyOf.contains(null)) {
            parts.add("(" + String.join(" OR ", anyOf) + ")");
        }
        return (parts.isEmpty()
                ? ""
                : "| WHERE " + String.join(" AND ", parts) + " ") + "| LIMIT " + (cap + 1) + " | STATS n = COUNT(*)";
    }

    /// One condition in ES|QL, for a count.
    ///
    /// @param filter The condition.
    /// @param type   Its field's type, or null if not known.
    /// @return The ES|QL, or null where it has no ES|QL form (a case-insensitive regex).
    static String esqlCondition(final Filter filter, final FieldType type) {
        if (filter.length()) {
            return lengthCondition(filter, type, "LENGTH(" + EsqlHints.name(filter.field()) + ")", " AND ");
        }
        final boolean text = isText(type);
        final boolean insensitive = filter.caseInsensitive() && text;
        final String name = insensitive
                ? "TO_LOWER(" + EsqlHints.name(filter.field()) + ")"
                : EsqlHints.name(filter.field());
        final String value = insensitive
                ? filter.value().toLowerCase(Locale.ROOT)
                : filter.value();
        return switch (filter.condition()) {
            case EQUALS -> {
                if (type == FieldType.IPV4_ADDRESS && value.contains("/")) {
                    yield "CIDR_MATCH(" + name + ", " + esqlString(value.strip()) + ")";
                } else if (text && value.contains("*")) {
                    yield name + " LIKE " + esqlString(value);
                }
                yield name + " == " + esqlLiteral(filter, type, value);
            }
            case NOT_EQUALS -> text && value.contains("*")
                    ? "NOT " + name + " LIKE " + esqlString(value)
                    : name + " != " + esqlLiteral(filter, type, value);
            case CONTAINS -> name + " LIKE " + esqlString("*" + value + "*");
            case STARTS_WITH -> name + " LIKE " + esqlString(value + "*");
            case ENDS_WITH -> name + " LIKE " + esqlString("*" + value);
            case MATCHES_REGEX -> insensitive
                    ? null
                    : name + " RLIKE " + esqlString(value);
            case GREATER_THAN -> name + " > " + esqlLiteral(filter, type, value);
            case GREATER_THAN_OR_EQUAL_TO -> name + " >= " + esqlLiteral(filter, type, value);
            case LESS_THAN -> name + " < " + esqlLiteral(filter, type, value);
            case LESS_THAN_OR_EQUAL_TO -> name + " <= " + esqlLiteral(filter, type, value);
            case BETWEEN -> {
                final List<String> ends = between(new Filter(filter.field(), filter.condition(), value, false));
                yield "(" + name + " >= " + esqlLiteral(filter, type, ends.get(0)) + " AND " + name + " <= "
                      + esqlLiteral(filter, type, ends.get(1)) + ")";
            }
            case IN -> {
                final List<String> values = values(new Filter(filter.field(), filter.condition(), value, false));
                final String not = filter.negated()
                        ? "NOT "
                        : "";
                if (type == FieldType.IPV4_ADDRESS && values.stream().anyMatch(v -> v.contains("/"))) {
                    yield not + "CIDR_MATCH(" + name + ", " + String.join(", ", values.stream()
                            .map(v -> esqlString(cidr(v)))
                            .toList()) + ")";
                }
                yield (filter.negated()
                        ? name + " NOT IN ("
                        : name + " IN (") + String.join(", ", values.stream()
                        .map(v -> esqlLiteral(filter, type, v))
                        .toList()) + ")";
            }
            case IS_NULL -> name + " IS NULL";
            case IS_NOT_NULL -> name + " IS NOT NULL";
            default -> throw new WorkspaceException("Condition " + filter.condition() + " can't be used in a step.");
        };
    }

    /// @param condition A condition.
    /// @return True if a step can take it.
    static boolean takes(final Condition condition) {
        return CONDITIONS.contains(condition);
    }

    /// Reads a window as the user or model gave it: `2m`, `90 seconds`, `1 hour`, `PT2M`.
    ///
    /// @param text The window.
    /// @return It as a duration.
    /// @throws WorkspaceException if it can't be read, is not positive, or is over [#MAX_MAXSPAN].
    static Duration maxspan(final String text) {
        final String given = NullSafe.trim(text);
        Duration duration = null;
        final Matcher matcher = MAXSPAN.matcher(given);
        if (matcher.matches()) {
            final long amount = Long.parseLong(matcher.group(1));
            final String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            if (unit.startsWith("ms") || unit.startsWith("milli")) {
                duration = Duration.ofMillis(amount);
            } else if (unit.startsWith("s")) {
                duration = Duration.ofSeconds(amount);
            } else if (unit.startsWith("m")) {
                duration = Duration.ofMinutes(amount);
            } else if (unit.startsWith("h")) {
                duration = Duration.ofHours(amount);
            } else {
                duration = Duration.ofDays(amount);
            }
        } else if (given.toUpperCase(Locale.ROOT).startsWith("P")) {
            try {
                duration = Duration.parse(given.toUpperCase(Locale.ROOT));
            } catch (final DateTimeParseException e) {
                duration = null;
            }
        }
        if (duration == null) {
            throw new WorkspaceException("The window '" + given + "' can't be read: give it as a number and a unit, "
                                         + "e.g. 30s, 2m, 1h.");
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new WorkspaceException("The window must be longer than nothing: e.g. 30s, 2m, 1h.");
        }
        if (duration.compareTo(MAX_MAXSPAN) > 0) {
            throw new WorkspaceException("The window may be at most 24h; '" + given + "' is longer. Narrow it, or "
                                         + "run separate sequences over parts of the time range.");
        }
        return duration;
    }

    /// @param duration A window.
    /// @return It as EQL writes it, in its largest whole unit: `2m`, `90s`, `1h`, `1500ms`.
    static String maxspanText(final Duration duration) {
        final long ms = duration.toMillis();
        if (ms % 3_600_000 == 0) {
            return ms / 3_600_000 + "h";
        } else if (ms % 60_000 == 0) {
            return ms / 60_000 + "m";
        } else if (ms % 1_000 == 0) {
            return ms / 1_000 + "s";
        }
        return ms + "ms";
    }

    /// @param fieldName A field's name.
    /// @return It as EQL names it: as it is, or in backquotes if it holds anything but letters, digits, `_`, `@`
    /// and `.`, or is an EQL keyword.
    static String eqlName(final String fieldName) {
        return PLAIN_NAME.matcher(fieldName).matches() && !EQL_KEYWORDS.contains(fieldName.toLowerCase(Locale.ROOT))
                ? fieldName
                : "`" + fieldName.replace("`", "``") + "`";
    }

    /// @param value A string.
    /// @return It as an EQL string, in double quotes, with `\`, `"` and control characters escaped.
    static String eqlString(final String value) {
        return "\"" + escaped(value) + "\"";
    }

    // ES|QL's "..." string takes the same escapes.
    private static String esqlString(final String value) {
        return "\"" + escaped(value) + "\"";
    }

    private static String escaped(final String value) {
        final StringBuilder sb = new StringBuilder();
        for (final char c : NullSafe.string(value).toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // A value as the field's type takes it: a number bare, a boolean bare, anything else a string.
    private static String eqlLiteral(final Filter filter, final FieldType type, final String value) {
        return literal(filter, type, value, true);
    }

    private static String esqlLiteral(final Filter filter, final FieldType type, final String value) {
        return literal(filter, type, value, false);
    }

    private static String literal(final Filter filter, final FieldType type, final String value, final boolean eql) {
        final String stripped = value.strip();
        if (NUMBERS.contains(type)) {
            if (!NUMBER.matcher(stripped).matches()) {
                throw new WorkspaceException("'" + filter.field() + "' is a number field, so '" + value + "' can't "
                                             + "be compared with it: give a number.");
            }
            return stripped;
        } else if (type == FieldType.BOOLEAN && ("true".equalsIgnoreCase(stripped)
                                                 || "false".equalsIgnoreCase(stripped))) {
            return stripped.toLowerCase(Locale.ROOT);
        }
        return eql
                ? eqlString(value)
                : esqlString(value);
    }

    // A CIDR, with a single address as one of its own (/32 or /128).
    private static String cidr(final String value) {
        final String stripped = value.strip();
        if (stripped.contains("/")) {
            return stripped;
        }
        return stripped + (stripped.contains(":")
                ? "/128"
                : "/32");
    }

    // BETWEEN's two ends, 'from,to'.
    private static List<String> between(final Filter filter) {
        final int comma = filter.value().indexOf(',');
        if (comma < 0) {
            throw new WorkspaceException("BETWEEN on '" + filter.field() + "' takes 'from,to'.");
        }
        return List.of(filter.value().substring(0, comma).strip(), filter.value().substring(comma + 1).strip());
    }

    // IN's values, separated by commas.
    private static List<String> values(final Filter filter) {
        final List<String> values = new ArrayList<>();
        for (final String value : filter.value().split(",")) {
            if (!value.isBlank()) {
                values.add(value.strip());
            }
        }
        if (values.isEmpty()) {
            throw new WorkspaceException("IN on '" + filter.field() + "' takes values separated by commas.");
        }
        return values;
    }

    private static boolean isText(final FieldType type) {
        return type == null || type == FieldType.KEYWORD || type == FieldType.TEXT;
    }


    // --------------------------------------------------------------------------------


    /// One condition of a step.
    ///
    /// @param field           The field.
    /// @param condition       The condition (IN for NOT_IN, with `negated`).
    /// @param value           The value ('from,to' for BETWEEN, values separated by commas for IN).
    /// @param caseInsensitive True to match text whatever its case.
    /// @param negated         True for NOT_IN: none of the values.
    /// @param length          True to compare the string's length (a whole number of characters), not its value.
    record Filter(String field,
                  Condition condition,
                  String value,
                  boolean caseInsensitive,
                  boolean negated,
                  boolean length) {

        /// A condition on the value, not negated.
        ///
        /// @param field           The field.
        /// @param condition       The condition.
        /// @param value           The value.
        /// @param caseInsensitive True to match text whatever its case.
        Filter(final String field, final Condition condition, final String value, final boolean caseInsensitive) {
            this(field, condition, value, caseInsensitive, false, false);
        }

        /// @param otherField Another field, e.g. the keyword subfield of a text field.
        /// @return This condition on that field.
        Filter onField(final String otherField) {
            return new Filter(otherField, condition, value, caseInsensitive, negated, length);
        }

        /// @param otherValue Another value, e.g. this one's time in the call's zone.
        /// @return This condition with that value.
        Filter withValue(final String otherValue) {
            return new Filter(field, condition, otherValue, caseInsensitive, negated, length);
        }
    }


    // --------------------------------------------------------------------------------


    /// One step of a sequence.
    ///
    /// @param doc        The position (from 1) of the step's document among the documents the steps read.
    /// @param filters    Its conditions, all of them.
    /// @param anyOf      Conditions of which at least one must hold, beside `filters`; empty for none.
    /// @param joinFields Its join fields, in the order every step names them.
    /// @param missing    True for an event that must not happen (`![...]`).
    /// @param runs       How many times it must happen in a row (`with runs=N`); 1 for once.
    /// @param fields     Fields of its events to show, beside the join fields and ids.
    /// @param types      The types of its document's fields, by name.
    /// @param restricted True if, with more than one document, it must be kept to its own document's events
    ///                   ([ElasticEql#DOC_FIELD]); false when its conditions already rule other documents' events
    ///                   out.
    record Step(int doc,
                List<Filter> filters,
                List<Filter> anyOf,
                List<String> joinFields,
                boolean missing,
                int runs,
                List<String> fields,
                Map<String, FieldType> types,
                boolean restricted) {

        /// Copies the lists.
        Step {
            filters = filters == null
                    ? List.of()
                    : List.copyOf(filters);
            anyOf = anyOf == null
                    ? List.of()
                    : List.copyOf(anyOf);
            joinFields = joinFields == null
                    ? List.of()
                    : List.copyOf(joinFields);
            fields = fields == null
                    ? List.of()
                    : List.copyOf(fields);
            types = types == null
                    ? Map.of()
                    : Map.copyOf(types);
        }

        /// A step kept to its own document.
        ///
        /// @param doc        The position (from 1) of its document.
        /// @param filters    Its conditions.
        /// @param anyOf      Conditions of which at least one must hold.
        /// @param joinFields Its join fields.
        /// @param missing    True for an event that must not happen.
        /// @param runs       How many times it must happen in a row.
        /// @param fields     Fields of its events to show.
        /// @param types      The types of its document's fields, by name.
        Step(final int doc,
             final List<Filter> filters,
             final List<Filter> anyOf,
             final List<String> joinFields,
             final boolean missing,
             final int runs,
             final List<String> fields,
             final Map<String, FieldType> types) {
            this(doc, filters, anyOf, joinFields, missing, runs, fields, types, true);
        }

        /// A step without an `any_of` group, kept to its own document.
        ///
        /// @param doc        The position (from 1) of its document.
        /// @param filters    Its conditions.
        /// @param joinFields Its join fields.
        /// @param missing    True for an event that must not happen.
        /// @param runs       How many times it must happen in a row.
        /// @param fields     Fields of its events to show.
        /// @param types      The types of its document's fields, by name.
        Step(final int doc,
             final List<Filter> filters,
             final List<String> joinFields,
             final boolean missing,
             final int runs,
             final List<String> fields,
             final Map<String, FieldType> types) {
            this(doc, filters, List.of(), joinFields, missing, runs, fields, types, true);
        }

        /// @param restricted Whether it must be kept to its own document's events.
        /// @return This step, so kept or not.
        Step withRestricted(final boolean restricted) {
            return new Step(doc, filters, anyOf, joinFields, missing, runs, fields, types, restricted);
        }
    }
}
