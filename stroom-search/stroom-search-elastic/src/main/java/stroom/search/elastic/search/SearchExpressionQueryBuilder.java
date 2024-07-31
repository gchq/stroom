/*
 * Copyright 2017-2024 Crown Copyright
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

import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.IndexField;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.util.NullSafe;
import stroom.util.functions.TriFunction;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.mapping.Property.Kind;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
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
    private final DocRef indexDocRef;
    private final IndexFieldCache indexFieldCache;
    private final WordListProvider wordListProvider;
    private final DateTimeSettings dateTimeSettings;

    public SearchExpressionQueryBuilder(final DocRef indexDocRef,
                                        final IndexFieldCache indexFieldCache,
                                        final WordListProvider wordListProvider,
                                        final DateTimeSettings dateTimeSettings) {
        this.indexDocRef = indexDocRef;
        this.indexFieldCache = indexFieldCache;
        this.wordListProvider = wordListProvider;
        this.dateTimeSettings = dateTimeSettings;
    }

    public Query buildQuery(final ExpressionOperator expression) {
        Query query = null;
        if (expression != null) {
            query = getQuery(expression);
        }
        if (query == null) {
            query = MatchAllQuery.of(q -> q)._toQuery();
        }
        return query;
    }

    private Query getQuery(final ExpressionItem item) {
        if (item.enabled()) {
            if (item instanceof final ExpressionTerm term) {
                // Create queries for single terms.
                return getTermQuery(term);
            } else if (item instanceof final ExpressionOperator operator) {
                // Create queries for expression tree nodes.
                if (operatorHasChildren(operator)) {
                    final List<Query> innerChildQueries = operator.getChildren().stream()
                            .map(this::getQuery)
                            .filter(Objects::nonNull).toList();

                    BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
                    final Op op = operator.getOp();
                    if (op == null || op.equals(Op.AND)) {
                        innerChildQueries.forEach(boolQueryBuilder::must);
                    } else if (op.equals(Op.OR)) {
                        innerChildQueries.forEach(boolQueryBuilder::should);
                    } else if (op.equals(Op.NOT)) {
                        innerChildQueries.forEach(boolQueryBuilder::mustNot);
                    }
                    return boolQueryBuilder.build()._toQuery();
                }
            }
        }

        return null;
    }

    private Query getTermQuery(final ExpressionTerm expressionTerm) {
        // Clean strings to remove unwanted whitespace that the user may have added accidentally
        final String field = NullSafe.trim(expressionTerm.getField());
        final Condition condition = expressionTerm.getCondition();
        final String value = NullSafe.trim(expressionTerm.getValue());
        final DocRef docRef = expressionTerm.getDocRef();

        // Validate the field
        if (field.isEmpty()) {
            throw new IllegalArgumentException("Field not set");
        }
        final IndexField indexField = indexFieldCache.get(indexDocRef, field);
        if (!(indexField instanceof final ElasticIndexField elasticIndexField)) {
            throw new SearchException("Field not found in index: " + field);
        }
        final String fieldName = elasticIndexField.getFldName();

        // Validate the expression
        if (value.isEmpty()) {
            return null;
        }
        if (Condition.IN_DICTIONARY.equals(condition)) {
            if (docRef == null || docRef.getUuid() == null) {
                throw new IllegalArgumentException("Dictionary not set for field: " + field);
            }
        }

        // Special case: if the expression is a wildcard, use the `exists` query
        if (value.equals("*")) {
            if (Condition.EQUALS.equals(condition)) {
                return QueryBuilders.exists(q -> q.field(fieldName));
            } else if (Condition.NOT_EQUALS.equals(condition)) {
                return QueryBuilders.bool()
                        .mustNot(QueryBuilders.exists(q -> q.field(fieldName)))
                        .build()._toQuery();
            }
        }

        // Create a query based on the field type and condition.
        final FieldType elasticFieldType = elasticIndexField.getFldType();
        if (elasticFieldType.equals(FieldType.ID) ||
                elasticFieldType.equals(FieldType.LONG) ||
                elasticFieldType.equals(FieldType.INTEGER)) {
            return buildScalarQuery(condition, elasticIndexField, fieldName, value, this::getNumber, docRef);
        } else if (elasticFieldType.equals(FieldType.FLOAT)) {
            return buildScalarQuery(condition, elasticIndexField, fieldName, value, this::getFloat, docRef);
        } else if (elasticFieldType.equals(FieldType.DOUBLE)) {
            return buildScalarQuery(condition, elasticIndexField, fieldName, value, this::getDouble, docRef);
        } else if (elasticFieldType.equals(FieldType.DATE)) {
            return buildScalarQuery(condition, elasticIndexField, fieldName, value, this::getDate, docRef);
        } else if (elasticFieldType.equals(FieldType.IPV4_ADDRESS)) {
            return buildScalarQuery(condition, elasticIndexField, fieldName, value, this::getIpV4Address, docRef);
        } else {
            return buildStringQuery(condition, value, docRef, elasticIndexField, fieldName);
        }
    }

    private Query buildStringQuery(final Condition condition,
                                   final String expression,
                                   final DocRef docRef,
                                   final ElasticIndexField indexField,
                                   final String fieldName) {
        final List<String> terms = tokenizeExpression(expression).filter(term -> !term.isEmpty()).toList();
        if (terms.isEmpty()) {
            return null;
        }

        final BiFunction<String, String, Query> buildQueryFn;
        if (Kind.Keyword.jsonValue().equals(indexField.getNativeType())) {
            // Elasticsearch field mapping type is `keyword`, so generate a term-level query
            buildQueryFn = this::buildKeywordQuery;
        } else {
            // This is a type other than `keyword`, such as `text` or `wildcard`. Perform a full-text match.
            buildQueryFn = this::buildTextQuery;
        }

        switch (condition) {
            case EQUALS -> {
                return buildQueryFn.apply(fieldName, expression);
            }
            case NOT_EQUALS -> {
                return negate(buildQueryFn.apply(fieldName, expression));
            }
            case MATCHES_REGEX -> {
                return QueryBuilders.regexp(q -> q
                        .field(fieldName)
                        .value(expression)
                );
            }
            case IN -> {
                if (terms.size() > 1) {
                    return BoolQuery.of(q -> q
                            .should(terms.stream()
                                    .map(term -> buildQueryFn.apply(fieldName, term))
                                    .collect(Collectors.toList()))
                    )._toQuery();
                } else {
                    return buildQueryFn.apply(fieldName, expression);
                }
            }
            case IN_DICTIONARY -> {
                return buildDictionaryQuery(condition, fieldName, docRef, indexField);
            }
            default -> throw new UnsupportedOperationException("Unsupported condition '" +
                    condition.getDisplayValue() + "' for " + indexField.getDisplayValue() +
                    " field type");
        }
    }

    /**
     * Creates an Elasticsearch query for a `keyword` field appropriate for the expression content
     */
    private Query buildKeywordQuery(final String fieldName, final String expression) {
        final Matcher quotedMatcher = QUOTED_PATTERN.matcher(expression);
        if (quotedMatcher.matches()) {
            // Expression is in quotes, so perform an exact match
            return QueryBuilders.term(q -> q
                    .field(fieldName)
                    .value(quotedMatcher.group(1)));
        } else if (WILDCARD_PATTERN.matcher(expression).matches()) {
            // Expression is a wildcard pattern, so return a wildcard query
            return QueryBuilders.wildcard(q -> q
                    .field(fieldName)
                    .value(expression)
                    .caseInsensitive(true));
        } else {
            // Perform an exact match on the provided term
            return QueryBuilders.term(q -> q
                    .field(fieldName)
                    .value(expression));
        }
    }

    private Query buildTextQuery(final String fieldName, final String expression) {
        // Contains wildcard chars, so use a query string query, which supports these
        return QueryBuilders.queryString(q -> q
                .fields(fieldName)
                .query(expression)
                .analyzeWildcard(true)
        );
    }

    private Query buildScalarQuery(
            final ExpressionTerm.Condition condition,
            final ElasticIndexField indexField,
            final String fieldName,
            final String rawValue,
            final TriFunction<Condition, String, String, FieldValue> valueParser,
            final DocRef docRef
    ) {
        FieldValue fieldValue;
        List<FieldValue> fieldValues;

        switch (condition) {
            case EQUALS -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return QueryBuilders
                        .term(q -> q
                                .field(fieldName)
                                .value(fieldValue));
            }
            case NOT_EQUALS -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return negate(QueryBuilders
                        .term(q -> q
                                .field(fieldName)
                                .value(fieldValue)));
            }
            case IN -> {
                fieldValues = tokenizeExpression(rawValue)
                        .map(val -> valueParser.apply(condition, fieldName, val))
                        .collect(Collectors.toList());
                return QueryBuilders
                        .terms(q -> q
                                .field(fieldName)
                                .terms(t -> t.value(fieldValues)));
            }
            case GREATER_THAN -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return QueryBuilders
                        .range(q -> q
                                .field(fieldName)
                                .gt(JsonData.of(fieldValue)));
            }
            case GREATER_THAN_OR_EQUAL_TO -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return QueryBuilders
                        .range(q -> q
                                .field(fieldName)
                                .gte(JsonData.of(fieldValue)));
            }
            case LESS_THAN -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return QueryBuilders
                        .range(q -> q
                                .field(fieldName)
                                .lt(JsonData.of(fieldValue)));
            }
            case LESS_THAN_OR_EQUAL_TO -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return QueryBuilders
                        .range(q -> q
                                .field(fieldName)
                                .lte(JsonData.of(fieldValue)));
            }
            case BETWEEN -> {
                fieldValues = tokenizeExpression(rawValue)
                        .map(val -> valueParser.apply(condition, fieldName, val))
                        .collect(Collectors.toList());
                if (fieldValues.size() != 2) {
                    throw new IllegalArgumentException(
                            "Two values needed for between query. Only " + fieldValues.size() + " provided");
                }
                return QueryBuilders
                        .range(q -> q
                                .field(fieldName)
                                .gte(JsonData.of(fieldValues.get(0)))
                                .lte(JsonData.of(fieldValues.get(1))));
            }
            case IN_DICTIONARY -> {
                return buildDictionaryQuery(condition, fieldName, docRef, indexField);
            }
            default -> throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for " +
                    indexField.getFldType().getDisplayValue() + " field type");
        }
    }

    private Query negate(final Query query) {
        return QueryBuilders.bool()
                .mustNot(query)
                .build()._toQuery();
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

    private FieldValue getDate(final Condition condition, final String fieldName, final String value) {
        return FieldValue.of(DateExpressionParser.getMs(fieldName, value, dateTimeSettings));
    }

    /**
     * Validates the format of an IPv4 address. CIDR notation is allowed (e.g. 192.168.1.1/24) for equality
     * conditions (EQUALS, IN and IN_DICTIONARY).
     */
    private FieldValue getIpV4Address(final Condition condition, final String fieldName, final String value) {
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
        return FieldValue.of(value);
    }

    private FieldValue getNumber(final Condition condition, final String fieldName, final String value) {
        try {
            return FieldValue.of(Long.parseLong(value));
        } catch (final NumberFormatException e) {
            throw new NumberFormatException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\""
            );
        }
    }

    private FieldValue getFloat(final Condition condition, final String fieldName, final String value) {
        try {
            return FieldValue.of(Float.parseFloat(value));
        } catch (final NumberFormatException e) {
            throw new NumberFormatException(
                    "Expected a decimal (float) value for field \"" + fieldName + "\" but was given string \"" +
                            value + "\""
            );
        }
    }

    private FieldValue getDouble(final Condition condition, final String fieldName, final String value) {
        try {
            return FieldValue.of(Double.parseDouble(value));
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
    private Query buildDictionaryQuery(
            final Condition condition,
            final String fieldName,
            final DocRef docRef,
            final ElasticIndexField indexField
    ) {
        final String[] lines = wordListProvider.getWords(docRef);
        final BoolQuery.Builder builder = new BoolQuery.Builder();

        for (final String line : lines) {
            final BoolQuery.Builder mustQueries = new BoolQuery.Builder();
            final FieldType elasticFieldType = indexField.getFldType();

            if (elasticFieldType.equals(FieldType.ID) ||
                    elasticFieldType.equals(FieldType.LONG) ||
                    elasticFieldType.equals(FieldType.INTEGER)) {
                mustQueries.must(QueryBuilders.term(q -> q
                        .field(fieldName)
                        .value(getNumber(condition, fieldName, line))));
            } else if (elasticFieldType.equals(FieldType.FLOAT)) {
                mustQueries.must(QueryBuilders.term(q -> q
                        .field(fieldName)
                        .value(getFloat(condition, fieldName, line))));
            } else if (elasticFieldType.equals(FieldType.DOUBLE)) {
                mustQueries.must(QueryBuilders.term(q -> q
                        .field(fieldName)
                        .value(getDouble(condition, fieldName, line))));
            } else if (elasticFieldType.equals(FieldType.DATE)) {
                mustQueries.must(QueryBuilders.term(q -> q
                        .field(fieldName)
                        .value(getDate(condition, fieldName, line))));
            } else if (elasticFieldType.equals(FieldType.IPV4_ADDRESS)) {
                mustQueries.must(QueryBuilders.term(q -> q
                        .field(fieldName)
                        .value(getIpV4Address(condition, fieldName, line))));
            } else if (indexField.getFldType().equals(FieldType.KEYWORD)) {
                mustQueries.must(buildKeywordQuery(fieldName, line));
            } else {
                mustQueries.must(buildTextQuery(fieldName, line));
            }

            builder.should(mustQueries.build()._toQuery());
        }

        return builder.build()._toQuery();
    }

    private boolean operatorHasChildren(final ExpressionOperator operator) {
        if (operator != null && operator.enabled() && operator.getChildren() != null) {
            for (final ExpressionItem child : operator.getChildren()) {
                if (child.enabled()) {
                    if (child instanceof final ExpressionOperator childOperator) {
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
