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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.sequence.StreamFilters;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// A timeline role's conditions (a Stroom expression, as [StreamFilters] makes them from a role's `where` and
/// `any_of`) held against one event's fetched values, in memory: so each source is read once, filtered by the user
/// and the period only, and its roles are assigned to the events read (decided with the user 2026-11-25), not a scan
/// per role.
///
/// The conditions are those [StreamFilters] writes: `EQUALS` (with `*` wildcards), `NOT_EQUALS`, `IN` (values
/// separated by commas), `MATCHES_REGEX` (the whole value), `GREATER_THAN`, `GREATER_THAN_OR_EQUAL_TO`,
/// `LESS_THAN`, `LESS_THAN_OR_EQUAL_TO` and `BETWEEN` (as numbers where both sides are, else as times where both
/// are, else as text), under `AND`, `OR` and `NOT`; the case-insensitive option is honoured. A field with several
/// values holds a condition if any of them does; a field with none holds only a negated one. A condition this
/// doesn't know never holds.
final class RoleConditions {

    private RoleConditions() {
        // Static only.
    }

    /// @param filter A role's conditions, or null for every event.
    /// @param fields An event's fetched values, by field.
    /// @return True if the event holds them.
    static boolean holds(final ExpressionItem filter, final Map<String, List<Object>> fields) {
        if (filter == null) {
            return true;
        }
        if (filter instanceof final ExpressionOperator operator) {
            final List<ExpressionItem> children = NullSafe.list(operator.getChildren()).stream()
                    .filter(child -> child.enabled())
                    .toList();
            return switch (operator.op()) {
                case AND -> children.stream().allMatch(child -> holds(child, fields));
                case OR -> children.isEmpty() || children.stream().anyMatch(child -> holds(child, fields));
                case NOT -> children.stream().noneMatch(child -> holds(child, fields));
            };
        }
        if (filter instanceof final ExpressionTerm term) {
            return term(term, NullSafe.map(fields).get(term.getField()));
        }
        return false;
    }

    /// @param filter A role's conditions, or null.
    /// @return The fields they read, in the order first named: fetched with the events, so they can be held.
    static Set<String> fields(final ExpressionItem filter) {
        final Set<String> out = new LinkedHashSet<>();
        collect(filter, out);
        return out;
    }

    private static void collect(final ExpressionItem item, final Set<String> into) {
        if (item instanceof final ExpressionOperator operator) {
            NullSafe.list(operator.getChildren()).forEach(child -> collect(child, into));
        } else if (item instanceof final ExpressionTerm term && NullSafe.isNonBlankString(term.getField())) {
            into.add(term.getField());
        }
    }

    private static boolean term(final ExpressionTerm term, final List<Object> values) {
        final boolean insensitive = term.getOptions() != null
                                    && "true".equals(term.getOptions().get(StreamFilters.CASE_INSENSITIVE));
        final List<String> texts = new ArrayList<>();
        for (final Object value : NullSafe.list(values)) {
            if (value != null) {
                texts.add(text(value));
            }
        }
        final String wanted = NullSafe.string(term.getValue());
        if (term.getCondition() == ExpressionTerm.Condition.NOT_EQUALS) {
            return texts.stream().noneMatch(text -> like(text, wanted, insensitive));
        }
        return texts.stream().anyMatch(text -> one(term.getCondition(), text, wanted, insensitive));
    }

    private static boolean one(final ExpressionTerm.Condition condition,
                               final String value,
                               final String wanted,
                               final boolean insensitive) {
        return switch (condition) {
            case EQUALS -> like(value, wanted, insensitive);
            case IN -> List.of(wanted.split(",")).stream().anyMatch(one -> like(value, one.strip(), insensitive));
            case MATCHES_REGEX -> regex(value, wanted, insensitive);
            case GREATER_THAN -> compare(value, wanted) > 0;
            case GREATER_THAN_OR_EQUAL_TO -> compare(value, wanted) >= 0;
            case LESS_THAN -> compare(value, wanted) < 0;
            case LESS_THAN_OR_EQUAL_TO -> compare(value, wanted) <= 0;
            case BETWEEN -> between(value, wanted);
            default -> false;
        };
    }

    private static boolean between(final String value, final String wanted) {
        final String[] bounds = wanted.split(",", 2);
        return bounds.length == 2 && compare(value, bounds[0].strip()) >= 0 && compare(value, bounds[1].strip()) <= 0;
    }

    // Numbers as numbers where both are, times as times where both are, else text.
    private static int compare(final String value, final String wanted) {
        final Double a = number(value);
        final Double b = number(wanted);
        if (a != null && b != null) {
            return Double.compare(a, b);
        }
        final Instant x = instant(value);
        final Instant y = instant(wanted);
        if (x != null && y != null) {
            return x.compareTo(y);
        }
        return value.compareTo(wanted);
    }

    private static boolean like(final String value, final String pattern, final boolean insensitive) {
        if (!pattern.contains("*")) {
            final Double a = number(value);
            final Double b = number(pattern);
            if (a != null && b != null) {
                return a.equals(b);
            }
            return insensitive
                    ? value.equalsIgnoreCase(pattern)
                    : value.equals(pattern);
        }
        final String regex = String.join(".*", List.of(pattern.split("\\*", -1)).stream()
                .map(Pattern::quote)
                .toList());
        return Pattern.compile(regex, flags(insensitive)).matcher(value).matches();
    }

    private static boolean regex(final String value, final String pattern, final boolean insensitive) {
        try {
            return Pattern.compile(pattern, flags(insensitive)).matcher(value).matches();
        } catch (final PatternSyntaxException e) {
            return false;
        }
    }

    private static int flags(final boolean insensitive) {
        return insensitive
                ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL
                : Pattern.DOTALL;
    }

    private static String text(final Object value) {
        return value instanceof final Double number && number == Math.rint(number) && !number.isInfinite()
                ? String.valueOf(number.longValue())
                : String.valueOf(value);
    }

    private static Double number(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.strip());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static Instant instant(final String text) {
        try {
            return Instant.parse(text.strip());
        } catch (final DateTimeParseException e) {
            return null;
        }
    }
}
