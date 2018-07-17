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

import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * Class used to represent processing a stream.
 */
@Entity
@Table(name = "PROCESSOR_FILTER_TASK")
public class ProcessorFilterTask extends TaskBasedEntity {
    public static final String TABLE_NAME = "PROCESSOR_FILTER_TASK";
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String ENTITY_TYPE = "StreamTask";
    public static final String STREAM_ID = "FK_STRM_ID";
    public static final String DATA = SQLNameConstants.DATA;
    private static final long serialVersionUID = 3926403008832938745L;
    private Long streamId;

    private String data;

    /**
     * We don't eager fetch this one ... you need to call load.
     */
    private ProcessorFilter streamProcessorFilter;

    @Column(name = STREAM_ID, nullable = false)
    public Long getStreamId() {
        return streamId;
    }

    public void setStreamId(final Long streamId) {
        this.streamId = streamId;
    }

    @Column(name = DATA, length = Integer.MAX_VALUE)
    @Lob
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    /**
     * TODO: MAKE OPTIONAL FALSE AFTER Stroom 4.0 RELEASE WHEN REMOVING STREAM
     * PROCESSOR AND PRIORITY
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = ProcessorFilter.FOREIGN_KEY)
    public ProcessorFilter getStreamProcessorFilter() {
        return streamProcessorFilter;
    }

    public void setStreamProcessorFilter(final ProcessorFilter streamProcessorFilter) {
        this.streamProcessorFilter = streamProcessorFilter;
    }

    @Override
    protected void toString(final StringBuilder sb) {
        super.toString(sb);
        sb.append(", streamId=");
        sb.append(streamId);
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
