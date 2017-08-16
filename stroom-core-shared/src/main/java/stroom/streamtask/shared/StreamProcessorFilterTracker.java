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

package stroom.streamtask.shared;

import stroom.entity.shared.BaseEntitySmall;
import stroom.entity.shared.SQLNameConstants;
import stroom.streamstore.shared.Stream;
import stroom.util.shared.ModelStringUtil;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Transient;

@Entity(name = "STRM_PROC_FILT_TRAC")
public class StreamProcessorFilterTracker extends BaseEntitySmall {
    public static final String TABLE_NAME = SQLNameConstants.STREAM + SEP + SQLNameConstants.PROCESSOR + SEP
            + SQLNameConstants.FILTER + SEP + SQLNameConstants.TRACKER;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String MIN_STREAM_CREATE_MS = SQLNameConstants.MIN + SEP + SQLNameConstants.STREAM + SEP
            + SQLNameConstants.CREATE + SQLNameConstants.MS_SUFFIX;
    public static final String MAX_STREAM_CREATE_MS = SQLNameConstants.MAX + SEP + SQLNameConstants.STREAM + SEP
            + SQLNameConstants.CREATE + SQLNameConstants.MS_SUFFIX;
    public static final String STREAM_CREATE_MS = SQLNameConstants.STREAM + SEP + SQLNameConstants.CREATE
            + SQLNameConstants.MS_SUFFIX;
    public static final String MIN_STREAM_ID = SQLNameConstants.MIN + SEP + SQLNameConstants.STREAM + SEP + Stream.ID;
    public static final String MIN_EVENT_ID = SQLNameConstants.MIN + SEP + SQLNameConstants.EVENT + SEP + Stream.ID;
    public static final String LAST_POLL_MS = SQLNameConstants.LAST + SEP + SQLNameConstants.POLL
            + SQLNameConstants.MS_SUFFIX;
    public static final String LAST_POLL_TASK_COUNT = SQLNameConstants.LAST + SEP + SQLNameConstants.POLL + SEP
            + SQLNameConstants.TASK + SQLNameConstants.COUNT_SUFFIX;
    public static final String STATUS = SQLNameConstants.STATUS;
    public static final String STREAM_COUNT = SQLNameConstants.STREAM + SEP + SQLNameConstants.COUNT;
    public static final String EVENT_COUNT = SQLNameConstants.EVENT + SEP + SQLNameConstants.COUNT;
    public static final String ENTITY_TYPE = "StreamProcessorFilterTracker";
    public static final String COMPLETE = "Complete";
    private static final long serialVersionUID = -2478788451478923825L;
    // These numbers are inclusive use getStreamRange to get a nice Stroom style
    private long minStreamId;
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
    private Long minStreamCreateMs;
    private Long maxStreamCreateMs;
    private Long streamCreateMs;

    private Long lastPollMs;
    private Integer lastPollTaskCount;
    private boolean complete;
    private String status;

    private Long streamCount;
    private Long eventCount;

    public StreamProcessorFilterTracker() {
        // Default constructor necessary for GWT serialisation.
    }

    @Override
    @Transient
    public String getType() {
        return ENTITY_TYPE;
    }

    @Column(name = MIN_STREAM_ID, nullable = false)
    public long getMinStreamId() {
        return minStreamId;
    }

    public void setMinStreamId(final long minStreamId) {
        this.minStreamId = minStreamId;
    }

    @Column(name = MIN_EVENT_ID, nullable = false)
    public long getMinEventId() {
        return minEventId;
    }

    public void setMinEventId(final long minEventId) {
        this.minEventId = minEventId;
    }

    @Column(name = MAX_STREAM_CREATE_MS)
    public Long getMaxStreamCreateMs() {
        return maxStreamCreateMs;
    }

    public void setMaxStreamCreateMs(final Long maxStreamCreateMs) {
        this.maxStreamCreateMs = maxStreamCreateMs;
    }

    @Column(name = MIN_STREAM_CREATE_MS)
    public Long getMinStreamCreateMs() {
        return minStreamCreateMs;
    }

    public void setMinStreamCreateMs(final Long minStreamCreateMs) {
        this.minStreamCreateMs = minStreamCreateMs;
    }

    /**
     * For UI use only to see current progress. Not used to influence task
     * creation.
     */
    @Column(name = STREAM_CREATE_MS)
    public Long getStreamCreateMs() {
        return streamCreateMs;
    }

    /**
     * For UI use only to see current progress. Not used to influence task
     * creation.
     */
    public void setStreamCreateMs(final Long streamCreateMs) {
        this.streamCreateMs = streamCreateMs;
    }

    @Column(name = LAST_POLL_MS)
    public Long getLastPollMs() {
        return lastPollMs;
    }

    public void setLastPollMs(final Long lastPollMs) {
        this.lastPollMs = lastPollMs;
    }

    @Column(name = LAST_POLL_TASK_COUNT)
    public Integer getLastPollTaskCount() {
        return lastPollTaskCount;
    }

    public void setLastPollTaskCount(final Integer lastPollTaskCount) {
        this.lastPollTaskCount = lastPollTaskCount;
    }

    @Column(name = STATUS)
    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    @Column(name = STREAM_COUNT)
    public Long getStreamCount() {
        return streamCount;
    }

    public void setStreamCount(final Long streamCount) {
        this.streamCount = streamCount;
    }

    @Column(name = EVENT_COUNT)
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
    @Transient
    public Integer getTrackerStreamCreatePercentage() {
        return getTrackerStreamCreatePercentage(System.currentTimeMillis());
    }

    public Integer getTrackerStreamCreatePercentage(final long now) {
        if (complete) {
            return 100;
        }

        if (minStreamCreateMs != null && streamCreateMs != null) {
            long max = now;
            if (lastPollMs != null) {
                max = lastPollMs;
            }
            if (maxStreamCreateMs != null) {
                max = maxStreamCreateMs;
            }

            final long windowSize = max - minStreamCreateMs;

            // If the window size is less than or equal to 0 then we are at 100%
            // for now.
            if (windowSize <= 0) {
                return 100;
            }

            final long trackerPos = Math.min(max, streamCreateMs);
            final long windowPos = Math.max(0, max - trackerPos);
            return ((int) ((100.0 * (windowSize - windowPos)) / windowSize));
        }

        return null;
    }

    /**
     * For UI use only to see current progress. Not used to influence task
     * creation.
     */
    @Transient
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
}
