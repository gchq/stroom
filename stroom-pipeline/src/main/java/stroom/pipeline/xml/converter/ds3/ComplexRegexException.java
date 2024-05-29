package stroom.pipeline.xml.converter.ds3;

import java.util.regex.Matcher;

/**
 * Throw this when a {@link StackOverflowError} is caught when matching with a regex.
 * A {@link StackOverflowError} is likely if the regex includes repetitive alternation,
 * .e.g {@code (A|B)*} with a large input.
 * <p>
 * See <a
 * href="https://stackoverflow.com/questions/7509905/java-lang-stackoverflowerror-while-using-a-regex-to-parse-big-strings">StackOverflow of all places</a>
 * </p>
 */
public class ComplexRegexException extends RuntimeException {

    private final Matcher matcher;

    public ComplexRegexException(final Matcher matcher) {
        super(buildMessage(matcher));
        this.matcher = matcher;
    }

    public Matcher getMatcher() {
        return matcher;
    }

    private static String buildMessage(final Matcher matcher) {
        if (matcher == null) {
            return "The regex pattern is too complex to process the input data.";
        } else {
            return "The regex pattern '" + matcher.pattern() + "' is too complex to " +
                    "process the input data region ["
                    + matcher.start() + "," + matcher.end() + "].";
        }
    }
}
