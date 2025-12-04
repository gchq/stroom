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

import java.util.List;

/**
 * Class to hold a statistic event, ie. the count or value of something in a
 * given time window
 */
public class StatisticEvent {

    private final long timeMs;
    private final TimeAgnosticStatisticEvent timeAgnosticStatisticEvent;

    private StatisticEvent(final long timeMs,
                           final TimeAgnosticStatisticEvent timeAgnosticStatisticEvent) {
        this.timeMs = timeMs;
        this.timeAgnosticStatisticEvent = timeAgnosticStatisticEvent;
    }

    /**
     * Constructor for value type events with floating point values
     *
     * @param timeMs  time of the event in millis since epoch
     * @param name    the name of the event
     * @param tagList list of tag/value pairs that describe the event. Must be
     *                ordered by tag name. Can be null.
     * @param count   the count of the event, e.g. the number of desktop logons
     */
    public static StatisticEvent createCount(final long timeMs,
                                             final String name,
                                             final List<StatisticTag> tagList,
                                             final long count) {
        return new StatisticEvent(timeMs, TimeAgnosticStatisticEvent.createCount(name, tagList, count));
    }

    /**
     * Constructor for value type events with floating point values
     *
     * @param timeMs  time of the event in millis since epoch
     * @param name    the name of the event
     * @param tagList list of tag/value pairs that describe the event. Must be
     *                ordered by tag name. Can be null.
     * @param value   the value of the event, e.g. the heap size in MB, bytes read,
     *                etc.
     */
    public static StatisticEvent createValue(final long timeMs,
                                             final String name,
                                             final List<StatisticTag> tagList,
                                             final double value) {
        return new StatisticEvent(timeMs, TimeAgnosticStatisticEvent.createValue(name, tagList, value));
    }

    /**
     * @return time in milliseconds since epoch
     */
    public long getTimeMs() {
        return timeMs;
    }

    public List<StatisticTag> getTagList() {
        return timeAgnosticStatisticEvent.getTagList();
    }

    public StatisticType getType() {
        return timeAgnosticStatisticEvent.getStatisticType();
    }

    public String getName() {
        return timeAgnosticStatisticEvent.getName();
    }

    public double getValue() {
        return timeAgnosticStatisticEvent.getValue();
    }

    public long getCount() {
        return timeAgnosticStatisticEvent.getCount();
    }

    public TimeAgnosticStatisticEvent getTimeAgnosticStatisticEvent() {
        return timeAgnosticStatisticEvent;
    }

    /**
     * @param tagName The name of the tag in a {@link StatisticTag} object
     * @return The position of the tag in the tag list (0 based)
     */
    public Integer getTagPosition(final String tagName) {
        return timeAgnosticStatisticEvent.getTagPosition(tagName);
    }


    /**
     * Convenience method for extracting the value of a tag in this statistic event
     *
     * @param tagName The name of the tag to extract
     * @return The value of the named tag
     */
    public String getTagValue(final String tagName) {
        return timeAgnosticStatisticEvent.getTagValue(tagName);
    }

    @Override
    public String toString() {
        return "StatisticEvent [timeMs=" + timeMs + ", timeAgnosticStatisticEvent="
                + timeAgnosticStatisticEvent + "]";
    }

//    public StatisticEvent duplicateWithNewTagList(final List<StatisticTag> newTagList) {
//        if (timeAgnosticStatisticEvent.getStatisticType().equals(StatisticType.COUNT)) {
//            return new StatisticEvent(timeMs, TimeAgnosticStatisticEvent.createCount(
//            timeAgnosticStatisticEvent.getName(), newTagList,
//                    timeAgnosticStatisticEvent.getCount()));
//        } else {
//            return new StatisticEvent(timeMs, TimeAgnosticStatisticEvent.createValue(
//            timeAgnosticStatisticEvent.getName(), newTagList,
//                    timeAgnosticStatisticEvent.getValue()));
//        }
//    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((timeAgnosticStatisticEvent == null)
                ? 0
                : timeAgnosticStatisticEvent.hashCode());
        result = prime * result + (int) (timeMs ^ (timeMs >>> 32));
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final StatisticEvent other = (StatisticEvent) obj;
        if (timeAgnosticStatisticEvent == null) {
            if (other.timeAgnosticStatisticEvent != null) {
                return false;
            }
        } else if (!timeAgnosticStatisticEvent.equals(other.timeAgnosticStatisticEvent)) {
            return false;
        }
        return timeMs == other.timeMs;
    }

}
