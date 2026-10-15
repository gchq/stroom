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

package stroom.ai.impl;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// Describes a query expression in plain words, e.g. "Feed is 'ACME' and (Type is 'Raw Events' or Type is
/// 'Events')", for people deciding whether to act on something that uses it, such as a processor filter.
final class ExpressionDescriber {

    private ExpressionDescriber() {
        // Static methods only.
    }

    /// @param expression An expression, or null.
    /// @return The expression in words, or null if it selects everything (it is null, or has no enabled terms).
    static String describe(final ExpressionOperator expression) {
        return expression == null
                ? null
                : describeOperator(expression, false);
    }

    private static String describe(final ExpressionItem item, final boolean nested) {
        if (item instanceof final ExpressionOperator operator) {
            return describeOperator(operator, nested);
        } else if (item instanceof final ExpressionTerm term) {
            return describeTerm(term);
        }
        return null;
    }

    private static String describeOperator(final ExpressionOperator operator, final boolean nested) {
        if (!operator.enabled()) {
            return null;
        }
        final Op op = Objects.requireNonNullElse(operator.op(), Op.AND);
        final List<String> parts = new ArrayList<>();
        for (final ExpressionItem child : NullSafe.list(operator.getChildren())) {
            final String part = describe(child, true);
            if (part != null) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        if (op == Op.NOT) {
            // NOT applies to its children taken together, as AND.
            return "not (" + String.join(" and ", parts) + ")";
        }
        final String joined = String.join(op == Op.OR
                ? " or "
                : " and ", parts);
        return nested && parts.size() > 1
                ? "(" + joined + ")"
                : joined;
    }

    private static String describeTerm(final ExpressionTerm term) {
        if (!term.enabled() || term.getField() == null || term.getCondition() == null) {
            return null;
        }
        final String field = term.getField();
        final Condition condition = term.getCondition();
        final String value = value(term);
        final boolean isTime = isTime(field);
        return switch (condition) {
            case EQUALS, IS_DOC_REF, IS_USER_REF -> field + " is " + value;
            case NOT_EQUALS, IS_NOT_DOC_REF, IS_NOT_USER_REF -> field + " is not " + value;
            case CONTAINS -> field + " contains " + value;
            case STARTS_WITH -> field + " starts with " + value;
            case ENDS_WITH -> field + " ends with " + value;
            case MATCHES_REGEX -> field + " matches the pattern " + value;
            case WORD_BOUNDARY -> field + " matches the words " + value;
            case EQUALS_CASE_SENSITIVE -> field + " is " + value + " (matching case)";
            case NOT_EQUALS_CASE_SENSITIVE -> field + " is not " + value + " (matching case)";
            case CONTAINS_CASE_SENSITIVE -> field + " contains " + value + " (matching case)";
            case STARTS_WITH_CASE_SENSITIVE -> field + " starts with " + value + " (matching case)";
            case ENDS_WITH_CASE_SENSITIVE -> field + " ends with " + value + " (matching case)";
            case MATCHES_REGEX_CASE_SENSITIVE -> field + " matches the pattern " + value + " (matching case)";
            case GREATER_THAN -> field + (isTime
                    ? " is after "
                    : " is more than ") + value;
            case GREATER_THAN_OR_EQUAL_TO -> field + (isTime
                    ? " is on or after "
                    : " is at least ") + value;
            case LESS_THAN -> field + (isTime
                    ? " is before "
                    : " is less than ") + value;
            case LESS_THAN_OR_EQUAL_TO -> field + (isTime
                    ? " is on or before "
                    : " is at most ") + value;
            case BETWEEN -> between(field, term.getValue());
            case IN -> field + " is one of " + list(term.getValue());
            case IN_DICTIONARY -> field + " is in the dictionary " + value;
            case IN_FOLDER -> field + " is in the folder " + value;
            case OF_DOC_REF -> field + " is of " + value;
            case IS_NULL -> field + " is empty";
            case IS_NOT_NULL -> field + " is not empty";
            default -> field + " " + condition.getDisplayValue() + " " + value;
        };
    }

    // The term's value, or the name of the document it names, quoted.
    private static String value(final ExpressionTerm term) {
        final DocRef docRef = term.getDocRef();
        if (docRef != null) {
            return quote(Objects.requireNonNullElse(docRef.getName(), docRef.getUuid()));
        }
        return quote(NullSafe.string(term.getValue()));
    }

    private static String between(final String field, final String value) {
        final String[] ends = NullSafe.string(value).split(",", 2);
        return ends.length == 2
                ? field + " is between " + quote(ends[0].strip()) + " and " + quote(ends[1].strip())
                : field + " is between " + quote(NullSafe.string(value));
    }

    // "'A', 'B' or 'C'", from a comma separated list.
    private static String list(final String value) {
        final List<String> items = Arrays.stream(NullSafe.string(value).split(","))
                .map(String::strip)
                .filter(item -> !item.isEmpty())
                .map(ExpressionDescriber::quote)
                .toList();
        if (items.size() <= 1) {
            return items.isEmpty()
                    ? quote("")
                    : items.getFirst();
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " or " + items.getLast();
    }

    private static boolean isTime(final String field) {
        final String lower = field.toLowerCase(Locale.ROOT);
        return lower.endsWith("time") || lower.endsWith("date");
    }

    private static String quote(final String text) {
        return "'" + text + "'";
    }
}
