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


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Class used to represent processing a stream.
 */
@JsonInclude(Include.NON_NULL)
public class ProcessorTask {

    // standard id and OCC fields
    @JsonProperty
    private long id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long metaId;
    @JsonProperty
    private String data;
    @JsonProperty
    private String nodeName;
    @JsonProperty
    private String feedName;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private Long statusTimeMs;
    @JsonProperty
    private Long startTimeMs;
    @JsonProperty
    private Long endTimeMs;
    @JsonProperty
    private TaskStatus status;

    // parent filter
    @JsonProperty
    private ProcessorFilter processorFilter;

    public ProcessorTask() {
        status = TaskStatus.CREATED;
    }

    @JsonCreator
    public ProcessorTask(@JsonProperty("id") final long id,
                         @JsonProperty("version") final Integer version,
                         @JsonProperty("metaId") final Long metaId,
                         @JsonProperty("data") final String data,
                         @JsonProperty("nodeName") final String nodeName,
                         @JsonProperty("feedName") final String feedName,
                         @JsonProperty("createTimeMs") final Long createTimeMs,
                         @JsonProperty("statusTimeMs") final Long statusTimeMs,
                         @JsonProperty("startTimeMs") final Long startTimeMs,
                         @JsonProperty("endTimeMs") final Long endTimeMs,
                         @JsonProperty("status") final TaskStatus status,
                         @JsonProperty("processorFilter") final ProcessorFilter processorFilter) {
        this.id = id;
        this.version = version;
        this.metaId = metaId;
        this.data = data;
        this.nodeName = nodeName;
        this.feedName = feedName;
        this.createTimeMs = createTimeMs;
        this.statusTimeMs = statusTimeMs;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.status = status;
        this.processorFilter = processorFilter;
    }

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

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(final String feedName) {
        this.feedName = feedName;
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
                ", feedName='" + feedName + '\'' +
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProcessorTask that = (ProcessorTask) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
