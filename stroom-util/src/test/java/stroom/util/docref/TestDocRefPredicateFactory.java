package stroom.util.docref;


import stroom.docref.DocRef;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestDocRefPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDocRefPredicateFactory.class);

    @Test
    void testNullInput() {

        doFuzzyMatchTest(null,
                List.of(
                        Tuple.of("MY_FEED", UUID.randomUUID().toString()),
                        Tuple.of("NOT_MY_FEED", UUID.randomUUID().toString())),
                Collections.emptyList());
    }

    @Test
    void testEmptyInput() {
        doFuzzyMatchTest("",
                List.of(
                        Tuple.of("MY_FEED", UUID.randomUUID().toString()),
                        Tuple.of("NOT_MY_FEED", UUID.randomUUID().toString())),
                Collections.emptyList());
    }

    @Test
    void testValidFullUuid() {
        final String myUuid = UUID.randomUUID().toString();
        final String notMyUuid = UUID.randomUUID().toString();
        Assertions.assertThat(myUuid).isNotEqualTo(notMyUuid);

        doFuzzyMatchTest(myUuid,
                List.of(
                        Tuple.of("MY_FEED", myUuid)),
                List.of(
                        Tuple.of("NOT_MY_FEED", notMyUuid)));
    }

    @Test
    void testUuidPartialMatch() {
        final String myUuid = "38b027d7-3dd9-43e7-9ec7-99a128fffc75";
        final String notMyUuid = "b26eff96-9ea7-4476-93df-d435eb6c9931";

        doFuzzyMatchTest("#" + myUuid,
                List.of(
                        Tuple.of("MY_FEED", myUuid)),
                List.of(
                        Tuple.of("NOT_MY_FEED", notMyUuid)));
    }

    @Test
    void testUuidPartialMatch2() {
        final String myUuid = "38b027d7-3dd9-43e7-9ec7-99a128fffc75";
        final String notMyUuid = "b26eff96-9ea7-4476-93df-d435eb6c9931";

        doFuzzyMatchTest("#43e7",
                List.of(
                        Tuple.of("MY_FEED", myUuid)),
                List.of(
                        Tuple.of("NOT_MY_FEED", notMyUuid)));
    }

    @Test
    void testPartialUuid() {
        final String myUuid = UUID.randomUUID().toString();
        final String notMyUuid = UUID.randomUUID().toString();
        Assertions.assertThat(myUuid).isNotEqualTo(notMyUuid);

        // We don't support partial matching of uuids else it will be very confusing for the user
        // if they enter some chars and it matches on some random entities when they have
        // no visibility of the UUIDs of those matches.
        doFuzzyMatchTest(myUuid.substring(0, 5),
                List.of(),
                List.of(
                        Tuple.of("MY_FEED", myUuid),
                        Tuple.of("NOT_MY_FEED", notMyUuid)));
    }

    @Test
    void testNameFuzzyMatching() {
        String myUuid = UUID.randomUUID().toString();
        String notMyUuid = UUID.randomUUID().toString();
        Assertions.assertThat(myUuid).isNotEqualTo(notMyUuid);


        // See TestStringPredicateFactory for more detailed tests of the name matching
        doFuzzyMatchTest("MF",
                List.of(
                        Tuple.of("MY_FEED", myUuid)),
                List.of(
                        Tuple.of("NOT_THE_FEED", notMyUuid)));
    }

    private void doFuzzyMatchTest(final String userInput,
                                  final List<Tuple2<String, String>> expectedMatches,
                                  final List<Tuple2<String, String>> expectedNonMatches) {

        LOGGER.info("Testing user input [{}]", userInput);

        final List<DocRef> expectedMatchesNodes = expectedMatches.stream()
                .map(this::createDocRef)
                .collect(Collectors.toList());

        logNodes("Expected matches:", expectedMatchesNodes);

        final List<DocRef> expectedNonMatchesNodes = expectedNonMatches.stream()
                .map(this::createDocRef)
                .collect(Collectors.toList());

        logNodes("Expected non-matches:", expectedNonMatchesNodes);

        final List<DocRef> actualMatchesNodes = Stream.concat(expectedMatchesNodes.stream(),
                expectedNonMatchesNodes.stream())
                .filter(DocRefPredicateFactory.createFuzzyMatchPredicate(userInput))
                .collect(Collectors.toList());

        logNodes("Actual matches:", actualMatchesNodes);

        Assertions.assertThat(actualMatchesNodes)
                .containsExactlyInAnyOrderElementsOf(expectedMatchesNodes);
    }

    private DocRef createDocRef(final Tuple2<String, String> nameUuidTuple) {
        DocRef docRef = new DocRef();
        docRef.setName(nameUuidTuple._1());
        docRef.setUuid(nameUuidTuple._2());
        return docRef;
    }

    private void logNodes(final String msg,
                          final List<DocRef> nodes) {
        String nodesStr = nodes.stream()
                .map(node -> "  " + node.getDisplayValue() + " - " + node.getUuid())
                .collect(Collectors.joining("\n"));
        LOGGER.info("{}\n{}", msg, nodesStr);
    }
}