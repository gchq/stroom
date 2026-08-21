package stroom.query.common.v2;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-memory half of the {@link ConditionSet} promise: every condition the UI sets declare must
 * actually be handled by {@link ExpressionPredicateFactory}.
 * <p>
 * See {@code TestSqlConditionSetConformance} for the SQL half. Between them they pin the property
 * that was broken three times before anyone noticed - a set declaring less, or more, than its
 * evaluator can do.
 */
class TestInMemoryConditionSetConformance {

    private static final QueryField TEXT_FIELD = QueryField.createUiText("name");
    private static final ValueFunctionFactories<String> TEXT_VALUES =
            StringValueFunctionFactory.create(TEXT_FIELD);
    private static final DocRef A_DICTIONARY = DocRef.builder()
            .type("Dictionary")
            .uuid("dict-uuid")
            .name("MyDictionary")
            .build();

    /**
     * IN_DICTIONARY resolves its value to a dictionary before it can build a predicate, so the
     * evaluator needs one that resolves. The contents do not matter - this asserts the condition is
     * handled, not what it matches.
     */
    private static final WordListProvider WORD_LIST_PROVIDER = mockWordListProvider();

    private static WordListProvider mockWordListProvider() {
        final WordListProvider mock = Mockito.mock(WordListProvider.class);
        Mockito.when(mock.findByName(Mockito.anyString())).thenReturn(List.of(A_DICTIONARY));
        Mockito.when(mock.findByUuid(Mockito.anyString())).thenReturn(Optional.of(A_DICTIONARY));
        Mockito.when(mock.getWords(Mockito.any())).thenReturn(new String[0]);
        return mock;
    }

    @TestFactory
    Stream<DynamicTest> everyConditionInAllUiTextIsHandled() {
        return ConditionSet.ALL_UI_TEXT.getConditionList().stream()
                .map(condition -> DynamicTest.dynamicTest("ALL_UI_TEXT -> " + condition.name(), () -> {
                    final Optional<Predicate<String>> predicate = createPredicate(condition);
                    assertThat(predicate)
                            .describedAs("ALL_UI_TEXT declares %s, so ExpressionPredicateFactory "
                                         + "must handle it", condition.name())
                            .isPresent();
                }));
    }

    /**
     * The quick filter surfaces that evaluate in memory all declare ALL_UI_TEXT, so every sigil a
     * user can type has to land somewhere it is honoured. This is the same property
     * {@code TestQuickFilterPredicateFactory} asserts at the parser end, checked here at the
     * evaluator end.
     */
    @Test
    void allUiTextCoversEverySigilTheParserCanEmit() {
        for (final Condition condition : new Condition[]{
                Condition.CONTAINS,
                Condition.EQUALS,
                Condition.STARTS_WITH,
                Condition.ENDS_WITH,
                Condition.GREATER_THAN,
                Condition.GREATER_THAN_OR_EQUAL_TO,
                Condition.LESS_THAN,
                Condition.LESS_THAN_OR_EQUAL_TO,
                Condition.MATCHES_REGEX,
                Condition.WORD_BOUNDARY,
                Condition.CONTAINS_CASE_SENSITIVE,
                Condition.EQUALS_CASE_SENSITIVE,
                Condition.STARTS_WITH_CASE_SENSITIVE,
                Condition.ENDS_WITH_CASE_SENSITIVE,
                Condition.MATCHES_REGEX_CASE_SENSITIVE}) {
            assertThat(ConditionSet.ALL_UI_TEXT.supportsCondition(condition))
                    .describedAs("the quick filter can spell %s, so an in-memory surface must "
                                 + "declare it", condition.name())
                    .isTrue();
        }
    }

    /**
     * {@code ~foo} works today only because the parser rewrites it to MATCHES_REGEX - there is no
     * CHARS_ANYWHERE condition. Spec §5.2 adds one, and when it does it has to join ALL_UI_TEXT and
     * SQL_TEXT in the same change, or {@code ~foo} starts being rejected everywhere at once.
     * <p>
     * Pinned by name so the day someone adds the enum constant, this test fails and points at the
     * two sets that need it.
     */
    @Test
    void charsAnywhereIsNotYetARealCondition() {
        assertThat(Stream.of(Condition.values()).map(Enum::name))
                .describedAs("spec §5.2 has landed - add CHARS_ANYWHERE to ALL_UI_TEXT and "
                             + "SQL_TEXT, and give it a case in both evaluators")
                .doesNotContain("CHARS_ANYWHERE");
    }

    private static Optional<Predicate<String>> createPredicate(final Condition condition) {
        final ExpressionOperator operator = ExpressionOperator
                .builder()
                .addTerm(ExpressionTerm
                        .builder()
                        .field(TEXT_FIELD.getFldName())
                        .condition(condition)
                        .value(valueFor(condition))
                        .build())
                .build();
        return new ExpressionPredicateFactory(WORD_LIST_PROVIDER)
                .createOptional(operator, TEXT_VALUES, DateTimeSettings.builder().build());
    }

    private static String valueFor(final Condition condition) {
        return switch (condition) {
            case IN -> "aaa,bbb";
            case IN_DICTIONARY -> A_DICTIONARY.getName();
            case MATCHES_REGEX, MATCHES_REGEX_CASE_SENSITIVE -> "a.*z";
            default -> "someValue";
        };
    }
}
