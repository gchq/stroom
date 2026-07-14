/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.ai.api.AiService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.util.functions.TriFunction;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.mapping.Property.Kind;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.UntypedRangeQuery;
import co.elastic.clients.json.JsonData;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.inject.Provider;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Convert our query objects to an Elasticsearch Query DSL query.
 */
public class SearchExpressionQueryBuilder {

    private static final String DELIMITER = ",";
    private static final String FIELD_PATH_SEPARATOR = ".";
    private static final Pattern FIELD_PATH_SPLIT_PATTERN = Pattern.compile("\\.");
    private static final Pattern WILDCARD_PATTERN = Pattern.compile(".*[*?].*");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("^\"(.+)\"$");
    private static final Pattern IPV4_ADDRESS_PATTERN =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?:/\\d{1,2})?$");
    private static final Pattern IPV4_CIDR_PATTERN =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+\\.\\d+/\\d{1,2}$");
    private final Provider<AiService> aiServiceProvider;
    private final ElasticIndexDoc indexDoc;
    private final IndexFieldCache indexFieldCache;
    private final WordListProvider wordListProvider;
    private final DateTimeSettings dateTimeSettings;
    private final ElasticQueryParams elasticQueryParams;

    public SearchExpressionQueryBuilder(
            final Provider<AiService> aiServiceProvider,
            final ElasticIndexDoc indexDoc,
            final IndexFieldCache indexFieldCache,
            final WordListProvider wordListProvider,
            final DateTimeSettings dateTimeSettings) {
        this.aiServiceProvider = aiServiceProvider;
        this.indexDoc = indexDoc;
        this.indexFieldCache = indexFieldCache;
        this.wordListProvider = wordListProvider;
        this.dateTimeSettings = dateTimeSettings;
        this.elasticQueryParams = new ElasticQueryParams();
    }

    public ElasticQueryParams buildQuery(final ExpressionOperator expression) {
        Query query = null;
        if (expression != null) {
            query = getQuery(expression);
        }
        if (query == null) {
            query = MatchAllQuery.of(q -> q)._toQuery();
        }

        elasticQueryParams.setQuery(query);
        return elasticQueryParams;
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

                    final BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
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
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("Field not set");
        }
        final IndexField indexField = indexFieldCache.get(indexDoc.asDocRef(), field);
        if (!(indexField instanceof final ElasticIndexField elasticIndexField)) {
            throw new SearchException("Field not found in index: " + field);
        }
        final String fieldName = elasticIndexField.getFldName();

        // Resolve the ordered list of `nested` object paths (outermost first) that this field lives
        // under. This is computed once per term and threaded through query construction so that the
        // leaf query can be wrapped in one Elasticsearch `nested` query per level of nesting.
        final List<String> nestedPaths = getNestedPaths(fieldName);

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
            if (Condition.EQUALS.equals(condition)) {
                return wrapInNested(nestedPaths, QueryBuilders.exists(q -> q.field(fieldName)));
            } else if (Condition.NOT_EQUALS.equals(condition)) {
                return negate(wrapInNested(nestedPaths, QueryBuilders.exists(q -> q.field(fieldName))));
            }
        }

        // Create a query based on the field type and condition.
        final FieldType elasticFieldType = elasticIndexField.getFldType();
        if (elasticFieldType.equals(FieldType.ID) ||
            elasticFieldType.equals(FieldType.LONG) ||
            elasticFieldType.equals(FieldType.INTEGER)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getNumber, docRef);
        } else if (elasticFieldType.equals(FieldType.FLOAT)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getFloat, docRef);
        } else if (elasticFieldType.equals(FieldType.DOUBLE)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getDouble, docRef);
        } else if (elasticFieldType.equals(FieldType.DATE)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getDate, docRef);
        } else if (elasticFieldType.equals(FieldType.IPV4_ADDRESS)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getIpV4Address, docRef);
        } else {
            return buildStringQuery(condition, value, docRef, elasticIndexField, fieldName, nestedPaths);
        }
    }

    private Query buildStringQuery(final Condition condition,
                                   final String expression,
                                   final DocRef docRef,
                                   final ElasticIndexField indexField,
                                   final String fieldName,
                                   final List<String> nestedPaths) {
        final List<String> terms = tokenizeExpression(expression).filter(term -> !term.isEmpty()).toList();
        if (terms.isEmpty()) {
            return null;
        }

        final BiFunction<String, String, Query> buildQueryFn;
        if (Kind.Keyword.jsonValue().equals(indexField.getNativeType())) {
            // Elasticsearch field mapping type is `keyword`, so generate a term-level query
            buildQueryFn = this::buildKeywordQuery;
        } else if (Kind.DenseVector.jsonValue().equals(indexField.getNativeType())) {
            // Type is `dense_vector`, so generate a `knn` query
            buildQueryFn = this::buildDenseVectorQuery;
        } else {
            // This is a type other than `keyword`, such as `text` or `wildcard`. Perform a full-text match.
            buildQueryFn = this::buildTextQuery;
        }

        switch (condition) {
            case EQUALS -> {
                return wrapInNested(nestedPaths, buildQueryFn.apply(fieldName, expression));
            }
            case NOT_EQUALS -> {
                return negate(wrapInNested(nestedPaths, buildQueryFn.apply(fieldName, expression)));
            }
            case MATCHES_REGEX -> {
                return wrapInNested(nestedPaths, QueryBuilders.regexp(q -> q
                        .field(fieldName)
                        .value(expression)
                ));
            }
            case IN -> {
                final Query inQuery;
                if (terms.size() > 1) {
                    inQuery = BoolQuery.of(q -> q
                            .should(terms.stream()
                                    .map(term -> buildQueryFn.apply(fieldName, term))
                                    .toList())
                    )._toQuery();
                } else {
                    inQuery = buildQueryFn.apply(fieldName, expression);
                }
                return wrapInNested(nestedPaths, inQuery);
            }
            case IN_DICTIONARY -> {
                return buildDictionaryQuery(condition, fieldName, docRef, indexField, nestedPaths);
            }
            default -> throw new UnsupportedOperationException(
                    "Unsupported condition '" + condition.getDisplayValue() + "' for " + indexField.getDisplayValue() +
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

    private Query buildDenseVectorQuery(final String fieldName, final String expression) {
        if (indexDoc.getVectorGenerationModelRef() == null) {
            throw new IllegalArgumentException("Vector embedding model is not defined in data source " +
                                               indexDoc.getName());
        }

        // Query the embeddings API for a vector representation of the query expression
        final OpenAIModelDoc modelDoc = aiServiceProvider.get().getOpenAIModelDoc(
                indexDoc.getVectorGenerationModelRef());
        try {
            final EmbeddingModel embeddingModel = aiServiceProvider.get().getEmbeddingModel(modelDoc);
            final List<Float> queryVector = embeddingModel.embed(expression).content().vectorAsList();
            elasticQueryParams.addKnnFieldQuery(fieldName, expression);
            return QueryBuilders.knn(q -> q
                    .field(fieldName)
                    .queryVector(queryVector));
        } catch (final Exception e) {
            throw new RuntimeException("Failed to create vector embeddings for field " + fieldName + ". " +
                                       e.getMessage(), e);
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
            final List<String> nestedPaths,
            final String rawValue,
            final TriFunction<Condition, String, String, FieldValue> valueParser,
            final DocRef docRef
    ) {
        final FieldValue fieldValue;
        final List<FieldValue> fieldValues;

        switch (condition) {
            case EQUALS -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return wrapInNested(nestedPaths, QueryBuilders
                        .term(q -> q
                                .field(fieldName)
                                .value(fieldValue)));
            }
            case NOT_EQUALS -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return negate(wrapInNested(nestedPaths, QueryBuilders
                        .term(q -> q
                                .field(fieldName)
                                .value(fieldValue))));
            }
            case IN -> {
                fieldValues = tokenizeExpression(rawValue)
                        .map(val -> valueParser.apply(condition, fieldName, val))
                        .toList();
                return wrapInNested(nestedPaths, QueryBuilders
                        .terms(q -> q
                                .field(fieldName)
                                .terms(t -> t.value(fieldValues))));
            }
            case GREATER_THAN -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return wrapInNested(nestedPaths, QueryBuilders
                        .range(q -> q.untyped(UntypedRangeQuery.of(r -> r
                                .field(fieldName)
                                .gt(JsonData.of(fieldValue)))
                        )));
            }
            case GREATER_THAN_OR_EQUAL_TO -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return wrapInNested(nestedPaths, QueryBuilders
                        .range(q -> q.untyped(UntypedRangeQuery.of(r -> r
                                .field(fieldName)
                                .gte(JsonData.of(fieldValue)))
                        )));
            }
            case LESS_THAN -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return wrapInNested(nestedPaths, QueryBuilders
                        .range(q -> q.untyped(UntypedRangeQuery.of(r -> r
                                .field(fieldName)
                                .lt(JsonData.of(fieldValue)))
                        )));
            }
            case LESS_THAN_OR_EQUAL_TO -> {
                fieldValue = valueParser.apply(condition, fieldName, rawValue);
                return wrapInNested(nestedPaths, QueryBuilders
                        .range(q -> q.untyped(UntypedRangeQuery.of(r -> r
                                .field(fieldName)
                                .lte(JsonData.of(fieldValue)))
                        )));
            }
            case BETWEEN -> {
                fieldValues = tokenizeExpression(rawValue)
                        .map(val -> valueParser.apply(condition, fieldName, val))
                        .toList();
                if (fieldValues.size() != 2) {
                    throw new IllegalArgumentException(
                            "Two values needed for between query. Only " + fieldValues.size() + " provided");
                }
                return wrapInNested(nestedPaths, QueryBuilders
                        .range(q -> q.untyped(UntypedRangeQuery.of(r -> r
                                .field(fieldName)
                                .gte(JsonData.of(fieldValues.get(0)))
                                .lte(JsonData.of(fieldValues.get(1))))
                        )));
            }
            case IN_DICTIONARY -> {
                return buildDictionaryQuery(condition, fieldName, docRef, indexField, nestedPaths);
            }
            default -> throw new SearchException("Unexpected condition '" + condition.getDisplayValue() + "' for " +
                                                 indexField.getFldType().getDisplayValue() + " field type");
        }
    }

    private Query negate(final Query query) {
        // To enable SQL style functionality we have to tell Elastic to match all except the specified term.
        return QueryBuilders.bool()
                .should(MatchAllQuery.of(q -> q)._toQuery())
                .mustNot(query)
                .build()._toQuery();
    }

    /**
     * Determines the ordered list of Elasticsearch `nested` object paths that a field lives under,
     * from outermost to innermost.
     * <p>
     * For a field {@code a.b.c.d} where {@code a}, {@code a.b} and {@code a.b.c} are all mapped as
     * `nested` types, this returns {@code [a, a.b, a.b.c]}. Only the ancestor path segments are
     * inspected (the leaf segment {@code d} is the scalar/text field being queried and is never a
     * nesting level itself). Ancestors that are not mapped as `nested` (for example plain `object`
     * fields, which do not require a nested query) are skipped.
     *
     * @param fieldName the fully qualified (dot-delimited) field name
     * @return the nesting paths in outermost-first order, or an empty list if the field is not nested
     */
    private List<String> getNestedPaths(final String fieldName) {
        if (fieldName == null || !fieldName.contains(FIELD_PATH_SEPARATOR)) {
            return Collections.emptyList();
        }

        final String[] segments = FIELD_PATH_SPLIT_PATTERN.split(fieldName);
        final List<String> nestedPaths = new ArrayList<>();
        final StringBuilder pathBuilder = new StringBuilder();

        // Inspect each ancestor path (i.e. every prefix except the leaf field itself) and collect
        // those that are mapped as `nested`. This naturally supports arbitrarily deep nesting.
        for (int i = 0; i < segments.length - 1; i++) {
            if (i > 0) {
                pathBuilder.append(FIELD_PATH_SEPARATOR);
            }
            pathBuilder.append(segments[i]);

            final String path = pathBuilder.toString();
            if (isNestedField(path)) {
                nestedPaths.add(path);
            }
        }

        return nestedPaths;
    }

    /**
     * @return true if the field at the given path is mapped in Elasticsearch as a `nested` type.
     */
    private boolean isNestedField(final String path) {
        final IndexField indexField = indexFieldCache.get(indexDoc.asDocRef(), path);
        return indexField instanceof final ElasticIndexField elasticIndexField &&
               Kind.Nested.jsonValue().equals(elasticIndexField.getNativeType());
    }

    /**
     * Convenience overload that resolves the nesting paths for the given field and wraps the query.
     *
     * @see #wrapInNested(List, Query)
     */
    private Query wrapInNested(final String fieldName, final Query query) {
        return wrapInNested(getNestedPaths(fieldName), query);
    }

    /**
     * Wraps a leaf query in one Elasticsearch `nested` query per level of nesting the field is
     * subject to, supporting arbitrarily deep nesting by chaining `nested` queries.
     * <p>
     * Given nesting paths {@code [a, a.b]} and a leaf query {@code Q}, this produces:
     * <pre>
     *   nested(path=a, query=
     *       nested(path=a.b, query=Q))
     * </pre>
     * The paths are supplied outermost-first, so wrapping is applied from the innermost path
     * outwards to preserve that structure. If the field is not nested, the leaf query is returned
     * unchanged, keeping behaviour identical for non-nested fields.
     *
     * @param nestedPaths the nesting paths in outermost-first order (see {@link #getNestedPaths})
     * @param query       the leaf query to wrap
     * @return the (possibly wrapped) query
     */
    private Query wrapInNested(final List<String> nestedPaths, final Query query) {
        if (nestedPaths == null || nestedPaths.isEmpty()) {
            return query;
        }

        Query wrapped = query;
        // Wrap from the innermost path outwards so that the outermost `nested` query is applied last.
        for (int i = nestedPaths.size() - 1; i >= 0; i--) {
            final String path = nestedPaths.get(i);
            final Query inner = wrapped;
            wrapped = QueryBuilders.nested(n -> n
                    .path(path)
                    .query(inner));
        }

        return wrapped;
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
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid IPv4 address format: " + value + " for field \"" +
                                               fieldName + "\"");
        }

        if (!condition.equals(Condition.EQUALS) && !condition.equals(Condition.IN) &&
            !condition.equals(Condition.IN_DICTIONARY) && IPV4_CIDR_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("CIDR notation is only supported for EQUALS and IN operators. " +
                                               "Value provided: \"" + value + "\". Operator: \"" +
                                               condition.getDisplayValue() + "\"");
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
     * <p>
     * When the field is nested, the resulting combined query is wrapped in the appropriate chain of
     * `nested` queries. All lines target the same field (and therefore the same nesting paths), so a
     * single outer wrap is applied to the combined query.
     */
    private Query buildDictionaryQuery(
            final Condition condition,
            final String fieldName,
            final DocRef docRef,
            final ElasticIndexField indexField,
            final List<String> nestedPaths
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

        return wrapInNested(nestedPaths, builder.build()._toQuery());
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
