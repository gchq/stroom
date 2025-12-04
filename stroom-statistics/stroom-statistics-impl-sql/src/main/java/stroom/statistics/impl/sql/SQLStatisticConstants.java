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

public interface SQLStatisticConstants {
    String DATE_FIELD = "Date Time";

    /**
     * the separator used in the delimited name stored in the STAT_KEY table For
     * reasons not fully understood this 'not sign' character is not displayable
     * when you use the mysql shell and a query like "where name like '%¬%'"
     * will not work. You need to instead do
     * "where name like concat('%',char(172),'%')".
     * <p>
     * Stroom can query the table quite happily though.
     * <p>
     * e.g StatName1¬Tag1¬Val1¬Tag2¬Val2
     */
    String NAME_SEPARATOR = "¬";

    /**
     * The character to replace unwanted characters with in statistic names and
     * tag values
     */
    String DIRTY_CHARACTER_REPLACEMENT = "#";

    /**
     * The value to store in the database when the tag's value is null
     */
    String NULL_VALUE_STRING = "<<<<NULL>>>>";

    int STAT_VAL_SRC_NAME_COLUMN_LENGTH = 766;
}
