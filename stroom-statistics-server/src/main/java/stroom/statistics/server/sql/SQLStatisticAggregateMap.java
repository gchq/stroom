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

package stroom.statistics.server.sql;

import org.apache.commons.lang.mutable.MutableLong;
import stroom.statistics.server.sql.exception.StatisticsEventValidationException;
import stroom.statistics.server.sql.rollup.RolledUpStatisticEvent;
import stroom.statistics.shared.StatisticType;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class SQLStatisticAggregateMap {
    private final Map<SQLStatKey, MutableLong> countMap = new HashMap<SQLStatKey, MutableLong>();
    private final Map<SQLStatKey, Double> valueMap = new HashMap<SQLStatKey, Double>();

    public void addRolledUpEvent(final RolledUpStatisticEvent rolledUpStatisticEvent, long precisionMs)
            throws StatisticsEventValidationException {
        // Round the number of milliseconds to supplied precision.
        long roundedMs = rolledUpStatisticEvent.getTimeMs();
        if (precisionMs != 0) {
            final long multiple = rolledUpStatisticEvent.getTimeMs() / precisionMs;
            roundedMs = multiple * precisionMs;
        }

        for (TimeAgnosticStatisticEvent timeAgnosticStatisticEvent : rolledUpStatisticEvent) {
            // Create a key using the rounded time
            final SQLStatKey key = new SQLStatKey(roundedMs, rolledUpStatisticEvent.getName(),
                    timeAgnosticStatisticEvent.getTagList());

            if (SQLStatisticsEventValidator.isKeyToLong(key.getName())) {
                throw new StatisticsEventValidationException(
                        String.format("Statistic event key [%s] is too long to store. Length is [%s]", key.getName(),
                                key.getName().length()));
            }

            if (StatisticType.COUNT == rolledUpStatisticEvent.getType()) {
                // Try and get the value
                final MutableLong v = countMap.get(key);
                if (v == null) {
                    countMap.put(key, new MutableLong(rolledUpStatisticEvent.getCount()));
                } else {
                    v.add(rolledUpStatisticEvent.getCount());
                }
            } else {
                valueMap.put(key, rolledUpStatisticEvent.getValue());
            }
        }
    }

    /**
     * Adds entries from another aggregate map into this one.
     *
     * @param aggregateMap
     */
    public void add(final SQLStatisticAggregateMap aggregateMap) {
        for (final Entry<SQLStatKey, MutableLong> entry : aggregateMap.countEntrySet()) {
            final MutableLong v = countMap.get(entry.getKey());
            if (v == null) {
                countMap.put(entry.getKey(), entry.getValue());
            } else {
                v.add(entry.getValue());
            }
        }
        valueMap.putAll(aggregateMap.valueMap);
    }

    public Set<Entry<SQLStatKey, MutableLong>> countEntrySet() {
        return countMap.entrySet();
    }

    public Set<Entry<SQLStatKey, Double>> valueEntrySet() {
        return valueMap.entrySet();
    }

    public int size() {
        return countMap.size() + valueMap.size();
    }

    @Override
    public String toString() {
        return "AggregateMap size=" + size();
    }
}
