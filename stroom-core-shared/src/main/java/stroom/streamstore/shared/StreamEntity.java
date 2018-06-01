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

package stroom.streamstore.shared;

import stroom.entity.shared.BaseEntityBig;
import stroom.entity.shared.SQLNameConstants;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamTask;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PostLoad;
import javax.persistence.Transient;

/**
 * <p>
 * Class that stores meta data about streams (events or reference data).
 * </p>
 * <p>
 * <p>
 * This class is also used to 'key' the file on the file system.
 * </p>
 * <p>
 * <p>
 * Each stream has a overall status. This would be one of:
 * <ol>
 * <li>UNLOCKED - Stream is ready to be accessed. A number of UNLOCKED
 * StreamVolume would exist.</li>
 * <li>LOCKED - Stream has an exclusive lock on the stream. Typically because it
 * is currently being written or it is about to be deleted. A number of LOCKED
 * StreamVolume would exist.</li>
 * <li>DELETED - Logical delete.... a later task will remove the files and
 * database record. A number of DELETED StreamVolume would exist. Last node to
 * delete their StreamVolume would also delete this node.</li>
 * </ol>
 */
@Entity(name = "STRM")
public class StreamEntity extends BaseEntityBig implements Stream {
    public static final String TABLE_NAME = SQLNameConstants.STREAM;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String CREATE_MS = SQLNameConstants.CREATE + SQLNameConstants.MS_SUFFIX;
    public static final String EFFECTIVE_MS = SQLNameConstants.EFFECTIVE + SQLNameConstants.MS_SUFFIX;
    public static final String STATUS = SQLNameConstants.STATUS;
    public static final String STATUS_MS = SQLNameConstants.STATUS + SQLNameConstants.MS_SUFFIX;
    public static final String PARENT_STREAM_ID = SQLNameConstants.PARENT + SEP + SQLNameConstants.STREAM + SEP + ID;
    public static final String STREAM_TASK_ID = StreamTask.TABLE_NAME + SEP + ID;
    public static final String ENTITY_TYPE = "Stream";
    private static final long serialVersionUID = -497426444934457256L;
    /**
     * We don't eager fetch this one ... you need to call loadFeed.
     */
    private FeedEntity feed;

    private StreamTypeEntity streamType;

    /**
     * The stream processor that was used to create this stream if relevant.
     */
    private StreamProcessor streamProcessor;

    /**
     * The stream task that was used to create this stream if relevant (not a
     * real relationship)
     */
    private Long streamTaskId;

    private byte pstatus;

    // The time the status was updated
    private Long statusMs;

    // Time the stream was created
    private long createMs;

    // Effective time of the stream
    private Long effectiveMs;

    private transient boolean checkImmutable = false;

    /**
     * The stream we are related to.
     */
    private Long parentStreamId;

    public StreamEntity() {
        // Default constructor necessary for GWT serialisation.
    }

    public static StreamEntity createStub(final long pk) {
        final StreamEntity stream = new StreamEntity();
        stream.setStub(pk);
        return stream;
    }

    @Override
    public String getFeedName() {
        if (feed == null) {
            return null;
        }
        return feed.getName();
    }

    @Override
    public String getStreamTypeName() {
        if (streamType == null) {
            return null;
        }
        return streamType.getName();
    }

    @Override
    public String getPipelineName() {
        if (streamProcessor == null) {
            return null;
        }
        return streamProcessor.getPipelineName();
    }

    @Override
    public Long getStreamProcessorId() {
        if (streamProcessor == null) {
            return null;
        }
        return streamProcessor.getId();
    }

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = FeedEntity.FOREIGN_KEY)
    public FeedEntity getFeed() {
        return feed;
    }

    public void setFeed(final FeedEntity feed) {
        this.feed = applySetter(this.feed, feed);
    }

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = StreamTypeEntity.FOREIGN_KEY)
    public StreamTypeEntity getStreamType() {
        return streamType;
    }

    public void setStreamType(final StreamTypeEntity streamType) {
        this.streamType = applySetter(this.streamType, streamType);
    }

    @Column(name = PARENT_STREAM_ID, nullable = true)
    public Long getParentStreamId() {
        return parentStreamId;
    }

    public void setParentStreamId(final Long parentStreamId) {
        this.parentStreamId = applySetter(this.parentStreamId, parentStreamId);
    }

    @Column(name = STREAM_TASK_ID, nullable = true)
    public Long getStreamTaskId() {
        return streamTaskId;
    }

    public void setStreamTaskId(final Long streamTaskId) {
        this.streamTaskId = streamTaskId;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = StreamProcessor.FOREIGN_KEY)
    public StreamProcessor getStreamProcessor() {
        return streamProcessor;
    }

    public void setStreamProcessor(final StreamProcessor streamProcessor) {
        this.streamProcessor = applySetter(this.streamProcessor, streamProcessor);
    }

    private <T> T applySetter(final T currentValue, final T newValue) {
        if (!checkImmutable) {
            return newValue;
        }
        // Loaded off the database ... make sure no change happens
        if (currentValue == null && newValue == null) {
            return null;
        }
        if (currentValue == null || newValue == null) {
            // Changing
            throw new RuntimeException("You can not change attributes of Stream once it has been persisted");
        }
        // Different Values
        if (!currentValue.equals(newValue)) {
            // Changing
            throw new RuntimeException("You can not change attributes of Stream once it has been persisted");
        }
        return newValue;

    }

    @Transient
    public StreamStatus getStatus() {
        return StreamStatusId.getStreamStatus(pstatus);
    }

    public void updateStatus(final StreamStatus status) {
        pstatus = StreamStatusId.getPrimitiveValue(status);
        statusMs = System.currentTimeMillis();
    }

    @Column(name = STATUS, nullable = false)
    public byte getPstatus() {
        return pstatus;
    }

    public void setPstatus(final byte pstatus) {
        this.pstatus = pstatus;
    }

    @Column(name = STATUS_MS, columnDefinition = BIGINT_UNSIGNED, nullable = false)
    public Long getStatusMs() {
        return statusMs;
    }

    public void setStatusMs(final Long statusMs) {
        this.statusMs = statusMs;
    }

    @Column(name = CREATE_MS, columnDefinition = BIGINT_UNSIGNED, nullable = false)
    public long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(final long createMs) {
        this.createMs = applySetter(this.createMs, createMs);
    }

    @Column(name = EFFECTIVE_MS, columnDefinition = BIGINT_UNSIGNED)
    public Long getEffectiveMs() {
        return effectiveMs;
    }

    public void setEffectiveMs(final Long effectiveMs) {
        this.effectiveMs = applySetter(this.effectiveMs, effectiveMs);
    }

    @PostLoad
    public void postLoad() {
        checkImmutable = true;
    }

    @Transient
    public void setCheckImmutable(final boolean checkImmutable) {
        this.checkImmutable = checkImmutable;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        if (streamType != null) {
            sb.append(", type=");
            sb.append(streamType.getId());
        }
        if (feed != null) {
            sb.append(", feed=");
            sb.append(feed.getId());
        }
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
