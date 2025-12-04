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
import stroom.util.shared.HasAuditInfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Processor implements HasAuditInfo, HasUuid {

    public static final String ENTITY_TYPE = "Processor";

    // standard id, OCC and audit fields
    @JsonProperty
    private Integer id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private Long updateTimeMs;
    @JsonProperty
    private String updateUser;
    @JsonProperty
    private String uuid;

    // Only One type for the moment
    @JsonProperty
    private ProcessorType processorType;
    @JsonProperty
    private String pipelineUuid;
    @JsonProperty
    private String pipelineName;
    @JsonProperty
    private boolean enabled;
    @JsonProperty
    private boolean deleted;

    public Processor() {
        processorType = ProcessorType.PIPELINE;
    }

    public Processor(final DocRef pipelineRef) {
        processorType = ProcessorType.PIPELINE;
        this.pipelineUuid = pipelineRef.getUuid();
    }

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

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    public ProcessorType getProcessorType() {
        return processorType;
    }

    public void setProcessorType(final ProcessorType processorType) {
        this.processorType = processorType;
    }

    public String getPipelineUuid() {
        return pipelineUuid;
    }

    public void setPipelineUuid(final String uuid) {
        this.pipelineUuid = uuid;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public void setPipelineName(final String pipelineName) {
        this.pipelineName = pipelineName;
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

    @JsonIgnore
    public void setPipeline(final DocRef pipelineDocRef) {
        this.pipelineUuid = pipelineDocRef.getUuid();
        this.pipelineName = pipelineDocRef.getName();
    }

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

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(final boolean deleted) {
        this.deleted = deleted;
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
}
