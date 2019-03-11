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

import stroom.docref.DocRef;
import stroom.docref.SharedObject;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasUuid;

import java.util.Objects;

public class Processor implements HasAuditInfo, HasUuid, SharedObject {
//    public static final String ENTITY_TYPE = "StreamProcessor";

    private static final String PIPELINE_STREAM_PROCESSOR_TASK_TYPE = "pipelineStreamProcessor";
    private static final long serialVersionUID = -958099873937223257L;

    // standard id, OCC and audit fields
    private Integer id;
    private Integer version;
    private Long createTimeMs;
    private String createUser;
    private Long updateTimeMs;
    private String updateUser;
    private String uuid;

    // Only One type for the moment
    private String taskType = PIPELINE_STREAM_PROCESSOR_TASK_TYPE;
    private String pipelineUuid;
    private boolean enabled;

    //TODO do we need pipelineName?
//    private String pipelineName;

    public Processor() {
        // Default constructor necessary for GWT serialisation.
    }

    public Processor(final DocRef pipelineRef) {
        this.pipelineUuid = pipelineRef.getUuid();
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Processor processor = (Processor) o;
        return enabled == processor.enabled &&
                Objects.equals(id, processor.id) &&
                Objects.equals(version, processor.version) &&
                Objects.equals(createTimeMs, processor.createTimeMs) &&
                Objects.equals(createUser, processor.createUser) &&
                Objects.equals(updateTimeMs, processor.updateTimeMs) &&
                Objects.equals(updateUser, processor.updateUser) &&
                Objects.equals(taskType, processor.taskType) &&
                Objects.equals(pipelineUuid, processor.pipelineUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, createTimeMs, createUser, updateTimeMs, updateUser, taskType, pipelineUuid, enabled);
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
}
