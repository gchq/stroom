/*
 * Copyright 2019 Crown Copyright
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


import stroom.util.shared.AbstractBuilder;
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
    private Integer id;
    @JsonProperty
    private Integer version;

    // These numbers are inclusive use getStreamRange to get a nice Stroom style
    @JsonProperty
    private long minMetaId;
    @JsonProperty
    private long minEventId;

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
    private Long minMetaCreateMs;
    @JsonProperty
    private Long maxMetaCreateMs;
    @JsonProperty
    private Long metaCreateMs;

    @JsonProperty
    private Long lastPollMs;
    @JsonProperty
    private Integer lastPollTaskCount;
    @JsonProperty
    private ProcessorFilterTrackerStatus status;
    @JsonProperty
    private String message;

    @JsonProperty
    private Long metaCount;
    @JsonProperty
    private Long eventCount;

    /**
     * The max meta id seen on the previous task creation poll. Task creation is bounded by this
     * rather than the live max meta id so that a meta row that was inserted but not yet committed
     * when the max was read has a full poll interval to become visible before this tracker moves
     * past it. Null until a poll has established a value.
     */
    @JsonProperty
    private Long prevMaxMetaId;

    /**
     * The earliest time that task creation should poll this filter again. It is only set when a
     * poll creates no tasks, and each successive non producing poll pushes it further out, up to
     * a maximum, so that filters with nothing to do are polled less often. Null means poll on the
     * next task creation run.
     */
    @JsonProperty
    private Long nextPollMs;

    public ProcessorFilterTracker() {
    }

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
                                  @JsonProperty("eventCount") final Long eventCount,
                                  @JsonProperty("prevMaxMetaId") final Long prevMaxMetaId,
                                  @JsonProperty("nextPollMs") final Long nextPollMs) {
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
        this.prevMaxMetaId = prevMaxMetaId;
        this.nextPollMs = nextPollMs;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public long getMinMetaId() {
        return minMetaId;
    }

    public void setMinMetaId(final long minMetaId) {
        this.minMetaId = minMetaId;
    }

    public long getMinEventId() {
        return minEventId;
    }

    public void setMinEventId(final long minEventId) {
        this.minEventId = minEventId;
    }

    public Long getMaxMetaCreateMs() {
        return maxMetaCreateMs;
    }

    public void setMaxMetaCreateMs(final Long maxMetaCreateMs) {
        this.maxMetaCreateMs = maxMetaCreateMs;
    }

    public Long getMinMetaCreateMs() {
        return minMetaCreateMs;
    }

    public void setMinMetaCreateMs(final Long minMetaCreateMs) {
        this.minMetaCreateMs = minMetaCreateMs;
    }

    /**
     * For UI use only to see current progress. Not used to influence task
     * creation.
     */
    public Long getMetaCreateMs() {
        return metaCreateMs;
    }

    /**
     * For UI use only to see current progress. Not used to influence task
     * creation.
     */
    public void setMetaCreateMs(final Long metaCreateMs) {
        this.metaCreateMs = metaCreateMs;
    }

    public Long getLastPollMs() {
        return lastPollMs;
    }

    public void setLastPollMs(final Long lastPollMs) {
        this.lastPollMs = lastPollMs;
    }

    public Integer getLastPollTaskCount() {
        return lastPollTaskCount;
    }

    public void setLastPollTaskCount(final Integer lastPollTaskCount) {
        this.lastPollTaskCount = lastPollTaskCount;
    }

    public ProcessorFilterTrackerStatus getStatus() {
        return status;
    }

    public void setStatus(final ProcessorFilterTrackerStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public Long getMetaCount() {
        return metaCount;
    }

    public void setMetaCount(final Long metaCount) {
        this.metaCount = metaCount;
    }

    public Long getEventCount() {
        return eventCount;
    }

    public void setEventCount(final Long eventCount) {
        this.eventCount = eventCount;
    }

    public Long getPrevMaxMetaId() {
        return prevMaxMetaId;
    }

    public void setPrevMaxMetaId(final Long prevMaxMetaId) {
        this.prevMaxMetaId = prevMaxMetaId;
    }

    public Long getNextPollMs() {
        return nextPollMs;
    }

    public void setNextPollMs(final Long nextPollMs) {
        this.nextPollMs = nextPollMs;
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
                ", prevMaxMetaId=" + prevMaxMetaId +
                ", nextPollMs=" + nextPollMs +
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<ProcessorFilterTracker, Builder> {

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
        private Long prevMaxMetaId;
        private Long nextPollMs;

        public Builder() {
        }

        public Builder(final ProcessorFilterTracker tracker) {
            this.id = tracker.id;
            this.version = tracker.version;
            this.minMetaId = tracker.minMetaId;
            this.minEventId = tracker.minEventId;
            this.minMetaCreateMs = tracker.minMetaCreateMs;
            this.maxMetaCreateMs = tracker.maxMetaCreateMs;
            this.metaCreateMs = tracker.metaCreateMs;
            this.lastPollMs = tracker.lastPollMs;
            this.lastPollTaskCount = tracker.lastPollTaskCount;
            this.status = tracker.status;
            this.message = tracker.message;
            this.metaCount = tracker.metaCount;
            this.eventCount = tracker.eventCount;
            this.prevMaxMetaId = tracker.prevMaxMetaId;
            this.nextPollMs = tracker.nextPollMs;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        public Builder minMetaId(final long minMetaId) {
            this.minMetaId = minMetaId;
            return self();
        }

        public Builder minEventId(final long minEventId) {
            this.minEventId = minEventId;
            return self();
        }

        public Builder minMetaCreateMs(final Long minMetaCreateMs) {
            this.minMetaCreateMs = minMetaCreateMs;
            return self();
        }

        public Builder maxMetaCreateMs(final Long maxMetaCreateMs) {
            this.maxMetaCreateMs = maxMetaCreateMs;
            return self();
        }

        public Builder metaCreateMs(final Long metaCreateMs) {
            this.metaCreateMs = metaCreateMs;
            return self();
        }

        public Builder lastPollMs(final Long lastPollMs) {
            this.lastPollMs = lastPollMs;
            return self();
        }

        public Builder lastPollTaskCount(final Integer lastPollTaskCount) {
            this.lastPollTaskCount = lastPollTaskCount;
            return self();
        }

        public Builder status(final ProcessorFilterTrackerStatus status) {
            this.status = status;
            return self();
        }

        public Builder message(final String message) {
            this.message = message;
            return self();
        }

        public Builder metaCount(final Long metaCount) {
            this.metaCount = metaCount;
            return self();
        }

        public Builder eventCount(final Long eventCount) {
            this.eventCount = eventCount;
            return self();
        }

        public Builder prevMaxMetaId(final Long prevMaxMetaId) {
            this.prevMaxMetaId = prevMaxMetaId;
            return self();
        }

        public Builder nextPollMs(final Long nextPollMs) {
            this.nextPollMs = nextPollMs;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
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
                    eventCount,
                    prevMaxMetaId,
                    nextPollMs);
        }
    }
}
