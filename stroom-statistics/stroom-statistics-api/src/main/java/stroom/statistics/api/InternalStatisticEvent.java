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

package stroom.statistics.api;

import com.google.common.base.Preconditions;

import java.util.Collections;
import java.util.SortedMap;

public class InternalStatisticEvent {

    private final InternalStatisticKey key;
    private final Type type;
    private final long timeMs;
    // Tags must be in tag order
    private final SortedMap<String, String> tags;
    private final Object value;

    private InternalStatisticEvent(final InternalStatisticKey key,
                                   final Type type,
                                   final long timeMs,
                                   final SortedMap<String, String> tags,
                                   final Object value) {
        Preconditions.checkArgument(timeMs >= 0);
        this.key = Preconditions.checkNotNull(key);
        this.type = Preconditions.checkNotNull(type);
        this.timeMs = timeMs;
        this.tags = tags == null
                ? Collections.emptySortedMap()
                : tags;
        this.value = Preconditions.checkNotNull(value);
    }

    public static InternalStatisticEvent createPlusOneCountStat(final InternalStatisticKey key,
                                                                final long timeMs,
                                                                final SortedMap<String, String> tags) {
        return new InternalStatisticEvent(key, Type.COUNT, timeMs, tags, 1L);
    }

    public static InternalStatisticEvent createPlusNCountStat(final InternalStatisticKey key,
                                                              final long timeMs,
                                                              final SortedMap<String, String> tags,
                                                              final long count) {
        return new InternalStatisticEvent(key, Type.COUNT, timeMs, tags, count);
    }

    public static InternalStatisticEvent createValueStat(final InternalStatisticKey key,
                                                         final long timeMs,
                                                         final SortedMap<String, String> tags,
                                                         final double value) {
        return new InternalStatisticEvent(key, Type.VALUE, timeMs, tags, value);
    }

    public InternalStatisticKey getKey() {
        return key;
    }

    public Type getType() {
        return type;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public SortedMap<String, String> getTags() {
        return tags;
    }

    public Object getValue() {
        return value;
    }

    public Long getValueAsLong() {
        if (type.equals(Type.VALUE)) {
            throw new UnsupportedOperationException("getValueAsDouble is not supported for a VALUE statistic");
        }
        return (Long) value;
    }

    public Double getValueAsDouble() {
        if (type.equals(Type.COUNT)) {
            throw new UnsupportedOperationException("getValueAsDouble is not supported for a COUNT statistic");
        }
        return (Double) value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final InternalStatisticEvent that = (InternalStatisticEvent) o;

        if (timeMs != that.timeMs) {
            return false;
        }
        if (!key.equals(that.key)) {
            return false;
        }
        if (!tags.equals(that.tags)) {
            return false;
        }
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + (int) (timeMs ^ (timeMs >>> 32));
        result = 31 * result + tags.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "InternalStatisticEvent{" +
                "key='" + key + '\'' +
                ", timeMs=" + timeMs +
                ", tags=" + tags +
                ", value=" + value +
                '}';
    }

    public enum Type {
        COUNT,
        VALUE
    }
}
