/*
 * Copyright 2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.db.util;

import stroom.docref.StringMatch;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.stream.Collectors;

// This is effectively tested via IndexFieldDaoImpl
public class StringMatchConditionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StringMatchConditionUtil.class);

    private StringMatchConditionUtil() {
        // Util.
    }

    public static Condition getCondition(final Field<String> field,
                                         final StringMatch stringMatch) {
        final Condition condition;
        if (stringMatch == null) {
            condition = DSL.trueCondition();
        } else {
            condition = switch (stringMatch.getMatchType()) {
                case NULL -> field.isNull();
                case NON_NULL -> field.isNotNull();
                case BLANK -> field.isNotNull().and(DSL.trim(field).equal(""));
                case NON_BLANK -> field.isNotNull().and(DSL.trim(field).notEqual(""));
                case EMPTY -> field.eq("");
                case NON_EMPTY -> field.isNotNull().and(field.notEqual(""));
                case NULL_OR_BLANK -> field.isNull().or(DSL.trim(field).equal(""));
                case NULL_OR_EMPTY -> field.isNull().or(field.eq(""));

                // IMPORTANT:
                // At the time of writing, the collation on all the tables is utf8mb4_0900_ai_ci,
                // which is insensitive on accent and case. If there is a unique key on a varchar
                // column, and you try to insert 'foo' and 'FOO' the latter will violate the key due
                // to the collation. Thus, even if we query in a case-sensitive way, the data may already
                // be affected by unique constraints in place.

                case CONTAINS -> getContainsCondition(field, stringMatch);
                case EQUALS -> getEqualsCondition(field, stringMatch);
                case NOT_EQUALS -> getNotEqualsCondition(field, stringMatch);
                case STARTS_WITH -> getStartsWithCondition(field, stringMatch);
                case ENDS_WITH -> getEndsWithCondition(field, stringMatch);
                case REGEX -> getRegexCondition(field, stringMatch);
                case CHARS_ANYWHERE -> getCharsAnywhereLikePattern(field, stringMatch);
                default -> DSL.trueCondition();
            };
        }
        LOGGER.trace("field: {}, stringMatch: {}, condition: {}", field, stringMatch, condition);
        return condition;
    }

    private static Condition getEndsWithCondition(final Field<String> field, final StringMatch stringMatch) {
        if (stringMatch.isCaseSensitive()) {
            // See note above
            return JooqUtil.withCaseSensitiveCollation(field)
                    .endsWith(stringMatch.getPattern());
        } else {
            return field.endsWithIgnoreCase(stringMatch.getPattern());
        }
    }

    private static Condition getStartsWithCondition(final Field<String> field, final StringMatch stringMatch) {
        if (stringMatch.isCaseSensitive()) {
            // See note above
            return JooqUtil.withCaseSensitiveCollation(field)
                    .startsWith(stringMatch.getPattern());
        } else {
            return field.startsWithIgnoreCase(stringMatch.getPattern());
        }
    }

    private static Condition getNotEqualsCondition(final Field<String> field, final StringMatch stringMatch) {
        if (stringMatch.isCaseSensitive()) {
            // See note above
            return JooqUtil.withCaseSensitiveCollation(field)
                    .notEqual(stringMatch.getPattern());
        } else {
            return field.notEqualIgnoreCase(stringMatch.getPattern());
        }
    }

    private static Condition getEqualsCondition(final Field<String> field, final StringMatch stringMatch) {
        if (stringMatch.isCaseSensitive()) {
            // See note above
            return JooqUtil.withCaseSensitiveCollation(field)
                    .equal(stringMatch.getPattern());
        } else {
            return field.equalIgnoreCase(stringMatch.getPattern());
        }
    }

    private static Condition getContainsCondition(final Field<String> field, final StringMatch stringMatch) {
        if (stringMatch.isCaseSensitive()) {
            // See note above
            return JooqUtil.withCaseSensitiveCollation(field)
                    .contains(stringMatch.getPattern());
        } else {
            return field.containsIgnoreCase(stringMatch.getPattern());
        }
    }

    private static Condition getRegexCondition(final Field<String> field, final StringMatch stringMatch) {
        if (stringMatch.isCaseSensitive()) {
            // See note above
            return JooqUtil.withCaseSensitiveCollation(field)
                    .likeRegex(stringMatch.getPattern());
        } else {
            return field.likeRegex(stringMatch.getPattern());
        }
    }

    private static Condition getCharsAnywhereLikePattern(final Field<String> field,
                                                         final StringMatch stringMatch) {
        String effectivePattern = stringMatch.getPattern().chars()
                .mapToObj(i -> (char) i)
                .map(chr -> Character.toString(chr))
                .collect(Collectors.joining("%"));
        effectivePattern = "%" + effectivePattern + "%";
        if (stringMatch.isCaseSensitive()) {
            return field.like(effectivePattern);
        } else {
            effectivePattern = effectivePattern.toLowerCase();
            return DSL.lower(field).like(effectivePattern);
        }
    }
}
