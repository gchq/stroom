/*
 * Copyright 2021 Crown Copyright
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    // The largest value of an Elasticsearch `unsigned_long` field, 2^64 - 1
    private static final BigInteger UNSIGNED_LONG_MAX = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);
    private static final Pattern WILDCARD_PATTERN = Pattern.compile(".*[*?].*");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("^\"(.+)\"$");
    private static final Pattern IPV4_ADDRESS_PATTERN =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?:/(\\d{1,2}))?$");
    private static final int IPV4_OCTET_COUNT = 4;
    private static final int IPV4_MAX_OCTET = 255;
    private static final int IPV4_MAX_PREFIX_LENGTH = 32;
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
            query = getQuery(expression, List.of());
        }
        if (query == null) {
            query = MatchAllQuery.of(q -> q)._toQuery();
        }

        elasticQueryParams.setQuery(query);
        return elasticQueryParams;
    }

    /**
     * Creates the query for an expression item.
     *
     * @param item           the expression term or operator
     * @param enclosingPaths the `nested` object paths (outermost first) that the query will be placed inside,
     *                       which the item's terms must not wrap themselves in again
     */
    private Query getQuery(final ExpressionItem item, final List<String> enclosingPaths) {
        if (item.enabled()) {
            if (item instanceof final ExpressionTerm term) {
                // Create queries for single terms.
                return getTermQuery(term, enclosingPaths);
            } else if (item instanceof final ExpressionOperator operator) {
                // Create queries for expression tree nodes.
                if (operatorHasChildren(operator)) {
                    final List<ExpressionItem> children = operator.getChildren().stream()
                            .filter(ExpressionItem::enabled)
                            .toList();

                    final BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
                    final Op op = operator.getOp();
                    if (op == null || op.equals(Op.AND)) {
                        getConjunctionQueries(children, enclosingPaths).forEach(boolQueryBuilder::must);
                    } else if (op.equals(Op.OR)) {
                        // Grouping the terms of a nested object makes no difference to an OR, so don't
                        children.stream()
                                .map(child -> getQuery(child, enclosingPaths))
                                .filter(Objects::nonNull)
                                .forEach(boolQueryBuilder::should);
                    } else if (op.equals(Op.NOT)) {
                        // NOT matches what matches none of its children, as for other index types. To exclude
                        // only what matches all of them, e.g. one nested object matching all of them, the
                        // children go in an AND within the NOT.
                        children.stream()
                                .map(child -> getQuery(child, enclosingPaths))
                                .filter(Objects::nonNull)
                                .forEach(boolQueryBuilder::mustNot);
                    }
                    return boolQueryBuilder.build()._toQuery();
                }
            }
        }

        return null;
    }

    /**
     * Creates the queries for items that must all match. Items whose terms are all fields of the same `nested`
     * object are grouped into a single `nested` query, so that they must all match the same object, e.g.
     * `Users.Name = alice AND Users.Role = admin` matches a user who is both alice and an admin, not just a
     * document with a user called alice and an admin user. Within a group, a `!=` term applies to the same
     * object too, e.g. `Users.Name = alice AND Users.Role != admin` matches a user who is alice and not an admin.
     * A single item is not grouped, so `Users.Role != admin` on its own still matches documents with no admin
     * user. Grouping is repeated within each group for objects nested within the grouped object.
     *
     * @param items          the items that must all match
     * @param enclosingPaths the `nested` object paths (outermost first) that the queries will be placed inside
     * @return the queries, all of which must match
     */
    private List<Query> getConjunctionQueries(final List<ExpressionItem> items, final List<String> enclosingPaths) {
        final List<Query> queries = new ArrayList<>();
        // The items of each nested object directly within the enclosing one, keyed by the object's path
        final Map<String, List<ExpressionItem>> nestedGroups = new LinkedHashMap<>();
        for (final ExpressionItem item : items) {
            final String nestedPath = getNextNestedPath(item, enclosingPaths);
            if (nestedPath != null) {
                nestedGroups.computeIfAbsent(nestedPath, k -> new ArrayList<>()).add(item);
            } else {
                final Query query = getQuery(item, enclosingPaths);
                if (query != null) {
                    queries.add(query);
                }
            }
        }

        nestedGroups.forEach((nestedPath, groupItems) -> {
            if (groupItems.size() == 1) {
                // Nothing to match within the same object, so leave a single item to wrap itself, keeping the
                // negation of a single `!=` term outside the nested query, i.e. no object has the value
                final Query query = getQuery(groupItems.getFirst(), enclosingPaths);
                if (query != null) {
                    queries.add(query);
                }
                return;
            }
            final List<String> groupEnclosingPaths = new ArrayList<>(enclosingPaths);
            groupEnclosingPaths.add(nestedPath);
            final List<Query> groupQueries = getConjunctionQueries(groupItems, groupEnclosingPaths);
            if (!groupQueries.isEmpty()) {
                final Query groupQuery = groupQueries.size() == 1
                        ? groupQueries.getFirst()
                        : BoolQuery.of(b -> b.must(groupQueries))._toQuery();
                queries.add(QueryBuilders.nested(n -> n
                        .path(nestedPath)
                        .query(groupQuery)));
            }
        });
        return queries;
    }

    /**
     * Returns the path of the `nested` object, directly within the enclosing paths, that every term of an
     * expression item is a field of (at any depth), or null if there isn't one. Disabled items are ignored.
     */
    private String getNextNestedPath(final ExpressionItem item, final List<String> enclosingPaths) {
        if (!item.enabled()) {
            return null;
        }
        if (item instanceof final ExpressionTerm term) {
            final String field = term.getField();
            if (field == null) {
                return null;
            }
            final IndexField indexField = indexFieldCache.get(indexDoc.asDocRef(), field.trim());
            if (!(indexField instanceof final ElasticIndexField elasticIndexField)) {
                return null;
            }
            final List<String> nestedPaths = getRemainingNestedPaths(
                    getNestedPaths(elasticIndexField.getFldName()), enclosingPaths);
            return nestedPaths.isEmpty()
                    ? null
                    : nestedPaths.getFirst();
        } else if (item instanceof final ExpressionOperator operator && operator.getChildren() != null) {
            String commonPath = null;
            for (final ExpressionItem child : operator.getChildren()) {
                if (child.enabled()) {
                    final String childPath = getNextNestedPath(child, enclosingPaths);
                    if (childPath == null || (commonPath != null && !commonPath.equals(childPath))) {
                        return null;
                    }
                    commonPath = childPath;
                }
            }
            return commonPath;
        }
        return null;
    }

    /**
     * Returns the nested paths of a field that are not already applied by the enclosing paths.
     */
    private static List<String> getRemainingNestedPaths(final List<String> nestedPaths,
                                                        final List<String> enclosingPaths) {
        if (enclosingPaths.isEmpty()
            || nestedPaths.size() < enclosingPaths.size()
            || !nestedPaths.subList(0, enclosingPaths.size()).equals(enclosingPaths)) {
            return nestedPaths;
        }
        return nestedPaths.subList(enclosingPaths.size(), nestedPaths.size());
    }

    private Query getTermQuery(final ExpressionTerm expressionTerm, final List<String> enclosingPaths) {
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
        // under, other than those the query is already inside. This is computed once per term and threaded
        // through query construction so that the leaf query can be wrapped in one Elasticsearch `nested`
        // query per remaining level of nesting.
        final List<String> nestedPaths = getRemainingNestedPaths(getNestedPaths(fieldName), enclosingPaths);

        // A boolean field is only compared with one of its two values
        if (Kind.Boolean.jsonValue().equals(elasticIndexField.getNativeType())
            && !Condition.EQUALS.equals(condition)
            && !Condition.NOT_EQUALS.equals(condition)) {
            throw new UnsupportedOperationException(
                    "Unsupported condition '" + condition.getDisplayValue() + "' for " +
                    elasticIndexField.getFldType().getDisplayValue() + " field type");
        }

        // Validate the expression. For IN_DICTIONARY the dictionary reference, not the value, defines
        // the terms to match, so build the dictionary query even if no value has been provided.
        if (Condition.IN_DICTIONARY.equals(condition)) {
            if (docRef == null || docRef.getUuid() == null) {
                throw new IllegalArgumentException("Dictionary not set for field: " + field);
            }
            return buildDictionaryQuery(condition, fieldName, docRef, elasticIndexField, nestedPaths);
        }
        if (value == null || value.isEmpty()) {
            return null;
        }

        // Special case: if the expression is a wildcard, use the `exists` query
        if (value.equals("*")) {
            if (Condition.EQUALS.equals(condition)) {
                return wrapInNested(nestedPaths, QueryBuilders.exists(q -> q.field(fieldName)));
            } else if (Condition.NOT_EQUALS.equals(condition)) {
                return negate(wrapInNested(nestedPaths, QueryBuilders.exists(q -> q.field(fieldName))));
            }
        }

        // Create a query based on the field type and condition. Some native types need values parsed differently
        // to the other native types of the same field type, so check those first.
        final String nativeType = elasticIndexField.getNativeType();
        final FieldType elasticFieldType = elasticIndexField.getFldType();
        if (Kind.UnsignedLong.jsonValue().equals(nativeType)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getUnsignedLong);
        } else if (Kind.Version.jsonValue().equals(nativeType)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getVersion);
        } else if (elasticFieldType.equals(FieldType.ID) ||
            elasticFieldType.equals(FieldType.LONG) ||
            elasticFieldType.equals(FieldType.INTEGER)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getNumber);
        } else if (elasticFieldType.equals(FieldType.FLOAT)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getFloat);
        } else if (elasticFieldType.equals(FieldType.DOUBLE)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getDouble);
        } else if (elasticFieldType.equals(FieldType.DATE)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getDate);
        } else if (elasticFieldType.equals(FieldType.IPV4_ADDRESS)) {
            return buildScalarQuery(
                    condition, elasticIndexField, fieldName, nestedPaths, value, this::getIpV4Address);
        } else {
            return buildStringQuery(condition, value, elasticIndexField, fieldName, nestedPaths);
        }
    }

    private Query buildStringQuery(final Condition condition,
                                   final String expression,
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
            final TriFunction<Condition, String, String, FieldValue> valueParser
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
                //noinspection SequencedCollectionMethodCanBeUsed
                return wrapInNested(nestedPaths, QueryBuilders
                        .range(q -> q.untyped(UntypedRangeQuery.of(r -> r
                                .field(fieldName)
                                .gte(JsonData.of(fieldValues.get(0)))
                                .lte(JsonData.of(fieldValues.get(1))))
                        )));
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
        if (!isValidIpV4Address(value)) {
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

    /**
     * @return True if the value is a dotted-decimal IPv4 address with each octet in the range 0-255 and,
     * if CIDR notation is used, a prefix length in the range 0-32. Octets and prefix lengths with
     * leading zeros (e.g. `01`) are rejected as they are ambiguous (decimal vs octal) and are not
     * accepted by Elasticsearch.
     */
    private static boolean isValidIpV4Address(final String value) {
        final Matcher ipAddressMatcher = IPV4_ADDRESS_PATTERN.matcher(value);
        if (!ipAddressMatcher.matches()) {
            return false;
        }
        for (int i = 1; i <= IPV4_OCTET_COUNT; i++) {
            if (!isValidIpV4Number(ipAddressMatcher.group(i), IPV4_MAX_OCTET)) {
                return false;
            }
        }
        final String prefixLength = ipAddressMatcher.group(IPV4_OCTET_COUNT + 1);
        return prefixLength == null || isValidIpV4Number(prefixLength, IPV4_MAX_PREFIX_LENGTH);
    }

    /**
     * @return True if the string of ASCII digits has no leading zero and is no greater than the maximum.
     */
    private static boolean isValidIpV4Number(final String digits, final int max) {
        if (digits.length() > 1 && digits.charAt(0) == '0') {
            return false;
        }
        return Integer.parseInt(digits) <= max;
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

    /**
     * Parses a value of an `unsigned_long` field, which may be up to 2<sup>64</sup>-1, beyond the range of a long.
     * A value that fits in a long is passed to Elasticsearch as a number, and a larger one as its decimal string,
     * which Elasticsearch parses as an unsigned long.
     */
    private FieldValue getUnsignedLong(final Condition condition, final String fieldName, final String value) {
        final BigInteger number;
        try {
            number = new BigInteger(value);
        } catch (final NumberFormatException e) {
            throw new NumberFormatException(
                    "Expected a numeric value for field \"" + fieldName + "\" but was given string \"" + value + "\""
            );
        }
        if (number.signum() < 0 || number.compareTo(UNSIGNED_LONG_MAX) > 0) {
            throw new IllegalArgumentException(
                    "Value \"" + value + "\" for field \"" + fieldName + "\" is outside the range of an " +
                    "unsigned long (0 to " + UNSIGNED_LONG_MAX + ")");
        }
        return number.bitLength() < Long.SIZE
                ? FieldValue.of(number.longValue())
                : FieldValue.of(number.toString());
    }

    /**
     * Returns the value of a `version` field, e.g. `1.2.0`, as a string. Elasticsearch parses it and compares
     * versions by their precedence, so it is not parsed here.
     */
    private FieldValue getVersion(final Condition condition, final String fieldName, final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Expected a version for field \"" + fieldName + "\"");
        }
        return FieldValue.of(value.trim());
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
     * Loads the specified Dictionary from the doc store and builds a query matching any of its lines,
     * i.e. one sub-query per line, combined with OR logic (`should` clauses).
     * <p>
     * Each line is treated as a single value rather than being split into terms: numeric, date and
     * IPv4 fields require the whole line to parse as one value, keyword fields match the whole line
     * exactly (or as a wildcard pattern if it contains `*` or `?`), and text fields use a
     * `query_string` query on the line, so multiple words on a line are evaluated using Elasticsearch
     * query string syntax (whose default operator is OR) rather than being ANDed.
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
            final String nativeType = indexField.getNativeType();
            final FieldType elasticFieldType = indexField.getFldType();

            if (Kind.UnsignedLong.jsonValue().equals(nativeType)) {
                mustQueries.must(QueryBuilders.term(q -> q
                        .field(fieldName)
                        .value(getUnsignedLong(condition, fieldName, line))));
            } else if (Kind.Version.jsonValue().equals(nativeType)) {
                mustQueries.must(QueryBuilders.term(q -> q
                        .field(fieldName)
                        .value(getVersion(condition, fieldName, line))));
            } else if (elasticFieldType.equals(FieldType.ID) ||
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
