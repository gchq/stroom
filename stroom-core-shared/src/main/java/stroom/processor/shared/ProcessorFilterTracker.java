/*
 * Copyright 2016 Crown Copyright
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

import stroom.docref.SharedObject;
import stroom.util.shared.ModelStringUtil;

import java.util.Objects;


public class ProcessorFilterTracker implements SharedObject {
    public static final String ENTITY_TYPE = "ProcessorFilterTracker";
    public static final String COMPLETE = "Complete";
    private static final long serialVersionUID = -2478788451478923825L;

    // standard id and OCC fields
    private Integer id;
    private Integer version;

    // These numbers are inclusive use getStreamRange to get a nice Stroom style
    private long minMetaId;
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
    private Long minMetaCreateMs;
    private Long maxMetaCreateMs;
    private Long metaCreateMs;

    private Long lastPollMs;
    private Integer lastPollTaskCount;
    private boolean complete;
    private String status;

    private Long metaCount;
    private Long eventCount;

    public ProcessorFilterTracker() {
        // Default constructor necessary for GWT serialisation.
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
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
    public Integer getTrackerStreamCreatePercentage() {
        return getTrackerStreamCreatePercentage(System.currentTimeMillis());
    }

    public Integer getTrackerStreamCreatePercentage(final long now) {
        if (complete) {
            return 100;
        }

        if (minMetaCreateMs != null && metaCreateMs != null) {
            long max = now;
            if (lastPollMs != null) {
                max = lastPollMs;
            }
            if (maxMetaCreateMs != null) {
                max = maxMetaCreateMs;
            }

            final long windowSize = max - minMetaCreateMs;

            // If the window size is less than or equal to 0 then we are at 100%
            // for now.
            if (windowSize <= 0) {
                return 100;
            }

            final long trackerPos = Math.min(max, metaCreateMs);
            final long windowPos = Math.max(0, max - trackerPos);
            return ((int) ((100.0 * (windowSize - windowPos)) / windowSize));
        }

        return null;
    }

    /**
     * For UI use only to see current progress. Not used to influence task
     * creation.
     */
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
                ", complete=" + complete +
                ", status='" + status + '\'' +
                ", metaCount=" + metaCount +
                ", eventCount=" + eventCount +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ProcessorFilterTracker that = (ProcessorFilterTracker) o;
        return minMetaId == that.minMetaId &&
                minEventId == that.minEventId &&
                complete == that.complete &&
                Objects.equals(id, that.id) &&
                Objects.equals(version, that.version) &&
                Objects.equals(minMetaCreateMs, that.minMetaCreateMs) &&
                Objects.equals(maxMetaCreateMs, that.maxMetaCreateMs) &&
                Objects.equals(metaCreateMs, that.metaCreateMs) &&
                Objects.equals(lastPollMs, that.lastPollMs) &&
                Objects.equals(lastPollTaskCount, that.lastPollTaskCount) &&
                Objects.equals(status, that.status) &&
                Objects.equals(metaCount, that.metaCount) &&
                Objects.equals(eventCount, that.eventCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, minMetaId, minEventId, minMetaCreateMs, maxMetaCreateMs, metaCreateMs, lastPollMs, lastPollTaskCount, complete, status, metaCount, eventCount);
    }
}
