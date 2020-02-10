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

package stroom.meta.shared;



import java.util.Objects;

public class Meta {
    private long id;
    private String feedName;
    private String typeName;
    private String processorUuid;
    private String pipelineUuid;
    private Long parentDataId;
    private Status status;
    private Long statusMs;
    private long createMs;
    private Long effectiveMs;

    public Meta() {
        // Default constructor necessary for GWT serialisation.
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

    public Long getParentMetaId() {
        return parentDataId;
    }

    public void setParentDataId(final Long parentDataId) {
        this.parentDataId = parentDataId;
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Meta meta = (Meta) o;
        return id == meta.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.valueOf(id);
    }

    public static class Builder {
        private final Meta meta = new Meta();

        public Builder() {
        }

        public Builder(final Meta meta) {
            id(meta.getId());
            feedName(meta.getFeedName());
            typeName(meta.getTypeName());
            pipelineUuid(meta.getPipelineUuid());
            processorUuid(meta.getProcessorUuid());
            parentDataId(meta.getParentMetaId());
            status(meta.getStatus());
            statusMs(meta.getStatusMs());
            createMs(meta.getCreateMs());
            effectiveMs(meta.getEffectiveMs());
        }

        public Builder id(final long id) {
            meta.id = id;
            return this;
        }

        public Builder feedName(final String feedName) {
            meta.feedName = feedName;
            return this;
        }

        public Builder typeName(final String typeName) {
            meta.typeName = typeName;
            return this;
        }

        public Builder processorUuid(final String processorUuid) {
            meta.processorUuid = processorUuid;
            return this;
        }

        public Builder pipelineUuid(final String pipelineUuid) {
            meta.pipelineUuid = pipelineUuid;
            return this;
        }

        public Builder parentDataId(final Long parentDataId) {
            meta.parentDataId = parentDataId;
            return this;
        }

        public Builder status(final Status status) {
            meta.status = status;
            return this;
        }

        public Builder statusMs(final Long statusMs) {
            meta.statusMs = statusMs;
            return this;
        }

        public Builder createMs(final long createMs) {
            meta.createMs = createMs;
            return this;
        }

        public Builder effectiveMs(final Long effectiveMs) {
            meta.effectiveMs = effectiveMs;
            return this;
        }

        public Meta build() {
            return meta;
        }
    }
}