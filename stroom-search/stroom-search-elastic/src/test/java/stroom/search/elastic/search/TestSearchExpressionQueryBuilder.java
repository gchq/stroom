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
import stroom.query.api.datasource.IndexField;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
    private static final String UNSIGNED_LONG_FIELD = "packets";
    private static final String VERSION_FIELD = "release";
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
    private static final Set<Condition> BOOLEAN_CONDITIONS = EnumSet.of(
            Condition.EQUALS,
            Condition.NOT_EQUALS);

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
        addField(UNSIGNED_LONG_FIELD, FieldType.LONG, "unsigned_long");
        addField(VERSION_FIELD, FieldType.KEYWORD, "version");
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

    // --------------------------------------------------------------------------------
    // Scalar (numeric, date and IP) field types
    // --------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("scalarTypeConditionMatrix")
    void testScalarFieldConditions(final ScalarCase scalarCase, final Condition condition) {
        final String field = scalarCase.field();
        final Query query;
        if (Condition.IN_DICTIONARY.equals(condition)) {
            when(wordListProvider.getWords(DICTIONARY_REF))
                    .thenReturn(new String[]{scalarCase.value1(), scalarCase.value2()});
            query = buildSingleTerm(dictionaryTerm(field));
        } else if (Condition.IN.equals(condition) || Condition.BETWEEN.equals(condition)) {
            query = buildSingleTerm(field, condition, scalarCase.value1() + ", " + scalarCase.value2());
        } else {
            query = buildSingleTerm(field, condition, scalarCase.value1());
        }

        switch (condition) {
            case EQUALS -> assertTerm(query, field, scalarCase.expected1());
            case NOT_EQUALS -> assertTerm(assertNegated(query), field, scalarCase.expected1());
            case IN -> {
                assertThat(query.isTerms()).isTrue();
                assertThat(query.terms().field()).isEqualTo(field);
                assertThat(query.terms().terms().value())
                        .extracting(FieldValue::_get)
                        .containsExactly(scalarCase.expected1(), scalarCase.expected2());
            }
            case GREATER_THAN -> {
                final UntypedRangeQuery range = assertRange(query, field);
                assertThat(rangeValue(range.gt())).isEqualTo(scalarCase.expected1());
                assertThat(range.gte()).isNull();
                assertThat(range.lt()).isNull();
                assertThat(range.lte()).isNull();
            }
            case GREATER_THAN_OR_EQUAL_TO -> {
                final UntypedRangeQuery range = assertRange(query, field);
                assertThat(rangeValue(range.gte())).isEqualTo(scalarCase.expected1());
                assertThat(range.gt()).isNull();
                assertThat(range.lt()).isNull();
                assertThat(range.lte()).isNull();
            }
            case LESS_THAN -> {
                final UntypedRangeQuery range = assertRange(query, field);
                assertThat(rangeValue(range.lt())).isEqualTo(scalarCase.expected1());
                assertThat(range.gt()).isNull();
                assertThat(range.gte()).isNull();
                assertThat(range.lte()).isNull();
            }
            case LESS_THAN_OR_EQUAL_TO -> {
                final UntypedRangeQuery range = assertRange(query, field);
                assertThat(rangeValue(range.lte())).isEqualTo(scalarCase.expected1());
                assertThat(range.gt()).isNull();
                assertThat(range.gte()).isNull();
                assertThat(range.lt()).isNull();
            }
            case BETWEEN -> {
                final UntypedRangeQuery range = assertRange(query, field);
                assertThat(rangeValue(range.gte())).isEqualTo(scalarCase.expected1());
                assertThat(rangeValue(range.lte())).isEqualTo(scalarCase.expected2());
                assertThat(range.gt()).isNull();
                assertThat(range.lt()).isNull();
            }
            case IN_DICTIONARY -> {
                final List<Query> lines = assertDictionaryLines(query, 2);
                assertTerm(lines.get(0), field, scalarCase.expected1());
                assertTerm(lines.get(1), field, scalarCase.expected2());
            }
            default -> throw new IllegalStateException("Unexpected condition " + condition);
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("scalarTypeUnsupportedConditionMatrix")
    void testScalarFieldUnsupportedConditions(final ScalarCase scalarCase, final Condition condition) {
        final FieldType fieldType = ((ElasticIndexField) indexFieldCache.get(null, scalarCase.field())).getFldType();

        assertThatThrownBy(() -> buildSingleTerm(scalarCase.field(), condition, scalarCase.value1()))
                .isInstanceOf(SearchException.class)
                .hasMessage("Unexpected condition '" + condition.getDisplayValue() + "' for " +
                            fieldType.getDisplayValue() + " field type");
    }

    @Test
    void testFloatValueIsWidenedFromFloatPrecision() {
        final Query query = buildSingleTerm(FLOAT_FIELD, Condition.EQUALS, "1.1");

        // The value is parsed as a float and then widened to a double
        assertTerm(query, FLOAT_FIELD, (double) 1.1F);
    }

    @Test
    void testIntegerFieldAcceptsNegativeNumbers() {
        assertTerm(buildSingleTerm(INTEGER_FIELD, Condition.EQUALS, "-5"), INTEGER_FIELD, -5L);
    }

    @Test
    void testUnsignedLongWithinTheRangeOfALongIsANumber() {
        assertTerm(buildSingleTerm(UNSIGNED_LONG_FIELD, Condition.EQUALS, "42"), UNSIGNED_LONG_FIELD, 42L);
    }

    @Test
    void testUnsignedLongBeyondTheRangeOfALongIsItsDecimalString() {
        final Query query = buildSingleTerm(UNSIGNED_LONG_FIELD, Condition.GREATER_THAN, "9223372036854775808");

        final UntypedRangeQuery range = assertRange(query, UNSIGNED_LONG_FIELD);
        assertThat(rangeValue(range.gt())).isEqualTo("9223372036854775808");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "18446744073709551616"})
    void testUnsignedLongOutOfRangeIsRejected(final String value) {
        assertThatThrownBy(() -> buildSingleTerm(UNSIGNED_LONG_FIELD, Condition.EQUALS, value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Value \"" + value + "\" for field \"" + UNSIGNED_LONG_FIELD +
                            "\" is outside the range of an unsigned long (0 to 18446744073709551615)");
    }

    @Test
    void testUnsignedLongOutOfRangeInDictionaryIsRejected() {
        when(wordListProvider.getWords(DICTIONARY_REF)).thenReturn(new String[]{"1", "-1"});

        assertThatThrownBy(() -> buildSingleTerm(dictionaryTerm(UNSIGNED_LONG_FIELD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is outside the range of an unsigned long");
    }

    @Test
    void testUnsignedLongNotANumberIsRejected() {
        assertThatThrownBy(() -> buildSingleTerm(UNSIGNED_LONG_FIELD, Condition.EQUALS, "lots"))
                .isInstanceOf(NumberFormatException.class)
                .hasMessage("Expected a numeric value for field \"" + UNSIGNED_LONG_FIELD +
                            "\" but was given string \"lots\"");
    }

    @Test
    void testDateFieldRelativeExpression() {
        final long before = System.currentTimeMillis();
        final Query query = buildSingleTerm(DATE_FIELD, Condition.GREATER_THAN, "now()");
        final long after = System.currentTimeMillis();

        final UntypedRangeQuery range = assertRange(query, DATE_FIELD);
        assertThat((Long) rangeValue(range.gt())).isBetween(before, after);
    }

    @ParameterizedTest
    @EnumSource(value = Condition.class, names = {"EQUALS", "IN"})
    void testIpCidrAllowedForEqualityConditions(final Condition condition) {
        final Query query = buildSingleTerm(IP_FIELD, condition, "10.0.0.0/8");

        if (Condition.EQUALS.equals(condition)) {
            assertTerm(query, IP_FIELD, "10.0.0.0/8");
        } else {
            assertThat(query.isTerms()).isTrue();
            assertThat(query.terms().terms().value())
                    .extracting(FieldValue::_get)
                    .containsExactly("10.0.0.0/8");
        }
    }

    @Test
    void testIpCidrAllowedInDictionary() {
        when(wordListProvider.getWords(DICTIONARY_REF)).thenReturn(new String[]{"10.0.0.0/8"});

        final List<Query> lines = assertDictionaryLines(buildSingleTerm(dictionaryTerm(IP_FIELD)), 1);

        assertTerm(lines.getFirst(), IP_FIELD, "10.0.0.0/8");
    }

    @ParameterizedTest
    @EnumSource(value = Condition.class, names = {
            "NOT_EQUALS",
            "GREATER_THAN",
            "GREATER_THAN_OR_EQUAL_TO",
            "LESS_THAN",
            "LESS_THAN_OR_EQUAL_TO"})
    void testIpCidrRejectedForNonEqualityConditions(final Condition condition) {
        assertThatThrownBy(() -> buildSingleTerm(IP_FIELD, condition, "10.0.0.0/8"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("CIDR notation is only supported for EQUALS and IN operators")
                .hasMessageContaining("\"10.0.0.0/8\"")
                .hasMessageContaining(condition.getDisplayValue());
    }

    @Test
    void testIpCidrRejectedForBetween() {
        assertThatThrownBy(() -> buildSingleTerm(IP_FIELD, Condition.BETWEEN, "10.0.0.0/8, 10.0.0.255"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("CIDR notation is only supported");
    }

    // --------------------------------------------------------------------------------
    // String (text, keyword, boolean, etc.) field types
    // --------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {TEXT_FIELD, BOOLEAN_FIELD, WILDCARD_FIELD})
    void testFullTextFieldEquals(final String field) {
        final Query query = buildSingleTerm(field, Condition.EQUALS, "some value*");

        assertQueryString(query, field, "some value*");
    }

    @ParameterizedTest
    @ValueSource(strings = {TEXT_FIELD, BOOLEAN_FIELD, WILDCARD_FIELD})
    void testFullTextFieldNotEquals(final String field) {
        final Query query = buildSingleTerm(field, Condition.NOT_EQUALS, "true");

        assertQueryString(assertNegated(query), field, "true");
    }

    @ParameterizedTest
    @ValueSource(strings = {TEXT_FIELD, WILDCARD_FIELD})
    void testFullTextFieldInSingleValue(final String field) {
        final Query query = buildSingleTerm(field, Condition.IN, "alpha");

        assertQueryString(query, field, "alpha");
    }

    @ParameterizedTest
    @ValueSource(strings = {TEXT_FIELD, WILDCARD_FIELD})
    void testFullTextFieldInMultipleValues(final String field) {
        final Query query = buildSingleTerm(field, Condition.IN, "alpha, beta ,,gamma");

        assertThat(query.isBool()).isTrue();
        final List<Query> should = query.bool().should();
        assertThat(should).hasSize(3);
        assertQueryString(should.get(0), field, "alpha");
        assertQueryString(should.get(1), field, "beta");
        assertQueryString(should.get(2), field, "gamma");
    }

    @ParameterizedTest
    @ValueSource(strings = {TEXT_FIELD, KEYWORD_FIELD, WILDCARD_FIELD})
    void testStringFieldMatchesRegex(final String field) {
        final Query query = buildSingleTerm(field, Condition.MATCHES_REGEX, "ab+c.*");

        assertThat(query.isRegexp()).isTrue();
        assertThat(query.regexp().field()).isEqualTo(field);
        assertThat(query.regexp().value()).isEqualTo("ab+c.*");
    }

    @Test
    void testTextFieldInDictionary() {
        when(wordListProvider.getWords(DICTIONARY_REF)).thenReturn(new String[]{"one", "two words"});

        final List<Query> lines = assertDictionaryLines(buildSingleTerm(dictionaryTerm(TEXT_FIELD)), 2);

        assertQueryString(lines.get(0), TEXT_FIELD, "one");
        assertQueryString(lines.get(1), TEXT_FIELD, "two words");
    }

    @Test
    void testKeywordFieldEqualsExactMatch() {
        assertTerm(buildSingleTerm(KEYWORD_FIELD, Condition.EQUALS, "abc"), KEYWORD_FIELD, "abc");
    }

    @Test
    void testKeywordFieldEqualsQuotedValueStripsQuotes() {
        // Quoting allows wildcard characters to be matched literally
        assertTerm(buildSingleTerm(KEYWORD_FIELD, Condition.EQUALS, "\"a*b\""), KEYWORD_FIELD, "a*b");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab*", "a?c", "*bc"})
    void testKeywordFieldEqualsWildcard(final String value) {
        assertWildcard(buildSingleTerm(KEYWORD_FIELD, Condition.EQUALS, value), KEYWORD_FIELD, value);
    }

    @Test
    void testKeywordFieldNotEquals() {
        final Query query = buildSingleTerm(KEYWORD_FIELD, Condition.NOT_EQUALS, "abc");

        assertTerm(assertNegated(query), KEYWORD_FIELD, "abc");
    }

    @Test
    void testKeywordFieldInSingleValue() {
        assertTerm(buildSingleTerm(KEYWORD_FIELD, Condition.IN, "abc"), KEYWORD_FIELD, "abc");
    }

    @Test
    void testKeywordFieldInMultipleValues() {
        final Query query = buildSingleTerm(KEYWORD_FIELD, Condition.IN, "abc, d*f, \"g?h\"");

        assertThat(query.isBool()).isTrue();
        final List<Query> should = query.bool().should();
        assertThat(should).hasSize(3);
        assertTerm(should.get(0), KEYWORD_FIELD, "abc");
        assertWildcard(should.get(1), KEYWORD_FIELD, "d*f");
        assertTerm(should.get(2), KEYWORD_FIELD, "g?h");
    }

    @Test
    void testKeywordFieldInDictionary() {
        when(wordListProvider.getWords(DICTIONARY_REF)).thenReturn(new String[]{"abc", "d*f", "\"g?h\""});

        final List<Query> lines = assertDictionaryLines(buildSingleTerm(dictionaryTerm(KEYWORD_FIELD)), 3);

        assertTerm(lines.get(0), KEYWORD_FIELD, "abc");
        assertWildcard(lines.get(1), KEYWORD_FIELD, "d*f");
        assertTerm(lines.get(2), KEYWORD_FIELD, "g?h");
    }

    @Test
    void testDictionaryWithNoWords() {
        when(wordListProvider.getWords(DICTIONARY_REF)).thenReturn(new String[0]);

        assertDictionaryLines(buildSingleTerm(dictionaryTerm(KEYWORD_FIELD)), 0);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("stringTypeUnsupportedConditionMatrix")
    void testStringFieldUnsupportedConditions(final String field, final Condition condition) {
        assertThatThrownBy(() -> buildSingleTerm(field, condition, "abc"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageStartingWith("Unsupported condition '" + condition.getDisplayValue() + "' for ")
                .hasMessageEndingWith(" field type");
    }

    // --------------------------------------------------------------------------------
    // Dense vector fields
    // --------------------------------------------------------------------------------

    @Test
    void testDenseVectorFieldEquals() {
        final SearchExpressionQueryBuilder builder = createBuilderWithVectorModel();
        final ElasticQueryParams params = builder.buildQuery(and(term(DENSE_VECTOR_FIELD, Condition.EQUALS, "cats")));

        final Query query = singleMust(params.getQuery());
        assertThat(query.isKnn()).isTrue();
        assertThat(query.knn().field()).isEqualTo(DENSE_VECTOR_FIELD);
        assertThat(query.knn().queryVector()).containsExactly(0.25F, 0.5F);
        assertThat(params.getKnnFieldQueries()).containsExactlyEntriesOf(Map.of(DENSE_VECTOR_FIELD, "cats"));
    }

    @Test
    void testDenseVectorFieldNotEquals() {
        final SearchExpressionQueryBuilder builder = createBuilderWithVectorModel();
        final ElasticQueryParams params = builder.buildQuery(
                and(term(DENSE_VECTOR_FIELD, Condition.NOT_EQUALS, "cats")));

        final Query inner = assertNegated(singleMust(params.getQuery()));
        assertThat(inner.isKnn()).isTrue();
        assertThat(inner.knn().field()).isEqualTo(DENSE_VECTOR_FIELD);
    }

    @Test
    void testDenseVectorFieldWithoutModel() {
        assertThatThrownBy(() -> buildSingleTerm(DENSE_VECTOR_FIELD, Condition.EQUALS, "cats"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vector embedding model is not defined in data source " + INDEX_NAME);
    }

    @Test
    void testDenseVectorFieldEmbeddingFailure() {
        final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(any(String.class))).thenThrow(new IllegalStateException("boom"));
        final SearchExpressionQueryBuilder builder = createBuilderWithVectorModel(embeddingModel);

        assertThatThrownBy(() -> builder.buildQuery(and(term(DENSE_VECTOR_FIELD, Condition.EQUALS, "cats"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to create vector embeddings for field " + DENSE_VECTOR_FIELD + ". boom")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    // --------------------------------------------------------------------------------
    // Special values (wildcard, empty, whitespace)
    // --------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            ID_FIELD, INTEGER_FIELD, LONG_FIELD, FLOAT_FIELD, DOUBLE_FIELD, DATE_FIELD, IP_FIELD,
            TEXT_FIELD, KEYWORD_FIELD, BOOLEAN_FIELD})
    void testEqualsStarIsExistsQuery(final String field) {
        final Query query = buildSingleTerm(field, Condition.EQUALS, " * ");

        assertThat(query.isExists()).isTrue();
        assertThat(query.exists().field()).isEqualTo(field);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ID_FIELD, INTEGER_FIELD, LONG_FIELD, FLOAT_FIELD, DOUBLE_FIELD, DATE_FIELD, IP_FIELD,
            TEXT_FIELD, KEYWORD_FIELD, BOOLEAN_FIELD})
    void testNotEqualsStarIsNegatedExistsQuery(final String field) {
        final Query inner = assertNegated(buildSingleTerm(field, Condition.NOT_EQUALS, "*"));

        assertThat(inner.isExists()).isTrue();
        assertThat(inner.exists().field()).isEqualTo(field);
    }

    @Test
    void testInStarOnTextFieldIsNotExistsQuery() {
        // Only EQUALS and NOT_EQUALS treat a lone `*` as an exists query
        assertQueryString(buildSingleTerm(TEXT_FIELD, Condition.IN, "*"), TEXT_FIELD, "*");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void testEmptyValueIsIgnored(final String value) {
        final Query query = buildQuery(and(term(LONG_FIELD, Condition.EQUALS, value)));

        assertEmptyBool(query);
    }

    @Test
    void testNullValueIsIgnored() {
        final Query query = buildQuery(and(term(TEXT_FIELD, Condition.EQUALS, null)));

        assertEmptyBool(query);
    }

    @Test
    void testDelimiterOnlyStringValueIsIgnored() {
        final Query query = buildQuery(and(term(TEXT_FIELD, Condition.EQUALS, " , , ")));

        assertEmptyBool(query);
    }

    @Test
    void testFieldAndValueAreTrimmed() {
        assertTerm(buildSingleTerm("  " + LONG_FIELD + " ", Condition.EQUALS, "  42 "), LONG_FIELD, 42L);
    }

    @Test
    void testInIgnoresEmptyTokens() {
        final Query query = buildSingleTerm(LONG_FIELD, Condition.IN, "1,, 2 ,");

        assertThat(query.terms().terms().value())
                .extracting(FieldValue::_get)
                .containsExactly(1L, 2L);
    }

    // --------------------------------------------------------------------------------
    // Operators
    // --------------------------------------------------------------------------------

    @Test
    void testNullExpressionIsMatchAll() {
        assertThat(buildQuery(null).isMatchAll()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Op.class)
    void testEmptyOperatorIsMatchAll(final Op op) {
        assertThat(buildQuery(ExpressionOperator.builder().op(op).build()).isMatchAll()).isTrue();
    }

    @Test
    void testDisabledRootOperatorIsMatchAll() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .enabled(false)
                .addTerm(term(LONG_FIELD, Condition.EQUALS, "1"))
                .build();

        assertThat(buildQuery(expression).isMatchAll()).isTrue();
    }

    @Test
    void testOperatorWithOnlyDisabledTermsIsMatchAll() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(disabled(term(LONG_FIELD, Condition.EQUALS, "1")))
                .addTerm(disabled(term(TEXT_FIELD, Condition.EQUALS, "abc")))
                .build();

        assertThat(buildQuery(expression).isMatchAll()).isTrue();
    }

    @Test
    void testOperatorWithOnlyEmptyChildOperatorsIsMatchAll() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().op(Op.OR).build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addOperator(ExpressionOperator.builder().build())
                        .build())
                .build();

        assertThat(buildQuery(expression).isMatchAll()).isTrue();
    }

    @Test
    void testDisabledTermsAreSkipped() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(term(LONG_FIELD, Condition.EQUALS, "1"))
                .addTerm(disabled(term(LONG_FIELD, Condition.EQUALS, "2")))
                .addTerm(term(LONG_FIELD, Condition.EQUALS, "3"))
                .build();

        final List<Query> must = buildQuery(expression).bool().must();
        assertThat(must).hasSize(2);
        assertTerm(must.get(0), LONG_FIELD, 1L);
        assertTerm(must.get(1), LONG_FIELD, 3L);
    }

    @Test
    void testDisabledChildOperatorIsSkipped() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(term(LONG_FIELD, Condition.EQUALS, "1"))
                .addOperator(ExpressionOperator.builder()
                        .enabled(false)
                        .op(Op.OR)
                        .addTerm(term(LONG_FIELD, Condition.EQUALS, "2"))
                        .build())
                .build();

        final List<Query> must = buildQuery(expression).bool().must();
        assertThat(must).hasSize(1);
        assertTerm(must.getFirst(), LONG_FIELD, 1L);
    }

    @Test
    void testEmptyChildOperatorIsSkipped() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(term(LONG_FIELD, Condition.EQUALS, "1"))
                .addOperator(ExpressionOperator.builder().op(Op.OR).build())
                .build();

        final List<Query> must = buildQuery(expression).bool().must();
        assertThat(must).hasSize(1);
        assertTerm(must.getFirst(), LONG_FIELD, 1L);
    }

    @Test
    void testAndOperator() {
        final BoolQuery bool = buildQuery(ExpressionOperator.builder()
                .op(Op.AND)
                .addTerm(term(LONG_FIELD, Condition.EQUALS, "1"))
                .addTerm(term(KEYWORD_FIELD, Condition.EQUALS, "abc"))
                .build()).bool();

        assertThat(bool.should()).isEmpty();
        assertThat(bool.mustNot()).isEmpty();
        assertThat(bool.must()).hasSize(2);
        assertTerm(bool.must().get(0), LONG_FIELD, 1L);
        assertTerm(bool.must().get(1), KEYWORD_FIELD, "abc");
    }

    @Test
    void testOrOperator() {
        final BoolQuery bool = buildQuery(ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(term(LONG_FIELD, Condition.EQUALS, "1"))
                .addTerm(term(KEYWORD_FIELD, Condition.EQUALS, "abc"))
                .build()).bool();

        assertThat(bool.must()).isEmpty();
        assertThat(bool.mustNot()).isEmpty();
        assertThat(bool.should()).hasSize(2);
        assertTerm(bool.should().get(0), LONG_FIELD, 1L);
        assertTerm(bool.should().get(1), KEYWORD_FIELD, "abc");
    }

    @Test
    void testNotOperator() {
        final BoolQuery bool = buildQuery(ExpressionOperator.builder()
                .op(Op.NOT)
                .addTerm(term(LONG_FIELD, Condition.EQUALS, "1"))
                .addTerm(term(KEYWORD_FIELD, Condition.EQUALS, "abc"))
                .build()).bool();

        // NOT(a, b) matches what matches neither a nor b
        assertThat(bool.must()).isEmpty();
        assertThat(bool.should()).isEmpty();
        assertThat(bool.mustNot()).hasSize(2);
        assertTerm(bool.mustNot().get(0), LONG_FIELD, 1L);
        assertTerm(bool.mustNot().get(1), KEYWORD_FIELD, "abc");
    }

    @Test
    void testNotOperatorWithOneChild() {
        final BoolQuery bool = buildQuery(ExpressionOperator.builder()
                .op(Op.NOT)
                .addTerm(term(LONG_FIELD, Condition.EQUALS, "1"))
                .build()).bool();

        assertThat(bool.mustNot()).hasSize(1);
        assertTerm(bool.mustNot().getFirst(), LONG_FIELD, 1L);
    }

    @Test
    void testNestedOperators() {
        // AND(id = 1, OR(tag = a, NOT(count > 5)), timestamp < DATE_1)
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(Op.AND)
                .addTerm(term(ID_FIELD, Condition.EQUALS, "1"))
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTerm(term(KEYWORD_FIELD, Condition.EQUALS, "a"))
                        .addOperator(ExpressionOperator.builder()
                                .op(Op.NOT)
                                .addTerm(term(INTEGER_FIELD, Condition.GREATER_THAN, "5"))
                                .build())
                        .build())
                .addTerm(term(DATE_FIELD, Condition.LESS_THAN, DATE_1))
                .build();

        final List<Query> must = buildQuery(expression).bool().must();
        assertThat(must).hasSize(3);
        assertTerm(must.get(0), ID_FIELD, 1L);
        assertThat(rangeValue(assertRange(must.get(2), DATE_FIELD).lt())).isEqualTo(DATE_1_MS);

        final List<Query> should = must.get(1).bool().should();
        assertThat(should).hasSize(2);
        assertTerm(should.get(0), KEYWORD_FIELD, "a");

        final List<Query> mustNot = should.get(1).bool().mustNot();
        assertThat(mustNot).hasSize(1);
        assertThat(rangeValue(assertRange(mustNot.getFirst(), INTEGER_FIELD).gt())).isEqualTo(5L);
    }

    // --------------------------------------------------------------------------------
    // Nested fields
    // --------------------------------------------------------------------------------

    @Test
    void testNestedFieldOneLevel() {
        addField("user", FieldType.NESTED, "nested");
        addField("user.name", FieldType.TEXT, "text");

        final Query query = buildSingleTerm("user.name", Condition.EQUALS, "bob");

        final Query leaf = assertNestedPaths(query, "user");
        assertQueryString(leaf, "user.name", "bob");
    }

    @Test
    void testNestedFieldSeveralLevels() {
        addField("a", FieldType.NESTED, "nested");
        addField("a.b", FieldType.NESTED, "nested");
        addField("a.b.c", FieldType.NESTED, "nested");
        addField("a.b.c.d", FieldType.LONG, "long");

        final Query query = buildSingleTerm("a.b.c.d", Condition.EQUALS, "7");

        final Query leaf = assertNestedPaths(query, "a", "a.b", "a.b.c");
        assertTerm(leaf, "a.b.c.d", 7L);
    }

    @Test
    void testNestedFieldSkipsObjectAncestors() {
        addField("a", FieldType.NESTED, "nested");
        addField("a.b", FieldType.TEXT, "object");
        addField("a.b.c", FieldType.NESTED, "nested");
        addField("a.b.c.d", FieldType.KEYWORD, "keyword");

        final Query query = buildSingleTerm("a.b.c.d", Condition.EQUALS, "x");

        final Query leaf = assertNestedPaths(query, "a", "a.b.c");
        assertTerm(leaf, "a.b.c.d", "x");
    }

    @Test
    void testNestedFieldSkipsUnknownAncestors() {
        // `a` is not in the field cache at all
        addField("a.b", FieldType.NESTED, "nested");
        addField("a.b.c", FieldType.KEYWORD, "keyword");

        final Query query = buildSingleTerm("a.b.c", Condition.EQUALS, "x");

        final Query leaf = assertNestedPaths(query, "a.b");
        assertTerm(leaf, "a.b.c", "x");
    }

    @Test
    void testObjectFieldIsNotNested() {
        addField("obj", FieldType.TEXT, "object");
        addField("obj.value", FieldType.LONG, "long");

        assertTerm(buildSingleTerm("obj.value", Condition.EQUALS, "3"), "obj.value", 3L);
    }

    @Test
    void testMultiFieldUnderNestedField() {
        addField("user", FieldType.NESTED, "nested");
        addField("user.name", FieldType.TEXT, "text");
        addField("user.name.keyword", FieldType.KEYWORD, "keyword");

        final Query query = buildSingleTerm("user.name.keyword", Condition.EQUALS, "bob");

        final Query leaf = assertNestedPaths(query, "user");
        assertTerm(leaf, "user.name.keyword", "bob");
    }

    @Test
    void testNestedFieldNotEqualsNegatesOutsideNested() {
        addNestedUserFields();

        final Query inner = assertNegated(buildSingleTerm("user.age", Condition.NOT_EQUALS, "30"));

        assertTerm(assertNestedPaths(inner, "user"), "user.age", 30L);
    }

    @Test
    void testNestedFieldExists() {
        addNestedUserFields();

        final Query query = buildSingleTerm("user.age", Condition.EQUALS, "*");

        final Query leaf = assertNestedPaths(query, "user");
        assertThat(leaf.isExists()).isTrue();
        assertThat(leaf.exists().field()).isEqualTo("user.age");
    }

    @Test
    void testNestedFieldNotExists() {
        addNestedUserFields();

        final Query inner = assertNegated(buildSingleTerm("user.age", Condition.NOT_EQUALS, "*"));

        final Query leaf = assertNestedPaths(inner, "user");
        assertThat(leaf.isExists()).isTrue();
    }

    @Test
    void testNestedFieldScalarIn() {
        addNestedUserFields();

        final Query leaf = assertNestedPaths(buildSingleTerm("user.age", Condition.IN, "1,2"), "user");

        assertThat(leaf.isTerms()).isTrue();
        assertThat(leaf.terms().terms().value()).extracting(FieldValue::_get).containsExactly(1L, 2L);
    }

    @Test
    void testNestedFieldRange() {
        addNestedUserFields();

        final Query leaf = assertNestedPaths(buildSingleTerm("user.age", Condition.BETWEEN, "1,2"), "user");

        final UntypedRangeQuery range = assertRange(leaf, "user.age");
        assertThat(rangeValue(range.gte())).isEqualTo(1L);
        assertThat(rangeValue(range.lte())).isEqualTo(2L);
    }

    @Test
    void testNestedFieldStringIn() {
        addNestedUserFields();

        final Query leaf = assertNestedPaths(buildSingleTerm("user.role", Condition.IN, "a,b"), "user");

        // The whole OR is wrapped once, rather than each term individually
        assertThat(leaf.bool().should()).hasSize(2);
        assertTerm(leaf.bool().should().get(0), "user.role", "a");
        assertTerm(leaf.bool().should().get(1), "user.role", "b");
    }

    @Test
    void testNestedFieldRegex() {
        addNestedUserFields();

        final Query leaf = assertNestedPaths(buildSingleTerm("user.role", Condition.MATCHES_REGEX, "a.*"), "user");

        assertThat(leaf.isRegexp()).isTrue();
        assertThat(leaf.regexp().field()).isEqualTo("user.role");
    }

    @Test
    void testNestedFieldInDictionary() {
        addNestedUserFields();
        when(wordListProvider.getWords(DICTIONARY_REF)).thenReturn(new String[]{"a", "b"});

        final Query query = buildSingleTerm(dictionaryTerm("user.role"));

        // The combined dictionary query is wrapped once
        final Query leaf = assertNestedPaths(query, "user");
        final List<Query> lines = assertDictionaryLines(leaf, 2);
        assertTerm(lines.get(0), "user.role", "a");
        assertTerm(lines.get(1), "user.role", "b");
    }

    @Test
    void testNestedTermsOfTheSameObjectAreGrouped() {
        addNestedUserFields();

        // Both terms must match the same user
        final Query query = buildQuery(ExpressionOperator.builder()
                .addTerm(term("user.age", Condition.EQUALS, "30"))
                .addTerm(term("user.role", Condition.EQUALS, "admin"))
                .build());

        final List<Query> groupMust = assertGroup(singleMust(query), "user", 2);
        assertTerm(groupMust.get(0), "user.age", 30L);
        assertTerm(groupMust.get(1), "user.role", "admin");
    }

    @Test
    void testNestedNotEqualsTermIsGroupedWithOtherTermsOfTheSameObject() {
        addNestedUserFields();

        // A user aged 30 who isn't an admin
        final Query query = buildQuery(ExpressionOperator.builder()
                .addTerm(term("user.age", Condition.EQUALS, "30"))
                .addTerm(term("user.role", Condition.NOT_EQUALS, "admin"))
                .build());

        final List<Query> groupMust = assertGroup(singleMust(query), "user", 2);
        assertTerm(groupMust.get(0), "user.age", 30L);
        assertTerm(assertNegated(groupMust.get(1)), "user.role", "admin");
    }

    @Test
    void testNestedTermsAreGroupedApartFromOtherTerms() {
        addNestedUserFields();

        final BoolQuery bool = buildQuery(ExpressionOperator.builder()
                .addTerm(term(ID_FIELD, Condition.EQUALS, "1"))
                .addTerm(term("user.age", Condition.EQUALS, "30"))
                .addTerm(term("user.role", Condition.EQUALS, "admin"))
                .build()).bool();

        assertThat(bool.must()).hasSize(2);
        assertTerm(bool.must().get(0), ID_FIELD, 1L);
        assertGroup(bool.must().get(1), "user", 2);
    }

    @Test
    void testOperatorOfTermsOfTheSameObjectIsGroupedWithThem() {
        addNestedUserFields();

        // A user aged 30 who is an admin or an owner
        final Query query = buildQuery(ExpressionOperator.builder()
                .addTerm(term("user.age", Condition.EQUALS, "30"))
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTerm(term("user.role", Condition.EQUALS, "admin"))
                        .addTerm(term("user.role", Condition.EQUALS, "owner"))
                        .build())
                .build());

        final List<Query> groupMust = assertGroup(singleMust(query), "user", 2);
        assertTerm(groupMust.get(0), "user.age", 30L);
        final List<Query> or = groupMust.get(1).bool().should();
        assertThat(or).hasSize(2);
        // Already inside the nested query, so not wrapped again
        assertTerm(or.get(0), "user.role", "admin");
        assertTerm(or.get(1), "user.role", "owner");
    }

    @Test
    void testOperatorMixingObjectsIsNotGrouped() {
        addNestedUserFields();

        final BoolQuery bool = buildQuery(ExpressionOperator.builder()
                .addTerm(term("user.age", Condition.EQUALS, "30"))
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTerm(term("user.role", Condition.EQUALS, "admin"))
                        .addTerm(term(ID_FIELD, Condition.EQUALS, "1"))
                        .build())
                .build()).bool();

        // Each user term is wrapped on its own (the order of `must` clauses doesn't matter)
        assertThat(bool.must()).hasSize(2);
        final Query orQuery = bool.must().stream().filter(Query::isBool).findFirst().orElseThrow();
        final Query ageQuery = bool.must().stream().filter(Query::isNested).findFirst().orElseThrow();
        final List<Query> or = orQuery.bool().should();
        assertTerm(assertNestedPaths(or.get(0), "user"), "user.role", "admin");
        assertTerm(or.get(1), ID_FIELD, 1L);
        assertTerm(assertNestedPaths(ageQuery, "user"), "user.age", 30L);
    }

    @Test
    void testObjectsNestedWithinAGroupedObjectAreGrouped() {
        addNestedUserFields();
        addField("user.sessions", FieldType.NESTED, "nested");
        addField("user.sessions.device", FieldType.KEYWORD, "keyword");
        addField("user.sessions.ip", FieldType.IPV4_ADDRESS, "ip");

        // An admin with a session on a phone from 10.0.0.1
        final Query query = buildQuery(ExpressionOperator.builder()
                .addTerm(term("user.role", Condition.EQUALS, "admin"))
                .addTerm(term("user.sessions.device", Condition.EQUALS, "phone"))
                .addTerm(term("user.sessions.ip", Condition.EQUALS, "10.0.0.1"))
                .build());

        final List<Query> userMust = assertGroup(singleMust(query), "user", 2);
        assertTerm(userMust.get(0), "user.role", "admin");
        final List<Query> sessionMust = assertGroup(userMust.get(1), "user.sessions", 2);
        assertTerm(sessionMust.get(0), "user.sessions.device", "phone");
        assertTerm(sessionMust.get(1), "user.sessions.ip", "10.0.0.1");
    }

    @Test
    void testSingleObjectWithinAGroupedObjectWrapsOnlyItsOwnLevel() {
        addNestedUserFields();
        addField("user.sessions", FieldType.NESTED, "nested");
        addField("user.sessions.device", FieldType.KEYWORD, "keyword");

        // An admin with a session on a phone
        final Query query = buildQuery(ExpressionOperator.builder()
                .addTerm(term("user.role", Condition.EQUALS, "admin"))
                .addTerm(term("user.sessions.device", Condition.EQUALS, "phone"))
                .build());

        final List<Query> userMust = assertGroup(singleMust(query), "user", 2);
        assertTerm(userMust.get(0), "user.role", "admin");
        assertTerm(assertNestedPaths(userMust.get(1), "user.sessions"), "user.sessions.device", "phone");
    }

    @Test
    void testNotOperatorDoesNotGroupTermsOfTheSameObject() {
        addNestedUserFields();

        // No user aged 30, and no admin user
        final BoolQuery bool = buildQuery(ExpressionOperator.builder()
                .op(Op.NOT)
                .addTerm(term("user.age", Condition.EQUALS, "30"))
                .addTerm(term("user.role", Condition.EQUALS, "admin"))
                .build()).bool();

        assertThat(bool.mustNot()).hasSize(2);
        assertTerm(assertNestedPaths(bool.mustNot().get(0), "user"), "user.age", 30L);
        assertTerm(assertNestedPaths(bool.mustNot().get(1), "user"), "user.role", "admin");
    }

    @Test
    void testNotOperatorOfAGroupedAndOperator() {
        addNestedUserFields();

        // No user who is both aged 30 and an admin
        final BoolQuery bool = buildQuery(ExpressionOperator.builder()
                .op(Op.NOT)
                .addOperator(ExpressionOperator.builder()
                        .addTerm(term("user.age", Condition.EQUALS, "30"))
                        .addTerm(term("user.role", Condition.EQUALS, "admin"))
                        .build())
                .build()).bool();

        assertThat(bool.mustNot()).hasSize(1);
        assertGroup(singleMust(bool.mustNot().getFirst()), "user", 2);
    }

    @Test
    void testOrOperatorDoesNotGroupTermsOfTheSameObject() {
        addNestedUserFields();

        // Grouping would make no difference to the result
        final BoolQuery bool = buildQuery(ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(term("user.age", Condition.EQUALS, "30"))
                .addTerm(term("user.role", Condition.EQUALS, "admin"))
                .build()).bool();

        assertThat(bool.should()).hasSize(2);
        assertTerm(assertNestedPaths(bool.should().get(0), "user"), "user.age", 30L);
        assertTerm(assertNestedPaths(bool.should().get(1), "user"), "user.role", "admin");
    }

    @Test
    void testDisabledTermIsNotGrouped() {
        addNestedUserFields();

        final Query query = buildQuery(ExpressionOperator.builder()
                .addTerm(term("user.age", Condition.EQUALS, "30"))
                .addTerm(ExpressionTerm.builder()
                        .field("user.role")
                        .condition(Condition.EQUALS)
                        .value("admin")
                        .enabled(false)
                        .build())
                .build());

        assertTerm(assertNestedPaths(singleMust(query), "user"), "user.age", 30L);
    }

    // --------------------------------------------------------------------------------
    // Invalid input
    // --------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {ID_FIELD, INTEGER_FIELD, LONG_FIELD})
    void testUnparseableWholeNumber(final String field) {
        for (final String value : List.of("abc", "1.5", "1e3", "12abc")) {
            assertThatThrownBy(() -> buildSingleTerm(field, Condition.EQUALS, value))
                    .isInstanceOf(NumberFormatException.class)
                    .hasMessage("Expected a numeric value for field \"" + field +
                                "\" but was given string \"" + value + "\"");
        }
    }

    @Test
    void testUnparseableFloat() {
        assertThatThrownBy(() -> buildSingleTerm(FLOAT_FIELD, Condition.EQUALS, "abc"))
                .isInstanceOf(NumberFormatException.class)
                .hasMessage("Expected a decimal (float) value for field \"" + FLOAT_FIELD +
                            "\" but was given string \"abc\"");
    }

    @Test
    void testUnparseableDouble() {
        assertThatThrownBy(() -> buildSingleTerm(DOUBLE_FIELD, Condition.GREATER_THAN, "1.2.3"))
                .isInstanceOf(NumberFormatException.class)
                .hasMessage("Expected a decimal (double) value for field \"" + DOUBLE_FIELD +
                            "\" but was given string \"1.2.3\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a date", "2021-13-45T00:00:00.000Z", "yesterday"})
    void testUnparseableDate(final String value) {
        assertThatThrownBy(() -> buildSingleTerm(DATE_FIELD, Condition.EQUALS, value))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Expected a standard date value for field \"" + DATE_FIELD +
                            "\" but was given string \"" + value + "\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.2.3", "1.2.3.4.5", "1234.1.1.1", "1.2.3.a", "::1"})
    void testUnparseableIpAddress(final String value) {
        assertThatThrownBy(() -> buildSingleTerm(IP_FIELD, Condition.EQUALS, value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid IPv4 address format: " + value + " for field \"" + IP_FIELD + "\"");
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
    void testUnparseableValueInList() {
        assertThatThrownBy(() -> buildSingleTerm(LONG_FIELD, Condition.IN, "1, two, 3"))
                .isInstanceOf(NumberFormatException.class)
                .hasMessageContaining("\"two\"");
    }

    @Test
    void testUnparseableDictionaryWord() {
        when(wordListProvider.getWords(DICTIONARY_REF)).thenReturn(new String[]{"1", "two"});

        assertThatThrownBy(() -> buildSingleTerm(dictionaryTerm(LONG_FIELD)))
                .isInstanceOf(NumberFormatException.class)
                .hasMessageContaining("\"two\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1,2,3", "1,,"})
    void testBetweenWithWrongNumberOfValues(final String value) {
        final int count = (int) Arrays.stream(value.split(",")).filter(s -> !s.isBlank()).count();

        assertThatThrownBy(() -> buildSingleTerm(LONG_FIELD, Condition.BETWEEN, value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Two values needed for between query. Only " + count + " provided");
    }

    @Test
    void testUnknownField() {
        assertThatThrownBy(() -> buildSingleTerm("nope", Condition.EQUALS, "x"))
                .isInstanceOf(SearchException.class)
                .hasMessage("Field not found in index: nope");
    }

    @Test
    void testNonElasticField() {
        indexFieldCache.put("other", mock(IndexField.class));

        assertThatThrownBy(() -> buildSingleTerm("other", Condition.EQUALS, "x"))
                .isInstanceOf(SearchException.class)
                .hasMessage("Field not found in index: other");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void testFieldNotSet(final String field) {
        assertThatThrownBy(() -> buildSingleTerm(field, Condition.EQUALS, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Field not set");
    }

    @Test
    void testNullField() {
        assertThatThrownBy(() -> buildSingleTerm(null, Condition.EQUALS, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Field not set");
    }

    @Test
    void testDictionaryNotSet() {
        final ExpressionTerm term = ExpressionTerm.builder()
                .field(KEYWORD_FIELD)
                .condition(Condition.IN_DICTIONARY)
                .value("dict")
                .build();

        assertThatThrownBy(() -> buildSingleTerm(term))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dictionary not set for field: " + KEYWORD_FIELD);
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
                // The second value is beyond the range of a long, so is passed on as its decimal string
                new ScalarCase(UNSIGNED_LONG_FIELD, "9223372036854775807", "18446744073709551615",
                        9223372036854775807L, "18446744073709551615"),
                // Versions are passed on as they are, for Elasticsearch to compare by precedence
                new ScalarCase(VERSION_FIELD, "1.2.0", "10.0.0", "1.2.0", "10.0.0"),
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
        final Stream<Arguments> stringFields = Stream.of(TEXT_FIELD, KEYWORD_FIELD, WILDCARD_FIELD)
                .flatMap(field -> EnumSet.complementOf(EnumSet.copyOf(STRING_CONDITIONS))
                        .stream()
                        .map(condition -> Arguments.of(field, condition)));
        // A boolean field is only compared with one of its two values, so IN, IN_DICTIONARY and MATCHES_REGEX
        // aren't supported
        final Stream<Arguments> booleanField = EnumSet.complementOf(EnumSet.copyOf(BOOLEAN_CONDITIONS))
                .stream()
                .map(condition -> Arguments.of(BOOLEAN_FIELD, condition));
        return Stream.concat(stringFields, booleanField);
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
    /// Asserts a single `nested` query for the path, grouping the expected number of queries that must all
    /// match the same object, and returns them.
    private static List<Query> assertGroup(final Query query, final String expectedPath, final int expectedSize) {
        assertThat(query.isNested()).as("Is a nested query for path " + expectedPath).isTrue();
        final NestedQuery nested = query.nested();
        assertThat(nested.path()).isEqualTo(expectedPath);
        assertThat(nested.query().isBool()).as("Is a group of queries").isTrue();
        final List<Query> must = nested.query().bool().must();
        assertThat(must).hasSize(expectedSize);
        return must;
    }

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
