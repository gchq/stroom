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

package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public final class TableBuilderAnalyticTrackerData extends AnalyticTrackerData {

    @JsonProperty
    private Long lastExecutionTimeMs;
    @JsonProperty
    private Long lastWindowStartTimeMs;
    @JsonProperty
    private Long lastWindowEndTimeMs;
    @JsonProperty
    private Integer lastStreamCount;
    @JsonProperty
    private Long lastStreamId;
    @JsonProperty
    private Long lastEventId;
    @JsonProperty
    private Long lastEventTime;
    @JsonProperty
    private Long totalStreamCount;
    @JsonProperty
    private Long totalEventCount;

    public TableBuilderAnalyticTrackerData() {
    }

    @JsonCreator
    public TableBuilderAnalyticTrackerData(@JsonProperty("lastExecutionTimeMs") final Long lastExecutionTimeMs,
                                           @JsonProperty("lastWindowStartTimeMs") final Long lastWindowStartTimeMs,
                                           @JsonProperty("lastWindowEndTimeMs") final Long lastWindowEndTimeMs,
                                           @JsonProperty("lastStreamCount") final Integer lastStreamCount,
                                           @JsonProperty("lastStreamId") final Long lastStreamId,
                                           @JsonProperty("lastEventId") final Long lastEventId,
                                           @JsonProperty("lastEventTime") final Long lastEventTime,
                                           @JsonProperty("totalStreamCount") final Long totalStreamCount,
                                           @JsonProperty("totalEventCount") final Long totalEventCount,
                                           @JsonProperty("message") final String message) {
        super(message);
        this.lastExecutionTimeMs = lastExecutionTimeMs;
        this.lastWindowStartTimeMs = lastWindowStartTimeMs;
        this.lastWindowEndTimeMs = lastWindowEndTimeMs;
        this.lastStreamCount = lastStreamCount;
        this.lastStreamId = lastStreamId;
        this.lastEventId = lastEventId;
        this.lastEventTime = lastEventTime;
        this.totalStreamCount = totalStreamCount;
        this.totalEventCount = totalEventCount;
    }

    public Long getLastExecutionTimeMs() {
        return lastExecutionTimeMs;
    }

    public void setLastExecutionTimeMs(final Long lastExecutionTimeMs) {
        this.lastExecutionTimeMs = lastExecutionTimeMs;
    }

    public Long getLastWindowStartTimeMs() {
        return lastWindowStartTimeMs;
    }

    public void setLastWindowStartTimeMs(final Long lastWindowStartTimeMs) {
        this.lastWindowStartTimeMs = lastWindowStartTimeMs;
    }

    public Long getLastWindowEndTimeMs() {
        return lastWindowEndTimeMs;
    }

    public void setLastWindowEndTimeMs(final Long lastWindowEndTimeMs) {
        this.lastWindowEndTimeMs = lastWindowEndTimeMs;
    }

    public Integer getLastStreamCount() {
        return lastStreamCount;
    }

    public void setLastStreamCount(final Integer lastStreamCount) {
        this.lastStreamCount = lastStreamCount;
    }

    public Long getLastStreamId() {
        return lastStreamId;
    }

    public void setLastStreamId(final Long lastStreamId) {
        this.lastStreamId = lastStreamId;
    }

    @JsonIgnore
    public long getMinStreamId() {
        if (lastStreamId == null) {
            return 1L;
        }

        if (lastEventId == null || lastEventId == -1) {
            // Start at the next stream.
            return lastStreamId + 1;
        }

        return lastStreamId;
    }

    public Long getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(final Long lastEventId) {
        this.lastEventId = lastEventId;
    }

    public Long getLastEventTime() {
        return lastEventTime;
    }

    public void setLastEventTime(final Long lastEventTime) {
        this.lastEventTime = lastEventTime;
    }

    public Long getTotalStreamCount() {
        return totalStreamCount;
    }

    public void setTotalStreamCount(final Long totalStreamCount) {
        this.totalStreamCount = totalStreamCount;
    }

    public Long getTotalEventCount() {
        return totalEventCount;
    }

    public void setTotalEventCount(final Long totalEventCount) {
        this.totalEventCount = totalEventCount;
    }

    public void incrementStreamCount() {
        if (totalStreamCount == null) {
            totalStreamCount = 1L;
        } else {
            totalStreamCount++;
        }
    }

    public void incrementEventCount() {
        if (totalEventCount == null) {
            totalEventCount = 1L;
        } else {
            totalEventCount++;
        }
    }

    public void addEventCount(final long delta) {
        if (totalEventCount == null) {
            totalEventCount = delta;
        } else {
            totalEventCount += delta;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final TableBuilderAnalyticTrackerData that = (TableBuilderAnalyticTrackerData) o;
        return Objects.equals(lastExecutionTimeMs, that.lastExecutionTimeMs) &&
                Objects.equals(lastWindowStartTimeMs, that.lastWindowStartTimeMs) &&
                Objects.equals(lastWindowEndTimeMs, that.lastWindowEndTimeMs) &&
                Objects.equals(lastStreamCount, that.lastStreamCount) &&
                Objects.equals(lastStreamId, that.lastStreamId) &&
                Objects.equals(lastEventId, that.lastEventId) &&
                Objects.equals(lastEventTime, that.lastEventTime) &&
                Objects.equals(totalStreamCount, that.totalStreamCount) &&
                Objects.equals(totalEventCount, that.totalEventCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                lastExecutionTimeMs,
                lastWindowStartTimeMs,
                lastWindowEndTimeMs,
                lastStreamCount,
                lastStreamId,
                lastEventId,
                lastEventTime,
                totalStreamCount,
                totalEventCount);
    }
}
