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

import stroom.statistics.impl.sql.shared.StatisticType;

import java.io.Serializable;
import java.util.Objects;
import java.util.OptionalDouble;

public class SQLStatValSourceDO implements Serializable {
    private static final long serialVersionUID = -4956231944878929284L;

    private long createMs;
    private String name;
    private double valueSum;
    private long count;
    private StatisticType type;

    private SQLStatValSourceDO(final long createMs,
                              final String name,
                              final double valueSum,
                              final long count,
                              final StatisticType type) {
        this.createMs = createMs;
        this.name = Objects.requireNonNull(name);
        this.valueSum = valueSum;
        this.count = count;
        this.type = Objects.requireNonNull(type);
    }

    public static SQLStatValSourceDO createCountStat(final long createMs,
                                                     final String name,
                                                     final long count) {
        // valueSum is not used for COUNT stats
        return new SQLStatValSourceDO(
                createMs,
                name,
                0,
                count,
                StatisticType.COUNT);
    }

    public static SQLStatValSourceDO createValueStat(final long createMs,
                                                     final String name,
                                                     final double valueSum,
                                                     final long count) {
        return new SQLStatValSourceDO(
                createMs,
                name,
                valueSum,
                count,
                StatisticType.VALUE);
    }

    public long getCreateMs() {
        return createMs;
    }

    public String getName() {
        return name;
    }

    /**
     * @return The sum of all stat event values for this time bucket.
     * Only applicable to VALUE stats
     */
    public OptionalDouble getValueSum() {
        if (StatisticType.COUNT.equals(type)) {
            return OptionalDouble.empty();
        } else {
            return OptionalDouble.of(valueSum);
        }
    }

    /**
     * @return The count aggregate for a COUNT stat, or the count of stat
     * events for a VALUE stat.
     */
    public long getCount() {
        return count;
    }

    public StatisticType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "SQLStatValSourceDO{" +
                "createMs=" + createMs +
                ", name='" + name + '\'' +
                ", valueSum=" + valueSum +
                ", count=" + count +
                ", type=" + type +
                '}';
    }
}
