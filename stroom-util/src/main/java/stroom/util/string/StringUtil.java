package stroom.util.string;

import com.google.common.base.Preconditions;

import java.security.SecureRandom;
import java.util.Objects;
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

    public static final char[] ALLOWED_CHARS_CASE_SENSITIVE_ALPHA_NUMERIC =
            "abcdefghijklmnopqrstuvwxyzABCDEFGJKLMNPRSTUVWXYZ0123456789".toCharArray();
    public static final char[] ALLOWED_CHARS_CASE_INSENSITIVE_ALPHA_NUMERIC =
            "ABCDEFGJKLMNPRSTUVWXYZ0123456789".toCharArray();
    public static final char[] ALLOWED_CHARS_HEX =
            "0123456789ABCDEF".toCharArray();
    // See Base58Check. This is NOT base58Check, but uses the same chars, ie. no 'o0il1' for readability
    public static final char[] ALLOWED_CHARS_BASE_58_STYLE = Base58.ALPHABET;

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

    public static String createRandomCode(final int length) {
        return createRandomCode(new SecureRandom(), length, ALLOWED_CHARS_BASE_58_STYLE);
    }

    public static String createRandomCode(final SecureRandom secureRandom,
                                          final int length) {
        return createRandomCode(secureRandom, length, ALLOWED_CHARS_BASE_58_STYLE);
    }

    public static String createRandomCode(final SecureRandom secureRandom,
                                          final int length,
                                          final char[] allowedChars) {
        Preconditions.checkArgument(length >= 1, "length must be >= 1");
        Objects.requireNonNull(allowedChars);
        final int count = allowedChars.length;
        Preconditions.checkArgument(count >= 1, "Need at least one allowedChar");

        final StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < length; i++) {
            stringBuilder.append(allowedChars[secureRandom.nextInt(count)]);
        }
        return stringBuilder.toString();
    }
}
