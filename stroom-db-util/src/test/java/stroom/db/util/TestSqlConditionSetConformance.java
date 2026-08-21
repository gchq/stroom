package stroom.db.util;

import stroom.collection.api.CollectionService;
import stroom.db.util.ExpressionMapper.MultiConverter;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;

import com.google.inject.Provider;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A {@link ConditionSet} is a promise about what an evaluator will do with a term. This checks the
 * SQL half of that promise: every condition the DB-backed quick filter sets declare must actually
 * be handled by {@link TermHandler}.
 * <p>
 * Three real bugs came from the two sides drifting apart - {@code WORD_BOUNDARY} and the four
 * ordering conditions being absent from sets whose evaluators implement them, and
 * {@code AnnotationTagFields.NAME_FIELD} declaring a date set on a text column. Each was found by
 * accident. This is the check that finds the next one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestSqlConditionSetConformance {

    private static final Field<String> TEXT_COLUMN = DSL.field("someColumn", String.class);
    private static final MultiConverter<String> IDENTITY = values -> values;

    @Mock
    private WordListProvider wordListProvider;
    @Mock
    private CollectionService collectionService;
    @Mock
    private DocFinder docFinder;

    /**
     * The sets a DB-backed quick filter surface can declare on a text field.
     */
    private static List<ConditionSet> sqlTextSets() {
        return List.of(
                ConditionSet.SQL_TEXT,
                ConditionSet.SQL_ENUM_TEXT,
                ConditionSet.DEFAULT_TEXT);
    }

    @TestFactory
    Stream<DynamicTest> everyDeclaredConditionIsHandled() {
        return sqlTextSets().stream().flatMap(conditionSet ->
                conditionSet.getConditionList().stream().map(condition ->
                        DynamicTest.dynamicTest(conditionSet.name() + " -> " + condition.name(), () -> {
                            final org.jooq.Condition result = applyTerm(conditionSet, condition);
                            assertThat(result)
                                    .describedAs("%s declares %s, so TermHandler must handle it",
                                            conditionSet.name(), condition.name())
                                    .isNotNull();
                        })));
    }

    /**
     * The other half of the promise: a condition a set deliberately omits should be one the
     * evaluator genuinely cannot honour, not an oversight. If one of these starts passing,
     * {@code SQL_TEXT} is under-declaring and should gain it - which is exactly how the ordering
     * conditions came to be added.
     */
    @TestFactory
    Stream<DynamicTest> deliberatelyOmittedConditionsStillCannotBeHandled() {
        return Stream.of(Condition.WORD_BOUNDARY, Condition.MATCHES_REGEX_CASE_SENSITIVE)
                .map(condition -> DynamicTest.dynamicTest("SQL_TEXT omits " + condition.name(), () -> {
                    assertThat(ConditionSet.SQL_TEXT.supportsCondition(condition)).isFalse();
                    assertThatThrownBy(() -> applyTerm(ConditionSet.SQL_TEXT, condition))
                            .describedAs("%s is omitted from SQL_TEXT because TermHandler cannot "
                                         + "honour it. If this no longer throws, add it to the set.",
                                    condition.name())
                            .isInstanceOf(RuntimeException.class);
                }));
    }

    /**
     * The case-sensitive variants are a different kind of omission: TermHandler has cases for them,
     * but the columns use a case-insensitive collation, so declaring them would promise case
     * sensitivity that is not delivered. Recorded as a decision in the syntax spec §8.2, and pinned
     * here so it is not "fixed" by someone reading only TermHandler.
     */
    @TestFactory
    Stream<DynamicTest> caseSensitiveConditionsAreOmittedByDecisionNotInability() {
        return Stream.of(
                        Condition.EQUALS_CASE_SENSITIVE,
                        Condition.CONTAINS_CASE_SENSITIVE,
                        Condition.STARTS_WITH_CASE_SENSITIVE,
                        Condition.ENDS_WITH_CASE_SENSITIVE)
                .map(condition -> DynamicTest.dynamicTest(condition.name(), () -> {
                    assertThat(ConditionSet.SQL_TEXT.supportsCondition(condition))
                            .describedAs("spec §8.2 keeps the case-sensitive variants out of every "
                                         + "DB-backed set")
                            .isFalse();
                    // But TermHandler does have a case for it - the omission is a promise we choose
                    // not to make, not one we cannot keep.
                    assertThat(applyTerm(ConditionSet.SQL_TEXT, condition)).isNotNull();
                }));
    }

    private org.jooq.Condition applyTerm(final ConditionSet conditionSet, final Condition condition) {
        final QueryField field = QueryField
                .builder()
                .fldName("someField")
                .fldType(FieldType.TEXT)
                .conditionSet(conditionSet)
                .build();
        final TermHandler<String> termHandler = new TermHandlerFactory(
                (Provider<WordListProvider>) () -> wordListProvider,
                (Provider<CollectionService>) () -> collectionService,
                (Provider<DocFinder>) () -> docFinder)
                .create(field, TEXT_COLUMN, IDENTITY);

        return termHandler.apply(ExpressionTerm
                .builder()
                .field(field.getFldName())
                .condition(condition)
                .value(valueFor(condition))
                .build());
    }

    /**
     * A value each condition can actually consume. BETWEEN wants two, IN wants a list.
     */
    private static String valueFor(final Condition condition) {
        return switch (condition) {
            case BETWEEN -> "aaa,zzz";
            case IN -> "aaa,bbb";
            case IN_DICTIONARY -> "";
            case MATCHES_REGEX, MATCHES_REGEX_CASE_SENSITIVE -> "a.*z";
            default -> "someValue";
        };
    }
}
