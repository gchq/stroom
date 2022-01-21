package stroom.util.filter;

import stroom.docref.DocRef;
import stroom.util.ConsoleColour;
import stroom.util.filter.QuickFilterPredicateFactory.MatchToken;
import stroom.util.shared.filter.FilterFieldDefinition;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestQuickFilterPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestQuickFilterPredicateFactory.class);

    private static final FilterFieldMappers<Pojo> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Status"), Pojo::getStatus),
            FilterFieldMapper.of(FilterFieldDefinition.defaultField("SimpleStr1"), Pojo::getSimpleStr1),
            FilterFieldMapper.of(FilterFieldDefinition.defaultField("SimpleStr2"), Pojo::getSimpleStr2),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Type"), Pojo::getDocRef, DocRef::getType),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Name"), Pojo::getDocRef, DocRef::getName),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Uuid"), Pojo::getDocRef, DocRef::getUuid));

    private static final FilterFieldMappers<Pojo> FIELD_MAPPERS_2 = FilterFieldMappers.of(
            FilterFieldMapper.of(FilterFieldDefinition.defaultField("Name"), Pojo::getStatus),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Age"), Pojo::getSimpleStr1),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Sex"), Pojo::getSimpleStr2));

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
        doTest("\"~myname\" \"~othername\"",
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
        doTest("\"/other name\"",
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

    @TestFactory
    List<DynamicTest> testParseMatchTokens() {

        return List.of(
                makeTokenTest("\"",
                        List.of(
                        ),
                        List.of()),
                makeTokenTest("a\\\"bc", // escaped dbl quote '\"'
                        List.of(
                                MatchToken.of("a\"bc") // 'a"bc'
                        ),
                        List.of()),
                makeTokenTest("\"abc", // un-matched dbl quote, should not parse
                        List.of(
                        ),
                        List.of()),
                makeTokenTest(" a b c ",
                        List.of(
                                MatchToken.of("a"),
                                MatchToken.of("b"),
                                MatchToken.of("c")
                        ),
                        List.of()),
                makeTokenTest(" \"a b c\"  \"d e f\" ",
                        List.of(
                                MatchToken.of("a b c"),
                                MatchToken.of("d e f")
                        ),
                        List.of()),
                makeTokenTest("foo:bar",
                        List.of(
                                MatchToken.of("foo", "bar")
                        ),
                        List.of("foo")),
                makeTokenTest("foo:", // Ignore empty qualified token
                        List.of(
//                                MatchToken.of("foo", "")
                        ),
                        List.of("foo")),
                makeTokenTest("colour:red size:big",
                        List.of(
                                MatchToken.of("colour", "red"),
                                MatchToken.of("size", "big")
                        ),
                        List.of("colour", "size")),
                makeTokenTest("\"colour:red\"        \"size:big\"",
                        List.of(
                                MatchToken.of("colour", "red"),
                                MatchToken.of("size", "big")
                        ),
                        List.of("colour", "size")),
                makeTokenTest("\"colour:red\"        big",
                        List.of(
                                MatchToken.of("colour", "red"),
                                MatchToken.of("big")
                        ),
                        List.of("colour"))
        );
    }

    @Test
    void testFilterStream_string_contains() {
        List<String> data = List.of(
                "Brown Fox",
                "Red Panda",
                "Blue Whale",
                "Brown Bear",
                "Black Bear",
                "Red Dragon");

        final List<String> filteredData = QuickFilterPredicateFactory.filterStream("bear", data.stream())
                .collect(Collectors.toList());

        Assertions.assertThat(filteredData)
                .containsExactly(
                        "Black Bear",
                        "Brown Bear");
    }

    @Test
    void testFilterStream_string_charsAnywhere() {
        List<String> data = List.of(
                "Brown Fox",
                "Red Panda",
                "Blue Whale",
                "Brown Bear",
                "Black Bear",
                "Red Dragon");

        final List<String> filteredData = QuickFilterPredicateFactory.filterStream("~ea", data.stream())
                .collect(Collectors.toList());

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
        List<String> data = List.of(
                "Brown Fox",
                "Red Panda",
                "Blue Whale",
                "Brown Bear",
                "Black Bear",
                "Red Dragon");

        final List<String> filteredData = QuickFilterPredicateFactory.filterStream("/e.*a", data.stream())
                .collect(Collectors.toList());

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
        final String expectedQualifiedInput = "name:xxx";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_MAPPERS_2);
    }

    @Test
    void testQualifyTerms2() {
        final String input = "?xxx";
        final String expectedQualifiedInput = "name:?xxx";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_MAPPERS_2);
    }

    @Test
    void testQualifyTerms3() {
        final String input = "jane sex:fe";
        final String expectedQualifiedInput = "name:jane AND sex:fe";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_MAPPERS_2);
    }

    @Test
    void testQualifyTerms4() {
        final String input = "name:jane sex:fe";
        final String expectedQualifiedInput = "name:jane AND sex:fe";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_MAPPERS_2);
    }

    @Test
    void testQualifyTerms5() {
        final String input = "xxx";
        final String expectedQualifiedInput = "(simplestr1:xxx OR simplestr2:xxx)";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_MAPPERS);
    }

    @Test
    void testQualifyTerms6() {
        final String input = "simplestr1:xxx";
        final String expectedQualifiedInput = "simplestr1:xxx";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_MAPPERS);
    }

    @Test
    void testQualifyTerms7() {
        final String input = "xxx name:fubar";
        final String expectedQualifiedInput = "(simplestr1:xxx OR simplestr2:xxx) AND name:fubar";
        doQualifyInputTest(input, expectedQualifiedInput, FIELD_MAPPERS);
    }

    private void doQualifyInputTest(final String input,
                                    final String expectedQualifiedInput,
                                    final FilterFieldMappers<?> fieldMappers) {
        final String qualifiedInput = QuickFilterPredicateFactory.fullyQualifyInput(input, fieldMappers);

        LOGGER.info("input: {}, qualifiedInput: {}", input, qualifiedInput);

        Assertions.assertThat(qualifiedInput)
                .isEqualTo(expectedQualifiedInput);
    }

    private DynamicTest makeTokenTest(final String input,
                                      final List<MatchToken> expectedTokens,
                                      final List<String> validQualifiers) {
        final FilterFieldMappers<String> fieldMappers = FilterFieldMappers.of(validQualifiers.stream()
                .map(str ->
                        FilterFieldMapper.of(FilterFieldDefinition.qualifiedField(str), Function.identity()))
                .collect(Collectors.toList()));
        return DynamicTest.dynamicTest("[" + input + "]", () -> {
            final List<MatchToken> matchTokens = QuickFilterPredicateFactory.extractMatchTokens(input, fieldMappers);

            LOGGER.info("Result: {}", matchTokens);
            Assertions.assertThat(matchTokens)
                    .containsExactlyElementsOf(expectedTokens);
        });
    }

    private void doTest(final String input,
                        final List<Pojo> shouldMatch,
                        final List<Pojo> shouldNotMatch) {

        LOGGER.info("Testing input [{}]", ConsoleColour.cyan(input));

        final Predicate<Pojo> predicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(input, FIELD_MAPPERS);

        final List<Pojo> matched = Stream.concat(shouldMatch.stream(), shouldNotMatch.stream())
                .filter(predicate)
                .collect(Collectors.toList());

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

        final List<Pojo> streamMatched = QuickFilterPredicateFactory.filterStream(
                input,
                FIELD_MAPPERS,
                Stream.concat(shouldMatch.stream(), shouldNotMatch.stream()))
                .collect(Collectors.toList());

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
