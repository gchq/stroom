/*
 * Copyright 2016 Crown Copyright
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

import stroom.statistics.impl.sql.shared.StatisticType;

import java.io.Serializable;

public class SQLStatValSourceDO implements Serializable {
    private static final long serialVersionUID = -4956231944878929284L;

    private long createMs;
    private String name;
    private long value;
    private long count;
    private StatisticType type;

    private SQLStatValSourceDO(final long createMs,
                              final String name,
                              final long value,
                              final long count,
                              final StatisticType type) {
        this.createMs = createMs;
        this.name = name;
        this.value = value;
        this.count = count;
        this.type = type;
    }

    public static SQLStatValSourceDO createCountStat(final long createMs,
                                                     final String name,
                                                     final long count) {
        // count is used for both value and count
        return new SQLStatValSourceDO(
                createMs,
                name,
                count,
                count,
                StatisticType.COUNT);
    }

    public static SQLStatValSourceDO createValueStat(final long createMs,
                                                     final String name,
                                                     final long value,
                                                     final long count) {
        return new SQLStatValSourceDO(
                createMs,
                name,
                value,
                count,
                StatisticType.VALUE);
    }

    public long getCreateMs() {
        return createMs;
    }

    public String getName() {
        return name;
    }

    public long getValue() {
        return value;
    }

    public long getCount() {
        return count;
    }

    public StatisticType getType() {
        return type;
    }
}
