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

import stroom.dictionary.server.DictionaryStore;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Convert our query objects to an Elasticsearch Query DSL query.
 */
public class SearchExpressionQueryBuilder {
    private static final String DELIMITER = ",";
    private final Map<String, ElasticIndexField> indexFieldsMap;
    private final DictionaryStore dictionaryStore;
    private final String timeZoneId;
    private final long nowEpochMilli;

    public SearchExpressionQueryBuilder(final DictionaryStore dictionaryStore,
                                        final Map<String, ElasticIndexField> indexFieldsMap,
                                        final String timeZoneId,
                                        final long nowEpochMilli
    ) {
        this.dictionaryStore = dictionaryStore;
        this.indexFieldsMap = indexFieldsMap;
        this.timeZoneId = timeZoneId;
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
            }
            else if (item instanceof ExpressionOperator) {
                // Create queries for expression tree nodes.
                final ExpressionOperator operator = (ExpressionOperator) item;
                if (operatorHasChildren(operator)) {
                    final List<QueryBuilder> innerChildQueries = operator.getChildren().stream()
                        .map(this::getQuery)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
                    switch (operator.getOp()) {
                        case AND:
                            innerChildQueries.forEach(boolQueryBuilder::must);
                            break;
                        case OR:
                            innerChildQueries.forEach(boolQueryBuilder::should);
                            break;
                        case NOT:
                            innerChildQueries.forEach(boolQueryBuilder::mustNot);
                            break;
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
        if (indexField.getFieldUse().isNumeric()) {
            long valueAsNum = getNumber(fieldName, value);
            switch (condition) {
                case EQUALS:
                    return QueryBuilders
                            .termQuery(fieldName, valueAsNum);
                case CONTAINS:
                case IN:
                    return QueryBuilders
                            .termsQuery(fieldName, tokenizeExpression(value));
                case GREATER_THAN:
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .gt(valueAsNum);
                case GREATER_THAN_OR_EQUAL_TO:
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .gte(valueAsNum);
                case LESS_THAN:
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .lt(valueAsNum);
                case LESS_THAN_OR_EQUAL_TO:
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .lte(valueAsNum);
                case BETWEEN:
                    final Long[] between = getNumbers(fieldName, value);
                    if (between.length != 2) {
                        throw new IllegalArgumentException("Two numbers needed for between query; " + between.length + " provided");
                    }
                    if (between[0] >= between[1]) {
                        throw new IllegalArgumentException("From number must be lower than to number");
                    }
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .gte(between[0])
                            .lte(between[1]);
                case IN_DICTIONARY:
                    return buildDictionaryQuery(fieldName, docRef, indexField);
                default:
                    throw new ElasticsearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldUse().getDisplayValue() + " field type");
            }
        } else if (ElasticIndexFieldType.DATE.equals(indexField.getFieldUse())) {
            final Long valueAsDate = getDate(fieldName, value);
            switch (condition) {
                case EQUALS:
                    return QueryBuilders
                            .termQuery(fieldName, valueAsDate);
                case CONTAINS:
                case IN:
                    final long[] dates = getDates(fieldName, value);
                    return QueryBuilders
                            .termsQuery(fieldName, dates);
                case GREATER_THAN:
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .gt(valueAsDate);
                case GREATER_THAN_OR_EQUAL_TO:
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .gte(valueAsDate);
                case LESS_THAN:
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .lt(valueAsDate);
                case LESS_THAN_OR_EQUAL_TO:
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .lte(valueAsDate);
                case BETWEEN:
                    final long[] between = getDates(fieldName, value);
                    if (between.length != 2) {
                        throw new IllegalArgumentException("Two dates needed for between query; " + between.length + " provided");
                    }
                    if (between[0] >= between[1]) {
                        throw new IllegalArgumentException("From date must occur before to date");
                    }
                    return QueryBuilders
                            .rangeQuery(fieldName)
                            .gte(between[0])
                            .lte(between[1]);
                case IN_DICTIONARY:
                    return buildDictionaryQuery(fieldName, docRef, indexField);
                default:
                    throw new ElasticsearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldUse().getDisplayValue() + " field type");
            }
        } else {
            switch (condition) {
                case EQUALS:
                    return QueryBuilders
                            .queryStringQuery(value)
                            .field(fieldName)
                            .analyzeWildcard(true);
                case CONTAINS:
                    // All terms must match
                    BoolQueryBuilder mustQuery = QueryBuilders.boolQuery();
                    String[] terms = tokenizeExpression(value);
                    for (final String term : terms) {
                        mustQuery.must(
                            QueryBuilders.termQuery(fieldName, term)
                        );
                    }
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
                    throw new ElasticsearchException("Unexpected condition '" + condition.getDisplayValue() + "' for "
                            + indexField.getFieldUse().getDisplayValue() + " field type");
            }
        }
    }

    /**
     * Split an expression into is component terms. Useful for extracting terms for use with "in" or "contains" conditions
     * @param expression - Example: "1,2, 3"
     * @return - Array of terms. Example: [ "1", "2", "3" ]
     */
    private String[] tokenizeExpression(final String expression) {
        if (expression != null) {
            return Arrays.stream(expression.split(DELIMITER))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toArray(String[]::new);
        }
        else {
            return new String[]{ };
        }
    }

    /**
     * Tokenize an expression and convert each item to a numeric `long`
     */
    private Long[] getNumbers(final String fieldName, final String expr) {
        return Arrays.stream(tokenizeExpression(expr))
            .map(token -> getNumber(fieldName, token))
            .toArray(Long[]::new);
    }

    /**
     * Tokenize an expression and convert each item to a date `long`
     */
    private long[] getDates(final String fieldName, final String expr) {
        return Arrays.stream(tokenizeExpression(expr))
            .mapToLong(token -> getDate(fieldName, token))
            .toArray();
    }

    private long getDate(final String fieldName, final String value) {
        try {
            // Empty optional will be caught below
            return DateExpressionParser.parse(value, timeZoneId, nowEpochMilli).get().toInstant().toEpochMilli();
        } catch (final Exception e) {
            throw new IllegalArgumentException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

    private long getNumber(final String fieldName, final String value) {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException e) {
            throw new NumberFormatException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\""
            );
        }
    }

    /**
     * Loads the specified Dictionary from the doc store.
     * For each line, constructs an AND query for all terms on that line.
     * All lines are combined into an OR query.
     */
    private QueryBuilder buildDictionaryQuery(final String fieldName, final DocRef docRef, final ElasticIndexField indexField) {
        final String[] lines = readDictLines(docRef);

        final BoolQueryBuilder builder = QueryBuilders.boolQuery();

        for (final String line : lines) {
            BoolQueryBuilder mustQuery = QueryBuilders.boolQuery();

            if (indexField.getFieldUse().isNumeric()) {
                final Long[] numbers = getNumbers(fieldName, line);
                for (final Long number : numbers) {
                    mustQuery.must(
                        QueryBuilders.termQuery(fieldName, number)
                    );
                }
            }
            else if (ElasticIndexFieldType.DATE.equals(indexField.getFieldUse())) {
                final long[] dates = getDates(fieldName, line);
                for (final Long date : dates) {
                    mustQuery.must(
                        QueryBuilders.termQuery(fieldName, date)
                    );
                }
            }
            else {
                final String[] terms = tokenizeExpression(line);
                for (final String term : terms) {
                    mustQuery.must(
                        QueryBuilders.termQuery(fieldName, term)
                    );
                }
            }

            builder.should(mustQuery);
        }

        return builder;
    }

    private String[] readDictLines(final DocRef docRef) {
        final String words = dictionaryStore.getCombinedData(docRef);
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
