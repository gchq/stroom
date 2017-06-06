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

package stroom.statistics.sql.search;

import stroom.statistics.shared.StatisticType;
import stroom.statistics.sql.StatisticEvent;
import stroom.statistics.sql.StatisticTag;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Value object to hold a statistic data point retreived from a statistic store.
 * This differs from a {@link StatisticEvent} in that this data point may
 * represent an aggregated value rather than a single statistic event.
 */
public class StatisticDataPoint {
    private final long timeMs;
    private final long precisionMs;
    private final List<StatisticTag> tags;
    private final long count;
    private final double value;
    private final double minValue;
    private final double maxValue;
    private final StatisticType statisticType;
    private final Map<String, String> tagToValueMap;

    /**
     * Constructor for a value type statistic data point
     *
     * @param timeMs
     *            The timestamp of the aggregated data point
     * @param tags
     *            The list of tav/value pairs that qualify the data point
     * @param value
     *            The mean value of the data point in this time period
     * @param count
     *            The count of the number of statistic events that have happened
     *            in this period
     * @param minValue
     *            The min value in this time period
     * @param maxValue
     *            The max value in this time period
     * @return A populated {@link StatisticDataPoint} instance
     */
    public static StatisticDataPoint valueInstance(final long timeMs, final long precisionMs,
            final List<StatisticTag> tags, final double value, final long count, final double minValue,
            final double maxValue) {
        return new StatisticDataPoint(timeMs, precisionMs, tags, count, value, minValue, maxValue, StatisticType.VALUE);
    }

    /**
     * Constructor for a count type statistic data point
     *
     * @param timeMs
     *            The timestamp of the aggregated data point
     * @param tags
     *            The list of tav/value pairs that qualify the data point
     * @param count
     *            The count of the number of statistic events that have happened
     *            in this period
     * @return A populated {@link StatisticDataPoint} instance
     */
    public static StatisticDataPoint countInstance(final long timeMs, final long precisionMs,
            final List<StatisticTag> tags, final long count) {
        return new StatisticDataPoint(timeMs, precisionMs, tags, count, 0D, 0, 0, StatisticType.COUNT);
    }

    // private StatisticDataPoint() {
    //
    // this.timeMs = 0;
    // this.count = 0L;
    // this.value = 0D;
    // this.minValue = 0;
    // this.maxValue = 0;
    // }

    private StatisticDataPoint(final long timeMs, final long precisionMs, final List<StatisticTag> tags,
            final Long count, final Double value, final double minValue, final double maxValue,
            StatisticType statisticType) {
        this.timeMs = timeMs;
        this.precisionMs = precisionMs;
        this.tags = tags == null ? Collections.emptyList() : tags;
        this.count = count;
        this.value = value;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.statisticType = statisticType;
        this.tagToValueMap = new HashMap<>();
            this.tags.forEach(statisticTag -> tagToValueMap.put(statisticTag.getTag(), statisticTag.getValue()));
    }

    public long getTimeMs() {
        return timeMs;
    }

    public long getPrecisionMs() {
        return precisionMs;
    }

    public List<StatisticTag> getTags() {
        return tags;
    }

    public Map<String, String> getTagsAsMap() {
        Map<String, String> map = new HashMap<String, String>();
        for (StatisticTag tag : tags) {
            map.put(tag.getTag(), tag.getValue());
        }
        return map;
    }

    public Long getCount() {
        return count;
    }

    public Double getValue() {
        if (!statisticType.equals(StatisticType.VALUE))
            throw new UnsupportedOperationException("Method only support for value type statistics");

        return value;
    }

    public String getTagValue(final String tagName) {
        return tagToValueMap.get(tagName);
    }

    public double getMinValue() {
        if (!statisticType.equals(StatisticType.VALUE))
            throw new UnsupportedOperationException("Method only support for value type statistics");

        return minValue;
    }

    public double getMaxValue() {
        if (!statisticType.equals(StatisticType.VALUE))
            throw new UnsupportedOperationException("Method only support for value type statistics");

        return maxValue;
    }

    public StatisticType getStatisticType() {
        return statisticType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (count ^ (count >>> 32));
        long temp;
        temp = Double.doubleToLongBits(maxValue);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(minValue);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (int) (precisionMs ^ (precisionMs >>> 32));
        result = prime * result + ((statisticType == null) ? 0 : statisticType.hashCode());
        result = prime * result + ((tags == null) ? 0 : tags.hashCode());
        result = prime * result + (int) (timeMs ^ (timeMs >>> 32));
        temp = Double.doubleToLongBits(value);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        StatisticDataPoint other = (StatisticDataPoint) obj;
        if (count != other.count)
            return false;
        if (Double.doubleToLongBits(maxValue) != Double.doubleToLongBits(other.maxValue))
            return false;
        if (Double.doubleToLongBits(minValue) != Double.doubleToLongBits(other.minValue))
            return false;
        if (precisionMs != other.precisionMs)
            return false;
        if (statisticType != other.statisticType)
            return false;
        if (tags == null) {
            if (other.tags != null)
                return false;
        } else if (!tags.equals(other.tags))
            return false;
        if (timeMs != other.timeMs)
            return false;
        return Double.doubleToLongBits(value) == Double.doubleToLongBits(other.value);
    }

    @Override
    public String toString() {
        return "StatisticDataPoint [timeMs=" + timeMs + ", precisionMs=" + precisionMs + ", tags=" + tags + ", count="
                + count + ", value=" + value + ", minValue=" + minValue + ", maxValue=" + maxValue + ", statisticType="
                + statisticType + "]";
    }

}
