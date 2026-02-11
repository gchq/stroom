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

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.docref.HasUuid;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.AbstractHasAuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Processor implements HasAuditInfoGetters, HasUuid {

    public static final String ENTITY_TYPE = "Processor";

    @JsonProperty
    private final Integer id;
    @JsonProperty
    private final Integer version;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String updateUser;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final ProcessorType processorType;
    @JsonProperty
    private final String pipelineUuid;
    @JsonProperty
    private final String pipelineName;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final boolean deleted;

    @JsonCreator
    public Processor(@JsonProperty("id") final Integer id,
                     @JsonProperty("version") final Integer version,
                     @JsonProperty("createTimeMs") final Long createTimeMs,
                     @JsonProperty("createUser") final String createUser,
                     @JsonProperty("updateTimeMs") final Long updateTimeMs,
                     @JsonProperty("updateUser") final String updateUser,
                     @JsonProperty("uuid") final String uuid,
                     @JsonProperty("processorType") final ProcessorType processorType,
                     @JsonProperty("pipelineUuid") final String pipelineUuid,
                     @JsonProperty("pipelineName") final String pipelineName,
                     @JsonProperty("enabled") final boolean enabled,
                     @JsonProperty("deleted") final boolean deleted) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.uuid = uuid;
        this.processorType = processorType;
        this.pipelineUuid = pipelineUuid;
        this.pipelineName = pipelineName;
        this.enabled = enabled;
        this.deleted = deleted;
    }

    public Integer getId() {
        return id;
    }

    public Integer getVersion() {
        return version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public ProcessorType getProcessorType() {
        return processorType;
    }

    public String getPipelineUuid() {
        return pipelineUuid;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    @JsonIgnore
    public DocRef getPipeline() {
        final String docType;
        if (ProcessorType.STREAMING_ANALYTIC.equals(processorType)) {
            docType = AnalyticRuleDoc.TYPE;
        } else {
            docType = PipelineDoc.TYPE;
        }
        return new DocRef(docType, pipelineUuid, pipelineName);
    }

//    @JsonIgnore
//    public void setPipeline(final DocRef pipelineDocRef) {
//        this.pipelineUuid = pipelineDocRef.getUuid();
//        this.pipelineName = pipelineDocRef.getName();
//    }

//    public String getPipelineName() {
//        return pipelineName;
//    }
//
//    public void setPipelineName(final String pipelineName) {
//        this.pipelineName = pipelineName;
//    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public String toString() {
        return "Processor{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", processorType='" + processorType + '\'' +
               ", pipelineUuid='" + pipelineUuid + '\'' +
               ", enabled=" + enabled +
               ", deleted=" + deleted +
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
        final Processor processor = (Processor) o;
        return Objects.equals(id, processor.id) || Objects.equals(uuid, processor.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(ENTITY_TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(ENTITY_TYPE);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractHasAuditInfoBuilder<Processor, Builder> {

        private Integer id;
        private Integer version;
        private String uuid;
        private ProcessorType processorType = ProcessorType.PIPELINE;
        private String pipelineUuid;
        private String pipelineName;
        private boolean enabled = false;
        private boolean deleted = false;

        private Builder() {
        }

        private Builder(final Processor processor) {
            super(processor);
            this.id = processor.id;
            this.version = processor.version;
            this.uuid = processor.uuid;
            this.processorType = processor.processorType;
            this.pipelineUuid = processor.pipelineUuid;
            this.pipelineName = processor.pipelineName;
            this.enabled = processor.enabled;
            this.deleted = processor.deleted;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return self();
        }

        public Builder version(final Integer version) {
            this.version = version;
            return self();
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return self();
        }

        public Builder processorType(final ProcessorType processorType) {
            this.processorType = processorType;
            return self();
        }

        public Builder pipelineUuid(final String pipelineUuid) {
            this.pipelineUuid = pipelineUuid;
            return self();
        }

        public Builder pipelineName(final String pipelineName) {
            this.pipelineName = pipelineName;
            return self();
        }

        public Builder pipeline(final DocRef pipelineDocRef) {
            this.pipelineUuid = pipelineDocRef.getUuid();
            this.pipelineName = pipelineDocRef.getName();
            return self();
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        public Builder deleted(final boolean deleted) {
            this.deleted = deleted;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Processor build() {
            return new Processor(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    uuid,
                    processorType,
                    pipelineUuid,
                    pipelineName,
                    enabled,
                    deleted);
        }
    }
}
