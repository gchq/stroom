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

package stroom.meta.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Meta implements SimpleMeta {

    @JsonProperty
    private long id;
    @JsonProperty
    private String feedName;
    @JsonProperty
    private String typeName;
    @JsonProperty
    private String processorUuid;
    @JsonProperty
    private String pipelineUuid;
    @JsonProperty
    private Integer processorFilterId;
    @JsonProperty
    private Long processorTaskId;
    @JsonProperty
    private Long parentMetaId;
    @JsonProperty
    private Status status;
    @JsonProperty
    private Long statusMs;
    @JsonProperty
    private long createMs;
    @JsonProperty
    private Long effectiveMs;

    public Meta() {
    }

    @JsonCreator
    public Meta(@JsonProperty("id") final long id,
                @JsonProperty("feedName") final String feedName,
                @JsonProperty("typeName") final String typeName,
                @JsonProperty("processorUuid") final String processorUuid,
                @JsonProperty("pipelineUuid") final String pipelineUuid,
                @JsonProperty("processorFilterId") final Integer processorFilterId,
                @JsonProperty("processorTaskId") final Long processorTaskId,
                @JsonProperty("parentMetaId") final Long parentMetaId,
                @JsonProperty("status") final Status status,
                @JsonProperty("statusMs") final Long statusMs,
                @JsonProperty("createMs") final long createMs,
                @JsonProperty("effectiveMs") final Long effectiveMs) {
        this.id = id;
        this.feedName = feedName;
        this.typeName = typeName;
        this.processorUuid = processorUuid;
        this.pipelineUuid = pipelineUuid;
        this.processorFilterId = processorFilterId;
        this.processorTaskId = processorTaskId;
        this.parentMetaId = parentMetaId;
        this.status = status;
        this.statusMs = statusMs;
        this.createMs = createMs;
        this.effectiveMs = effectiveMs;
    }

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(final String feedName) {
        this.feedName = feedName;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(final String typeName) {
        this.typeName = typeName;
    }

    public String getProcessorUuid() {
        return processorUuid;
    }

    public void setProcessorUuid(final String processorUuid) {
        this.processorUuid = processorUuid;
    }

    public String getPipelineUuid() {
        return pipelineUuid;
    }

    public void setPipelineUuid(final String pipelineUuid) {
        this.pipelineUuid = pipelineUuid;
    }

    public Integer getProcessorFilterId() {
        return processorFilterId;
    }

    public void setProcessorFilterId(final Integer processorFilterId) {
        this.processorFilterId = processorFilterId;
    }

    public Long getProcessorTaskId() {
        return processorTaskId;
    }

    public void setProcessorTaskId(final Long processorTaskId) {
        this.processorTaskId = processorTaskId;
    }

    public Long getParentMetaId() {
        return parentMetaId;
    }

    public void setParentMetaId(final Long parentMetaId) {
        this.parentMetaId = parentMetaId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    public Long getStatusMs() {
        return statusMs;
    }

    public void setStatusMs(final Long statusMs) {
        this.statusMs = statusMs;
    }

    public long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(final long createMs) {
        this.createMs = createMs;
    }

    public Long getEffectiveMs() {
        return effectiveMs;
    }

    public void setEffectiveMs(final Long effectiveMs) {
        this.effectiveMs = effectiveMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Meta meta = (Meta) o;
        return id == meta.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id + " - " + feedName + " - " + typeName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private long id;
        private String feedName;
        private String typeName;
        private String processorUuid;
        private String pipelineUuid;
        private Integer processorFilterId;
        private Long processorTaskId;
        private Long parentMetaId;
        private Status status;
        private Long statusMs;
        private long createMs;
        private Long effectiveMs;

        private Builder() {
        }

        private Builder(final Meta meta) {
            this.id = meta.id;
            this.feedName = meta.feedName;
            this.typeName = meta.typeName;
            this.processorUuid = meta.processorUuid;
            this.pipelineUuid = meta.pipelineUuid;
            this.processorFilterId = meta.processorFilterId;
            this.processorTaskId = meta.processorTaskId;
            this.parentMetaId = meta.parentMetaId;
            this.status = meta.status;
            this.statusMs = meta.statusMs;
            this.createMs = meta.createMs;
            this.effectiveMs = meta.effectiveMs;
        }

        public Builder id(final long id) {
            this.id = id;
            return this;
        }

        public Builder feedName(final String feedName) {
            this.feedName = feedName;
            return this;
        }

        public Builder typeName(final String typeName) {
            this.typeName = typeName;
            return this;
        }

        public Builder processorUuid(final String processorUuid) {
            this.processorUuid = processorUuid;
            return this;
        }

        public Builder pipelineUuid(final String pipelineUuid) {
            this.pipelineUuid = pipelineUuid;
            return this;
        }

        public Builder processorFilterId(final Integer processorFilterId) {
            this.processorFilterId = processorFilterId;
            return this;
        }

        public Builder processorTaskId(final Long processorTaskId) {
            this.processorTaskId = processorTaskId;
            return this;
        }

        public Builder parentDataId(final Long parentDataId) {
            this.parentMetaId = parentDataId;
            return this;
        }

        public Builder status(final Status status) {
            this.status = status;
            return this;
        }

        public Builder statusMs(final Long statusMs) {
            this.statusMs = statusMs;
            return this;
        }

        public Builder createMs(final long createMs) {
            this.createMs = createMs;
            return this;
        }

        public Builder effectiveMs(final Long effectiveMs) {
            this.effectiveMs = effectiveMs;
            return this;
        }

        public Meta build() {
            return new Meta(
                    id,
                    feedName,
                    typeName,
                    processorUuid,
                    pipelineUuid,
                    processorFilterId,
                    processorTaskId,
                    parentMetaId,
                    status,
                    statusMs,
                    createMs,
                    effectiveMs
            );
        }
    }
}
