package stroom.util.docref;

import stroom.docref.DocRef;
import stroom.util.string.StringPredicateFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;
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
    
    public static Predicate<DocRef> createFuzzyMatchPredicate(final String userInput) {
        return createFuzzyMatchPredicate(userInput, MatchMode.NAME);
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
    public static Predicate<DocRef> createFuzzyMatchPredicate(
            final String userInput, 
            final MatchMode matchMode) {

        Objects.requireNonNull(matchMode);

        LOGGER.trace("Creating predicate for userInput [{}]", userInput);
        final Predicate<DocRef> predicate;
        if (userInput == null || userInput.isEmpty()) {
            LOGGER.trace("Creating null input predicate");
            // No input so get everything
            predicate = stringUnderTest -> true;
        } else if (userInput.startsWith(UUID_MATCH_PREFIX)) {
            // User input like: '#ab68'
            return createUuidContainsPredicate(userInput);
        } else if (UUID_PATTERN.matcher(userInput).matches()) {
            // User input like: 'a92aea37-ab68-479c-9c2a-b04c6c705ed9'
            return createUuidExactMatchPredicate(userInput);
        } else {
            // Doesn't look like uuid input so match on (either) name and/or type
            return createDefaultPredicate(userInput, matchMode);
        }
        return predicate;
    }

    private static Predicate<DocRef> createDefaultPredicate(
            final String userInput,
            final MatchMode matchMode) {

        LOGGER.trace("Creating fuzzy name predicate with mode {}", matchMode);
        final Predicate<String> fuzzyStringPredicate =
                StringPredicateFactory.createFuzzyMatchPredicate(userInput);
        if (MatchMode.NAME.equals(matchMode)) {
            return StringPredicateFactory.toNullSafePredicate(
                    false,
                    docRef -> fuzzyStringPredicate.test(docRef.getName()));
        } else if (MatchMode.TYPE.equals(matchMode)) {
            return StringPredicateFactory.toNullSafePredicate(
                    false,
                    docRef -> fuzzyStringPredicate.test(docRef.getType()));
        } else if (MatchMode.NAME_OR_TYPE.equals(matchMode)) {
            return StringPredicateFactory.toNullSafePredicate(
                    false,
                    docRef ->
                            fuzzyStringPredicate.test(docRef.getName())
                                    ||fuzzyStringPredicate.test(docRef.getType()));
        } else {
            throw new RuntimeException("Unexpected type " + matchMode);
        }
    }

    private static Predicate<DocRef> createUuidExactMatchPredicate(final String userInput) {
        LOGGER.trace("Creating UUID exact match predicate");
        return toNullSafePredicate(
                false,
                DocRef::getUuid,
                uuid -> uuid.equalsIgnoreCase(userInput));
    }

    private static Predicate<DocRef> createUuidContainsPredicate(final String userInput) {
        LOGGER.trace("Creating UUID partial match predicate");
        // Strip the prefix that tells us it is a uuid partial match
        final String lowerCaseInput = userInput.substring(1);
        return toNullSafePredicate(
                false,
                DocRef::getUuid,
                uuid -> uuid.toLowerCase().contains(lowerCaseInput));
    }

    private static Predicate<DocRef> toNullSafePredicate(final boolean resultIfNull,
                                                  final Function<DocRef, String> valueExtractor,
                                                  final Predicate<String> stringPredicate) {
        return docRef -> {
            final boolean result;
            if (docRef != null) {
                final String val = valueExtractor.apply(docRef);
                if (val != null) {
                    result = stringPredicate.test(val);
                } else {
                    // Null value
                    result = resultIfNull;
                }
            } else {
                // Null docref
                result = resultIfNull;
            }
            return result;
        };
    }

    public enum MatchMode {
        /**
         * Matches on DocRef.name only
         */
        NAME,
        /**
         * Matches on DocRef.type only
         */
        TYPE,
        /**
         * Matches on either DocRef.name or DocRef.type
         */
        NAME_OR_TYPE
    }
}
