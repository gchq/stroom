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

package stroom.statistics.impl.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SQLStatisticsEventValidator {

    private static final Pattern DIRTY_CHARACTER_PATTERN = Pattern.compile(SQLStatisticConstants.NAME_SEPARATOR);

    public static List<String> validateEvent(final StatisticEvent statisticEvent) {
        final List<String> warningList = new ArrayList<>();

        for (final StatisticTag tag : statisticEvent.getTagList()) {
            if (DIRTY_CHARACTER_PATTERN.matcher(tag.getTag()).find()) {
                // found bad chars in the tag
                warningList.add(String.format("Statistic tag [%s] contains unsupported characters, as defined by " +
                                "the regex %s. They will be replaced with a '%s'",
                        tag.getTag(), DIRTY_CHARACTER_PATTERN.toString(),
                        SQLStatisticConstants.DIRTY_CHARACTER_REPLACEMENT));
            }

            if (DIRTY_CHARACTER_PATTERN.matcher(tag.getValue()).find()) {
                // found bad chars in the tag
                warningList.add(String.format("Statistic value [%s] contains unsupported characters, as defined " +
                                "by the regex %s. They will be replaced with a '%s'",
                        tag.getValue(), DIRTY_CHARACTER_PATTERN.toString(),
                        SQLStatisticConstants.DIRTY_CHARACTER_REPLACEMENT));
            }
        }
        return warningList;
    }

    public static String cleanString(final String dirtyString) {
        if (dirtyString != null) {
            final String cleanedString = dirtyString.replaceAll(SQLStatisticConstants.NAME_SEPARATOR,
                    SQLStatisticConstants.DIRTY_CHARACTER_REPLACEMENT);

            return cleanedString;
        } else {
            return dirtyString;
        }
    }

    public static boolean isKeyTooLong(final String statisticKey) {
        return statisticKey.length() > SQLStatisticConstants.STAT_VAL_SRC_NAME_COLUMN_LENGTH;
    }
}
