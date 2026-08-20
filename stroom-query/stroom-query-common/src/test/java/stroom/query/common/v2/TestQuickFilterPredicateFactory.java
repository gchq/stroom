/*
 * Copyright 2020 Crown Copyright
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

package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.datasource.QuickFilterFields;
import stroom.query.api.token.TokenException;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.util.ConsoleColour;
import stroom.util.shared.NullSafe;
import stroom.util.shared.filter.FilterFieldDefinition;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestQuickFilterPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestQuickFilterPredicateFactory.class);

    private static final List<FilterFieldDefinition> FIELD_DEFINITIONS = List.of(
            FilterFieldDefinition.qualifiedField("Status"),
            FilterFieldDefinition.defaultField("SimpleStr1"),
            FilterFieldDefinition.defaultField("SimpleStr2"),
            FilterFieldDefinition.qualifiedField("Type"),
            FilterFieldDefinition.qualifiedField("Name"),
            FilterFieldDefinition.qualifiedField("Uuid"));

    private static final FieldProvider FIELD_PROVIDER = new FieldProviderImpl(
            QuickFilterFields.uiTextDefaults(FIELD_DEFINITIONS),
            QuickFilterFields.uiText(FIELD_DEFINITIONS));

    private static final List<FilterFieldDefinition> FIELD_DEFINITIONS_2 = List.of(
            FilterFieldDefinition.defaultField("Name"),
            FilterFieldDefinition.qualifiedField("Age"),
            FilterFieldDefinition.qualifiedField("Sex"));

    private static final FieldProvider FIELD_PROVIDER_2 = new FieldProviderImpl(
            QuickFilterFields.uiTextDefaults(FIELD_DEFINITIONS_2),
            QuickFilterFields.uiText(FIELD_DEFINITIONS_2));

    private static final ValueFunctionFactoriesImpl<Pojo> VALUE_FUNCTION_FACTORIES =
            new ValueFunctionFactoriesImpl<Pojo>()
                    .put(FilterFieldDefinition.qualifiedField("Status"), Pojo::getStatus)
                    .put(FilterFieldDefinition.defaultField("SimpleStr1"), Pojo::getSimpleStr1)
                    .put(FilterFieldDefinition.defaultField("SimpleStr2"), Pojo::getSimpleStr2)
                    .put(FilterFieldDefinition.qualifiedField("Type"), pojo ->
                            NullSafe.get(pojo, Pojo::getDocRef, DocRef::getType))
                    .put(FilterFieldDefinition.qualifiedField("Name"), pojo ->
                            NullSafe.get(pojo, Pojo::getDocRef, DocRef::getName))
                    .put(FilterFieldDefinition.qualifiedField("Uuid"), pojo ->
                            NullSafe.get(pojo, Pojo::getDocRef, DocRef::getUuid));

    private static final Pojo POJO_1 = new Pojo(
            "OK",
            "MY NAME",
            "OTHER NAME",
            "DocRefName",
            "MyType",
            "70dd91a8-2ffd-496c-abf7-8105d39297ac");

    private static final Pojo POJO_1_MISSING = new Pojo(
            "MISSING",
            "MY NAME",
            "OTHER NAME",
            "DocRefName",
            "MyType",
            "d07e18ce-3aed-4bee-95ec-17d2116dc11e");

    private static final Pojo POJO_1_BAD_NAME = new Pojo(
            "OK",
            "BAD NAME",
            "OTHER BAD NAME",
            "DocRefName",
            "MyType",
            "4568bae0-4cde-41ae-8e61-c7e3595637ac");

    private static final Pojo POJO_1_NOT_MY_TYPE = new Pojo(
            "OK",
            "MY NAME",
            "OTHER NAME",
            "DocRefName",
            "NotMyType",
            "1f91063b-b653-4501-9479-70de65827877");

    private final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();

    @Test
    void test_malformed1() {
        doTest("name uuid:", // uuid: ignored
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_BAD_NAME,
                        POJO_1_NOT_MY_TYPE),
                List.of());
    }

    @Test
    void test_malformed2() {
        doTest("notfound uuid:", // uuid: ignored
                List.of(),
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_BAD_NAME,
                        POJO_1_NOT_MY_TYPE));
    }

    @Test
    void test_malformed3() {
        doTest("name unknownfield:",
                List.of(),
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_BAD_NAME,
                        POJO_1_NOT_MY_TYPE));
    }

    @Test
    void test_uuidExactMatch() {
        doTest("uuid:70dd91a8-2ffd-496c-abf7-8105d39297ac",
                List.of(POJO_1),
                List.of(POJO_1_MISSING,
                        POJO_1_BAD_NAME,
                        POJO_1_NOT_MY_TYPE));
    }

    @Test
    void test_uuidPartialMatch() {
        doTest("uuid:70d",
                List.of(POJO_1,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_MISSING,
                        POJO_1_BAD_NAME));
    }

    @Test
    void test_uuidPrefixMatch() {
        doTest("uuid:^70d",
                List.of(POJO_1),
                List.of(POJO_1_MISSING,
                        POJO_1_BAD_NAME,
                        POJO_1_NOT_MY_TYPE));
    }

    @Test
    void test_threeFields() {
        doTest("status:ok \"my name\" type:=mytype",
                List.of(POJO_1),
                List.of(POJO_1_MISSING,
                        POJO_1_BAD_NAME,
                        POJO_1_NOT_MY_TYPE));
    }

    @Test
    void test_oneDefaultField() {
        doTest("~myname",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }

    @Test
    void test_oneDefaultFieldNegated() {
        doTest("!~myname",
                List.of(POJO_1_BAD_NAME),
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE));
    }

    @Test
    void test_qualifyDefaultField() {
        doTest(" simplestr1:~myname ",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }

    @Test
    void test_qualifyDefaultFieldNegated() {
        doTest(" simplestr1:!~myname ",
                List.of(POJO_1_BAD_NAME),
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE));
    }

    @Test
    void test_defaultFieldTwice_charsAnywhere() {

        // Need quotes to treat them as two tokens
        doTest("~myname ~othername",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }

    @Test
    void test_defaultFieldTwice_contains() {

        // Need quotes to treat them as two tokens
        doTest("my name",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }

    @Test
    void test_defaultFieldTwice_contains_dupTokens() {

        // Two identical tokens
        doTest("my my",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }

    @Test
    void test_defaultFieldTwice_contains_dupQualifiedTokens() {

        // Two identical tokens
        doTest("status:ok status:ok",
                List.of(POJO_1,
                        POJO_1_BAD_NAME,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_MISSING));
    }

    @Test
    void test_defaultFieldTwice_contains_noMatch() {

        // Need quotes to treat them as two tokens
        doTest("my nomatch",
                Collections.emptyList(),
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE,
                        POJO_1_BAD_NAME));
    }

    @Test
    void test_matchSecondDefaultField_regex() {

        // term needs to be quoted to stop the two words being treated as two tokens ('/other' and 'name')
        doTest("/\"other name\"",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }

    @Test
    void test_matchSecondDefaultField_contains() {

        // term needs to be quoted to stop the two words being treated as two tokens ('/other' and 'name')
        doTest("\"other name\"",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }
//
//    @TestFactory
//    List<DynamicTest> testParseMatchTokens() {
//
//        return List.of(
//                makeTokenTest("\"",
//                        List.of(
//                        ),
//                        List.of()),
//                makeTokenTest("a\\\"bc", // escaped dbl quote '\"'
//                        List.of(
//                                MatchToken.of("a\"bc") // 'a"bc'
//                        ),
//                        List.of()),
//                makeTokenTest("\"abc", // un-matched dbl quote, should not parse
//                        List.of(
//                        ),
//                        List.of()),
//                makeTokenTest(" a b c ",
//                        List.of(
//                                MatchToken.of("a"),
//                                MatchToken.of("b"),
//                                MatchToken.of("c")
//                        ),
//                        List.of()),
//                makeTokenTest(" \"a b c\"  \"d e f\" ",
//                        List.of(
//                                MatchToken.of("a b c"),
//                                MatchToken.of("d e f")
//                        ),
//                        List.of()),
//                makeTokenTest("foo:bar",
//                        List.of(
//                                MatchToken.of("foo", "bar")
//                        ),
//                        List.of("foo")),
//                makeTokenTest("foo:", // Ignore empty qualified token
//                        List.of(
////                                MatchToken.of("foo", "")
//                        ),
//                        List.of("foo")),
//                makeTokenTest("colour:red size:big",
//                        List.of(
//                                MatchToken.of("colour", "red"),
//                                MatchToken.of("size", "big")
//                        ),
//                        List.of("colour", "size")),
//                makeTokenTest("\"colour:red\"        \"size:big\"",
//                        List.of(
//                                MatchToken.of("colour", "red"),
//                                MatchToken.of("size", "big")
//                        ),
//                        List.of("colour", "size")),
//                makeTokenTest("\"colour:red\"        big",
//                        List.of(
//                                MatchToken.of("colour", "red"),
//                                MatchToken.of("big")
//                        ),
//                        List.of("colour"))
//        );
//    }

    @Test
    void testFilterStream_string_contains() {
        final List<String> data = List.of(
                "Brown Fox",
                "Red Panda",
                "Blue Whale",
                "Brown Bear",
                "Black Bear",
                "Red Dragon");

        final List<String> filteredData = expressionPredicateFactory.filterAndSortStream(
                        data.stream(),
                        "bear",
                        Optional.of(Comparator.naturalOrder()))
                .toList();

        Assertions.assertThat(filteredData)
                .containsExactly(
                        "Black Bear",
                        "Brown Bear");
    }

    @Test
    void testFilterStream_string_charsAnywhere() {
        final List<String> data = List.of(
                "Brown Fox",
                "Red Panda",
                "Blue Whale",
                "Brown Bear",
                "Black Bear",
                "Red Dragon");

        final List<String> filteredData = expressionPredicateFactory.filterAndSortStream(
                        data.stream(),
                        "~ea",
                        Optional.of(Comparator.naturalOrder()))
                .toList();

        // ea closest together in bEAr, furthest in rEd drAgon
        Assertions.assertThat(filteredData)
                .containsExactly(
                        "Black Bear",
                        "Brown Bear",
                        "Red Panda",
                        "Blue Whale",
                        "Red Dragon");
    }

    @Test
    void testFilterStream_string_regex() {
        final List<String> data = List.of(
                "Brown Fox",
                "Red Panda",
                "Blue Whale",
                "Brown Bear",
                "Black Bear",
                "Red Dragon");

        final List<String> filteredData = expressionPredicateFactory.filterAndSortStream(
                        data.stream(),
                        "/e.*a",
                        Optional.of(Comparator.naturalOrder()))
                .toList();

        // ea closest together in bEAr, furthest in rEd drAgon
        Assertions.assertThat(filteredData)
                .containsExactly(
                        "Black Bear",
                        "Brown Bear",
                        "Blue Whale",
                        "Red Dragon",
                        "Red Panda"); // matches on e to 2nd a, i.e. rED PANDA
    }

    @Test
    void testQualifyTerms() {
        final String input = "xxx";
        final String expectedQualifiedInput = "AND {name contains xxx}";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_PROVIDER_2);
    }

    @Test
    void testQualifyTerms2() {
        final String input = "?xxx";
        final String expectedQualifiedInput = "AND {name word boundary xxx}";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_PROVIDER_2);
    }

    @Test
    void testQualifyTerms3() {
        final String input = "jane sex:fe";
        final String expectedQualifiedInput = "AND {name contains jane, sex contains fe}";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_PROVIDER_2);
    }

    @Test
    void testQualifyTerms4() {
        final String input = "name:jane sex:fe";
        final String expectedQualifiedInput = "AND {name contains jane, sex contains fe}";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_PROVIDER_2);
    }

    @Test
    void testQualifyTerms5() {
        final String input = "xxx";
        final String expectedQualifiedInput = "OR {simplestr1 contains xxx, simplestr2 contains xxx}";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_PROVIDER);
    }

    @Test
    void testQualifyTerms6() {
        final String input = "simplestr1:xxx";
        final String expectedQualifiedInput = "AND {simplestr1 contains xxx}";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_PROVIDER);
    }

    @Test
    void testQualifyTerms7() {
        final String input = "xxx name:fubar";
        final String expectedQualifiedInput =
                "AND {OR {simplestr1 contains xxx, simplestr2 contains xxx}, name contains fubar}";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_PROVIDER);
    }

    /**
     * A ':' only introduces a qualifier when the text before it names a field. Previously any
     * unescaped ':' anywhere in a token was read as a qualifier, which then failed to resolve and
     * threw "Unknown field: ...", so these values could only be searched for if quoted.
     */
    @Test
    void testUnresolvedQualifierIsPartOfTheValue() {
        doQualifyInputTest("12:30",
                "OR {simplestr1 contains 12:30, simplestr2 contains 12:30}",
                FIELD_PROVIDER);
        doQualifyInputTest("http://example.com",
                "OR {simplestr1 contains http://example.com, simplestr2 contains http://example.com}",
                FIELD_PROVIDER);
        doQualifyInputTest("2000-01-01T00:00:00.000Z",
                "OR {simplestr1 contains 2000-01-01T00:00:00.000Z, "
                + "simplestr2 contains 2000-01-01T00:00:00.000Z}",
                FIELD_PROVIDER);
    }

    /**
     * A resolvable qualifier must still qualify, and must still win over the value interpretation.
     */
    @Test
    void testResolvedQualifierStillQualifies() {
        doQualifyInputTest("name:fubar", "AND {name contains fubar}", FIELD_PROVIDER);
        // The field is resolved on the text before the FIRST colon, so the rest is the value.
        doQualifyInputTest("name:12:30", "AND {name contains 12:30}", FIELD_PROVIDER);
    }

    /**
     * A column value filter always applies to its own column, so nothing ever resolves and ':' is
     * always an ordinary value character.
     */
    @Test
    void testSingleFieldProvider_colonIsNotAQualifier() {
        final FieldProvider fieldProvider = new SingleFieldProvider(QueryField.createUiText("test"));

        doQualifyInputTest("12:30", "AND {test contains 12:30}", fieldProvider);
        doQualifyInputTest("http://example.com", "AND {test contains http://example.com}", fieldProvider);
        doQualifyInputTest("key:value", "AND {test contains key:value}", fieldProvider);
        // An unquoted timestamp - previously only worked if the user quoted it.
        doQualifyInputTest("2000-01-01T00:00:00.000Z",
                "AND {test contains 2000-01-01T00:00:00.000Z}",
                fieldProvider);
    }

    /**
     * The colon fix must not disturb operator parsing on the same surface.
     */
    @Test
    void testSingleFieldProvider_operatorsStillApply() {
        final FieldProvider fieldProvider = new SingleFieldProvider(QueryField.createUiText("test"));

        doQualifyInputTest("=12:30", "AND {test = 12:30}", fieldProvider);
        doQualifyInputTest("^12:30", "AND {test starts with 12:30}", fieldProvider);
        // A lone NOT operator is returned directly rather than wrapped in an outer AND.
        doQualifyInputTest("!12:30", "NOT {test contains 12:30}", fieldProvider);
    }

    /**
     * An unknown qualifier is no longer an error - it is searched for literally. This trades the
     * "Unknown field" diagnostic for the ability to type colon-bearing values unquoted. Restoring
     * that feedback as a non-fatal warning is tracked in the surface syntax spec; note the old
     * exception was in practice invisible, being swallowed by each DAO and shown as an empty grid.
     */
    @Test
    void testUnknownQualifierIsNotAnError() {
        doQualifyInputTest("nosuchfield:x",
                "OR {simplestr1 contains nosuchfield:x, simplestr2 contains nosuchfield:x}",
                FIELD_PROVIDER);
    }

    // --------------------------------------------------------------------------------
    // A term the user writes with no sigil takes its condition from the field it resolved
    // against, rather than always being CONTAINS. See SimpleStringExpressionParser.defaultCondition.

    private static final FieldProvider NARROW_FIELD_PROVIDER = new FieldProviderImpl(
            List.of(QueryField.builder()
                    .fldName("status")
                    .fldType(FieldType.TEXT)
                    .conditionSet(ConditionSet.SQL_ENUM_TEXT)
                    .build()),
            List.of(QueryField.builder()
                    .fldName("status")
                    .fldType(FieldType.TEXT)
                    .conditionSet(ConditionSet.SQL_ENUM_TEXT)
                    .build()));

    @Test
    void testDefaultCondition_fieldSupportingContainsGetsContains() {
        doQualifyInputTest("jane", "AND {name contains jane}", FIELD_PROVIDER_2);
    }

    @Test
    void testDefaultCondition_fieldNotSupportingContainsGetsEquals() {
        // SQL_ENUM_TEXT is {EQUALS, NOT_EQUALS}. Defaulting to CONTAINS here would emit a term the
        // field's own declaration says is unsupported - which is exactly what "status:OK" did on
        // the Dependencies screen.
        doQualifyInputTest("OK", "AND {status = OK}", NARROW_FIELD_PROVIDER);
        doQualifyInputTest("status:OK", "AND {status = OK}", NARROW_FIELD_PROVIDER);
    }

    @Test
    void testDefaultCondition_explicitSigilStillWins() {
        // The fallback only applies when the user wrote no sigil. An explicit operator is
        // honoured verbatim rather than silently rewritten to something the field does support.
        doQualifyInputTest("=OK", "AND {status = OK}", NARROW_FIELD_PROVIDER);
    }

    @Test
    void testCapabilityCheck_explicitUnsupportedConditionIsRejected() {
        // '^' is honoured verbatim, so it reaches the capability check and is rejected there with
        // a positional message naming what the field does support - rather than reaching
        // TermHandler's default case and being swallowed into an empty grid by the DAO.
        Assertions.assertThatThrownBy(() ->
                        SimpleStringExpressionParser.create(NARROW_FIELD_PROVIDER, "^OK"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("does not support 'starts with'")
                .hasMessageContaining("'='");
    }

    @Test
    void testCapabilityCheck_orderingConditionsAreSupportedOnTextFields() {
        // Both evaluators compare text lexicographically for these, so both text sets declare
        // them. Arming the check without them would have broken '>foo' everywhere it works.
        doQualifyInputTest(">m", "AND {name > m}", FIELD_PROVIDER_2);
        doQualifyInputTest(">=m", "AND {name >= m}", FIELD_PROVIDER_2);
        doQualifyInputTest("<m", "AND {name < m}", FIELD_PROVIDER_2);
        doQualifyInputTest("<=m", "AND {name <= m}", FIELD_PROVIDER_2);
    }

    @Test
    void testDefaultCondition_negationUsesTheFieldsDefault() {
        doQualifyInputTest("!OK", "NOT {status = OK}", NARROW_FIELD_PROVIDER);
    }

    /**
     * Every field the Dependencies quick filter declares must be able to honour a bare term, or
     * arming the {@code CommonExpressionMapper} capability check turns a working filter into a
     * rejection. {@code QF_STATUS} is {@code SQL_ENUM_TEXT} and was the known offender.
     */
    @Test
    void testDefaultCondition_isSupportedByEveryDeclaredField() {
        for (final QueryField field : List.of(
                QueryField.createSqlText("a"),
                QueryField.createUiText("b"),
                QueryField.createText("c"),
                QueryField.builder()
                        .fldName("d")
                        .fldType(FieldType.TEXT)
                        .conditionSet(ConditionSet.SQL_ENUM_TEXT)
                        .build())) {
            final FieldProvider provider = new FieldProviderImpl(List.of(field), List.of(field));
            final ExpressionOperator operator = SimpleStringExpressionParser
                    .create(provider, "someValue")
                    .orElseThrow();
            final ExpressionTerm term = (ExpressionTerm) operator.getChildren().getFirst();
            Assertions.assertThat(field.supportsCondition(term.getCondition()))
                    .describedAs("field %s declares %s but a bare term produced %s",
                            field.getFldName(), field.getConditionSet(), term.getCondition())
                    .isTrue();
        }
    }

    // --------------------------------------------------------------------------------
    // The contract every quick filter surface's catch block depends on. GlobalConfigService,
    // TaskManagerImpl, ExplorerServiceImpl, ActivityServiceImpl and the five DAOs all catch
    // TokenException specifically; if the parser threw something else for realistic mid-keystroke
    // input, those catches would miss and the user would get an error instead of an empty result.

    @Test
    void testMidKeystrokeInput_throwsTokenExceptionNotSomethingElse() {
        // A trailing operator is the ordinary transient state of typing "foo and bar" - the
        // debounce fires on "foo and" long before the user finishes.
        for (final String input : List.of("foo and", "foo or", "foo AND", "not", "and", "or")) {
            Assertions.assertThatThrownBy(() ->
                            SimpleStringExpressionParser.create(FIELD_PROVIDER_2, input))
                    .describedAs("input [%s]", input)
                    .isInstanceOf(TokenException.class);
        }
    }

    @Test
    void testMidKeystrokeInput_unclosedQuotesAndBracketsDoNotThrow() {
        // Documenting the boundary rather than asserting a wish: these parse cleanly and simply
        // match little or nothing. Worth pinning because it is the opposite of what the name
        // "unparseable filter" suggests, and because a future tokeniser change that made them
        // throw would silently widen what the catch blocks have to handle.
        for (final String input : List.of("\"unbalanced", "(unclosed", ")", "\\", "foo or or bar")) {
            Assertions.assertThatCode(() ->
                            SimpleStringExpressionParser.create(FIELD_PROVIDER_2, input))
                    .describedAs("input [%s]", input)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void testMidKeystrokeInput_tokenExceptionIsPositional() {
        // The diagnostic is only useful if it can point at something, so the exception must carry
        // a token. TokenErrorUtil turns this into the from/to on ResultPage.filterError.
        final TokenException e = Assertions.catchThrowableOfType(
                () -> SimpleStringExpressionParser.create(FIELD_PROVIDER_2, "foo and"),
                TokenException.class);
        Assertions.assertThat(e.getToken()).isNotNull();
        Assertions.assertThat(e.getMessage()).isNotBlank();
    }

    private void doQualifyInputTest(final String input,
                                    final String expectedQualifiedInput,
                                    final FieldProvider fieldProvider) {
        final Optional<ExpressionOperator> expressionOperator =
                SimpleStringExpressionParser.create(fieldProvider, input);

        final String expression = expressionOperator.map(Object::toString).orElse(null);
        LOGGER.info("input: {}, qualifiedInput: {}", input, expression);

        Assertions.assertThat(expression).isEqualTo(expectedQualifiedInput);
    }

//    private DynamicTest makeTokenTest(final String input,
//                                      final List<MatchToken> expectedTokens,
//                                      final List<String> validQualifiers) {
//        final FilterFieldMappers<String> fieldMappers = FilterFieldMappers.of(validQualifiers.stream()
//                .map(str ->
//                        FilterFieldMapper.of(FilterFieldDefinition.qualifiedField(str), Function.identity()))
//                .collect(Collectors.toList()));
//        return DynamicTest.dynamicTest("[" + input + "]", () -> {
//            final List<MatchToken> matchTokens = QuickFilterPredicateFactory.extractMatchTokens(input, fieldMappers);
//
//            LOGGER.info("Result: {}", matchTokens);
//            Assertions.assertThat(matchTokens)
//                    .containsExactlyElementsOf(expectedTokens);
//        });
//    }

    private void doTest(final String input,
                        final List<Pojo> shouldMatch,
                        final List<Pojo> shouldNotMatch) {

        LOGGER.info("Testing input [{}]", ConsoleColour.cyan(input));

        final List<Pojo> matched = expressionPredicateFactory.filterAndSortStream(
                        Stream.concat(shouldMatch.stream(), shouldNotMatch.stream()),
                        input,
                        FIELD_PROVIDER,
                        VALUE_FUNCTION_FACTORIES,
                        Optional.empty())
                .toList();

        LOGGER.info("Should match:\n{}",
                ConsoleColour.green(shouldMatch.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining("\n"))));
        LOGGER.info("Should NOT match:\n{}",
                ConsoleColour.red(shouldNotMatch.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining("\n"))));

        LOGGER.info("Matched:\n{}",
                ConsoleColour.green(matched.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining("\n"))));

        Assertions.assertThat(matched)
                .containsExactlyInAnyOrderElementsOf(shouldMatch);

        // Now test it as a stream
        final Predicate<Pojo> predicate = expressionPredicateFactory.create(
                input,
                FIELD_PROVIDER,
                VALUE_FUNCTION_FACTORIES,
                DateTimeSettings.builder().build());
        final List<Pojo> streamMatched = Stream
                .concat(shouldMatch.stream(), shouldNotMatch.stream())
                .filter(predicate)
                .toList();

        Assertions.assertThat(matched)
                .containsExactlyElementsOf(shouldMatch);
    }

    private static class Pojo {

        private final String status;
        private final String simpleStr1;
        private final String simpleStr2;
        private final DocRef docRef;

        public Pojo(final String status, final String simpleStr1, final String simpleStr2, final DocRef docRef) {
            this.status = status;
            this.simpleStr1 = simpleStr1;
            this.simpleStr2 = simpleStr2;
            this.docRef = docRef;
        }

        public Pojo(final String status,
                    final String simpleStr1,
                    final String simpleStr2,
                    final String docRefName,
                    final String docRefType,
                    final String uuid) {
            this.status = status;
            this.simpleStr1 = simpleStr1;
            this.simpleStr2 = simpleStr2;
            this.docRef = new DocRef(docRefType, uuid, docRefName);
        }

        public String getSimpleStr1() {
            return simpleStr1;
        }

        public String getSimpleStr2() {
            return simpleStr2;
        }

        public String getStatus() {
            return status;
        }

        public DocRef getDocRef() {
            return docRef;
        }

        @Override
        public String toString() {
            return "Pojo{" +
                   "status='" + status + '\'' +
                   ", simpleStr1='" + simpleStr1 + '\'' +
                   ", simpleStr2='" + simpleStr2 + '\'' +
                   ", docRef=" + docRef +
                   '}';
        }
    }

}
