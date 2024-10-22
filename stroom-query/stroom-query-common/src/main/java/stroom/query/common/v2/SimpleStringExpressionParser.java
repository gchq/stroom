package stroom.query.common.v2;

import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.NullSafe;
import stroom.util.shared.GwtNullSafe;

import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

public class SimpleStringExpressionParser {

    public static Optional<ExpressionOperator> create(final String field,
                                                      final String string,
                                                      final boolean caseSensitive) {
        if (GwtNullSafe.isBlankString(string)) {
            return Optional.empty();
        }
        if (string.length() > 1 && string.startsWith("!")) {
            final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.NOT);
            final String value = string.substring(1);
            final Optional<ExpressionOperator> child = create(field, value, caseSensitive);
            if (child.isEmpty()) {
                return Optional.empty();
            }
            builder.children(Collections.singletonList(child.get()));
            return Optional.of(builder.build());
        } else {
            final Optional<ExpressionTerm> term = createTerm(field, string, caseSensitive);
            return term.map(expressionTerm -> ExpressionOperator.builder().addTerm(expressionTerm).build());
        }
    }

    private static Optional<ExpressionTerm> createTerm(final String field,
                                                       final String string,
                                                       final boolean caseSensitive) {
        if (NullSafe.isEmptyString(string)) {
            return Optional.empty();
        }

        Condition condition;
        String value = string;
        if (string.startsWith("?")) {
            // Word boundary matching
            condition = Condition.WORD_BOUNDARY;
            value = string.substring(1);

        } else if (string.startsWith("/")) {
            // Regex matching.
            condition = Condition.MATCHES_REGEX;
            value = string.substring(1);

        } else if (string.startsWith("^")) {
            // Starts with.
            condition = Condition.STARTS_WITH;
            value = string.substring(1);

        } else if (string.startsWith("$")) {
            // Ends with.
            condition = Condition.ENDS_WITH;
            value = string.substring(1);

        } else if (string.startsWith(">=")) {
            // Greater than or equal to numeric matching.
            condition = Condition.GREATER_THAN_OR_EQUAL_TO;
            value = string.substring(2).trim();

        } else if (string.startsWith("<=")) {
            // Less than or equal to numeric matching.
            condition = Condition.LESS_THAN_OR_EQUAL_TO;
            value = string.substring(2).trim();

        } else if (string.startsWith(">")) {
            // Greater than numeric matching.
            condition = Condition.GREATER_THAN;
            value = string.substring(1).trim();

        } else if (string.startsWith("<")) {
            // Less than numeric matching.
            condition = Condition.LESS_THAN;
            value = string.substring(1).trim();

        } else if (string.startsWith("~")) {
            // Characters Anywhere Matching.
            condition = Condition.MATCHES_REGEX;
            value = string.substring(1);
            char[] chars = value.toCharArray();
            final StringBuilder sb = new StringBuilder();
            for (final char c : chars) {
                sb.append(".*?");

                if (c == '*') {
                    // TODO @AT Why is this * block here
                    sb.append(".*?");
                } else if (Character.isLetterOrDigit(c)) {
                    sb.append(c);
                } else {
                    // Might be a special char so escape it
                    sb.append(Pattern.quote(String.valueOf(c)));
                }
            }
            value = sb.toString();

        } else if (string.startsWith("=")) {
            // Equals.
            value = string.substring(1);
            final String possibleRegex = replaceWildcards(value);
            if (possibleRegex.equals(value)) {
                condition = Condition.EQUALS;
                value = possibleRegex;
            } else {
                condition = Condition.MATCHES_REGEX;
                value = "^" + possibleRegex + "$";
            }

        } else if (string.startsWith("\\")) {
            // Escaped contains.
            value = string.substring(1);
            final String possibleRegex = replaceWildcards(value);
            if (possibleRegex.equals(value)) {
                condition = Condition.CONTAINS;
                value = possibleRegex;
            } else {
                condition = Condition.MATCHES_REGEX;
                value = possibleRegex;
            }

        } else {
            // Contains.
            final String possibleRegex = replaceWildcards(value);
            if (possibleRegex.equals(value)) {
                condition = Condition.CONTAINS;
                value = possibleRegex;
            } else {
                condition = Condition.MATCHES_REGEX;
                value = possibleRegex;
            }
        }

        return Optional.of(ExpressionTerm
                .builder()
                .field(field)
                .condition(condition)
                .value(value)
                .caseSensitive(caseSensitive)
                .build());
    }

    private static String replaceWildcards(final String string) {
        char[] chars = string.toCharArray();
        final StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (final char c : chars) {
            if (c == '\\') {
                escape = !escape;
                if (!escape) {
                    sb.append(c);
                }
            } else {
                if (!escape && (c == '*' || c == '?')) {
                    sb.append(".");
                }
                escape = false;
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
