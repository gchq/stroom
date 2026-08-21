package stroom.query.language.filter;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.token.TokenException;
import stroom.query.language.filter.SimpleStringExpressionParser.FieldProvider;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The contract every quick filter surface has to keep, checked against every sigil a user can type
 * rather than one hand-picked example.
 * <p>
 * The surfaces themselves live in a dozen modules, so this works from the {@link ConditionSet}s
 * they declare - each flavour of surface is represented by the set it uses.
 * {@code TestSqlConditionSetConformance} and {@code TestInMemoryConditionSetConformance} check the
 * other end, that the evaluators honour what these sets promise.
 */
class TestQuickFilterSurfaceConformance {

    /**
     * Every sigil {@code SimpleStringExpressionParser} recognises, plus the bare case.
     */
    private static final List<String> INPUTS = List.of(
            "abc", "=abc", "^abc", "$abc", ">abc", ">=abc", "<abc", "<=abc",
            "/a.*c", "?ABC", "~abc", "==abc", "=^abc", "=$abc", "!abc", "!=abc");

    /**
     * The sets a quick filter surface can legitimately declare on a text field.
     */
    private static final List<ConditionSet> TEXT_SETS = List.of(
            ConditionSet.ALL_UI_TEXT,
            ConditionSet.SQL_TEXT,
            ConditionSet.SQL_ENUM_TEXT,
            ConditionSet.DEFAULT_TEXT);

    /**
     * The property that makes "the server behaves the same for each" checkable: whatever a user
     * types, a surface either produces a term its field declares it can honour, or rejects the
     * input outright. It must never quietly produce a term the evaluator will choke on - that is
     * the empty grid this work exists to remove.
     */
    @TestFactory
    Stream<DynamicTest> everyInputEitherResolvesToADeclaredConditionOrIsRejected() {
        return TEXT_SETS.stream().flatMap(conditionSet ->
                INPUTS.stream().map(input -> DynamicTest.dynamicTest(
                        conditionSet.name() + " <- [" + input + "]", () -> {
                            final QueryField field = textField(conditionSet);
                            final FieldProvider provider = provider(field);

                            final List<ExpressionTerm> terms = new ArrayList<>();
                            try {
                                SimpleStringExpressionParser.create(provider, input)
                                        .ifPresent(operator -> collectTerms(operator, terms));
                            } catch (final TokenException e) {
                                // Rejected outright, naming the field. Acceptable - the user is
                                // told, rather than shown an empty grid.
                                assertThat(e.getMessage()).contains(field.getFldName());
                                return;
                            }

                            assertThat(terms)
                                    .describedAs("input [%s] produced no term", input)
                                    .isNotEmpty();
                            terms.forEach(term -> assertThat(field.supportsCondition(term.getCondition()))
                                    .describedAs("input [%s] produced %s, which %s does not declare",
                                            input, term.getCondition(), conditionSet.name())
                                    .isTrue());
                        })));
    }

    /**
     * A qualifier resolves case-insensitively, and an unknown one leaves the ':' as an ordinary
     * value character rather than throwing - the behaviour spec §2.6.1 settled on, which is what
     * makes "12:30" a literal.
     */
    @Test
    void qualifiersResolveCaseInsensitivelyAndUnknownOnesAreNotAnError() {
        final QueryField name = QueryField.createUiText("Name");
        final QueryField status = QueryField.createUiText("Status");
        final FieldProvider provider = new FieldProviderImpl(List.of(name), List.of(name, status));

        assertThat(provider.getQualifiedField("status")).contains(status);
        assertThat(provider.getQualifiedField("STATUS")).contains(status);
        assertThat(provider.getQualifiedField("nope")).isEmpty();

        final List<ExpressionTerm> terms = parse(provider, "12:30");
        assertThat(terms).hasSize(1);
        assertThat(terms.getFirst().getField()).isEqualTo("Name");
        assertThat(terms.getFirst().getValue()).isEqualTo("12:30");
    }

    /**
     * A bare term ORs across the default fields and only those - a surface must not silently search
     * a field it did not offer as a default.
     */
    @Test
    void bareTermsOrAcrossTheDefaultFieldsAndOnlyThose() {
        final QueryField a = QueryField.createUiText("A");
        final QueryField b = QueryField.createUiText("B");
        final QueryField qualifiedOnly = QueryField.createUiText("C");
        final FieldProvider provider = new FieldProviderImpl(List.of(a, b), List.of(a, b, qualifiedOnly));

        assertThat(parse(provider, "abc")).extracting(ExpressionTerm::getField)
                .containsExactlyInAnyOrder("A", "B");
        // ...but it is still reachable by name.
        assertThat(parse(provider, "c:abc")).extracting(ExpressionTerm::getField)
                .containsExactly("C");
    }

    /**
     * A single-field surface has no qualifiers at all, which is what makes ':' an ordinary value
     * character there - spec §3.3.
     */
    @Test
    void singleFieldSurfacesHaveNoQualifiers() {
        final QueryField field = QueryField.createUiText("value");
        final FieldProvider provider = new SingleFieldProvider(field);

        assertThat(provider.getDefaultFields()).containsExactly(field);
        assertThat(provider.getQualifiedField("value")).isEmpty();

        final List<ExpressionTerm> terms = parse(provider, "http://example.com");
        assertThat(terms).hasSize(1);
        assertThat(terms.getFirst().getValue()).isEqualTo("http://example.com");
    }

    /**
     * A blank filter is not a filter - it must not become a term that matches nothing.
     */
    @Test
    void blankInputProducesNoExpression() {
        final FieldProvider provider = provider(textField(ConditionSet.ALL_UI_TEXT));
        assertThat(SimpleStringExpressionParser.create(provider, null)).isEmpty();
        assertThat(SimpleStringExpressionParser.create(provider, "")).isEmpty();
        assertThat(SimpleStringExpressionParser.create(provider, "   ")).isEmpty();
    }

    /**
     * A field declaring neither CONTAINS nor EQUALS cannot honour a bare term at all, and must say
     * so rather than falling back to something it also does not declare.
     */
    @Test
    void fieldThatCanHonourNoDefaultConditionSaysSo() {
        final QueryField field = QueryField
                .builder()
                .fldName("docref")
                .fldType(FieldType.DOC_REF)
                .conditionSet(ConditionSet.DOC_REF_UUID)
                .build();

        assertThatThrownBy(() -> SimpleStringExpressionParser.create(provider(field), "abc"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("docref")
                .hasMessageContaining("does not support");
    }

    private static List<ExpressionTerm> parse(final FieldProvider provider, final String input) {
        final List<ExpressionTerm> terms = new ArrayList<>();
        SimpleStringExpressionParser.create(provider, input).ifPresent(op -> collectTerms(op, terms));
        return terms;
    }

    private static QueryField textField(final ConditionSet conditionSet) {
        return QueryField
                .builder()
                .fldName("field")
                .fldType(FieldType.TEXT)
                .conditionSet(conditionSet)
                .build();
    }

    private static FieldProvider provider(final QueryField field) {
        return new FieldProviderImpl(List.of(field), List.of(field));
    }

    private static void collectTerms(final ExpressionItem item, final List<ExpressionTerm> into) {
        if (item instanceof final ExpressionTerm term) {
            into.add(term);
        } else if (item instanceof final ExpressionOperator operator && operator.getChildren() != null) {
            operator.getChildren().forEach(child -> collectTerms(child, into));
        }
    }
}
