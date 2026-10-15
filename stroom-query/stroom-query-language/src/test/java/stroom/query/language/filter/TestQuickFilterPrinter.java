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

package stroom.query.language.filter;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.filter.SimpleStringExpressionParser.FieldProvider;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The printer's contract: what it prints, the parser reads back as the same tree; and what the
 * parser reads, the printer writes back in one canonical spelling.
 */
class TestQuickFilterPrinter {

    private static final QueryField NAME = QueryField.createUiText("name");
    private static final QueryField DESC = QueryField.createUiText("desc");
    private static final QueryField UUID = QueryField.createUiText("uuid");
    // EQUALS is the default condition on a field without CONTAINS, as on the Dependencies screen.
    private static final QueryField STATUS = QueryField
            .builder()
            .fldName("status")
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.SQL_ENUM_TEXT)
            .build();

    /** Two default fields, so a bare term becomes an OR group. */
    private static final FieldProvider TWO_DEFAULTS =
            new FieldProviderImpl(List.of(NAME, DESC), List.of(NAME, DESC, UUID, STATUS));
    /** One default field, so a bare term is a single term. */
    private static final FieldProvider ONE_DEFAULT =
            new FieldProviderImpl(List.of(NAME), List.of(NAME, UUID, STATUS));

    // --------------------------------------------------------------------------------
    // text -> tree -> text: canonical spelling, and the same tree again
    // --------------------------------------------------------------------------------

    record Canonical(String input, String expected) {

        static Canonical same(final String text) {
            return new Canonical(text, text);
        }
    }

    private static final List<Canonical> CANONICAL_TWO_DEFAULTS = List.of(
            Canonical.same("abc"),
            Canonical.same("^abc"),
            Canonical.same("$abc"),
            Canonical.same("=abc"),
            Canonical.same("==abc"),
            Canonical.same("=^abc"),
            Canonical.same("=$abc"),
            Canonical.same("=+abc"),
            Canonical.same("=/a.*c"),
            Canonical.same(">5"),
            Canonical.same(">=5"),
            Canonical.same("<5"),
            Canonical.same("<=5"),
            Canonical.same("/a.*c"),
            Canonical.same("?ABC"),
            Canonical.same("!abc"),
            Canonical.same("!^abc"),
            Canonical.same("!=abc"),
            Canonical.same("name:abc"),
            Canonical.same("name:^abc"),
            Canonical.same("name:!abc"),
            Canonical.same("name:!^abc"),
            Canonical.same("uuid:abc"),
            Canonical.same("status:OK"),
            Canonical.same("abc def"),
            Canonical.same("abc or def"),
            Canonical.same("(abc or def) ghi"),
            Canonical.same("not (abc def)"),
            Canonical.same("not (abc or def)"),
            Canonical.same("\"abc def\""),
            Canonical.same("name:\"abc def\""),
            Canonical.same("^\"abc def\""),
            Canonical.same("12:30"),
            Canonical.same("name:12:30"),
            // '//' opens a comment wherever it appears, and only survives unquoted because the
            // parser glues the comment token back onto the value. Quoted, it is not a gamble.
            new Canonical("http://example.com", "\"http://example.com\""),
            Canonical.same("name:x or desc:=x"),
            Canonical.same("\"and\""),
            Canonical.same("name:\"\""),
            // Canonicalised: a sigil that is the field's default is dropped.
            new Canonical("+abc", "abc"),
            new Canonical("status:=OK", "status:OK"),
            // Canonicalised: 'and' is implicit, 'not' folds into the term, brackets only where
            // precedence needs them, and the lowercase keyword spelling.
            new Canonical("abc and def", "abc def"),
            new Canonical("not abc", "!abc"),
            new Canonical("not name:abc", "name:!abc"),
            new Canonical("abc or def ghi", "(abc or def) ghi"),
            new Canonical("abc OR def", "abc or def"),
            new Canonical("(abc)", "abc"),
            new Canonical("  abc   def  ", "abc def"),
            // Chars-anywhere is rewritten to a regex by the parser (spec §5.2) so that is what
            // comes back. Lossy, and correct.
            new Canonical("~abc", "/a.*?b.*?c"));

    @TestFactory
    Stream<DynamicTest> textRoundTripsThroughItsCanonicalSpelling() {
        return CANONICAL_TWO_DEFAULTS.stream().map(c -> DynamicTest.dynamicTest("[" + c.input + "]", () -> {
            final ExpressionOperator tree = parse(TWO_DEFAULTS, c.input);
            final String printed = QuickFilterPrinter.print(tree, TWO_DEFAULTS);
            assertThat(printed).isEqualTo(c.expected);
            assertThat(parse(TWO_DEFAULTS, printed))
                    .describedAs("re-parsing the canonical spelling [%s]", printed)
                    .isEqualTo(tree);
        }));
    }

    @Test
    void testSingleDefaultFieldPrintsBare() {
        for (final String text : List.of("abc", "^abc", "!abc", "x or uuid:y", "\"a b\"")) {
            final ExpressionOperator tree = parse(ONE_DEFAULT, text);
            final String printed = QuickFilterPrinter.print(tree, ONE_DEFAULT);
            assertThat(printed).isEqualTo(text);
            assertThat(parse(ONE_DEFAULT, printed)).isEqualTo(tree);
        }
        // With one default the qualifier is redundant and is dropped.
        assertThat(QuickFilterPrinter.print(parse(ONE_DEFAULT, "name:abc"), ONE_DEFAULT)).isEqualTo("abc");
        assertThat(QuickFilterPrinter.print(parse(ONE_DEFAULT, "name:x or uuid:y"), ONE_DEFAULT))
                .isEqualTo("x or uuid:y");
    }

    @Test
    void testBlankPrintsAsEmpty() {
        assertThat(QuickFilterPrinter.print(null, TWO_DEFAULTS)).isEmpty();
        assertThat(QuickFilterPrinter.print(ExpressionOperator.builder().build(), TWO_DEFAULTS)).isEmpty();
    }

    // --------------------------------------------------------------------------------
    // tree -> text -> tree: trees the dialog can build that the parser never would
    // --------------------------------------------------------------------------------

    record Built(String label, ExpressionOperator tree, String expected) {

    }

    private static ExpressionTerm term(final QueryField field, final Condition condition, final String value) {
        return ExpressionTerm.builder().field(field.getFldName()).condition(condition).value(value).build();
    }

    private static ExpressionOperator and(final stroom.query.api.ExpressionItem... items) {
        return ExpressionOperator.builder().op(Op.AND).children(List.of(items)).build();
    }

    private static ExpressionOperator or(final stroom.query.api.ExpressionItem... items) {
        return ExpressionOperator.builder().op(Op.OR).children(List.of(items)).build();
    }

    private static ExpressionOperator not(final stroom.query.api.ExpressionItem item) {
        return ExpressionOperator.builder().op(Op.NOT).children(List.of(item)).build();
    }

    private static final List<Built> BUILT = List.of(
            // Values that would be read as structure are quoted.
            new Built("value starting with a sigil", and(term(NAME, Condition.CONTAINS, "^abc")), "\"^abc\""),
            new Built("value starting with '!'", and(term(NAME, Condition.CONTAINS, "!abc")), "\"!abc\""),
            new Built("value starting with '~'", and(term(NAME, Condition.CONTAINS, "~abc")), "\"~abc\""),
            new Built("value starting with '\\'", and(term(NAME, Condition.CONTAINS, "\\abc")), "\"\\\\abc\""),
            new Built("value that is a keyword", and(term(NAME, Condition.CONTAINS, "or")), "\"or\""),
            new Built("value ending in '=' before a keyword", or(term(NAME, Condition.CONTAINS, "a="),
                    term(NAME, Condition.CONTAINS, "b")), "\"a=\" or b"),
            new Built("value with a bracket", and(term(NAME, Condition.CONTAINS, "a(b")), "\"a(b\""),
            new Built("value with a quote", and(term(NAME, Condition.CONTAINS, "a\"b")), "\"a\\\"b\""),
            // Only a leading backslash is an escape; one in the middle is an ordinary character.
            new Built("value with a backslash", and(term(NAME, Condition.CONTAINS, "a\\b")), "a\\b"),
            new Built("empty value", and(term(NAME, Condition.CONTAINS, "")), "\"\""),
            new Built("value that looks like a qualifier", and(term(NAME, Condition.CONTAINS, "status:x")),
                    "\"status:x\""),
            new Built("qualified value with a colon needs no quoting", and(term(UUID, Condition.CONTAINS, "a:b")),
                    "uuid:a:b"),
            // A value that would extend the sigil into a longer one is quoted.
            new Built("'=' then a value starting with '='", and(term(NAME, Condition.EQUALS, "=abc")), "=\"=abc\""),
            new Built("'=' then a value starting with '^'", and(term(NAME, Condition.EQUALS, "^abc")), "=\"^abc\""),
            new Built("'/' then a value opening a comment", and(term(NAME, Condition.MATCHES_REGEX, "/tmp/.*")),
                    "/\"/tmp/.*\""),
            new Built("'/' then a value opening a block comment", and(term(NAME, Condition.MATCHES_REGEX, "*a")),
                    "/\"*a\""),
            new Built("'>' then a value starting with '='", and(term(NAME, Condition.GREATER_THAN, "=5")), ">\"=5\""),
            // Negation the parser cannot fold is spelled out.
            new Built("negated quoted value", not(term(NAME, Condition.CONTAINS, "a b")), "not \"a b\""),
            new Built("negated qualified quoted value", and(not(term(UUID, Condition.CONTAINS, "a b"))),
                    "not uuid:\"a b\""),
            new Built("negated with a sigil and a quoted value", not(term(NAME, Condition.STARTS_WITH, "a b")),
                    "!^\"a b\""),
            // Structure.
            new Built("AND under AND", and(term(NAME, Condition.CONTAINS, "a"),
                    and(term(NAME, Condition.CONTAINS, "b"), term(NAME, Condition.CONTAINS, "c"))), "a (b c)"),
            new Built("OR under OR", or(term(NAME, Condition.CONTAINS, "a"),
                    or(term(NAME, Condition.CONTAINS, "b"), term(NAME, Condition.CONTAINS, "c"))), "a or (b or c)"),
            new Built("AND under OR", or(term(NAME, Condition.CONTAINS, "a"),
                    and(term(NAME, Condition.CONTAINS, "b"), term(NAME, Condition.CONTAINS, "c"))), "a or (b c)"),
            new Built("NOT under OR", or(term(NAME, Condition.CONTAINS, "a"),
                    not(and(term(NAME, Condition.CONTAINS, "b"), term(NAME, Condition.CONTAINS, "c")))),
                    "a or not (b c)"),
            new Built("NOT under NOT", not(not(and(term(NAME, Condition.CONTAINS, "b"),
                    term(NAME, Condition.CONTAINS, "c")))), "not ( not (b c))"),
            new Built("NOT first inside a bracket", and(term(NAME, Condition.CONTAINS, "a"),
                    or(not(and(term(NAME, Condition.CONTAINS, "b"), term(NAME, Condition.CONTAINS, "c"))),
                            term(NAME, Condition.CONTAINS, "d"))), "a ( not (b c) or d)"),
            new Built("qualified term on a non-default field", and(term(STATUS, Condition.EQUALS, "OK")),
                    "status:OK"));

    @TestFactory
    Stream<DynamicTest> builtTreesRoundTrip() {
        return BUILT.stream().map(b -> DynamicTest.dynamicTest(b.label, () -> {
            final String printed = QuickFilterPrinter.print(b.tree, ONE_DEFAULT);
            assertThat(printed).isEqualTo(b.expected);
            final ExpressionOperator reparsed = parse(ONE_DEFAULT, printed);
            // The parser wraps a lone term in an AND and returns a lone operator as itself, so
            // compare after the same normalisation.
            assertThat(reparsed).isEqualTo(normalise(b.tree));
        }));
    }

    /**
     * The parser reads {@code !=} as {@code NOT(EQUALS)} rather than {@code NOT_EQUALS} (spec
     * §5.1 is not yet implemented there), so a {@code NOT_EQUALS} term prints as {@code !=} and
     * comes back as the equivalent negation. Pinned so the day the parser changes, this test says
     * so.
     */
    @Test
    void testNotEqualsPrintsAsTheParserWillReadIt() {
        assertThat(QuickFilterPrinter.print(and(term(NAME, Condition.NOT_EQUALS, "abc")), ONE_DEFAULT))
                .isEqualTo("!=abc");
        assertThat(parse(ONE_DEFAULT, "!=abc"))
                .isEqualTo(not(term(NAME, Condition.EQUALS, "abc")));

        assertThat(QuickFilterPrinter.print(and(term(STATUS, Condition.NOT_EQUALS, "OK")), ONE_DEFAULT))
                .isEqualTo("status:!=OK");
        assertThat(parse(ONE_DEFAULT, "status:!=OK"))
                .isEqualTo(not(term(STATUS, Condition.EQUALS, "OK")));

        // And a negated NOT_EQUALS cannot fold: '!!=abc' would read as a CONTAINS of '!=abc'.
        assertThat(QuickFilterPrinter.print(not(term(NAME, Condition.NOT_EQUALS, "abc")), ONE_DEFAULT))
                .isEqualTo("not !=abc");
        assertThat(parse(ONE_DEFAULT, "not !=abc"))
                .isEqualTo(not(not(term(NAME, Condition.EQUALS, "abc"))));
    }

    @Test
    void testDefaultFieldGroupWithAnUnspellableConditionIsRefusedNotCollapsed() {
        final ExpressionOperator tree = or(term(NAME, Condition.IN, "x"), term(DESC, Condition.IN, "x"));
        assertThatThrownBy(() -> QuickFilterPrinter.print(tree, TWO_DEFAULTS))
                .isInstanceOf(QuickFilterPrintException.class)
                .hasMessageContaining(Condition.IN.getDisplayValue());
    }

    @Test
    void testNegatedNotEqualsGroupIsSpelledOut() {
        final ExpressionOperator tree = not(or(
                term(NAME, Condition.NOT_EQUALS, "x"), term(DESC, Condition.NOT_EQUALS, "x")));
        assertThat(QuickFilterPrinter.print(tree, TWO_DEFAULTS)).isEqualTo("not !=x");
        assertThat(parse(TWO_DEFAULTS, "not !=x")).isEqualTo(not(not(or(
                term(NAME, Condition.EQUALS, "x"), term(DESC, Condition.EQUALS, "x")))));
    }

    @Test
    void testDefaultFieldGroupWithMixedConditionsIsNotCollapsed() {
        // The parser only ever produces this shape with each field's own default condition or one
        // sigil across all of them; anything else is printed as what it is.
        final ExpressionOperator tree = or(term(NAME, Condition.CONTAINS, "x"), term(DESC, Condition.EQUALS, "x"));
        assertThat(QuickFilterPrinter.print(tree, TWO_DEFAULTS)).isEqualTo("name:x or desc:=x");
    }

    // --------------------------------------------------------------------------------
    // refusals
    // --------------------------------------------------------------------------------

    @Test
    void testDisabledItemIsRefused() {
        final ExpressionTerm disabled = ExpressionTerm.builder()
                .enabled(false).field("name").condition(Condition.CONTAINS).value("abc").build();
        assertThatThrownBy(() -> QuickFilterPrinter.print(and(disabled), ONE_DEFAULT))
                .isInstanceOf(QuickFilterPrintException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void testUnspellableConditionIsRefused() {
        for (final Condition condition : List.of(Condition.IN, Condition.BETWEEN, Condition.IS_NULL,
                Condition.IN_DICTIONARY, Condition.IS_DOC_REF)) {
            assertThatThrownBy(() -> QuickFilterPrinter.print(and(term(NAME, condition, "x")), ONE_DEFAULT))
                    .describedAs(condition.name())
                    .isInstanceOf(QuickFilterPrintException.class)
                    .hasMessageContaining(condition.getDisplayValue());
            assertThat(QuickFilterPrinter.isPrintable(condition)).isFalse();
        }
    }

    /**
     * The client cannot see the printer, so it decides what to offer in the tree editor from
     * {@link stroom.query.api.datasource.QuickFilterFields#isQuickFilterCondition}. The two must
     * agree for every condition or the dialog offers something OK cannot write.
     */
    @Test
    void testClientSideSpellabilityMatchesThePrinter() {
        for (final Condition condition : Condition.values()) {
            assertThat(stroom.query.api.datasource.QuickFilterFields.isQuickFilterCondition(condition))
                    .describedAs(condition.name())
                    .isEqualTo(QuickFilterPrinter.isPrintable(condition));
        }
    }

    @Test
    void testUnknownFieldIsRefused() {
        final ExpressionTerm term = ExpressionTerm.builder().field("nope").condition(Condition.CONTAINS).value("x")
                .build();
        assertThatThrownBy(() -> QuickFilterPrinter.print(and(term), ONE_DEFAULT))
                .isInstanceOf(QuickFilterPrintException.class)
                .hasMessageContaining("nope");
    }

    /**
     * A surface that lists a default field without also listing it as qualified (every real one
     * does both) cannot write a term on that field alone; say so rather than "no such field".
     */
    @Test
    void testDefaultFieldWithoutQualifierIsRefusedWithAReason() {
        final FieldProvider lopsided = new FieldProviderImpl(List.of(NAME, DESC), List.of(NAME));
        assertThatThrownBy(() -> QuickFilterPrinter.print(and(term(DESC, Condition.CONTAINS, "x")), lopsided))
                .isInstanceOf(QuickFilterPrintException.class)
                .hasMessageContaining("default field");
    }

    @Test
    void testMalformedNotIsRefused() {
        final ExpressionOperator twoChildren = ExpressionOperator.builder().op(Op.NOT)
                .children(List.of(term(NAME, Condition.CONTAINS, "a"), term(NAME, Condition.CONTAINS, "b"))).build();
        assertThatThrownBy(() -> QuickFilterPrinter.print(and(twoChildren), ONE_DEFAULT))
                .isInstanceOf(QuickFilterPrintException.class);
    }

    // --------------------------------------------------------------------------------

    private static ExpressionOperator parse(final FieldProvider provider, final String text) {
        return SimpleStringExpressionParser.create(provider, text).orElseThrow();
    }

    /**
     * What the parser returns for the text a tree prints as: an AND root with one operator child
     * collapses to that child.
     */
    private static ExpressionOperator normalise(final ExpressionOperator tree) {
        if (tree.op() == Op.AND
            && tree.getChildren().size() == 1
            && tree.getChildren().getFirst() instanceof final ExpressionOperator only) {
            return only;
        }
        return tree;
    }
}
