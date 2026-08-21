package stroom.query.language.filter;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.token.TokenException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link QuickFilter#and} is what lets a screen compose terms of its own - "children of this
 * group", "only groups" - with text the user typed, without the two languages mixing. Getting the
 * combination wrong would let a filter widen what the screen asked for, which on the permission
 * screens would mean showing rows the caller never requested.
 */
class TestQuickFilter {

    private static final QueryField NAME = QueryField.createUiText("name");
    private static final QueryField STATUS = QueryField.createUiText("status");
    private static final List<QueryField> DEFAULTS = List.of(NAME);
    private static final List<QueryField> ALL = List.of(NAME, STATUS);

    private static final ExpressionOperator STRUCTURAL = ExpressionOperator
            .builder()
            .addTerm(ExpressionTerm
                    .builder()
                    .field("ChildrenOf")
                    .condition(Condition.EQUALS)
                    .value("some-uuid")
                    .build())
            .build();

    @Test
    void blankFilterLeavesTheScreensOwnExpressionUntouched() {
        for (final String blank : new String[]{null, "", "   "}) {
            assertThat(QuickFilter.and(STRUCTURAL, blank, DEFAULTS, ALL))
                    .describedAs("blank input [%s]", blank)
                    .isSameAs(STRUCTURAL);
        }
    }

    @Test
    void withNoScreenExpressionTheFilterStandsAlone() {
        final ExpressionOperator result = QuickFilter.and(null, "abc", DEFAULTS, ALL);

        assertThat(ExpressionUtil.fields(result)).containsExactly("name");
    }

    // --------------------------------------------------------------------------------
    // parse() is what the eleven surfaces with no expression of their own use. and() is defined in
    // terms of it, so these also pin the shared half.

    @Test
    void parseReturnsNullWhenThereIsNothingToParse() {
        for (final String blank : new String[]{null, "", "   "}) {
            assertThat(QuickFilter.parse(blank, DEFAULTS, ALL))
                    .describedAs("blank input [%s]", blank)
                    .isNull();
        }
    }

    @Test
    void parseResolvesAgainstTheDeclaredFields() {
        assertThat(ExpressionUtil.fields(QuickFilter.parse("abc", DEFAULTS, ALL)))
                .containsExactly("name");
        assertThat(ExpressionUtil.fields(QuickFilter.parse("status:live", DEFAULTS, ALL)))
                .containsExactly("status");
    }

    @Test
    void parseThrowsSoTheCallerCanReportIt() {
        assertThatThrownBy(() -> QuickFilter.parse("abc and", DEFAULTS, ALL))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void andWithNoScreenExpressionIsJustParse() {
        assertThat(QuickFilter.and(null, "status:live", DEFAULTS, ALL))
                .isEqualTo(QuickFilter.parse("status:live", DEFAULTS, ALL));
    }

    @Test
    void anEmptyScreenExpressionIsNotWrappedAround() {
        final ExpressionOperator empty = ExpressionOperator.builder().build();
        final ExpressionOperator result = QuickFilter.and(empty, "abc", DEFAULTS, ALL);

        // Not AND(empty, filter) - just the filter, so the tree stays readable.
        assertThat(ExpressionUtil.fields(result)).containsExactly("name");
    }

    /**
     * The important one: both sets of terms survive, and they are ANDed, so a filter can only ever
     * narrow what the screen already asked for.
     */
    @Test
    void bothSetsOfTermsSurviveAndAreAnded() {
        final ExpressionOperator result = QuickFilter.and(STRUCTURAL, "status:live", DEFAULTS, ALL);

        assertThat(result.op()).isEqualTo(Op.AND);
        assertThat(ExpressionUtil.fields(result)).containsExactlyInAnyOrder("ChildrenOf", "status");
    }

    @Test
    void theScreensTermsAreNotReinterpretedAsUserText() {
        // "ChildrenOf" is not one of the quick filter's fields, so if the two were merged rather
        // than ANDed the structural term would be lost or mangled.
        final ExpressionOperator result = QuickFilter.and(STRUCTURAL, "abc", DEFAULTS, ALL);

        assertThat(ExpressionUtil.fields(result)).contains("ChildrenOf");
        assertThat(result.getChildren()).hasSize(2);
    }

    @Test
    void badFilterThrowsSoTheCallerCanReportIt() {
        assertThatThrownBy(() -> QuickFilter.and(STRUCTURAL, "abc and", DEFAULTS, ALL))
                .describedAs("callers turn this into an empty page carrying the reason")
                .isInstanceOf(TokenException.class);
    }

    @Test
    void anUnsupportedConditionThrowsRatherThanReachingTheEvaluator() {
        final QueryField narrow = QueryField.createText("narrow");
        assertThatThrownBy(() -> QuickFilter.and(
                null, "^abc", List.of(narrow), List.of(narrow)))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("does not support");
    }
}
