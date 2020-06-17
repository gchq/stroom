package stroom.util.filter;

import stroom.docref.DocRef;
import stroom.util.ConsoleColour;
import stroom.util.shared.filter.FilterFieldDefinition;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestQuickFilterPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestQuickFilterPredicateFactory.class);

    private static final FilterFieldMappers<Pojo> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Status"), Pojo::getStatus),
            FilterFieldMapper.of(FilterFieldDefinition.defaultField("SimpleStr1"), Pojo::getSimpleStr1),
            FilterFieldMapper.of(FilterFieldDefinition.defaultField("SimpleStr2"), Pojo::getSimpleStr2),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("type"), Pojo::getDocRef, DocRef::getType),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("name"), Pojo::getDocRef, DocRef::getName)
    );
    private static final Pojo POJO_1 = new Pojo(
            "OK",
            "MY NAME",
            "OTHER NAME",
            "DocRefName",
            "MyType",
            "123");

    private static final Pojo POJO_1_MISSING = new Pojo(
            "MISSING",
            "MY NAME",
            "OTHER NAME",
            "DocRefName",
            "MyType",
            "123");

    private static final Pojo POJO_1_BAD_NAME = new Pojo(
            "OK",
            "BAD NAME",
            "OTHER BAD NAME",
            "DocRefName",
            "MyType",
            "123");

    private static final Pojo POJO_1_NOT_MY_TYPE = new Pojo(
            "OK",
            "MY NAME",
            "OTHER NAME",
            "DocRefName",
            "NotMyType",
            "123");

    @Test
    void test_threeFields() {
        doTest("status:ok \"my name\" type:^mytype$",
                List.of(POJO_1),
                List.of(POJO_1_MISSING,
                        POJO_1_BAD_NAME,
                        POJO_1_NOT_MY_TYPE));
    }

    @Test
    void test_oneDefaultField() {
        doTest("myname",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }

    @Test
    void test_qualifyDefaultField() {
        doTest(" simplestr1:myname ",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }

    @Test
    void test_defaultFieldTwice() {

        // Need quotes to treat them as two tokens
        doTest("\"myname\" \"othername\"",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
    }

    @Test
    void test_matchSecondDefaultField() {

        doTest("/other name",
                List.of(POJO_1,
                        POJO_1_MISSING,
                        POJO_1_NOT_MY_TYPE),
                List.of(POJO_1_BAD_NAME));
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