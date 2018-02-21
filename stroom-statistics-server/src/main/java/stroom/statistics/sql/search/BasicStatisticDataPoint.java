/*
 * Copyright 2017 Crown Copyright
 *
 * This file is part of Stroom-Stats.
 *
 * Stroom-Stats is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Stroom-Stats is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Stroom-Stats.  If not, see <http://www.gnu.org/licenses/>.
 */

package stroom.statistics.sql.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import stroom.statistics.sql.StatisticTag;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BasicStatisticDataPoint implements StatisticDataPoint {

    private static final Map<String, Function<StatisticDataPoint, String>> FIELD_VALUE_FUNCTION_MAP;

    static {
        FIELD_VALUE_FUNCTION_MAP = ImmutableMap.<String, Function<StatisticDataPoint, String>>builder()
                .put(StatisticStoreEntity.FIELD_NAME_DATE_TIME, dataPoint -> Long.toString(dataPoint.getTimeMs()))
                .put(StatisticStoreEntity.FIELD_NAME_PRECISION_MS, dataPoint -> Long.toString(dataPoint.getPrecisionMs()))
                .build();
    }

    private final long timeMs;
    private final long precisionMs;
    private final List<StatisticTag> tags;
    private final Map<String, String> tagToValueMap;


    BasicStatisticDataPoint(final long timeMs, final long precisionMs, final List<StatisticTag> tags) {
        Preconditions.checkArgument(timeMs >= 0);
        Preconditions.checkArgument(precisionMs >= 0);
        Preconditions.checkNotNull(tags);

        this.timeMs = timeMs;
        this.precisionMs = precisionMs;
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags));

        final Map<String, String> tempMap = new HashMap<>();
        tags.forEach(tag -> tempMap.put(tag.getTag(), tag.getValue()));
        this.tagToValueMap = Collections.unmodifiableMap(tempMap);
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
        Map<String, String> map = new HashMap<>();
        for (StatisticTag tag : tags) {
            map.put(tag.getTag(), tag.getValue());
        }
        return ImmutableMap.copyOf(tagToValueMap);
    }

    @Override
    public StatisticType getStatisticType() {
        throw new UnsupportedOperationException("A BasicStatisticDataPoint has no type");
    }

    @Override
    public String getFieldValue(final String fieldName) {
        Function<StatisticDataPoint, String> fieldValueFunction = FIELD_VALUE_FUNCTION_MAP.get(fieldName);

        if (fieldValueFunction == null) {
            //either it is a tag field or we don't know about this field
            return tagToValueMap.get(fieldName);
        } else {
            return fieldValueFunction.apply(this);
        }
    }

    @Override
    public String toString() {
        return "BasicStatisticDataPoint{" +
                ", timeMs=" + timeMs +
                ", precisionMs=" + precisionMs +
                ", tags=" + tags +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final BasicStatisticDataPoint that = (BasicStatisticDataPoint) o;

        if (timeMs != that.timeMs) return false;
        if (precisionMs != that.precisionMs) return false;
        if (!tags.equals(that.tags)) return false;
        return tagToValueMap.equals(that.tagToValueMap);
    }

    @Override
    public int hashCode() {
        int result = (int) (timeMs ^ (timeMs >>> 32));
        result = 31 * result + (int) (precisionMs ^ (precisionMs >>> 32));
        result = 31 * result + tags.hashCode();
        result = 31 * result + tagToValueMap.hashCode();
        return result;
    }
}
