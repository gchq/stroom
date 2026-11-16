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

/// Writes the EQL of an `eql_sequence` call from its structured steps, and the ES|QL counts its pre-flight makes of
/// each step. Nothing here runs anything.
///
/// A step's conditions take the `search_events` `where` form (field, condition, value), plus `caseInsensitive`.
/// In EQL: EQUALS is `==` (`:` when case-insensitive), or `like`/`like~` with a `*` wildcard, or `cidrMatch` for a
/// CIDR on an IP field; IN is `in`/`in~`, or `cidrMatch` with CIDRs; CONTAINS, STARTS_WITH and ENDS_WITH are
/// `like`/`like~` patterns; MATCHES_REGEX is `regex`/`regex~`; ranges are `<`, `<=`, `>`, `>=` (BETWEEN both
/// ends, inclusive); IS_NULL and IS_NOT_NULL are `== null` and `!= null`. Numbers are written bare, the rest as
/// strings.
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

    private EqlQueryWriter() {
        // Static only.
    }

    /// The EQL of a sequence.
    ///
    /// @param steps    The steps, in order, two or more.
    /// @param until    The step that ends a sequence before it is complete, or null.
    /// @param maxspan  How far apart the first and last events may be.
    /// @param docCount How many documents the steps read: with more than one, each step matches its own
    ///                 document's events only ([ElasticEql#DOC_FIELD]).
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
        if (docCount > 1) {
            parts.add(ElasticEql.DOC_FIELD + " == \"" + step.doc() + "\"");
        }
        for (final Filter filter : step.filters()) {
            parts.add(eqlCondition(filter, step.types().get(filter.field())));
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
        final String name = eqlName(filter.field());
        final boolean text = isText(type);
        final boolean insensitive = filter.caseInsensitive() && text;
        final String value = filter.value();
        return switch (filter.condition()) {
            case EQUALS -> {
                if (type == FieldType.IPV4_ADDRESS && value.contains("/")) {
                    yield "cidrMatch(" + name + ", " + eqlString(value.strip()) + ")";
                } else if (text && value.contains("*")) {
                    yield name + (insensitive
                            ? " like~ "
                            : " like ") + eqlString(value);
                }
                yield name + (insensitive
                        ? " : "
                        : " == ") + eqlLiteral(filter, type, value);
            }
            case NOT_EQUALS -> text && value.contains("*")
                    ? "not " + name + (insensitive
                    ? " like~ "
                    : " like ") + eqlString(value)
                    : insensitive
                            ? "not " + name + " : " + eqlString(value)
                            : name + " != " + eqlLiteral(filter, type, value);
            case CONTAINS -> like(name, insensitive, "*" + value + "*");
            case STARTS_WITH -> like(name, insensitive, value + "*");
            case ENDS_WITH -> like(name, insensitive, "*" + value);
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

    /// The count of one step's events, as an ES|QL pipeline: its conditions in `WHERE`, then `STATS n = COUNT(*)`.
    /// A case-insensitive regex has no ES|QL form, so it is left out (the count is then at most what it would be).
    ///
    /// @param step The step.
    /// @return The pipeline, e.g. `| WHERE action == "Copy" | STATS n = COUNT(*)`.
    static String countPipeline(final Step step) {
        final List<String> parts = new ArrayList<>();
        for (final Filter filter : step.filters()) {
            final String condition = esqlCondition(filter, step.types().get(filter.field()));
            if (condition != null) {
                parts.add(condition);
            }
        }
        return (parts.isEmpty()
                ? ""
                : "| WHERE " + String.join(" AND ", parts) + " ") + "| STATS n = COUNT(*)";
    }

    /// One condition in ES|QL, for a count.
    ///
    /// @param filter The condition.
    /// @param type   Its field's type, or null if not known.
    /// @return The ES|QL, or null where it has no ES|QL form (a case-insensitive regex).
    static String esqlCondition(final Filter filter, final FieldType type) {
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
                if (type == FieldType.IPV4_ADDRESS && values.stream().anyMatch(v -> v.contains("/"))) {
                    yield "CIDR_MATCH(" + name + ", " + String.join(", ", values.stream()
                            .map(v -> esqlString(cidr(v)))
                            .toList()) + ")";
                }
                yield name + " IN (" + String.join(", ", values.stream()
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
    /// @param condition       The condition.
    /// @param value           The value ('from,to' for BETWEEN, values separated by commas for IN).
    /// @param caseInsensitive True to match text whatever its case.
    record Filter(String field, Condition condition, String value, boolean caseInsensitive) {

    }


    // --------------------------------------------------------------------------------


    /// One step of a sequence.
    ///
    /// @param doc        The position (from 1) of the step's document among the documents the steps read.
    /// @param filters    Its conditions, all of them.
    /// @param joinFields Its join fields, in the order every step names them.
    /// @param missing    True for an event that must not happen (`![...]`).
    /// @param runs       How many times it must happen in a row (`with runs=N`); 1 for once.
    /// @param fields     Fields of its events to show, beside the join fields and ids.
    /// @param types      The types of its document's fields, by name.
    record Step(int doc,
                List<Filter> filters,
                List<String> joinFields,
                boolean missing,
                int runs,
                List<String> fields,
                Map<String, FieldType> types) {

        /// Copies the lists.
        Step {
            filters = filters == null
                    ? List.of()
                    : List.copyOf(filters);
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
    }
}
