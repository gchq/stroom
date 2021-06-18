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

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Convert our query objects to an Elasticsearch Query DSL query.
 */
public class SearchExpressionQueryBuilder {
    private static final String DELIMITER = ",";
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
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

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

        // Try and find the referenced field.
        if (field == null || field.length() == 0) {
            throw new IllegalArgumentException("Field not set");
        }
        final ElasticIndexField indexField = indexFieldsMap.get(field);
        if (indexField == null) {
            throw new ResourceNotFoundException("Field not found in index: " + field);
        }
        final String fieldName = indexField.getFieldName();

        // Ensure an appropriate value has been provided for the condition type.
        if (Condition.IN_DICTIONARY.equals(condition)) {
            if (docRef == null || docRef.getUuid() == null) {
                throw new IllegalArgumentException("Dictionary not set for field: " + field);
            }
        } else {
            if (value == null || value.length() == 0) {
                return null;
            }
        }
        if (Condition.IS_DOC_REF.equals(condition)) {
            if (docRef == null || docRef.getUuid() == null) {
                throw new IllegalArgumentException("Doc Ref not set for field: " + field);
            }
        }

        // Create a query based on the field type and condition.
        final ElasticIndexFieldType elasticFieldType = indexField.getFieldUse();
        if (indexField.getFieldUse().isNumeric()) {
            return buildNumericQuery(condition, indexField, fieldName, value, this::getNumber, docRef);
        } else if (elasticFieldType.equals(ElasticIndexFieldType.FLOAT)) {
            return buildNumericQuery(condition, indexField, fieldName, value, this::getFloat, docRef);
        } else if (elasticFieldType.equals(ElasticIndexFieldType.DOUBLE)) {
            return buildNumericQuery(condition, indexField, fieldName, value, this::getDouble, docRef);
        } else if (elasticFieldType.equals(ElasticIndexFieldType.DATE)) {
            return buildNumericQuery(condition, indexField, fieldName, value, this::getDate, docRef);
        } else {
            // A string-based term
            switch (condition) {
                case EQUALS:
                    return QueryBuilders
                            .queryStringQuery(value)
                            .field(fieldName)
                            .analyzeWildcard(true);
                case CONTAINS:
                    // Note: This is redundant as `Term::setCondition` in `stroom-core-client` prevents this condition
                    // from being selected.

                    // All terms must match
                    BoolQueryBuilder mustQuery = QueryBuilders.boolQuery();
                    tokenizeExpression(value).forEach(term -> {
                        mustQuery.must(QueryBuilders.termQuery(fieldName, term));
                    });
                    return mustQuery;
                case IN:
                    // One or more terms must match
                    return QueryBuilders
                            .termsQuery(fieldName, tokenizeExpression(value));
                case IN_DICTIONARY:
                    return buildDictionaryQuery(fieldName, docRef, indexField);
                case IS_DOC_REF:
                    return QueryBuilders
                            .termQuery(fieldName, docRef.getUuid());
                default:
                    throw new RuntimeException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldUse().getDisplayValue() + " field type");
            }
        }
    }

    private <T extends Number> QueryBuilder buildNumericQuery(
            final ExpressionTerm.Condition condition, final ElasticIndexField indexField, final String fieldName,
            final String fieldValue, BiFunction<String, String, T> valueParser, final DocRef docRef
    ) {
        T numericValue;
        List<T> numericValues;

        switch (condition) {
            case EQUALS:
                numericValue = valueParser.apply(fieldName, fieldValue);
                return QueryBuilders
                        .termQuery(fieldName, numericValue);
            case CONTAINS:
            case IN:
                numericValues = tokenizeExpression(fieldValue)
                        .map(val -> valueParser.apply(fieldName, val))
                        .collect(Collectors.toList());
                return QueryBuilders
                        .termsQuery(fieldName, numericValues);
            case GREATER_THAN:
                numericValue = valueParser.apply(fieldName, fieldValue);
                return QueryBuilders
                        .rangeQuery(fieldName)
                        .gt(numericValue);
            case GREATER_THAN_OR_EQUAL_TO:
                numericValue = valueParser.apply(fieldName, fieldValue);
                return QueryBuilders
                        .rangeQuery(fieldName)
                        .gte(numericValue);
            case LESS_THAN:
                numericValue = valueParser.apply(fieldName, fieldValue);
                return QueryBuilders
                        .rangeQuery(fieldName)
                        .lt(numericValue);
            case LESS_THAN_OR_EQUAL_TO:
                numericValue = valueParser.apply(fieldName, fieldValue);
                return QueryBuilders
                        .rangeQuery(fieldName)
                        .lte(numericValue);
            case BETWEEN:
                numericValues = tokenizeExpression(fieldValue)
                        .map(val -> valueParser.apply(fieldName, val))
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
                return buildDictionaryQuery(fieldName, docRef, indexField);
            default:
                throw new RuntimeException("Unexpected condition '" + condition.getDisplayValue() + "' for " +
                        indexField.getFieldUse().getDisplayValue() + " field type");
        }
    }

    /**
     * Split an expression into is component terms. Useful for extracting terms for use with "in" or "contains"
     * conditions
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

    private Long getDate(final String fieldName, final String value) {
        try {
            // Empty optional will be caught below
            return DateExpressionParser.parse(value, dateTimeSettings, nowEpochMilli).get().toInstant().toEpochMilli();
        } catch (final Exception e) {
            throw new IllegalArgumentException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

    private Long getNumber(final String fieldName, final String value) {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            throw new NumberFormatException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\""
            );
        }
    }

    private Float getFloat(final String fieldName, final String value) {
        try {
            return Float.parseFloat(value);
        } catch (final NumberFormatException e) {
            throw new NumberFormatException(
                    "Expected a decimal (float) value for field \"" + fieldName + "\" but was given string \"" +
                    value + "\""
            );
        }
    }

    private Double getDouble(final String fieldName, final String value) {
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
     * Loads the specified Dictionary from the doc store.
     * For each line, constructs an AND query for all terms on that line.
     * All lines are combined into an OR query.
     */
    private QueryBuilder buildDictionaryQuery(
            final String fieldName,
            final DocRef docRef,
            final ElasticIndexField indexField
    ) {
        final String[] lines = readDictLines(docRef);

        final BoolQueryBuilder builder = QueryBuilders.boolQuery();

        for (final String line : lines) {
            BoolQueryBuilder mustQuery = QueryBuilders.boolQuery();
            final ElasticIndexFieldType elasticFieldType = indexField.getFieldUse();

            if (elasticFieldType.isNumeric()) {
                tokenizeExpression(line)
                        .map(val -> getNumber(fieldName, val))
                        .forEach(number -> mustQuery.must(QueryBuilders.termQuery(fieldName, number)));
            } else if (elasticFieldType.equals(ElasticIndexFieldType.FLOAT)) {
                tokenizeExpression(line)
                        .map(val -> getFloat(fieldName, val))
                        .forEach(number -> mustQuery.must(QueryBuilders.termQuery(fieldName, number)));
            } else if (elasticFieldType.equals(ElasticIndexFieldType.DOUBLE)) {
                tokenizeExpression(line)
                        .map(val -> getDouble(fieldName, val))
                        .forEach(number -> mustQuery.must(QueryBuilders.termQuery(fieldName, number)));
            } else if (ElasticIndexFieldType.DATE.equals(indexField.getFieldUse())) {
                tokenizeExpression(line)
                        .map(val -> getDate(fieldName, val))
                        .forEach(number -> mustQuery.must(QueryBuilders.termQuery(fieldName, number)));
            } else {
                tokenizeExpression(line)
                        .forEach(term -> mustQuery.must(QueryBuilders.termQuery(fieldName, term)));
            }

            builder.should(mustQuery);
        }

        return builder;
    }

    private String[] readDictLines(final DocRef docRef) {
        final String words = wordListProvider.getCombinedData(docRef);
        if (words == null) {
            throw new ResourceNotFoundException("Dictionary \"" + docRef + "\" not found");
        }

        return words.trim().split("\n");
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
