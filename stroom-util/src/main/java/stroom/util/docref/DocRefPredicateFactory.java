package stroom.util.docref;

import stroom.docref.DocRef;
import stroom.util.string.StringPredicateFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DocRefPredicateFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocRefPredicateFactory.class);

    // Format as returned by UUID.randomUUID().toString()
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$", Pattern.CASE_INSENSITIVE);

    private static final String UUID_MATCH_PREFIX = "#";

    private DocRefPredicateFactory() {
    }

    /**
     * Creates a predicate based on userInput that will match on the UUID or name.
     * If userInput is null or empty return an always false predicate.
     * If userInput matches the format of a complete UUID, return a predicate that
     * does an exact case insensitive match on the UUID of the {@link DocRef}.
     * If userInput starts with '#' return a predicate that does a case insensitive
     * contains match on the UUID of the {@link DocRef}.
     * In all other cases it returns a predicate that does a fuzzy string match on the
     * name of the {@link DocRef}, see {@link StringPredicateFactory}.
     */
    public static Predicate<DocRef> createFuzzyMatchPredicate(final String userInput) {

        LOGGER.trace("Creating predicate for userInput [{}]", userInput);
        final Predicate<DocRef> predicate;
        if (userInput == null || userInput.isEmpty()) {
            LOGGER.trace("Creating null input predicate");
            // No input so get everything
            predicate = stringUnderTest -> true;
        } else if (userInput.startsWith(UUID_MATCH_PREFIX)) {
            LOGGER.trace("Creating UUID partial match predicate");
            // Strip the prefix that tells us it is a uuid partial match
            final String lowerCaseInput = userInput.substring(1);
            return StringPredicateFactory.toNullSafePredicate(
                    false,
                    docRef -> docRef.getUuid().toLowerCase().contains(lowerCaseInput));
        } else if (UUID_PATTERN.matcher(userInput).matches()) {
            LOGGER.trace("Creating UUID exact match predicate");
            return StringPredicateFactory.toNullSafePredicate(
                    false,
                    docRef -> docRef.getUuid().equalsIgnoreCase(userInput));
        } else {
            LOGGER.trace("Creating fuzzy name predicate");
            final Predicate<String> fuzzyStringPredicate = StringPredicateFactory.createFuzzyMatchPredicate(userInput);
            return StringPredicateFactory.toNullSafePredicate(
                    false,
                    docRef -> fuzzyStringPredicate.test(docRef.getName()));
        }
        return predicate;
    }
}
