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
}
