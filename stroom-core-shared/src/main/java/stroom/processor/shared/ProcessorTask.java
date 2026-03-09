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


import stroom.util.shared.AbstractBuilder;

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
    private final long id;
    @JsonProperty
    private final Integer version;
    @JsonProperty
    private final Long metaId;
    @JsonProperty
    private final String data;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final String feedName;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final Long statusTimeMs;
    @JsonProperty
    private final Long startTimeMs;
    @JsonProperty
    private final Long endTimeMs;
    @JsonProperty
    private final TaskStatus status;

    // parent filter
    @JsonProperty
    private final ProcessorFilter processorFilter;

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

    public Integer getVersion() {
        return version;
    }

    public Long getMetaId() {
        return metaId;
    }

    public String getData() {
        return data;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getFeedName() {
        return feedName;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public Long getStatusTimeMs() {
        return statusTimeMs;
    }

    public Long getEndTimeMs() {
        return endTimeMs;
    }

    public ProcessorFilter getProcessorFilter() {
        return processorFilter;
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<ProcessorTask, Builder> {

        private long id;
        private Integer version;
        private Long metaId;
        private String data;
        private String nodeName;
        private String feedName;
        private Long createTimeMs;
        private Long statusTimeMs;
        private Long startTimeMs;
        private Long endTimeMs;
        private TaskStatus status;
        private ProcessorFilter processorFilter;

        public Builder() {
            status = TaskStatus.CREATED;
        }

        public Builder(final ProcessorTask processorTask) {
            this.id = processorTask.id;
            this.version = processorTask.version;
            this.metaId = processorTask.metaId;
            this.data = processorTask.data;
            this.nodeName = processorTask.nodeName;
            this.feedName = processorTask.feedName;
            this.createTimeMs = processorTask.createTimeMs;
            this.statusTimeMs = processorTask.statusTimeMs;
            this.startTimeMs = processorTask.startTimeMs;
            this.endTimeMs = processorTask.endTimeMs;
            this.status = processorTask.status;
            this.processorFilter = processorTask.processorFilter;
        }

        public Builder id(final long id) {
            this.id = id;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        public Builder metaId(final Long metaId) {
            this.metaId = metaId;
            return self();
        }

        public Builder data(final String data) {
            this.data = data;
            return self();
        }

        public Builder nodeName(final String nodeName) {
            this.nodeName = nodeName;
            return self();
        }

        public Builder feedName(final String feedName) {
            this.feedName = feedName;
            return self();
        }

        public Builder createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return self();
        }

        public Builder statusTimeMs(final Long statusTimeMs) {
            this.statusTimeMs = statusTimeMs;
            return self();
        }

        public Builder startTimeMs(final Long startTimeMs) {
            this.startTimeMs = startTimeMs;
            return self();
        }

        public Builder endTimeMs(final Long endTimeMs) {
            this.endTimeMs = endTimeMs;
            return self();
        }

        public Builder status(final TaskStatus status) {
            this.status = status;
            return self();
        }

        public Builder processorFilter(final ProcessorFilter processorFilter) {
            this.processorFilter = processorFilter;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ProcessorTask build() {
            return new ProcessorTask(
                    id,
                    version,
                    metaId,
                    data,
                    nodeName,
                    feedName,
                    createTimeMs,
                    statusTimeMs,
                    startTimeMs,
                    endTimeMs,
                    status,
                    processorFilter);
        }
    }
}
