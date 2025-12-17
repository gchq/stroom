/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.explorer.shared.StringMatch;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.stream.Collectors;

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
                case BLANK -> condition = field.isNotNull().and(DSL.trim(field).equal(""));
                case NON_BLANK -> condition = field.isNotNull().and(DSL.trim(field).notEqual(""));
                case EMPTY -> condition = field.eq("");
                case NON_EMPTY -> condition = field.isNotNull().and(field.notEqual(""));
                case NULL_OR_BLANK -> condition = field.isNull().or(DSL.trim(field).equal(""));
                case NULL_OR_EMPTY -> condition = field.isNull().or(field.eq(""));
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
                case CHARS_ANYWHERE -> condition = getCharsAnywhereLikePattern(field, stringMatch);
            }
        }
        return condition;
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
