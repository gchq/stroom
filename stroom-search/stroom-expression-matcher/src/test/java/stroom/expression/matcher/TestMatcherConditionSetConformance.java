package stroom.expression.matcher;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The third evaluator. {@code TermHandler} turns terms into SQL and
 * {@code ExpressionPredicateFactory} evaluates them in memory; this one also evaluates in memory
 * but is much more limited, and backs the explorer's document permission screens.
 * <p>
 * It was missed when the other two conformance suites were written, and the gap cost a regression:
 * {@code DocumentPermissionFields.DOCUMENT_NAME} declared {@code DEFAULT_TEXT}, which has no
 * {@code CONTAINS}, so a bare quick filter term fell back to {@code EQUALS} and matched exactly
 * where it used to match substrings.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestMatcherConditionSetConformance {

    private static final QueryField TEXT_FIELD = QueryField
            .builder()
            .fldName("someField")
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.MATCHER_TEXT)
            .build();

    @Mock
    private WordListProvider wordListProvider;

    @TestFactory
    Stream<DynamicTest> everyConditionInMatcherTextIsHandled() {
        return ConditionSet.MATCHER_TEXT.getConditionList().stream()
                .map(condition -> DynamicTest.dynamicTest("MATCHER_TEXT -> " + condition.name(), () ->
                        assertThat(match(condition, valueFor(condition), "someValue"))
                                .describedAs("MATCHER_TEXT declares %s, so ExpressionMatcher must "
                                             + "handle it", condition.name())
                                .isNotNull()));
    }

    /**
     * The regression this suite exists for. A bare quick filter term resolves to CONTAINS, and on
     * these screens CONTAINS has to mean substring - typing "dash" must find "My Dashboard".
     */
    @Test
    void containsMatchesASubstring() {
        assertThat(match(Condition.CONTAINS, "dash", "My Dashboard")).isTrue();
        assertThat(match(Condition.CONTAINS, "nope", "My Dashboard")).isFalse();
    }

    /**
     * And a bare term does resolve to CONTAINS on a MATCHER_TEXT field, rather than falling back
     * to EQUALS - which is what made the document permission screens match exactly.
     */
    @Test
    void matcherTextDeclaresContainsSoBareTermsAreNotExact() {
        assertThat(ConditionSet.MATCHER_TEXT.supportsCondition(Condition.CONTAINS))
                .describedAs("without this, SimpleStringExpressionParser.defaultCondition falls "
                             + "back to EQUALS and the filter matches exactly")
                .isTrue();
    }

    /**
     * The conditions this evaluator genuinely cannot honour must stay out of the set. If one of
     * these stops throwing, ExpressionMatcher has gained a case and MATCHER_TEXT can declare it.
     */
    @TestFactory
    Stream<DynamicTest> unsupportedConditionsAreNotDeclared() {
        return Stream.of(Condition.STARTS_WITH, Condition.ENDS_WITH, Condition.MATCHES_REGEX,
                        Condition.WORD_BOUNDARY, Condition.CONTAINS_CASE_SENSITIVE)
                .map(condition -> DynamicTest.dynamicTest(condition.name(), () -> {
                    assertThat(ConditionSet.MATCHER_TEXT.supportsCondition(condition)).isFalse();
                    assertThatThrownBy(() -> match(condition, "abc", "xxabcxx"))
                            .describedAs("%s is omitted because ExpressionMatcher throws on it",
                                    condition.name())
                            .isInstanceOf(RuntimeException.class);
                }));
    }

    /**
     * EQUALS and CONTAINS shared one code path until 2026-08-21, which is what made the document
     * permission screens match exactly. They must stay distinguishable.
     */
    @Test
    void equalsIsAnchoredWhereContainsIsNot() {
        assertThat(match(Condition.EQUALS, "dash", "My Dashboard"))
                .describedAs("EQUALS anchors - a partial value must not match")
                .isFalse();
        assertThat(match(Condition.CONTAINS, "dash", "My Dashboard"))
                .describedAs("CONTAINS is a substring match")
                .isTrue();
    }

    /**
     * EQUALS keeps its wildcard behaviour, which existing rules rely on.
     */
    @Test
    void equalsStillHonoursWildcards() {
        assertThat(match(Condition.EQUALS, "*dash*", "My Dashboard")).isTrue();
        assertThat(match(Condition.EQUALS, "My Dashboard", "My Dashboard")).isTrue();
    }

    /**
     * CONTAINS takes the value literally, matching what TermHandler does in SQL.
     */
    @Test
    void containsDoesNotInterpretWildcards() {
        assertThat(match(Condition.CONTAINS, "My*board", "My Dashboard"))
                .describedAs("no wildcard interpretation - the literal substring is not present")
                .isFalse();
    }

    private Boolean match(final Condition condition, final String termValue, final String attribute) {
        final ExpressionMatcher matcher = new ExpressionMatcher(
                Map.of(TEXT_FIELD.getFldName(), TEXT_FIELD),
                wordListProvider,
                null,
                DateTimeSettings.builder().build());

        final ExpressionTerm.Builder term = ExpressionTerm
                .builder()
                .field(TEXT_FIELD.getFldName())
                .condition(condition)
                .value(termValue);
        if (Condition.IN_DICTIONARY.equals(condition)) {
            final DocRef docRef = DocRef.builder().type("Dictionary").randomUuid().name("d").build();
            Mockito.when(wordListProvider.getWords(Mockito.any())).thenReturn(new String[]{"someValue"});
            term.docRef(docRef);
        }

        return matcher.match(
                Map.of(TEXT_FIELD.getFldName(), attribute),
                ExpressionOperator.builder().addTerm(term.build()).build());
    }

    private static String valueFor(final Condition condition) {
        return switch (condition) {
            case IN -> "someValue,other";
            case IN_DICTIONARY -> "";
            default -> "someValue";
        };
    }
}
