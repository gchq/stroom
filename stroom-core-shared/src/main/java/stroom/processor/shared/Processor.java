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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasUuid;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@XmlRootElement(name = "parameters")
public class Processor implements HasAuditInfo, HasUuid {
    public static final String ENTITY_TYPE = "Processor";
    private static final String PIPELINE_STREAM_PROCESSOR_TASK_TYPE = "pipelineStreamProcessor";

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
    private String taskType;
    @JsonProperty
    private String pipelineUuid;
    @JsonProperty
    private boolean enabled;

    //TODO do we need pipelineName?
//    private String pipelineName;

    public Processor() {
        taskType = PIPELINE_STREAM_PROCESSOR_TASK_TYPE;
    }

    public Processor(final DocRef pipelineRef) {
        taskType = PIPELINE_STREAM_PROCESSOR_TASK_TYPE;
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
                     @JsonProperty("taskType") final String taskType,
                     @JsonProperty("pipelineUuid") final String pipelineUuid,
                     @JsonProperty("enabled") final boolean enabled) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.uuid = uuid;
        this.taskType = taskType;
        this.pipelineUuid = pipelineUuid;
        this.enabled = enabled;
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

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getPipelineUuid() {
        return pipelineUuid;
    }

    public void setPipelineUuid(final String pipelineUuid) {
        this.pipelineUuid = pipelineUuid;
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

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
                ", taskType='" + taskType + '\'' +
                ", pipelineUuid='" + pipelineUuid + '\'' +
                ", enabled=" + enabled +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Processor processor = (Processor) o;
        return Objects.equals(id, processor.id)  || Objects.equals(uuid, processor.uuid) ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
