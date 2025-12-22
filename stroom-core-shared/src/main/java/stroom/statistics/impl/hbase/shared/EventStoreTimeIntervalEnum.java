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

package stroom.statistics.impl.hbase.shared;

import stroom.docref.HasDisplayValue;

/**
 * Enum used to hold the intervals used in storing the event stats. The time of
 * the event is rounded to two levels, the row key level and the column level
 * within each row. The column interval is the finest granularity available. If
 * the column interval is too small relative to the row key interval then you
 * will end up with too many columns.
 */
//TODO should consider storing these values in config so new ones can be added in stroom-stats
//without having to rebuild stroom.
public enum EventStoreTimeIntervalEnum implements HasDisplayValue {
    SECOND("Second"),
    MINUTE("Minute"),
    HOUR("Hour"),
    DAY("Day"),
    FOREVER("Forever");

    private final String longName;

    /**
     * Constructor
     * @param longName       The Human readable name for the interval
     */
    EventStoreTimeIntervalEnum(final String longName) {
        this.longName = longName;
    }

    @Override
    public String getDisplayValue() {
        return this.longName;
    }

}
