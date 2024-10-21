package stroom.query.common.v2;

import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSimpleStringExpressionParser {

    @Test
    void testWordBoundary() {
        final ExpressionTerm term = getTerm("?TEST");
        assertThat(term.getCondition()).isEqualTo(Condition.MATCHES_REGEX);
        final Pattern pattern = Pattern.compile(term.getValue());
        assertThat(pattern.matcher("Test Every Single Thing").find()).isTrue();
        assertThat(pattern.matcher("Test Every Other Thing").find()).isFalse();
    }

    @Test
    void testMatchesRegex() {
        final ExpressionTerm term = getTerm("/This (is|or) that");
        assertThat(term.getCondition()).isEqualTo(Condition.MATCHES_REGEX);
        final Pattern pattern = Pattern.compile(term.getValue());
        assertThat(pattern.matcher("This is that").find()).isTrue();
        assertThat(pattern.matcher("This or that").find()).isTrue();
    }

    @Test
    void testStartsWith() {
        final ExpressionTerm term = getTerm("^test");
        assertThat(term.getCondition()).isEqualTo(Condition.STARTS_WITH);
        assertThat("test this".startsWith(term.getValue())).isTrue();
    }

    @Test
    void testEndsWith() {
        final ExpressionTerm term = getTerm("$test");
        assertThat(term.getCondition()).isEqualTo(Condition.ENDS_WITH);
        assertThat("this test".endsWith(term.getValue())).isTrue();
    }

    @Test
    void testGreaterThanEquals() {
        final ExpressionTerm term = getTerm(">=3");
        assertThat(term.getCondition()).isEqualTo(Condition.GREATER_THAN_OR_EQUAL_TO);
        assertThat(term.getValue()).isEqualTo("3");
    }

    @Test
    void testGreaterThan() {
        final ExpressionTerm term = getTerm(">3");
        assertThat(term.getCondition()).isEqualTo(Condition.GREATER_THAN);
        assertThat(term.getValue()).isEqualTo("3");
    }

    @Test
    void tesLessThanEquals() {
        final ExpressionTerm term = getTerm("<=3");
        assertThat(term.getCondition()).isEqualTo(Condition.LESS_THAN_OR_EQUAL_TO);
        assertThat(term.getValue()).isEqualTo("3");
    }

    @Test
    void testLessThan() {
        final ExpressionTerm term = getTerm("<3");
        assertThat(term.getCondition()).isEqualTo(Condition.LESS_THAN);
        assertThat(term.getValue()).isEqualTo("3");
    }

    @Test
    void testAnywhere() {
        final ExpressionTerm term = getTerm("~test");
        assertThat(term.getCondition()).isEqualTo(Condition.MATCHES_REGEX);
        final Pattern pattern = Pattern.compile(term.getValue());
        assertThat(pattern.matcher("this exists somewhere there").find()).isTrue();
        assertThat(pattern.matcher("non match").find()).isFalse();
    }

    @Test
    void testEquals() {
        final ExpressionTerm term = getTerm("=test");
        assertThat(term.getCondition()).isEqualTo(Condition.EQUALS);
        assertThat(term.getValue()).isEqualTo("test");
    }

    @Test
    void testContains() {
        final ExpressionTerm term = getTerm("test");
        assertThat(term.getCondition()).isEqualTo(Condition.CONTAINS);
        assertThat(term.getValue()).isEqualTo("test");
    }

    @Test
    void testContainsEscape() {
        final ExpressionTerm term = getTerm("\\^test");
        assertThat(term.getCondition()).isEqualTo(Condition.CONTAINS);
        assertThat(term.getValue()).isEqualTo("^test");
    }

    private ExpressionTerm getTerm(final String string) {
        return (ExpressionTerm) SimpleStringExpressionParser
                .create("test", string, false)
                .orElseThrow()
                .getChildren()
                .getFirst();
    }
}
