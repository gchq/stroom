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

import stroom.statistics.impl.sql.exception.StatisticsEventValidationException;
import stroom.statistics.impl.sql.rollup.RolledUpStatisticEvent;
import stroom.statistics.impl.sql.shared.StatisticType;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class SQLStatisticAggregateMap {
    private final Map<SQLStatKey, LongAdder> countMap = new HashMap<>();
    private final Map<SQLStatKey, ValueStatValue> valueMap = new HashMap<>();
    private final Instant createTime;

    public SQLStatisticAggregateMap() {
        createTime = Instant.now();
    }

    public void addRolledUpEvent(final RolledUpStatisticEvent rolledUpStatisticEvent,
                                 final long precisionMs)
            throws StatisticsEventValidationException {
        // Round the number of milliseconds to supplied precision.
        long roundedMs = rolledUpStatisticEvent.getTimeMs();
        if (precisionMs != 0) {
            final long multiple = rolledUpStatisticEvent.getTimeMs() / precisionMs;
            roundedMs = multiple * precisionMs;
        }

        for (final TimeAgnosticStatisticEvent timeAgnosticStatisticEvent : rolledUpStatisticEvent) {
            // Create a key using the rounded time
            final SQLStatKey key = new SQLStatKey(roundedMs, rolledUpStatisticEvent.getName(),
                    timeAgnosticStatisticEvent.getTagList());

            if (SQLStatisticsEventValidator.isKeyTooLong(key.getName())) {
                throw new StatisticsEventValidationException(
                        String.format("Statistic event key [%s] is too long to store. Length is [%s]",
                                key.getName(),
                                key.getName().length()));
            }

            if (StatisticType.COUNT == rolledUpStatisticEvent.getType()) {
                // Try and get the value
                countMap.computeIfAbsent(key, k -> new LongAdder())
                        .add(rolledUpStatisticEvent.getCount());
            } else {
                valueMap.computeIfAbsent(key, k -> new ValueStatValue())
                        .add(rolledUpStatisticEvent.getValue());
            }
        }
    }

    /**
     * Adds entries from another aggregate map into this one.
     *
     * @param aggregateMap
     */
    public void add(final SQLStatisticAggregateMap aggregateMap) {
        aggregateMap.countEntrySet().forEach(entry ->
                countMap.computeIfAbsent(entry.getKey(), k -> new LongAdder())
                        .add(entry.getValue().longValue()));

        aggregateMap.valueEntrySet().forEach(entry ->
                valueMap.computeIfAbsent(entry.getKey(), k -> new ValueStatValue())
                        .add(entry.getValue()));
    }

    public Set<Entry<SQLStatKey, LongAdder>> countEntrySet() {
        return countMap.entrySet();
    }

    public Set<Entry<SQLStatKey, ValueStatValue>> valueEntrySet() {
        return valueMap.entrySet();
    }

    public int size() {
        return countMap.size() + valueMap.size();
    }

    public Duration getAge() {
        return Duration.between(createTime, Instant.now());
    }

    @Override
    public String toString() {
        return "" +
                "countMapSize=" + countMap.size() +
                ", valueMapSize=" + valueMap.size() +
                ", age=" + getAge().toString();
    }

    public static class ValueStatValue {
        // We are basically storing the sum of values and the count so
        // we can compute the mean
        private final DoubleAdder value;
        private final LongAdder count;

        public ValueStatValue() {
            this.value = new DoubleAdder();
            this.count = new LongAdder();
        }

        public void add(final double value) {
            this.value.add(value);
            this.count.add(1);
        }

        public void add(final ValueStatValue other) {
            this.value.add(other.getValue());
            this.count.add(other.getCount());
        }

        public double getValue() {
            return value.doubleValue();
        }

        public long getCount() {
            return count.longValue();
        }

        @Override
        public String toString() {
            return "ValueStatValue{" +
                    "value=" + value +
                    ", count=" + count +
                    '}';
        }
    }
}
