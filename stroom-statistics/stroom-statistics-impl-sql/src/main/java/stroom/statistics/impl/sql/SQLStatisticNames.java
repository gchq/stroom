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

public class SQLStatisticNames {
    public static final String FK_PREFIX = "FK_";
    public static final String ID_SUFFIX = "_ID";
    public static final String SEP = "_";
    public static final String VERSION = "VER";
    public static final String ID = "ID";

    public static final String SQL_STATISTIC_KEY_TABLE_NAME =
            SQLNameConstants.SQL + SEP +
                    SQLNameConstants.STATISTIC + SEP +
                    SQLNameConstants.KEY;

    public static final String SQL_STATISTIC_KEY_FOREIGN_KEY = FK_PREFIX + SQL_STATISTIC_KEY_TABLE_NAME + ID_SUFFIX;

    public static final String SQL_STATISTIC_VALUE_TABLE_NAME =
            SQLNameConstants.SQL + SEP +
                    SQLNameConstants.STATISTIC + SEP +
                    SQLNameConstants.VALUE;

    public static final String SQL_STATISTIC_VALUE_TEMP_TABLE_NAME =
            SQL_STATISTIC_VALUE_TABLE_NAME + SEP +
                    SQLNameConstants.TEMP;

    public static final String SQL_STATISTIC_VALUE_FOREIGN_KEY = FK_PREFIX + SQL_STATISTIC_VALUE_TABLE_NAME + ID_SUFFIX;

    public static final String SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME =
            SQLNameConstants.SQL + SEP +
                    SQLNameConstants.STATISTIC + SEP +
                    SQLNameConstants.VALUE + SEP +
                    SQLNameConstants.SOURCE;

    public static final String SQL_STATISTIC_VALUE_SOURCE_FOREIGN_KEY = FK_PREFIX
            + SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME + ID_SUFFIX;

    public static final String TIME_MS = SQLNameConstants.TIME + SQLNameConstants.MS_SUFFIX;

    public static final String NAME = SQLNameConstants.NAME;
    public static final String VALUE = SQLNameConstants.VALUE;
    public static final String COUNT = SQLNameConstants.COUNT;
    public static final String VALUE_TYPE = SQLNameConstants.VALUE + SQLNameConstants.TYPE_SUFFIX;

    public static final String PRECISION = SQLNameConstants.PRECISION;
}
