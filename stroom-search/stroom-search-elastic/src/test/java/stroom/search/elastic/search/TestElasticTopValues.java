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

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticTopValues {

    private static final String TIME_FIELD = "@timestamp";

    private static final Function<String, String> NO_OTHER_FIELDS = name -> null;

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "wildcard", "ip", "boolean", "integer", "short", "byte", "long"})
    void testGetAggregationField_aggregatableTypes(final String nativeType) {
        assertThat(ElasticTopValues.getAggregationField("field", nativeType, NO_OTHER_FIELDS))
                .isEqualTo("field");
    }

    @ParameterizedTest
    @ValueSource(strings = {"date", "date_nanos", "float", "double", "unsigned_long", "version",
            "constant_keyword", "dense_vector", "nested"})
    void testGetAggregationField_unsupportedTypes(final String nativeType) {
        assertThat(ElasticTopValues.getAggregationField("field", nativeType, NO_OTHER_FIELDS))
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "match_only_text"})
    void testGetAggregationField_textUsesKeywordMultiField(final String nativeType) {
        final Map<String, String> types = Map.of("message.keyword", "keyword");

        assertThat(ElasticTopValues.getAggregationField("message", nativeType, types::get))
                .isEqualTo("message.keyword");
    }

    @Test
    void testGetAggregationField_textWithoutKeywordMultiField() {
        final Map<String, String> types = Map.of("message.raw", "keyword", "message.keyword", "text");

        assertThat(ElasticTopValues.getAggregationField("message", "text", types::get))
                .isNull();
    }

    @Test
    void testGetAggregationField_withinNestedObject() {
        final Map<String, String> types = Map.of("user", "nested", "event", "object");

        assertThat(ElasticTopValues.getAggregationField("user.name", "keyword", types::get))
                .isNull();
        // Fields of plain objects can be aggregated
        assertThat(ElasticTopValues.getAggregationField("event.action", "keyword", types::get))
                .isEqualTo("event.action");
    }

    @Test
    void testGetAggregationField_withinDeeperNestedObject() {
        final Map<String, String> types = Map.of("a", "object", "a.b", "nested");

        assertThat(ElasticTopValues.getAggregationField("a.b.c", "keyword", types::get))
                .isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "keyword, true",
            "wildcard, true",
            "ip, false",
            "boolean, false",
            "long, false"
    })
    void testIsFilterable(final String nativeType, final boolean expected) {
        assertThat(ElasticTopValues.isFilterable(nativeType))
                .isEqualTo(expected);
    }

    @Test
    void testToContainsRegex_caseSensitive() {
        assertThat(ElasticTopValues.toContainsRegex("Admin", false))
                .isEqualTo(".*Admin.*");
    }

    @Test
    void testToContainsRegex_caseInsensitive() {
        assertThat(ElasticTopValues.toContainsRegex("Ad1-", true))
                .isEqualTo(".*[aA][dD]1-.*");
    }

    @Test
    void testToContainsRegex_escapesSpecialCharacters() {
        final String filter = "a.b*c?(d)[e]{f}|g+h\"i\\j#k@l&m<n>o~p";
        final String regex = ElasticTopValues.toContainsRegex(filter, false);

        assertThat(regex)
                .isEqualTo(".*a\\.b\\*c\\?\\(d\\)\\[e\\]\\{f\\}\\|g\\+h\\\"i\\\\j\\#k\\@l\\&m\\<n\\>o\\~p.*");
        // Lucene's syntax is close enough to Java's for these characters to check the value is matched literally
        assertThat(Pattern.compile(regex.replace("\\#", "#")
                        .replace("\\@", "@")
                        .replace("\\&", "&")
                        .replace("\\<", "<")
                        .replace("\\>", ">")
                        .replace("\\~", "~")
                        .replace("\\\"", "\""))
                .matcher("xx" + filter + "yy")
                .matches())
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Condition.class, names = {
            "GREATER_THAN", "GREATER_THAN_OR_EQUAL_TO", "LESS_THAN", "LESS_THAN_OR_EQUAL_TO", "BETWEEN"})
    void testGetTimeRangeTerms_rangeConditions(final Condition condition) {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(TIME_FIELD, condition, "2026-10-01T00:00:00.000Z")
                .build();

        assertThat(ElasticTopValues.getTimeRangeTerms(expression, TIME_FIELD))
                .extracting(ExpressionTerm::getCondition)
                .containsExactly(condition);
    }

    @ParameterizedTest
    @EnumSource(value = Condition.class, names = {"EQUALS", "NOT_EQUALS", "IS_NULL", "IS_NOT_NULL"})
    void testGetTimeRangeTerms_otherConditionsIgnored(final Condition condition) {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(TIME_FIELD, condition, "2026-10-01T00:00:00.000Z")
                .build();

        assertThat(ElasticTopValues.getTimeRangeTerms(expression, TIME_FIELD))
                .isEmpty();
    }

    @Test
    void testGetTimeRangeTerms_nestedAndOperators() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(TIME_FIELD, Condition.GREATER_THAN_OR_EQUAL_TO, "from")
                .addOperator(ExpressionOperator.builder()
                        .addTerm(TIME_FIELD, Condition.LESS_THAN, "to")
                        .addTerm("user", Condition.EQUALS, "alice")
                        .build())
                .build();

        assertThat(ElasticTopValues.getTimeRangeTerms(expression, TIME_FIELD))
                .extracting(ExpressionTerm::getValue)
                .containsExactly("from", "to");
    }

    @Test
    void testGetTimeRangeTerms_orAndNotIgnored() {
        // A condition under OR or NOT doesn't always limit the time range
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTerm(TIME_FIELD, Condition.GREATER_THAN, "or")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addTerm(TIME_FIELD, Condition.GREATER_THAN, "not")
                        .build())
                .build();

        assertThat(ElasticTopValues.getTimeRangeTerms(expression, TIME_FIELD))
                .isEmpty();
        assertThat(ElasticTopValues.getTimeRangeTerms(
                ExpressionOperator.builder().op(Op.OR).addTerm(TIME_FIELD, Condition.GREATER_THAN, "x").build(),
                TIME_FIELD))
                .isEmpty();
    }

    @Test
    void testGetTimeRangeTerms_disabledIgnored() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .enabled(false)
                        .field(TIME_FIELD)
                        .condition(Condition.GREATER_THAN)
                        .value("term")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .enabled(false)
                        .addTerm(TIME_FIELD, Condition.GREATER_THAN, "operator")
                        .build())
                .build();

        assertThat(ElasticTopValues.getTimeRangeTerms(expression, TIME_FIELD))
                .isEmpty();
    }

    @Test
    void testGetTimeRangeTerms_otherFieldsAndBlankValuesIgnored() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm("EventTime", Condition.GREATER_THAN, "2026-10-01T00:00:00.000Z")
                .addTerm(TIME_FIELD, Condition.GREATER_THAN, " ")
                .build();

        assertThat(ElasticTopValues.getTimeRangeTerms(expression, TIME_FIELD))
                .isEmpty();
    }

    @Test
    void testGetTimeRangeTerms_noExpressionOrTimeField() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(TIME_FIELD, Condition.GREATER_THAN, "2026-10-01T00:00:00.000Z")
                .build();

        assertThat(ElasticTopValues.getTimeRangeTerms(null, TIME_FIELD))
                .isEmpty();
        assertThat(ElasticTopValues.getTimeRangeTerms(expression, null))
                .isEmpty();
    }
}
