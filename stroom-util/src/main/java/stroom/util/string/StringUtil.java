package stroom.util.string;

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A partner to {@link stroom.util.shared.StringUtil} that is for server side
 * use only so can contain java regex goodness.
 */
public class StringUtil {

    // Split on one or more unix/windows/dos line ends
    private static final Pattern LINE_SPLIT_PATTERN = Pattern.compile("(\r?\n)");

    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("((\r?\n)|\\s)");

    private StringUtil() {
    }

    /**
     * Splits text into lines, where a line is delimited by \n or \r\n.
     * @param trimLines If true, trims any leading/trailing space and ignores any blank lines
     */
    public static Stream<String> splitToLines(final String text,
                                              final boolean trimLines) {
        if (text == null || text.isEmpty() || (trimLines && text.isBlank())) {
            return Stream.empty();
        } else {
            Stream<String> stream = LINE_SPLIT_PATTERN.splitAsStream(text);

            if (trimLines) {
                stream = stream.map(String::trim)
                        .filter(str -> !str.isBlank());
            }

            return stream;
        }
    }

    /**
     * Splits text into words where delimiters are taken to be \n, \r\n or the regex \s.
     * Ignores blank words.
     */
    public static Stream<String> splitToWords(final String text) {
        if (text == null || text.isBlank()) {
            return Stream.empty();
        } else {

            return WORD_SPLIT_PATTERN.splitAsStream(text)
                    .filter(str -> !str.isBlank());
        }
    }
}
