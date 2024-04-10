package stroom.db.util;

import stroom.docref.StringMatch;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

public class StringMatchConditionUtil {

    private StringMatchConditionUtil() {
        // Util.
    }

    public static Condition getCondition(final Field<String> field,
                                         final StringMatch stringMatch) {
        Condition condition = DSL.trueCondition();
        if (stringMatch != null) {
            switch (stringMatch.getMatchType()) {
                case ANY -> condition = DSL.trueCondition();
                case NULL -> condition = field.isNull();
                case NON_NULL -> condition = field.isNotNull();
                case BLANK -> condition = field.likeRegex("^[[:space:]]*$");
                case EMPTY -> condition = field.eq("");
                case NULL_OR_BLANK -> condition = field.isNull().or(field.likeRegex(
                        "^[[:space:]]*$"));
                case NULL_OR_EMPTY -> condition = field.isNull().or(field.eq(
                        ""));
                case CONTAINS -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.contains(stringMatch.getPattern());
                    } else {
                        condition = field.containsIgnoreCase(stringMatch.getPattern());
                    }
                }
                case EQUALS -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.equal(stringMatch.getPattern());
                    } else {
                        condition = field.equalIgnoreCase(stringMatch.getPattern());
                    }
                }
                case NOT_EQUALS -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.notEqual(stringMatch.getPattern());
                    } else {
                        condition = field.notEqualIgnoreCase(stringMatch.getPattern());
                    }
                }
                case STARTS_WITH -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.startsWith(stringMatch.getPattern());
                    } else {
                        condition = field.startsWithIgnoreCase(stringMatch.getPattern());
                    }
                }
                case ENDS_WITH -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.endsWith(stringMatch.getPattern());
                    } else {
                        condition = field.endsWithIgnoreCase(stringMatch.getPattern());
                    }
                }
                case REGEX -> condition = field.likeRegex(stringMatch.getPattern());
            }
        }
        return condition;
    }
}
