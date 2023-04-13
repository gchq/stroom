/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.search.elastic.search;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;
import stroom.util.functions.TriFunction;

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Convert our query objects to an Elasticsearch Query DSL query.
 */
public class SearchExpressionQueryBuilder {

    private static final String DELIMITER = ",";
    private static final Pattern WILDCARD_PATTERN = Pattern.compile(".*[*?].*");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("^\"(.+)\"$");
    private static final Pattern IPV4_ADDRESS_PATTERN =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?:/\\d{1,2})?$");
    private static final Pattern IPV4_CIDR_PATTERN =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+/\\d{1,2}$");
    private final Map<String, ElasticIndexField> indexFieldsMap;
    private final WordListProvider wordListProvider;
    private final DateTimeSettings dateTimeSettings;
    private final long nowEpochMilli;

    public SearchExpressionQueryBuilder(final WordListProvider wordListProvider,
                                        final Map<String, ElasticIndexField> indexFieldsMap,
                                        final DateTimeSettings dateTimeSettings,
                                        final long nowEpochMilli
    ) {
        this.wordListProvider = wordListProvider;
        this.indexFieldsMap = indexFieldsMap;
        this.dateTimeSettings = dateTimeSettings;
        this.nowEpochMilli = nowEpochMilli;
    }

    public QueryBuilder buildQuery(final ExpressionOperator expression) {
        if (expression == null) {
            throw new IllegalArgumentException("No search expression has been provided!");
        }
        if (!operatorHasChildren(expression)) {
            throw new IllegalArgumentException("No search terms have been specified!");
        }

        return getQuery(expression);
    }

    private QueryBuilder getQuery(final ExpressionItem item) {
        if (item.enabled()) {
            if (item instanceof ExpressionTerm) {
                // Create queries for single terms.
                final ExpressionTerm term = (ExpressionTerm) item;
                return getTermQuery(term);
            } else if (item instanceof ExpressionOperator) {
                // Create queries for expression tree nodes.
                final ExpressionOperator operator = (ExpressionOperator) item;
                if (operatorHasChildren(operator)) {
                    final List<QueryBuilder> innerChildQueries = operator.getChildren().stream()
                            .map(this::getQuery)
                            .filter(Objects::nonNull).toList();

                    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
                    final Op op = operator.getOp();
                    if (op == null || op.equals(Op.AND)) {
                        innerChildQueries.forEach(boolQueryBuilder::must);
                    } else if (op.equals(Op.OR)) {
                        innerChildQueries.forEach(boolQueryBuilder::should);
                    } else if (op.equals(Op.NOT)) {
                        innerChildQueries.forEach(boolQueryBuilder::mustNot);
                    }
                    return boolQueryBuilder;
                }
            }
        }

        return null;
    }

    private QueryBuilder getTermQuery(final ExpressionTerm expressionTerm) {
        String field = expressionTerm.getField();
        final Condition condition = expressionTerm.getCondition();
        String value = expressionTerm.getValue();
        final DocRef docRef = expressionTerm.getDocRef();

        // Clean strings to remove unwanted whitespace that the user may have added accidentally
        if (field != null) {
            field = field.trim();
        }
        if (value != null) {
            value = value.trim();
        }

        // Validate the field
        if (field == null || field.length() == 0) {
            throw new IllegalArgumentException("Field not set");
        }
        final ElasticIndexField indexField = indexFieldsMap.get(field);
        if (indexField == null) {
            throw new ResourceNotFoundException("Field not found in index: " + field);
        }
        final String fieldName = indexField.getFieldName();

        // Validate the expression
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (Condition.IN_DICTIONARY.equals(condition)) {
            if (docRef == null || docRef.getUuid() == null) {
                throw new IllegalArgumentException("Dictionary not set for field: " + field);
            }
        }

        // Special case: if the expression is a wildcard, use the `exists` query
        if (value.equals("*")) {
            return QueryBuilders.existsQuery(fieldName);
        }

        // Create a query based on the field type and condition.
        final ElasticIndexFieldType elasticFieldType = indexField.getFieldUse();
        if (indexField.getFieldUse().isNumeric()) {
            return buildScalarQuery(condition, indexField, fieldName, value, this::getNumber, docRef);
        } else if (elasticFieldType.equals(ElasticIndexFieldType.FLOAT)) {
            return buildScalarQuery(condition, indexField, fieldName, value, this::getFloat, docRef);
        } else if (elasticFieldType.equals(ElasticIndexFieldType.DOUBLE)) {
            return buildScalarQuery(condition, indexField, fieldName, value, this::getDouble, docRef);
        } else if (elasticFieldType.equals(ElasticIndexFieldType.DATE)) {
            return buildScalarQuery(condition, indexField, fieldName, value, this::getDate, docRef);
        } else if (elasticFieldType.equals(ElasticIndexFieldType.IPV4_ADDRESS)) {
            return buildScalarQuery(condition, indexField, fieldName, value, this::getIpV4Address, docRef);
        } else {
            return buildStringQuery(condition, value, docRef, indexField, fieldName);
        }
    }

    private QueryBuilder buildStringQuery(final Condition condition,
                                          final String expression,
                                          final DocRef docRef,
                                          final ElasticIndexField indexField,
                                          final String fieldName) {
        final List<String> terms = tokenizeExpression(expression).filter(term -> !term.isEmpty()).toList();
        if (terms.isEmpty()) {
            return null;
        }

        if (indexField.getFieldType().equals("keyword")) {
            // Elasticsearch field mapping type is `keyword`, so generate a term-level query
            switch (condition) {
                case EQUALS:
                    return buildKeywordQuery(fieldName, expression);
                case MATCHES_REGEX:
                    return QueryBuilders.regexpQuery(fieldName, expression);
                case IN:
                    if (terms.size() > 1) {
                        final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                        terms.forEach(term -> boolQuery.should(buildKeywordQuery(fieldName, term)));
                        return boolQuery;
                    } else {
                        return buildKeywordQuery(fieldName, expression);
                    }
                case IN_DICTIONARY:
                    return buildDictionaryQuery(condition, fieldName, docRef, indexField);
                default:
                    throw new UnsupportedOperationException("Unsupported condition '" + condition.getDisplayValue() +
                            "' for " + indexField.getFieldUse().getDisplayValue() + " field type");
            }
        } else {
            // This is a type other than `keyword`, such as `text` or `wildcard`. Perform a full-text match.
            switch (condition) {
                case EQUALS:
                    return buildTextQuery(fieldName, expression);
                case MATCHES_REGEX:
                    return QueryBuilders.regexpQuery(fieldName, expression);
                case IN:
                    if (terms.size() > 1) {
                        final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                        terms.forEach(term -> boolQuery.should(buildTextQuery(fieldName, term)));
                        return boolQuery;
                    } else {
                        return buildTextQuery(fieldName, expression);
                    }
                case IN_DICTIONARY:
                    return buildDictionaryQuery(condition, fieldName, docRef, indexField);
                default:
                    throw new RuntimeException("Unsupported condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldUse().getDisplayValue() + " field type");
            }
        }
    }

    /**
     * Creates an Elasticsearch query for a `keyword` field appropriate for the expression content
     */
    private QueryBuilder buildKeywordQuery(final String fieldName, final String expression) {
        final Matcher quotedMatcher = QUOTED_PATTERN.matcher(expression);
        if (quotedMatcher.matches()) {
            // Expression is in quotes, so perform an exact match
            return QueryBuilders.termQuery(fieldName, quotedMatcher.group(1));
        } else if (WILDCARD_PATTERN.matcher(expression).matches()) {
            // Expression is a wildcard pattern, so return a wildcard query
            return QueryBuilders.wildcardQuery(fieldName, expression)
                    .caseInsensitive(true);
        } else {
            // Perform an exact match on the provided term
            return QueryBuilders.termQuery(fieldName, expression);
        }
    }

    private QueryBuilder buildTextQuery(final String fieldName, final String expression) {
        // Contains wildcard chars, so use a query string query, which supports these
        return QueryBuilders.queryStringQuery(expression)
                .field(fieldName)
                .analyzeWildcard(true);
    }

    private <T> QueryBuilder buildScalarQuery(
            final ExpressionTerm.Condition condition, final ElasticIndexField indexField, final String fieldName,
            final String fieldValue, TriFunction<Condition, String, String, T> valueParser,
            final DocRef docRef
    ) {
        T numericValue;
        List<T> numericValues;

        switch (condition) {
            case EQUALS:
                numericValue = valueParser.apply(condition, fieldName, fieldValue);
                return QueryBuilders
                        .termQuery(fieldName, numericValue);
            case CONTAINS:
            case IN:
                numericValues = tokenizeExpression(fieldValue)
                        .map(val -> valueParser.apply(condition, fieldName, val))
                        .collect(Collectors.toList());
                return QueryBuilders
                        .termsQuery(fieldName, numericValues);
            case GREATER_THAN:
                numericValue = valueParser.apply(condition, fieldName, fieldValue);
                return QueryBuilders
                        .rangeQuery(fieldName)
                        .gt(numericValue);
            case GREATER_THAN_OR_EQUAL_TO:
                numericValue = valueParser.apply(condition, fieldName, fieldValue);
                return QueryBuilders
                        .rangeQuery(fieldName)
                        .gte(numericValue);
            case LESS_THAN:
                numericValue = valueParser.apply(condition, fieldName, fieldValue);
                return QueryBuilders
                        .rangeQuery(fieldName)
                        .lt(numericValue);
            case LESS_THAN_OR_EQUAL_TO:
                numericValue = valueParser.apply(condition, fieldName, fieldValue);
                return QueryBuilders
                        .rangeQuery(fieldName)
                        .lte(numericValue);
            case BETWEEN:
                numericValues = tokenizeExpression(fieldValue)
                        .map(val -> valueParser.apply(condition, fieldName, val))
                        .collect(Collectors.toList());
                if (numericValues.size() != 2) {
                    throw new IllegalArgumentException(
                            "Two values needed for between query; " + numericValues.size() + " provided");
                }
                return QueryBuilders
                        .rangeQuery(fieldName)
                        .gte(numericValues.get(0))
                        .lte(numericValues.get(1));
            case IN_DICTIONARY:
                return buildDictionaryQuery(condition, fieldName, docRef, indexField);
            default:
                throw new RuntimeException("Unexpected condition '" + condition.getDisplayValue() + "' for " +
                        indexField.getFieldUse().getDisplayValue() + " field type");
        }
    }

    /**
     * Split an expression into is component terms. Useful for extracting terms for use with "in" or "contains"
     * conditions
     *
     * @param expression - Example: "1,2, 3"
     * @return - Stream of terms. Example: [ "1", "2", "3" ]
     */
    private Stream<String> tokenizeExpression(final String expression) {
        if (expression != null) {
            return Arrays.stream(expression.split(DELIMITER))
                    .map(String::trim)
                    .filter(token -> !token.isEmpty());
        } else {
            return Stream.empty();
        }
    }

    private Long getDate(final Condition condition, final String fieldName, final String value) {
        try {
            // Empty optional will be caught below
            return DateExpressionParser.parse(value, dateTimeSettings, nowEpochMilli).get().toInstant().toEpochMilli();
        } catch (final Exception e) {
            throw new IllegalArgumentException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

    /**
     * Validates the format of an IPv4 address. CIDR notation is allowed (e.g. 192.168.1.1/24) for equality
     * conditions (EQUALS, IN and IN_DICTIONARY).
     */
    private String getIpV4Address(final Condition condition, final String fieldName, final String value) {
        final Matcher ipAddressMatcher = IPV4_ADDRESS_PATTERN.matcher(value);
        try {
            if (ipAddressMatcher.matches()) {
                InetAddress.getByAddress(new byte[]{
                        Integer.valueOf(ipAddressMatcher.group(1)).byteValue(),
                        Integer.valueOf(ipAddressMatcher.group(2)).byteValue(),
                        Integer.valueOf(ipAddressMatcher.group(3)).byteValue(),
                        Integer.valueOf(ipAddressMatcher.group(4)).byteValue()
                });
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid IPv4 address format: " + value + " for field \"" +
                    fieldName + "\"");
        }

        if (!condition.equals(Condition.EQUALS) && !condition.equals(Condition.IN) &&
                !condition.equals(Condition.IN_DICTIONARY) && IPV4_CIDR_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("CIDR notation is only supported for EQUALS and IN operators. " +
                    "Value provided: \"" + value + "\". Operator: \"" + condition.getDisplayValue() + "\"");
        }
        return value;
    }

    private Long getNumber(final Condition condition, final String fieldName, final String value) {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            throw new NumberFormatException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\""
            );
        }
    }

    private Float getFloat(final Condition condition, final String fieldName, final String value) {
        try {
            return Float.parseFloat(value);
        } catch (final NumberFormatException e) {
            throw new NumberFormatException(
                    "Expected a decimal (float) value for field \"" + fieldName + "\" but was given string \"" +
                            value + "\""
            );
        }
    }

    private Double getDouble(final Condition condition, final String fieldName, final String value) {
        try {
            return Double.parseDouble(value);
        } catch (final NumberFormatException e) {
            throw new NumberFormatException(
                    "Expected a decimal (double) value for field \"" + fieldName + "\" but was given string \"" +
                            value + "\""
            );
        }
    }

    /**
     * Loads the specified Dictionary from the doc store. Each line is combined with AND logic.
     */
    private QueryBuilder buildDictionaryQuery(
            final Condition condition,
            final String fieldName,
            final DocRef docRef,
            final ElasticIndexField indexField
    ) {
        final String[] lines = wordListProvider.getWords(docRef);
        final BoolQueryBuilder builder = QueryBuilders.boolQuery();

        for (final String line : lines) {
            final BoolQueryBuilder mustQueries = QueryBuilders.boolQuery();
            final ElasticIndexFieldType elasticFieldType = indexField.getFieldUse();

            if (elasticFieldType.isNumeric()) {
                mustQueries.must(QueryBuilders.termQuery(fieldName, getNumber(condition, fieldName, line)));
            } else if (elasticFieldType.equals(ElasticIndexFieldType.FLOAT)) {
                mustQueries.must(QueryBuilders.termQuery(fieldName, getFloat(condition, fieldName, line)));
            } else if (elasticFieldType.equals(ElasticIndexFieldType.DOUBLE)) {
                mustQueries.must(QueryBuilders.termQuery(fieldName, getDouble(condition, fieldName, line)));
            } else if (elasticFieldType.equals(ElasticIndexFieldType.DATE)) {
                mustQueries.must(QueryBuilders.termQuery(fieldName, getDate(condition, fieldName, line)));
            } else if (elasticFieldType.equals(ElasticIndexFieldType.IPV4_ADDRESS)) {
                mustQueries.must(QueryBuilders.termQuery(fieldName, getIpV4Address(condition, fieldName, line)));
            } else if (indexField.getFieldType().equals("keyword")) {
                mustQueries.must(buildKeywordQuery(fieldName, line));
            } else {
                mustQueries.must(buildTextQuery(fieldName, line));
            }

            builder.should(mustQueries);
        }

        return builder;
    }

    private boolean operatorHasChildren(final ExpressionOperator operator) {
        if (operator != null && operator.enabled() && operator.getChildren() != null) {
            for (final ExpressionItem child : operator.getChildren()) {
                if (child.enabled()) {
                    if (child instanceof ExpressionOperator) {
                        final ExpressionOperator childOperator = (ExpressionOperator) child;
                        if (operatorHasChildren(childOperator)) {
                            return true;
                        }
                    } else {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
