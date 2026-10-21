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

import stroom.ai.api.AiService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.MockIndexFieldCache;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.UntypedRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.WildcardQuery;
import co.elastic.clients.json.JsonData;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSearchExpressionQueryBuilder {

    private static final String INDEX_NAME = "test-index";

    private static final String ID_FIELD = "id";
    private static final String INTEGER_FIELD = "count";
    private static final String LONG_FIELD = "bytes";
    private static final String FLOAT_FIELD = "ratio";
    private static final String DOUBLE_FIELD = "score";
    private static final String DATE_FIELD = "timestamp";
    private static final String IP_FIELD = "ip";
    private static final String TEXT_FIELD = "title";
    private static final String KEYWORD_FIELD = "tag";
    private static final String BOOLEAN_FIELD = "flag";
    private static final String WILDCARD_FIELD = "pattern";
    private static final String DENSE_VECTOR_FIELD = "embedding";

    private static final String DATE_1 = "2021-02-17T01:23:34.000Z";
    private static final long DATE_1_MS = 1613525014000L;
    private static final String DATE_2 = "2021-02-18T01:23:34.000Z";
    private static final long DATE_2_MS = 1613611414000L;

    private static final DocRef DICTIONARY_REF = new DocRef("Dictionary", UUID.randomUUID().toString(), "dict");
    private static final DocRef MODEL_REF = new DocRef("OpenAIModel", UUID.randomUUID().toString(), "model");

    // The conditions the UI offers for each kind of Elastic field (see ConditionSet.getElastic)
    private static final Set<Condition> SCALAR_CONDITIONS = EnumSet.of(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.GREATER_THAN,
            Condition.GREATER_THAN_OR_EQUAL_TO,
            Condition.LESS_THAN,
            Condition.LESS_THAN_OR_EQUAL_TO,
            Condition.BETWEEN,
            Condition.IN,
            Condition.IN_DICTIONARY);
    private static final Set<Condition> STRING_CONDITIONS = EnumSet.of(
            Condition.EQUALS,
            Condition.NOT_EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY,
            Condition.MATCHES_REGEX);

    private MockIndexFieldCache indexFieldCache;
    private WordListProvider wordListProvider;
    private AiService aiService;
    private ElasticIndexDoc indexDoc;

    @BeforeEach
    void setUp() {
        indexFieldCache = new MockIndexFieldCache();
        wordListProvider = mock(WordListProvider.class);
        aiService = mock(AiService.class);
        indexDoc = ElasticIndexDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name(INDEX_NAME)
                .build();

        addField(ID_FIELD, FieldType.ID, "long");
        addField(INTEGER_FIELD, FieldType.INTEGER, "integer");
        addField(LONG_FIELD, FieldType.LONG, "long");
        addField(FLOAT_FIELD, FieldType.FLOAT, "float");
        addField(DOUBLE_FIELD, FieldType.DOUBLE, "double");
        addField(DATE_FIELD, FieldType.DATE, "date");
        addField(IP_FIELD, FieldType.IPV4_ADDRESS, "ip");
        addField(TEXT_FIELD, FieldType.TEXT, "text");
        addField(KEYWORD_FIELD, FieldType.KEYWORD, "keyword");
        addField(BOOLEAN_FIELD, FieldType.BOOLEAN, "boolean");
        addField(WILDCARD_FIELD, FieldType.KEYWORD, "wildcard");
        addField(DENSE_VECTOR_FIELD, FieldType.DENSE_VECTOR, "dense_vector");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "256.1.1.1", "1.256.1.1", "1.1.256.1", "1.1.1.256", "1.2.3.999", "999.999.999.999",
            "10.0.0.0/33", "10.0.0.0/99", "256.0.0.0/8",
            "+1.2.3.4", "-1.2.3.4", "1.2.3.-4", "1.2.3.4/-1", "1.2.3.4/", "1.2.3.4/100",
            "01.2.3.4", "1.2.3.010", "1.2.3.4/08", "1.2.3.4/00"})
    void testOutOfRangeIpAddress(final String value) {
        assertThatThrownBy(() -> buildSingleTerm(IP_FIELD, Condition.EQUALS, value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid IPv4 address format: " + value + " for field \"" + IP_FIELD + "\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0", "255.255.255.255", "0.255.0.255", "10.0.0.0/0", "10.0.0.0/8", "10.0.0.0/32",
            "192.168.1.1/24"})
    void testBoundaryIpAddress(final String value) {
        assertTerm(buildSingleTerm(IP_FIELD, Condition.EQUALS, value), IP_FIELD, value);
    }

    @Test
    void testOutOfRangeIpAddressInDictionary() {
        when(wordListProvider.getWords(DICTIONARY_REF)).thenReturn(new String[]{"10.0.0.1", "10.0.0.256"});

        assertThatThrownBy(() -> buildSingleTerm(dictionaryTerm(IP_FIELD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid IPv4 address format: 10.0.0.256 for field \"" + IP_FIELD + "\"");
    }

    @Test
    void testDictionaryNotSetWithoutValue() {
        final ExpressionTerm term = ExpressionTerm.builder()
                .field(KEYWORD_FIELD)
                .condition(Condition.IN_DICTIONARY)
                .build();

        assertThatThrownBy(() -> buildSingleTerm(term))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dictionary not set for field: " + KEYWORD_FIELD);
    }

    @Test
    void testDictionaryWithoutValue() {
        when(wordListProvider.getWords(DICTIONARY_REF)).thenReturn(new String[]{"a"});
        final ExpressionTerm term = ExpressionTerm.builder()
                .field(KEYWORD_FIELD)
                .condition(Condition.IN_DICTIONARY)
                .docRef(DICTIONARY_REF)
                .build();

        // The Lucene builder only requires the dictionary reference, not a value
        final List<Query> lines = assertDictionaryLines(buildSingleTerm(term), 1);
        assertTerm(lines.getFirst(), KEYWORD_FIELD, "a");
    }

    // --------------------------------------------------------------------------------
    // Parameter sources
    // --------------------------------------------------------------------------------

    static Stream<ScalarCase> scalarCases() {
        return Stream.of(
                new ScalarCase(ID_FIELD, "1", "2", 1L, 2L),
                new ScalarCase(INTEGER_FIELD, "10", "20", 10L, 20L),
                new ScalarCase(LONG_FIELD, "9000000000", "9000000001", 9000000000L, 9000000001L),
                new ScalarCase(FLOAT_FIELD, "1.5", "2.25", 1.5D, 2.25D),
                new ScalarCase(DOUBLE_FIELD, "1.1", "2.2", 1.1D, 2.2D),
                new ScalarCase(DATE_FIELD, DATE_1, DATE_2, DATE_1_MS, DATE_2_MS),
                new ScalarCase(IP_FIELD, "192.168.1.1", "192.168.1.20", "192.168.1.1", "192.168.1.20"));
    }

    static Stream<Arguments> scalarTypeConditionMatrix() {
        return scalarCases().flatMap(scalarCase -> SCALAR_CONDITIONS.stream()
                .map(condition -> Arguments.of(scalarCase, condition)));
    }

    static Stream<Arguments> scalarTypeUnsupportedConditionMatrix() {
        return scalarCases().flatMap(scalarCase -> EnumSet.complementOf(EnumSet.copyOf(SCALAR_CONDITIONS))
                .stream()
                .map(condition -> Arguments.of(scalarCase, condition)));
    }

    static Stream<Arguments> stringTypeUnsupportedConditionMatrix() {
        return Stream.of(TEXT_FIELD, KEYWORD_FIELD, BOOLEAN_FIELD, WILDCARD_FIELD)
                .flatMap(field -> EnumSet.complementOf(EnumSet.copyOf(STRING_CONDITIONS))
                        .stream()
                        .map(condition -> Arguments.of(field, condition)));
    }

    // --------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------

    private void addField(final String name, final FieldType fieldType, final String nativeType) {
        indexFieldCache.put(name, ElasticIndexField.builder()
                .fldName(name)
                .fldType(fieldType)
                .nativeType(nativeType)
                .build());
    }

    private void addNestedUserFields() {
        addField("user", FieldType.NESTED, "nested");
        addField("user.age", FieldType.LONG, "long");
        addField("user.role", FieldType.KEYWORD, "keyword");
    }

    private SearchExpressionQueryBuilder createBuilder() {
        return new SearchExpressionQueryBuilder(
                () -> aiService,
                indexDoc,
                indexFieldCache,
                wordListProvider,
                DateTimeSettings.builder().build());
    }

    private SearchExpressionQueryBuilder createBuilderWithVectorModel() {
        final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(any(String.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{0.25F, 0.5F})));
        return createBuilderWithVectorModel(embeddingModel);
    }

    private SearchExpressionQueryBuilder createBuilderWithVectorModel(final EmbeddingModel embeddingModel) {
        indexDoc = indexDoc.copy().vectorGenerationModelRef(MODEL_REF).build();
        final OpenAIModelDoc modelDoc = mock(OpenAIModelDoc.class);
        when(aiService.getOpenAIModelDoc(MODEL_REF)).thenReturn(modelDoc);
        when(aiService.getEmbeddingModel(modelDoc)).thenReturn(embeddingModel);
        return createBuilder();
    }

    private Query buildQuery(final ExpressionOperator expression) {
        return createBuilder().buildQuery(expression).getQuery();
    }

    /// Builds a query from an `AND` operator containing a single term and returns the term's query.
    private Query buildSingleTerm(final String field, final Condition condition, final String value) {
        return buildSingleTerm(term(field, condition, value));
    }

    private Query buildSingleTerm(final ExpressionTerm term) {
        return singleMust(buildQuery(and(term)));
    }

    private static ExpressionOperator and(final ExpressionTerm term) {
        return ExpressionOperator.builder().op(Op.AND).addTerm(term).build();
    }

    private static ExpressionTerm term(final String field, final Condition condition, final String value) {
        return ExpressionTerm.builder().field(field).condition(condition).value(value).build();
    }

    private static ExpressionTerm dictionaryTerm(final String field) {
        return ExpressionTerm.builder()
                .field(field)
                .condition(Condition.IN_DICTIONARY)
                .value(DICTIONARY_REF.getName())
                .docRef(DICTIONARY_REF)
                .build();
    }

    private static ExpressionTerm disabled(final ExpressionTerm term) {
        return term.copy().enabled(false).build();
    }

    private static Query singleMust(final Query query) {
        assertThat(query.isBool()).as("Root query is a bool query").isTrue();
        final BoolQuery bool = query.bool();
        assertThat(bool.should()).isEmpty();
        assertThat(bool.mustNot()).isEmpty();
        assertThat(bool.must()).hasSize(1);
        return bool.must().getFirst();
    }

    private static void assertEmptyBool(final Query query) {
        assertThat(query.isBool()).isTrue();
        assertThat(query.bool().must()).isEmpty();
        assertThat(query.bool().should()).isEmpty();
        assertThat(query.bool().mustNot()).isEmpty();
    }

    private static void assertTerm(final Query query, final String field, final Object expectedValue) {
        assertThat(query.isTerm()).as("Is a term query").isTrue();
        final TermQuery termQuery = query.term();
        assertThat(termQuery.field()).isEqualTo(field);
        assertThat(termQuery.value()._get()).isEqualTo(expectedValue);
    }

    private static void assertWildcard(final Query query, final String field, final String expectedValue) {
        assertThat(query.isWildcard()).as("Is a wildcard query").isTrue();
        final WildcardQuery wildcard = query.wildcard();
        assertThat(wildcard.field()).isEqualTo(field);
        assertThat(wildcard.value()).isEqualTo(expectedValue);
        assertThat(wildcard.caseInsensitive()).isTrue();
    }

    private static void assertQueryString(final Query query, final String field, final String expectedValue) {
        assertThat(query.isQueryString()).as("Is a query_string query").isTrue();
        final QueryStringQuery queryString = query.queryString();
        assertThat(queryString.fields()).containsExactly(field);
        assertThat(queryString.query()).isEqualTo(expectedValue);
        assertThat(queryString.analyzeWildcard()).isTrue();
    }

    private static UntypedRangeQuery assertRange(final Query query, final String field) {
        assertThat(query.isRange()).as("Is a range query").isTrue();
        final UntypedRangeQuery range = query.range().untyped();
        assertThat(range.field()).isEqualTo(field);
        return range;
    }

    private static Object rangeValue(final JsonData jsonData) {
        assertThat(jsonData).isNotNull();
        return jsonData.to(FieldValue.class)._get();
    }

    /// Asserts the query is the `match_all AND NOT x` form produced for negated conditions and
    /// returns the negated query `x`.
    private static Query assertNegated(final Query query) {
        assertThat(query.isBool()).as("Is a negated bool query").isTrue();
        final BoolQuery bool = query.bool();
        assertThat(bool.must()).isEmpty();
        assertThat(bool.should()).hasSize(1);
        assertThat(bool.should().getFirst().isMatchAll()).isTrue();
        assertThat(bool.mustNot()).hasSize(1);
        return bool.mustNot().getFirst();
    }

    /// Asserts a dictionary query (one `should` clause per line, each a `bool` with a single `must`)
    /// and returns the per-line queries.
    private static List<Query> assertDictionaryLines(final Query query, final int expectedLines) {
        assertThat(query.isBool()).as("Is a dictionary bool query").isTrue();
        final BoolQuery bool = query.bool();
        assertThat(bool.must()).isEmpty();
        assertThat(bool.mustNot()).isEmpty();
        assertThat(bool.should()).hasSize(expectedLines);
        return bool.should().stream()
                .map(line -> {
                    assertThat(line.isBool()).isTrue();
                    assertThat(line.bool().must()).hasSize(1);
                    return line.bool().must().getFirst();
                })
                .toList();
    }

    /// Asserts the query is a chain of `nested` queries with the given paths (outermost first) and
    /// returns the innermost (leaf) query.
    private static Query assertNestedPaths(final Query query, final String... expectedPaths) {
        Query current = query;
        for (final String expectedPath : expectedPaths) {
            assertThat(current.isNested()).as("Is a nested query for path " + expectedPath).isTrue();
            final NestedQuery nested = current.nested();
            assertThat(nested.path()).isEqualTo(expectedPath);
            current = nested.query();
        }
        assertThat(current.isNested()).as("No further nesting").isFalse();
        return current;
    }

    // --------------------------------------------------------------------------------

    /// A scalar field with two sample raw values and the values they are expected to be parsed into.
    record ScalarCase(String field,
                      String value1,
                      String value2,
                      Object expected1,
                      Object expected2) {

        @Override
        public String toString() {
            return field;
        }
    }
}
