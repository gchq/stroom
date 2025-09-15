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

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.docref.HasUuid;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ProcessorFilter implements HasAuditInfo, HasUuid, HasIntegerId {

    public static final String ENTITY_TYPE = "ProcessorFilter";
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 100;
    public static final int DEFAULT_PRIORITY = 10;
    /**
     * Zero means un-bounded
     */
    public static final int DEFAULT_MAX_PROCESSING_TASKS = 0;
    public static final int MIN_MAX_PROCESSING_TASKS = 0;
    public static final int MAX_MAX_PROCESSING_TASKS = Integer.MAX_VALUE;

    public static final Comparator<ProcessorFilter> HIGHEST_PRIORITY_FIRST_COMPARATOR = (o1, o2) -> {
        if (o1.getPriority() == o2.getPriority()) {
            // If priorities are the same then compare stream ids to
            // prioritise lower stream ids.
            if (o1.getProcessorFilterTracker().getMinMetaId() == o2.getProcessorFilterTracker()
                    .getMinMetaId()) {
                // If stream ids are the same then compare event ids to
                // prioritise lower event ids.
                return Long.compare(o1.getProcessorFilterTracker().getMinEventId(),
                        o2.getProcessorFilterTracker().getMinEventId());
            }

            return Long.compare(o1.getProcessorFilterTracker().getMinMetaId(),
                    o2.getProcessorFilterTracker().getMinMetaId());
        }

        // Highest Priority is important.
        return Integer.compare(o2.getPriority(), o1.getPriority());
    };

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
    @JsonProperty
    private QueryData queryData;
    @JsonProperty
    private ProcessorType processorType;
    @JsonProperty
    private String processorUuid;
    @JsonProperty
    private String pipelineUuid;
    @JsonProperty
    private String pipelineName;
    @JsonProperty
    private UserRef runAsUser;

    @JsonProperty
    private Processor processor;
    @JsonProperty
    private ProcessorFilterTracker processorFilterTracker;

    /**
     * The higher the number the higher the priority. So 1 is LOW, 10 is medium,
     * 20 is high.
     */
    @JsonProperty
    private int priority;
    @JsonProperty
    private int maxProcessingTasks;
    @JsonProperty
    private boolean reprocess;
    @JsonProperty
    private boolean enabled;
    @JsonProperty
    private boolean deleted;
    @JsonProperty
    private boolean export;
    @JsonProperty
    private Long minMetaCreateTimeMs;
    @JsonProperty
    private Long maxMetaCreateTimeMs;

    public ProcessorFilter() {
        priority = DEFAULT_PRIORITY;
    }

    @JsonCreator
    public ProcessorFilter(@JsonProperty("id") final Integer id,
                           @JsonProperty("version") final Integer version,
                           @JsonProperty("createTimeMs") final Long createTimeMs,
                           @JsonProperty("createUser") final String createUser,
                           @JsonProperty("updateTimeMs") final Long updateTimeMs,
                           @JsonProperty("updateUser") final String updateUser,
                           @JsonProperty("uuid") final String uuid,
                           @JsonProperty("queryData") final QueryData queryData,
                           @JsonProperty("processor") final Processor processor,
                           @JsonProperty("processorFilterTracker") final ProcessorFilterTracker processorFilterTracker,
                           @JsonProperty("priority") final int priority,
                           @JsonProperty("maxProcessingTasks") final int maxProcessingTasks,
                           @JsonProperty("reprocess") final boolean reprocess,
                           @JsonProperty("enabled") final boolean enabled,
                           @JsonProperty("deleted") final boolean deleted,
                           @JsonProperty("export") final boolean export,
                           @JsonProperty("processorType") final ProcessorType processorType,
                           @JsonProperty("processorUuid") final String processorUuid,
                           @JsonProperty("pipelineUuid") final String pipelineUuid,
                           @JsonProperty("pipelineName") final String pipelineName,
                           @JsonProperty("runAsUser") final UserRef runAsUser,
                           @JsonProperty("minMetaCreateTimeMs") final Long minMetaCreateTimeMs,
                           @JsonProperty("maxMetaCreateTimeMs") final Long maxMetaCreateTimeMs) {

        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.createUser = createUser;
        this.updateTimeMs = updateTimeMs;
        this.updateUser = updateUser;
        this.uuid = uuid;
        this.queryData = queryData;
        this.processor = processor;
        this.processorFilterTracker = processorFilterTracker;
        this.pipelineUuid = pipelineUuid;
        this.priority = priority > 0
                ? priority
                : DEFAULT_PRIORITY;
        this.maxProcessingTasks = maxProcessingTasks;
        this.reprocess = reprocess;
        this.enabled = enabled;
        this.deleted = deleted;
        this.export = export;
        this.processorType = processorType == null
                ? ProcessorType.PIPELINE
                : processorType;
        this.processorUuid = processorUuid;
        this.pipelineName = pipelineName;
        this.runAsUser = runAsUser;
        this.minMetaCreateTimeMs = minMetaCreateTimeMs;
        this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
    }

    @Override
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

    public int getPriority() {
        return priority;
    }

    public void setPriority(final int priority) {
        this.priority = priority;
    }

    /**
     * Zero means processing tasks are unbounded.
     */
    public int getMaxProcessingTasks() {
        return maxProcessingTasks;
    }

    /**
     * Zero means processing tasks are unbounded.
     */
    public void setMaxProcessingTasks(final int maxProcessingTasks) {
        this.maxProcessingTasks = maxProcessingTasks;
    }

    @JsonIgnore
    public boolean isProcessingTaskCountBounded() {
        return maxProcessingTasks > 0;
    }

    public boolean isHigherPriority(final ProcessorFilter other) {
        return HIGHEST_PRIORITY_FIRST_COMPARATOR.compare(this, other) < 0;
    }

    public Processor getProcessor() {
        return processor;
    }

    public ProcessorType getProcessorType() {
        if (processorType == null) {
            if (processor != null) {
                processorType = getProcessor().getProcessorType();
            } else {
                processorType = ProcessorType.PIPELINE;
            }
        }
        return processorType;
    }

    public String getProcessorUuid() {
        if (processorUuid == null && processor != null) {
            processorUuid = getProcessor().getUuid();
        }
        return processorUuid;
    }

    public String getPipelineUuid() {
        if (pipelineUuid == null) {
            final Processor processor = getProcessor();
            if (processor != null) {
                pipelineUuid = processor.getPipelineUuid();
            }
        }
        return pipelineUuid;
    }

    public void setPipelineUuid(final String pipelineUuid) {
        this.pipelineUuid = pipelineUuid;
        if (processor != null) {
            processor.setPipelineUuid(pipelineUuid);
        }
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public void setPipelineName(final String pipelineName) {
        this.pipelineName = pipelineName;
        if (processor != null) {
            processor.setPipelineName(pipelineName);
        }
    }

    @JsonIgnore
    public DocRef getPipeline() {
        if (processor != null) {
            return processor.getPipeline();
        }

        final String docType;
        if (ProcessorType.STREAMING_ANALYTIC.equals(processorType)) {
            docType = AnalyticRuleDoc.TYPE;
        } else {
            docType = PipelineDoc.TYPE;
        }
        return new DocRef(docType, pipelineUuid, pipelineName);
    }

    public void setRunAsUser(final UserRef runAsUser) {
        this.runAsUser = runAsUser;
    }

    public UserRef getRunAsUser() {
        return runAsUser;
    }

    public void setProcessor(final Processor processor) {
        this.processor = processor;

        if (processor != null) {
            processorType = processor.getProcessorType();
            processorUuid = processor.getUuid();
            pipelineUuid = processor.getPipelineUuid();
            pipelineName = processor.getPipelineName();
        }
    }

    public ProcessorFilterTracker getProcessorFilterTracker() {
        return processorFilterTracker;
    }

    public void setProcessorFilterTracker(final ProcessorFilterTracker processorFilterTracker) {
        this.processorFilterTracker = processorFilterTracker;
    }

    public QueryData getQueryData() {
        return queryData;
    }

    public void setQueryData(final QueryData queryData) {
        this.queryData = queryData;
    }

    public boolean isReprocess() {
        return reprocess;
    }

    public void setReprocess(final boolean reprocess) {
        this.reprocess = reprocess;
    }

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

    /**
     * @return true if the Processor Filter should be exported, false if not.
     */
    public boolean isExport() {
        return export;
    }

    /**
     * Sets whether the Processor Filter should be exported.
     */
    public void setExport(final boolean export) {
        this.export = export;
    }

    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs;
    }

    public void setMinMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
        this.minMetaCreateTimeMs = minMetaCreateTimeMs;
    }

    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs;
    }

    public void setMaxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
        this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProcessorFilter that = (ProcessorFilter) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @JsonIgnore
    public String getFilterInfo() {
        final StringBuilder sb = new StringBuilder();
        sb.append("filter id=");
        sb.append(id);

        if (uuid != null) {
            sb.append(", filter uuid=");
            sb.append(uuid);
        }
        if (processor != null && processor.getPipelineUuid() != null) {
            sb.append(", pipeline uuid=");
            sb.append(processor.getPipelineUuid());
        }
        if (pipelineName != null) {
            sb.append(", pipeline name=");
            sb.append(pipelineName);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "ProcessorFilter{" +
               "id=" + id +
               ", version=" + version +
               ", createTimeMs=" + createTimeMs +
               ", createUser='" + createUser + '\'' +
               ", updateTimeMs=" + updateTimeMs +
               ", updateUser='" + updateUser + '\'' +
               ", uuid='" + uuid + '\'' +
               ", queryData=" + queryData +
               ", processorType=" + processorType +
               ", processorUuid='" + processorUuid + '\'' +
               ", pipelineUuid='" + pipelineUuid + '\'' +
               ", pipelineName='" + pipelineName + '\'' +
               ", runAsUser='" + runAsUser + '\'' +
               ", processor=" + processor +
               ", processorFilterTracker=" + processorFilterTracker +
               ", priority=" + priority +
               ", maxProcessingTasks=" + maxProcessingTasks +
               ", reprocess=" + reprocess +
               ", enabled=" + enabled +
               ", deleted=" + deleted +
               ", export=" + export +
               ", minMetaCreateTimeMs=" + minMetaCreateTimeMs +
               ", maxMetaCreateTimeMs=" + maxMetaCreateTimeMs +
               '}';
    }

    /**
     * @return A {@link DocRef} with uuid and type.
     */
    public DocRef asDocRef() {
        // Doesn't really have a name
        return DocRef.builder()
                .type(ENTITY_TYPE)
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


    // --------------------------------------------------------------------------------


    public static class Builder {

        private Integer id;
        private Integer version;
        private Long createTimeMs;
        private String createUser;
        private Long updateTimeMs;
        private String updateUser;
        private String uuid;
        private QueryData queryData;
        private ProcessorType processorType;
        private String processorUuid;
        private String pipelineUuid;
        private String pipelineName;
        private UserRef runAsUser;
        private Processor processor;
        private ProcessorFilterTracker processorFilterTracker;

        /**
         * The higher the number the higher the priority. So 1 is LOW, 10 is medium,
         * 20 is high.
         */
        private int priority;
        private int maxProcessingTasks;
        private boolean reprocess;
        private boolean enabled;
        private boolean deleted;
        private boolean export;
        private Long minMetaCreateTimeMs;
        private Long maxMetaCreateTimeMs;

        public Builder() {

        }

        public Builder(final ProcessorFilter filter) {
            this.id = filter.id;
            this.version = filter.version;
            this.createTimeMs = filter.createTimeMs;
            this.createUser = filter.createUser;
            this.updateTimeMs = filter.updateTimeMs;
            this.updateUser = filter.updateUser;
            this.uuid = filter.uuid;
            this.queryData = filter.queryData != null
                    ? filter.queryData.copy().build()
                    : null;
            this.processorType = filter.processorType;
            this.processorUuid = filter.processorUuid;
            this.pipelineUuid = filter.pipelineUuid;
            this.pipelineName = filter.pipelineName;
            this.runAsUser = filter.runAsUser;
            this.processor = filter.processor;
            this.processorFilterTracker = filter.processorFilterTracker;
            this.priority = filter.priority;
            this.maxProcessingTasks = filter.maxProcessingTasks;
            this.reprocess = filter.reprocess;
            this.enabled = filter.enabled;
            this.deleted = filter.deleted;
            this.export = filter.export;
            this.minMetaCreateTimeMs = filter.minMetaCreateTimeMs;
            this.maxMetaCreateTimeMs = filter.maxMetaCreateTimeMs;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return this;
        }

        public Builder version(final Integer version) {
            this.version = version;
            return this;
        }

        public Builder createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return this;
        }

        public Builder createUser(final String createUser) {
            this.createUser = createUser;
            return this;
        }

        public Builder updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return this;
        }

        public Builder updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return this;
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder queryData(final QueryData queryData) {
            this.queryData = queryData;
            return this;
        }

        public Builder processorType(final ProcessorType processorType) {
            this.processorType = processorType;
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

        public Builder pipelineName(final String pipelineName) {
            this.pipelineName = pipelineName;
            return this;
        }

        public Builder runAsUser(final UserRef runAsUser) {
            this.runAsUser = runAsUser;
            return this;
        }

        public Builder processor(final Processor processor) {
            this.processor = processor;
            return this;
        }

        public Builder processorFilterTracker(final ProcessorFilterTracker processorFilterTracker) {
            this.processorFilterTracker = processorFilterTracker;
            return this;
        }

        public Builder priority(final int priority) {
            this.priority = priority;
            return this;
        }

        public Builder maxProcessingTasks(final int maxProcessingTasks) {
            this.maxProcessingTasks = maxProcessingTasks;
            return this;
        }

        public Builder reprocess(final boolean reprocess) {
            this.reprocess = reprocess;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder deleted(final boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        /**
         * Flag the Processor Filter to show whether it should be exported.
         */
        public Builder export(final boolean export) {
            this.export = export;
            return this;
        }

        public Builder minMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
            this.minMetaCreateTimeMs = minMetaCreateTimeMs;
            return this;
        }

        public Builder maxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
            this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
            return this;
        }

        public ProcessorFilter build() {
            return new ProcessorFilter(
                    id,
                    version,
                    createTimeMs,
                    createUser,
                    updateTimeMs,
                    updateUser,
                    uuid,
                    queryData,
                    processor,
                    processorFilterTracker,
                    priority,
                    maxProcessingTasks,
                    reprocess,
                    enabled,
                    deleted,
                    export,
                    processorType,
                    processorUuid,
                    pipelineUuid,
                    pipelineName,
                    runAsUser,
                    minMetaCreateTimeMs,
                    maxMetaCreateTimeMs);
        }
    }
}
