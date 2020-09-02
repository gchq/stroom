package stroom.util.docref;

import stroom.docref.DocRef;
import stroom.util.string.StringPredicateFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

public class DocRefPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocRefPredicateFactory.class);

    private DocRefPredicateFactory() {
    }

    /**
     * Creates a {@link Predicate<DocRef>} using the supplied input.
     *
     * @see StringPredicateFactory#createFuzzyMatchPredicate(String)
     */
    public static Predicate<DocRef> createFuzzyNameMatchPredicate(final String userInput) {
        final Predicate<String> namePredicate = StringPredicateFactory.createFuzzyMatchPredicate(userInput);
        return docRef ->
                namePredicate.test(docRef.getName());
    }
}
