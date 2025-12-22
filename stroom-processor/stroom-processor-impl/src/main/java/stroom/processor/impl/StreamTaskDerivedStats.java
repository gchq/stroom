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

package stroom.processor.impl;

import stroom.util.shared.ModelStringUtil;

/**
 * Helper to derive some fields.
 */
public class StreamTaskDerivedStats {
    private static final int MS_IN_SEC = 1000;

    private final StreamTaskStats stats;
    private final Long durationMs;

    public StreamTaskDerivedStats(final StreamTaskStats stats) {
        this.stats = stats;

        if (stats.getStartTimeMs() != null && stats.getEndTimeMs() != null) {
            durationMs = stats.getEndTimeMs() - stats.getStartTimeMs();
        } else {
            durationMs = null;
        }
    }

    /**
     * @return a nice looking string
     */
    public String getDurationString() {
        if (durationMs == null) {
            return "";
        }
        final long timeMs = durationMs;
        if (timeMs < MS_IN_SEC) {
            return timeMs + "ms";
        }
        final long timeS = timeMs / MS_IN_SEC;
        final long remMs = timeMs % MS_IN_SEC;
        return timeS + "." + ModelStringUtil.zeroPad(3, Long.toString(remMs)) + "s";
    }

    /**
     * @return number of records read per second
     */
    public Integer getRecordsReadPs() {
        if (stats.getRecordsRead() != null && durationMs != null && durationMs > 0) {
            return (int) ((double) stats.getRecordsRead().longValue() / durationMs * MS_IN_SEC);
        }
        return null;
    }
}
