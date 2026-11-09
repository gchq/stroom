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

package stroom.search.elastic.search;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.NullSafe;

import co.elastic.clients.elasticsearch._types.mapping.Property.Kind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/// Works out which Elasticsearch fields can list their most common values with a `terms` aggregation, for the
/// value picker of an expression term, and builds the filter applied to those values.
public final class ElasticTopValues {

    /// The name of the `keyword` multi-field that a `text` field's whole values are aggregated on.
    static final String KEYWORD_SUB_FIELD = ".keyword";

    // Mapping types whose values can be aggregated directly. A `text` field can't be, so it uses its
    // `keyword` multi-field if it has one.
    private static final Set<String> AGGREGATABLE_TYPES = Set.of(
            Kind.Keyword.jsonValue(),
            Kind.Wildcard.jsonValue(),
            Kind.Ip.jsonValue(),
            Kind.Boolean.jsonValue(),
            Kind.Integer.jsonValue(),
            Kind.Short.jsonValue(),
            Kind.Byte.jsonValue(),
            Kind.Long.jsonValue());
    private static final Set<String> STRING_TYPES = Set.of(
            Kind.Keyword.jsonValue(),
            Kind.Wildcard.jsonValue());
    private static final Set<String> TEXT_TYPES = Set.of(
            Kind.Text.jsonValue(),
            Kind.MatchOnlyText.jsonValue());
    // Conditions that limit a time field to a range
    private static final Set<Condition> TIME_RANGE_CONDITIONS = EnumSet.of(
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.BETWEEN);
    // Characters with a special meaning in Lucene regular expressions
    private static final String REGEX_SPECIAL_CHARS = ".?+*|{}[]()\"\\#@&<>~";

    private ElasticTopValues() {
        // Utility class.
    }

    /// Finds the field to aggregate to list a field's most common values.
    ///
    /// @param fieldName         The name of the field.
    /// @param nativeType        The Elasticsearch mapping type of the field.
    /// @param nativeTypeLookup  Returns the mapping type of another field by name, or null if there is no such
    ///                          field. Used to find a `text` field's `keyword` multi-field.
    /// @return The name of the field to aggregate, or null if the field's values can't be listed. A field within
    /// a `nested` object is aggregated within that object, see [#getNestedPath].
    public static String getAggregationField(final String fieldName,
                                             final String nativeType,
                                             final Function<String, String> nativeTypeLookup) {
        if (AGGREGATABLE_TYPES.contains(nativeType)) {
            return fieldName;
        } else if (TEXT_TYPES.contains(nativeType)) {
            final String keywordField = fieldName + KEYWORD_SUB_FIELD;
            if (Kind.Keyword.jsonValue().equals(nativeTypeLookup.apply(keywordField))) {
                return keywordField;
            }
        }
        return null;
    }

    /// Finds the `nested` object a field is within, which its values must be aggregated within using a
    /// `nested` aggregation. For a field within `nested` objects within each other, this is the innermost one.
    ///
    /// @param fieldName         The name of the field.
    /// @param nativeTypeLookup  Returns the mapping type of another field by name, or null if there is no such
    ///                          field.
    /// @return The path of the innermost `nested` object the field is within, e.g. `user` for `user.name`, or
    /// null if it isn't within one.
    public static String getNestedPath(final String fieldName,
                                       final Function<String, String> nativeTypeLookup) {
        String nestedPath = null;
        int separatorIndex = fieldName.indexOf('.');
        while (separatorIndex > 0) {
            final String path = fieldName.substring(0, separatorIndex);
            if (Kind.Nested.jsonValue().equals(nativeTypeLookup.apply(path))) {
                nestedPath = path;
            }
            separatorIndex = fieldName.indexOf('.', separatorIndex + 1);
        }
        return nestedPath;
    }

    /// @param nativeType The Elasticsearch mapping type of the field being aggregated.
    /// @return True if the field holds strings, so its values can be filtered with a regular expression.
    public static boolean isFilterable(final String nativeType) {
        return STRING_TYPES.contains(nativeType);
    }

    /// Builds a Lucene regular expression that matches values containing the filter text.
    ///
    /// @param filter          The text the values must contain.
    /// @param caseInsensitive Whether letters match regardless of case.
    /// @return The regular expression.
    public static String toContainsRegex(final String filter, final boolean caseInsensitive) {
        final StringBuilder sb = new StringBuilder(".*");
        filter.codePoints().forEach(codePoint -> {
            final String upper = new String(Character.toChars(Character.toUpperCase(codePoint)));
            final String lower = new String(Character.toChars(Character.toLowerCase(codePoint)));
            if (caseInsensitive && !upper.equals(lower)) {
                sb.append('[').append(lower).append(upper).append(']');
            } else {
                if (REGEX_SPECIAL_CHARS.indexOf(codePoint) >= 0) {
                    sb.append('\\');
                }
                sb.appendCodePoint(codePoint);
            }
        });
        return sb.append(".*").toString();
    }

    /// Finds the terms of a query expression that limit the index's time field to a range and must all hold:
    /// enabled `>`, `>=`, `<`, `<=` and `between` terms on the time field, directly under the root AND
    /// operator or under enabled AND operators within it. A term under an OR or NOT operator doesn't always
    /// limit the time range, so it is left out, as are disabled terms and operators.
    ///
    /// @param expression The query expression, or null.
    /// @param timeField  The index's time field, or null.
    /// @return The terms, in the order they appear in the expression. Empty if there are none.
    public static List<ExpressionTerm> getTimeRangeTerms(final ExpressionOperator expression,
                                                         final String timeField) {
        final List<ExpressionTerm> terms = new ArrayList<>();
        if (NullSafe.isNonBlankString(timeField)) {
            addTimeRangeTerms(expression, timeField, terms);
        }
        return terms;
    }

    private static void addTimeRangeTerms(final ExpressionOperator operator,
                                          final String timeField,
                                          final List<ExpressionTerm> terms) {
        if (operator == null || !operator.enabled() || !Op.AND.equals(operator.op())) {
            return;
        }
        for (final ExpressionItem child : NullSafe.list(operator.getChildren())) {
            if (child instanceof final ExpressionTerm term) {
                if (term.enabled()
                    && timeField.equals(term.getField())
                    && TIME_RANGE_CONDITIONS.contains(term.getCondition())
                    && NullSafe.isNonBlankString(term.getValue())) {
                    terms.add(term);
                }
            } else if (child instanceof final ExpressionOperator childOperator) {
                addTimeRangeTerms(childOperator, timeField, terms);
            }
        }
    }
}
