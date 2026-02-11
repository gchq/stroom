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

package stroom.processor.shared;


import stroom.util.shared.HasIntegerId;
import stroom.util.shared.ModelStringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ProcessorFilterTracker implements HasIntegerId {

    public static final String ENTITY_TYPE = "ProcessorFilterTracker";

    // standard id and OCC fields
    @JsonProperty
    private final Integer id;
    @JsonProperty
    private final Integer version;

    // These numbers are inclusive use getStreamRange to get a nice Stroom style
    @JsonProperty
    private final long minMetaId;
    @JsonProperty
    private final long minEventId;

    // For info only to display in the GUI

    /**
     * This shows the window that the tasks are being created from. When a
     * tracker starts this is the first item that it creates. When a tracker has
     * done everything available at a given time (i.e. lastPollTaskCount = 0)
     * this resets to the poll count. For example you may process all streams
     * since a year ago and so half way through you would get a tracker 50% and
     * once it completes it would read 100%. If then some more data came in it
     * would show the % complete since the last complete time.
     */
    @JsonProperty
    private final Long minMetaCreateMs;
    @JsonProperty
    private final Long maxMetaCreateMs;
    @JsonProperty
    private final Long metaCreateMs;

    @JsonProperty
    private final Long lastPollMs;
    @JsonProperty
    private final Integer lastPollTaskCount;
    @JsonProperty
    private final ProcessorFilterTrackerStatus status;
    @JsonProperty
    private final String message;

    @JsonProperty
    private final Long metaCount;
    @JsonProperty
    private final Long eventCount;

    @JsonCreator
    public ProcessorFilterTracker(@JsonProperty("id") final Integer id,
                                  @JsonProperty("version") final Integer version,
                                  @JsonProperty("minMetaId") final long minMetaId,
                                  @JsonProperty("minEventId") final long minEventId,
                                  @JsonProperty("minMetaCreateMs") final Long minMetaCreateMs,
                                  @JsonProperty("maxMetaCreateMs") final Long maxMetaCreateMs,
                                  @JsonProperty("metaCreateMs") final Long metaCreateMs,
                                  @JsonProperty("lastPollMs") final Long lastPollMs,
                                  @JsonProperty("lastPollTaskCount") final Integer lastPollTaskCount,
                                  @JsonProperty("status") final ProcessorFilterTrackerStatus status,
                                  @JsonProperty("message") final String message,
                                  @JsonProperty("metaCount") final Long metaCount,
                                  @JsonProperty("eventCount") final Long eventCount) {
        this.id = id;
        this.version = version;
        this.minMetaId = minMetaId;
        this.minEventId = minEventId;
        this.minMetaCreateMs = minMetaCreateMs;
        this.maxMetaCreateMs = maxMetaCreateMs;
        this.metaCreateMs = metaCreateMs;
        this.lastPollMs = lastPollMs;
        this.lastPollTaskCount = lastPollTaskCount;
        this.status = status;
        this.message = message;
        this.metaCount = metaCount;
        this.eventCount = eventCount;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    public long getMinMetaId() {
        return minMetaId;
    }

    public long getMinEventId() {
        return minEventId;
    }

    public Long getMaxMetaCreateMs() {
        return maxMetaCreateMs;
    }

    public Long getMinMetaCreateMs() {
        return minMetaCreateMs;
    }

    /**
     * For UI use only to see current progress. Not used to influence task
     * creation.
     */
    public Long getMetaCreateMs() {
        return metaCreateMs;
    }

    public Long getLastPollMs() {
        return lastPollMs;
    }

    public Integer getLastPollTaskCount() {
        return lastPollTaskCount;
    }

    public ProcessorFilterTrackerStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Long getMetaCount() {
        return metaCount;
    }

    public Long getEventCount() {
        return eventCount;
    }

    /**
     * For UI use only to see current progress. Not used to influence task
     * creation.
     */
    @JsonIgnore
    public String getLastPollAge() {
        if (lastPollMs != null) {
            final long ageMs = System.currentTimeMillis() - lastPollMs;
            if (ageMs > 0) {
                return ModelStringUtil.formatDurationString(ageMs);
            } else {
                return "0";
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ProcessorFilterTracker{" +
               "id=" + id +
               ", version=" + version +
               ", minMetaId=" + minMetaId +
               ", minEventId=" + minEventId +
               ", minMetaCreateMs=" + minMetaCreateMs +
               ", maxMetaCreateMs=" + maxMetaCreateMs +
               ", metaCreateMs=" + metaCreateMs +
               ", lastPollMs=" + lastPollMs +
               ", lastPollTaskCount=" + lastPollTaskCount +
               ", status=" + status +
               ", message='" + message + '\'' +
               ", metaCount=" + metaCount +
               ", eventCount=" + eventCount +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProcessorFilterTracker that = (ProcessorFilterTracker) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private Integer id;
        private Integer version;
        private long minMetaId;
        private long minEventId;
        private Long minMetaCreateMs;
        private Long maxMetaCreateMs;
        private Long metaCreateMs;
        private Long lastPollMs;
        private Integer lastPollTaskCount;
        private ProcessorFilterTrackerStatus status;
        private String message;
        private Long metaCount;
        private Long eventCount;

        private Builder() {
        }

        private Builder(final ProcessorFilterTracker processorFilterTracker) {
            this.id = processorFilterTracker.id;
            this.version = processorFilterTracker.version;
            this.minMetaId = processorFilterTracker.minMetaId;
            this.minEventId = processorFilterTracker.minEventId;
            this.minMetaCreateMs = processorFilterTracker.minMetaCreateMs;
            this.maxMetaCreateMs = processorFilterTracker.maxMetaCreateMs;
            this.metaCreateMs = processorFilterTracker.metaCreateMs;
            this.lastPollMs = processorFilterTracker.lastPollMs;
            this.lastPollTaskCount = processorFilterTracker.lastPollTaskCount;
            this.status = processorFilterTracker.status;
            this.message = processorFilterTracker.message;
            this.metaCount = processorFilterTracker.metaCount;
            this.eventCount = processorFilterTracker.eventCount;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return this;
        }

        public Builder version(final Integer version) {
            this.version = version;
            return this;
        }

        public Builder minMetaId(final long minMetaId) {
            this.minMetaId = minMetaId;
            return this;
        }

        public Builder minEventId(final long minEventId) {
            this.minEventId = minEventId;
            return this;
        }

        public Builder minMetaCreateMs(final Long minMetaCreateMs) {
            this.minMetaCreateMs = minMetaCreateMs;
            return this;
        }

        public Builder maxMetaCreateMs(final Long maxMetaCreateMs) {
            this.maxMetaCreateMs = maxMetaCreateMs;
            return this;
        }

        public Builder metaCreateMs(final Long metaCreateMs) {
            this.metaCreateMs = metaCreateMs;
            return this;
        }

        public Builder lastPollMs(final Long lastPollMs) {
            this.lastPollMs = lastPollMs;
            return this;
        }

        public Builder lastPollTaskCount(final Integer lastPollTaskCount) {
            this.lastPollTaskCount = lastPollTaskCount;
            return this;
        }

        public Builder status(final ProcessorFilterTrackerStatus status) {
            this.status = status;
            return this;
        }

        public Builder message(final String message) {
            this.message = message;
            return this;
        }

        public Builder metaCount(final Long metaCount) {
            this.metaCount = metaCount;
            return this;
        }

        public Builder eventCount(final Long eventCount) {
            this.eventCount = eventCount;
            return this;
        }

        public ProcessorFilterTracker build() {
            return new ProcessorFilterTracker(
                    id,
                    version,
                    minMetaId,
                    minEventId,
                    minMetaCreateMs,
                    maxMetaCreateMs,
                    metaCreateMs,
                    lastPollMs,
                    lastPollTaskCount,
                    status,
                    message,
                    metaCount,
                    eventCount);
        }
    }
}
