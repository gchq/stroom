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

package stroom.processor.impl;

import stroom.processor.shared.QueryData;

class StreamTask {

    // Most important data, probably
    private String filterName;
    private String pipelineName;
    private Long pipelineId;
    private Long trackerMs;
    private Integer trackerPercent;
    private String lastPollAge;
    private Integer taskCount;
    private Integer priority; //TODO: Updatable?
    private Integer streamCount;
    private Integer eventCount;
    private String status;
    private Boolean enabled; //TODO: Updatable?
    private QueryData filter;

    // Supporting data
    private Integer filterId;
    private String createUser;
    private Long createdOn;
    private String updateUser;
    private Long updatedOn;
    private Long minStreamId;
    private Long minEventId;

    public String getPipelineName() {
        return pipelineName;
    }

    public Long getPipelineId() {
        return pipelineId;
    }

    public Long getTrackerMs() {
        return trackerMs;
    }

    public Integer getTrackerPercent() {
        return trackerPercent;
    }

    public String getLastPollAge() {
        return lastPollAge;
    }

    public Integer getTaskCount() {
        return taskCount;
    }

    public Integer getPriority() {
        return priority;
    }

    public Integer getStreamCount() {
        return streamCount;
    }

    public Integer getEventCount() {
        return eventCount;
    }

    public String getStatus() {
        return status;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Integer getFilterId() {
        return filterId;
    }

    public String getCreateUser() {
        return createUser;
    }

    public Long getCreatedOn() {
        return createdOn;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public Long getUpdatedOn() {
        return updatedOn;
    }

    public Long getMinStreamId() {
        return minStreamId;
    }

    public Long getMinEventId() {
        return minEventId;
    }

    public String getFilterName() {
        return filterName;
    }

    public QueryData getFilter() {
        return filter;
    }

    public static StreamTaskBuilder builder() {
        return new StreamTaskBuilder();
    }

    public static final class StreamTaskBuilder {

        // Most important data, probably
        private String filterName;
        private String pipelineName;
        private Long pipelineId;
        private Long trackerMs;
        private Integer trackerPercent;
        private String lastPollAge;
        private Integer taskCount;
        private Integer priority; //TODO: Updatable?
        private Integer streamCount;
        private Integer eventCount;
        private String status;
        private Boolean enabled; //TODO: Updatable?
        private QueryData filter;
        // Supporting data
        private Integer filterId;
        private String createUser;
        private Long createdOn;
        private String updateUser;
        private Long updatedOn;
        private Long minStreamId;
        private Long minEventId;

        private StreamTaskBuilder() {
        }

        public StreamTaskBuilder withFilterName(final String filterName) {
            this.filterName = filterName;
            return this;
        }

        public StreamTaskBuilder withPipelineName(final String pipelineName) {
            this.pipelineName = pipelineName;
            return this;
        }

        public StreamTaskBuilder withPipelineId(final Long pipelineId) {
            this.pipelineId = pipelineId;
            return this;
        }

        public StreamTaskBuilder withTrackerMs(final Long trackerMs) {
            this.trackerMs = trackerMs;
            return this;
        }

        public StreamTaskBuilder withTrackerPercent(final Integer trackerPercent) {
            this.trackerPercent = trackerPercent;
            return this;
        }

        public StreamTaskBuilder withLastPollAge(final String lastPollAge) {
            this.lastPollAge = lastPollAge;
            return this;
        }

        public StreamTaskBuilder withTaskCount(final Integer taskCount) {
            this.taskCount = taskCount;
            return this;
        }

        public StreamTaskBuilder withPriority(final Integer priority) {
            this.priority = priority;
            return this;
        }

        public StreamTaskBuilder withStreamCount(final Integer streamCount) {
            this.streamCount = streamCount;
            return this;
        }

        public StreamTaskBuilder withEventCount(final Integer eventCount) {
            this.eventCount = eventCount;
            return this;
        }

        public StreamTaskBuilder withStatus(final String status) {
            this.status = status;
            return this;
        }

        public StreamTaskBuilder withEnabled(final Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public StreamTaskBuilder withFilter(final QueryData filter) {
            this.filter = filter;
            return this;
        }

        public StreamTaskBuilder withFilterId(final Integer filterId) {
            this.filterId = filterId;
            return this;
        }

        public StreamTaskBuilder withCreateUser(final String createUser) {
            this.createUser = createUser;
            return this;
        }

        public StreamTaskBuilder withCreatedOn(final Long createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public StreamTaskBuilder withUpdateUser(final String updateUser) {
            this.updateUser = updateUser;
            return this;
        }

        public StreamTaskBuilder withUpdatedOn(final Long updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }

        public StreamTaskBuilder withMinStreamId(final Long minStreamId) {
            this.minStreamId = minStreamId;
            return this;
        }

        public StreamTaskBuilder withMinEventId(final Long minEventId) {
            this.minEventId = minEventId;
            return this;
        }

        public StreamTask build() {
            final StreamTask streamTask = new StreamTask();
            streamTask.priority = this.priority;
            streamTask.trackerPercent = this.trackerPercent;
            streamTask.minEventId = this.minEventId;
            streamTask.pipelineName = this.pipelineName;
            streamTask.pipelineId = this.pipelineId;
            streamTask.minStreamId = this.minStreamId;
            streamTask.status = this.status;
            streamTask.createUser = this.createUser;
            streamTask.eventCount = this.eventCount;
            streamTask.updatedOn = this.updatedOn;
            streamTask.lastPollAge = this.lastPollAge;
            streamTask.streamCount = this.streamCount;
            streamTask.taskCount = this.taskCount;
            streamTask.enabled = this.enabled;
            streamTask.trackerMs = this.trackerMs;
            streamTask.updateUser = this.updateUser;
            streamTask.createdOn = this.createdOn;
            streamTask.filter = this.filter;
            streamTask.filterId = this.filterId;
            streamTask.filterName = this.filterName;
            return streamTask;
        }
    }
}
