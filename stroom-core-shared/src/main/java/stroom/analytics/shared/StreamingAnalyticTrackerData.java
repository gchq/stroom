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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public final class StreamingAnalyticTrackerData extends AnalyticTrackerData {

    @JsonProperty
    private Long lastExecutionTimeMs;
    @JsonProperty
    private Integer lastStreamCount;
    @JsonProperty
    private Long lastStreamId;
    @JsonProperty
    private Long totalStreamCount;
    @JsonProperty
    private Long totalEventCount;

    public StreamingAnalyticTrackerData() {
    }

    @JsonCreator
    public StreamingAnalyticTrackerData(@JsonProperty("lastExecutionTimeMs") final Long lastExecutionTimeMs,
                                        @JsonProperty("lastStreamCount") final Integer lastStreamCount,
                                        @JsonProperty("lastStreamId") final Long lastStreamId,
                                        @JsonProperty("totalStreamCount") final Long totalStreamCount,
                                        @JsonProperty("totalEventCount") final Long totalEventCount,
                                        @JsonProperty("message") final String message) {
        super(message);
        this.lastExecutionTimeMs = lastExecutionTimeMs;
        this.lastStreamCount = lastStreamCount;
        this.lastStreamId = lastStreamId;
        this.totalStreamCount = totalStreamCount;
        this.totalEventCount = totalEventCount;
    }

    public Long getLastExecutionTimeMs() {
        return lastExecutionTimeMs;
    }

    public void setLastExecutionTimeMs(final Long lastExecutionTimeMs) {
        this.lastExecutionTimeMs = lastExecutionTimeMs;
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
        final StreamingAnalyticTrackerData that = (StreamingAnalyticTrackerData) o;
        return Objects.equals(lastExecutionTimeMs, that.lastExecutionTimeMs) &&
                Objects.equals(lastStreamCount, that.lastStreamCount) &&
                Objects.equals(lastStreamId, that.lastStreamId) &&
                Objects.equals(totalStreamCount, that.totalStreamCount) &&
                Objects.equals(totalEventCount, that.totalEventCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                lastExecutionTimeMs,
                lastStreamCount,
                lastStreamId,
                totalStreamCount,
                totalEventCount);
    }
}
