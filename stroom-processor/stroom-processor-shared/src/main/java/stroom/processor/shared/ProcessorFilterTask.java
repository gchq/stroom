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

import java.util.Objects;

/**
 * Class used to represent processing a stream.
 */
public class ProcessorFilterTask implements SharedObject {
    public static final String ENTITY_TYPE = "StreamTask";
    private static final long serialVersionUID = 3926403008832938745L;

    // standard id and OCC fields
    private Integer id;
    private Integer version;

    private Long streamId;
    private String data;
    private String node_name;
    private Long createMs;
    private Long statusMs;
    private Long startTimeMs;
    private Long endTimeMs;
    private byte statusId = TaskStatus.UNPROCESSED.getPrimitiveValue();

    // parent filter
    private ProcessorFilter streamProcessorFilter;

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

    public Long getStreamId() {
        return streamId;
    }

    public void setStreamId(final Long streamId) {
        this.streamId = streamId;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public byte getPstatus() {
        return statusId;
    }

    public void setPstatus(final byte pstatus) {
        this.statusId = pstatus;
    }

    public TaskStatus getStatus() {
        return TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(statusId);
    }

    public void setStatus(final TaskStatus processStreamStatus) {
        statusId = processStreamStatus.getPrimitiveValue();
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(final Long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    public Long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(final Long createMs) {
        this.createMs = createMs;
    }

    public Long getStatusMs() {
        return statusMs;
    }

    public void setStatusMs(final Long statusMs) {
        this.statusMs = statusMs;
    }

    public Long getEndTimeMs() {
        return endTimeMs;
    }

    public void setEndTimeMs(final Long endTimeMs) {
        this.endTimeMs = endTimeMs;
    }

    /**
     * TODO: MAKE OPTIONAL FALSE AFTER Stroom 4.0 RELEASE WHEN REMOVING STREAM
     * PROCESSOR AND PRIORITY
     */
    public ProcessorFilter getStreamProcessorFilter() {
        return streamProcessorFilter;
    }

    public void setStreamProcessorFilter(final ProcessorFilter streamProcessorFilter) {
        this.streamProcessorFilter = streamProcessorFilter;
    }

    @Override
    public String toString() {
        return "ProcessorFilterTask{" +
                "id=" + id +
                ", version=" + version +
                ", streamId=" + streamId +
                ", data='" + data + '\'' +
                ", node_name='" + node_name + '\'' +
                ", createMs=" + createMs +
                ", statusMs=" + statusMs +
                ", startTimeMs=" + startTimeMs +
                ", endTimeMs=" + endTimeMs +
                ", statusId=" + statusId +
                ", streamProcessorFilter=" + streamProcessorFilter +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ProcessorFilterTask that = (ProcessorFilterTask) o;
        return statusId == that.statusId &&
                Objects.equals(id, that.id) &&
                Objects.equals(version, that.version) &&
                Objects.equals(streamId, that.streamId) &&
                Objects.equals(data, that.data) &&
                Objects.equals(node_name, that.node_name) &&
                Objects.equals(createMs, that.createMs) &&
                Objects.equals(statusMs, that.statusMs) &&
                Objects.equals(startTimeMs, that.startTimeMs) &&
                Objects.equals(endTimeMs, that.endTimeMs) &&
                Objects.equals(streamProcessorFilter, that.streamProcessorFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, streamId, data, node_name, createMs, statusMs, startTimeMs, endTimeMs, statusId, streamProcessorFilter);
    }
}
