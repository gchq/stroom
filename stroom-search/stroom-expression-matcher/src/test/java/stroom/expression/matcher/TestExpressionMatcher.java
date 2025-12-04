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

package stroom.expression.matcher;

import stroom.data.shared.StreamTypeNames;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.query.api.datasource.QueryField;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This ought to behave in a consistent way with CommonExpressionMapper
 */
@ExtendWith(MockitoExtension.class)
class TestExpressionMatcher {

    @Mock
    private WordListProvider mockWordListProvider;

    public static final QueryField FEED = QueryField.createDocRefByUniqueName("Feed", "Feed");
    private static final QueryField TYPE = QueryField.createText("Type");
    private static final QueryField FRUIT = QueryField.createText("Fruit");
    private static final Map<String, QueryField> FIELD_MAP = Map.of(
            FEED.getFldName(),
            FEED,
            TYPE.getFldName(),
            TYPE);

    @Test
    void testSimpleMatch_match() {
        test(createAttributeMap(), createExpression(Op.AND, "TEST_FEED"), true);
    }

    @Test
    void testSimpleMatch_noMatch() {
        test(createAttributeMap(), createExpression(Op.AND, "XXX"), false);
    }

    @Test
    void testEnabledState() {
        // TEST_FEED term is disabled so there should be no match
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
        builder.addTextTerm(FEED, Condition.EQUALS, "FOO");
        final ExpressionTerm disabledTerm = ExpressionTerm.builder()
                .field(FEED.getFldName())
                .condition(Condition.EQUALS)
                .value("TEST_FEED")
                .enabled(false)
                .build();
        builder.addTerm(disabledTerm);
        final ExpressionOperator expressionOperator = builder.build();

        test(createAttributeMap(), expressionOperator, false);
    }

    @TestFactory
    List<DynamicTest> makeEmptyOpTests() {
        return Arrays.stream(ExpressionOperator.Op.values())
                .map(opType -> DynamicTest.dynamicTest(opType.getDisplayValue(), () -> {
                    final ExpressionOperator expressionOperator = ExpressionOperator.builder().op(opType).build();

                    test(createAttributeMap(), expressionOperator, true);
                }))
                .collect(Collectors.toList());
    }

    @TestFactory
    List<DynamicTest> makeDisabledEmptyOpTests() {
        return Arrays.stream(ExpressionOperator.Op.values())
                .map(opType -> DynamicTest.dynamicTest(opType.getDisplayValue(), () -> {
                    final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                            .op(opType)
                            .enabled(false)
                            .build();

                    test(createAttributeMap(), expressionOperator, true);
                }))
                .collect(Collectors.toList());
    }

    @Test
    void testLeadingWildcardMatch() {
        test(createAttributeMap(), createExpression(Op.AND, "*FEED"), true);
    }

    @Test
    void testTrailingWildcardMatch() {
        test(createAttributeMap(), createExpression(Op.AND, "TEST*"), true);
    }

    @Test
    void testNotMatch() {
        test(createAttributeMap(), createExpression(Op.NOT, "TEST_FEED"), false);
    }

    @Test
    void testMatchAll() {
        test(createAttributeMap(), ExpressionOperator.builder().build(), true);
    }

    @Test
    void testRootDisabled() {
        final ExpressionOperator rootOp = ExpressionOperator.builder()
                .op(Op.AND)
                .enabled(false)
                .build();

        test(createAttributeMap(), rootOp, true);
    }

    @Test
    void testMatchNone1() {
        // Null expression same as match all
        test(createAttributeMap(), null, true);
    }

    @Test
    void testMatchNone2() {
        test(createAttributeMap(),
                ExpressionOperator.builder()
                        .enabled(false)
                        .build(),
                true);
    }

    @Test
    void testInDictionary() {
        final DocRef docRef = DictionaryDoc.buildDocRef()
                .randomUuid()
                .name("foo")
                .build();

        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(
                Map.of(FRUIT.getFldName(), FRUIT),
                mockWordListProvider,
                null,
                DateTimeSettings.builder()
                        .timeZone(UserTimeZone.builder()
                                .use(Use.UTC)
                                .build())
                        .build());

        Mockito.when(mockWordListProvider.getWords(Mockito.eq(docRef)))
                .thenReturn(new String[]{
                        "banana",
                        "orange",
                        "apple",
                        "kiwi fruit"});

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field(FRUIT.getFldName())
                        .condition(Condition.IN_DICTIONARY)
                        .docRef(docRef)
                        .build())
                .build();

        final boolean match = expressionMatcher.match(
                Map.of(FRUIT.getFldName(), "orange"),
                expression);
        assertThat(match)
                .isTrue();

        // This probably ought to work, but stroom.expression.matcher.ExpressionMatcher#isIn
        // is splitting dictionary lines on spaces
//        match = expressionMatcher.match(
//                Map.of(FRUIT.getName(), "kiwi fruit"),
//                expression);
//        assertThat(match)
//                .isTrue();
    }

    private void test(final Map<String, Object> attributeMap,
                      final ExpressionOperator expression,
                      final boolean outcome) {
        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(
                FIELD_MAP,
                null,
                null,
                DateTimeSettings.builder().build());
        assertThat(expressionMatcher.match(attributeMap, expression))
                .isEqualTo(outcome);
    }

    private ExpressionOperator createExpression(final Op op, final String feedName) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(op);
        builder.addTextTerm(FEED, Condition.EQUALS, feedName);
        return builder.build();
    }

    private Map<String, Object> createAttributeMap() {
        final Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put(FEED.getFldName(), "TEST_FEED");
        attributeMap.put(TYPE.getFldName(), StreamTypeNames.RAW_EVENTS);
        return attributeMap;
    }
}
