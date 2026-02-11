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
public class ProcessorFilter implements HasAuditInfoGetters, HasUuid, HasIntegerId {

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
    private final QueryData queryData;
    @JsonProperty
    private final ProcessorType processorType;
    @JsonProperty
    private final String processorUuid;
    @JsonProperty
    private final String pipelineUuid;
    @JsonProperty
    private final String pipelineName;
    @JsonProperty
    private final UserRef runAsUser;

    @JsonProperty
    private final Processor processor;
    @JsonProperty
    private final ProcessorFilterTracker processorFilterTracker;

    /**
     * The higher the number the higher the priority. So 1 is LOW, 10 is medium,
     * 20 is high.
     */
    @JsonProperty
    private final int priority;
    @JsonProperty
    private final int maxProcessingTasks;
    @JsonProperty
    private final boolean reprocess;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final boolean deleted;
    @JsonProperty
    private final boolean export;
    @JsonProperty
    private final Long minMetaCreateTimeMs;
    @JsonProperty
    private final Long maxMetaCreateTimeMs;

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

    public int getPriority() {
        return priority;
    }

    /**
     * Zero means processing tasks are unbounded.
     */
    public int getMaxProcessingTasks() {
        return maxProcessingTasks;
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
//        if (processorType == null) {
//            if (processor != null) {
//                processorType = getProcessor().getProcessorType();
//            } else {
//                processorType = ProcessorType.PIPELINE;
//            }
//        }
        return processorType;
    }

    public String getProcessorUuid() {
//        if (processorUuid == null && processor != null) {
//            processorUuid = getProcessor().getUuid();
//        }
        return processorUuid;
    }

    public String getPipelineUuid() {
//        if (pipelineUuid == null) {
//            final Processor processor = getProcessor();
//            if (processor != null) {
//                pipelineUuid = processor.getPipelineUuid();
//            }
//        }
        return pipelineUuid;
    }

    public String getPipelineName() {
        return pipelineName;
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

    public UserRef getRunAsUser() {
        return runAsUser;
    }

    public ProcessorFilterTracker getProcessorFilterTracker() {
        return processorFilterTracker;
    }

    public QueryData getQueryData() {
        return queryData;
    }

    public boolean isReprocess() {
        return reprocess;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /**
     * @return true if the Processor Filter should be exported, false if not.
     */
    public boolean isExport() {
        return export;
    }

    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs;
    }

    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs;
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
               ", runAsUser=" + runAsUser +
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


    // -------------------------------------------------------------------------


    public static class Builder extends AbstractHasAuditInfoBuilder<ProcessorFilter, Builder> {

        private Integer id;
        private Integer version;
        private String uuid;
        private QueryData queryData;
        private ProcessorType processorType = ProcessorType.PIPELINE;
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

        private Builder() {
        }

        private Builder(final ProcessorFilter filter) {
            super(filter);
            this.id = filter.id;
            this.version = filter.version;
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

        public Builder queryData(final QueryData queryData) {
            this.queryData = queryData;
            return self();
        }

        public Builder processorType(final ProcessorType processorType) {
            this.processorType = processorType;
            return self();
        }

        public Builder processorUuid(final String processorUuid) {
            this.processorUuid = processorUuid;
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

        public Builder runAsUser(final UserRef runAsUser) {
            this.runAsUser = runAsUser;
            return self();
        }

        public Builder processor(final Processor processor) {
            this.processor = processor;
            if (processor != null) {
                processorType = processor.getProcessorType();
                processorUuid = processor.getUuid();
                pipelineUuid = processor.getPipelineUuid();
                pipelineName = processor.getPipelineName();
            }
            return self();
        }

        public Builder processorFilterTracker(final ProcessorFilterTracker processorFilterTracker) {
            this.processorFilterTracker = processorFilterTracker;
            return self();
        }

        public Builder priority(final int priority) {
            this.priority = priority;
            return self();
        }

        public Builder maxProcessingTasks(final int maxProcessingTasks) {
            this.maxProcessingTasks = maxProcessingTasks;
            return self();
        }

        public Builder reprocess(final boolean reprocess) {
            this.reprocess = reprocess;
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

        /**
         * Flag the Processor Filter to show whether it should be exported.
         */
        public Builder export(final boolean export) {
            this.export = export;
            return self();
        }

        public Builder minMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
            this.minMetaCreateTimeMs = minMetaCreateTimeMs;
            return self();
        }

        public Builder maxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
            this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
            return self();
        }

        @Override
        protected Builder self() {
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
