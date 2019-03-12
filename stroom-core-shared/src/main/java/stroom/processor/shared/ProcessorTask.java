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
public class ProcessorTask implements SharedObject {
//    public static final String ENTITY_TYPE = "StreamTask";
    private static final long serialVersionUID = 3926403008832938745L;

    // standard id and OCC fields
    private long id;
    private Integer version;

    private Long metaId;
    private String data;
    private String nodeName;
    private Long createTimeMs;
    private Long statusTimeMs;
    private Long startTimeMs;
    private Long endTimeMs;
    private TaskStatus status = TaskStatus.UNPROCESSED;

    // parent filter
    private ProcessorFilter processorFilter;

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public Long getMetaId() {
        return metaId;
    }

    public void setMetaId(final Long metaId) {
        this.metaId = metaId;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(final TaskStatus status) {
        this.status = status;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(final Long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    public Long getStatusTimeMs() {
        return statusTimeMs;
    }

    public void setStatusTimeMs(final Long statusTimeMs) {
        this.statusTimeMs = statusTimeMs;
    }

    public Long getEndTimeMs() {
        return endTimeMs;
    }

    public void setEndTimeMs(final Long endTimeMs) {
        this.endTimeMs = endTimeMs;
    }

    public ProcessorFilter getProcessorFilter() {
        return processorFilter;
    }

    public void setProcessorFilter(final ProcessorFilter processorFilter) {
        this.processorFilter = processorFilter;
    }

    @Override
    public String toString() {
        return "ProcessorTask{" +
                "id=" + id +
                ", version=" + version +
                ", metaId=" + metaId +
                ", data='" + data + '\'' +
                ", nodeName='" + nodeName + '\'' +
                ", createTimeMs=" + createTimeMs +
                ", statusTimeMs=" + statusTimeMs +
                ", startTimeMs=" + startTimeMs +
                ", endTimeMs=" + endTimeMs +
                ", status=" + status +
                ", processorFilter=" + processorFilter +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ProcessorTask that = (ProcessorTask) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
