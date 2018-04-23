package stroom.streamtask.resource;

class StreamTask {

    // Most important data, probably
    private String filterName;
    private String pipelineName;
    private Long pipelineId;
    private Long  trackerMs;
    private Integer trackerPercent;
    private String lastPollAge;
    private Integer taskCount;
    private Integer priority; //TODO: Updatable?
    private Integer streamCount;
    private Integer eventCount;
    private String status;
    private Boolean enabled; //TODO: Updatable?

    // Supporting data
    private Long filterId;
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

    public Long getFilterId() {
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

    public static final class StreamTaskBuilder {
        // Most important data, probably
        private String filterName;
        private String pipelineName;
        private Long pipelineId;
        private Long  trackerMs;
        private Integer trackerPercent;
        private String lastPollAge;
        private Integer taskCount;
        private Integer priority; //TODO: Updatable?
        private Integer streamCount;
        private Integer eventCount;
        private String status;
        private Boolean enabled; //TODO: Updatable?
        // Supporting data
        private Long filterId;
        private String createUser;
        private Long createdOn;
        private String updateUser;
        private Long updatedOn;
        private Long minStreamId;
        private Long minEventId;

        private StreamTaskBuilder() {
        }

        public static StreamTaskBuilder aStreamTask() {
            return new StreamTaskBuilder();
        }

        public StreamTaskBuilder withFilterName(String filterName) {
            this.filterName = filterName;
            return this;
        }

        public StreamTaskBuilder withPipelineName(String pipelineName) {
            this.pipelineName = pipelineName;
            return this;
        }

        public StreamTaskBuilder withPipelineId(Long pipelineId) {
            this.pipelineId = pipelineId;
            return this;
        }

        public StreamTaskBuilder withTrackerMs(Long trackerMs) {
            this.trackerMs = trackerMs;
            return this;
        }

        public StreamTaskBuilder withTrackerPercent(Integer trackerPercent) {
            this.trackerPercent = trackerPercent;
            return this;
        }

        public StreamTaskBuilder withLastPollAge(String lastPollAge) {
            this.lastPollAge = lastPollAge;
            return this;
        }

        public StreamTaskBuilder withTaskCount(Integer taskCount) {
            this.taskCount = taskCount;
            return this;
        }

        public StreamTaskBuilder withPriority(Integer priority) {
            this.priority = priority;
            return this;
        }

        public StreamTaskBuilder withStreamCount(Integer streamCount) {
            this.streamCount = streamCount;
            return this;
        }

        public StreamTaskBuilder withEventCount(Integer eventCount) {
            this.eventCount = eventCount;
            return this;
        }

        public StreamTaskBuilder withStatus(String status) {
            this.status = status;
            return this;
        }

        public StreamTaskBuilder withEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public StreamTaskBuilder withFilterId(Long filterId) {
            this.filterId = filterId;
            return this;
        }

        public StreamTaskBuilder withCreateUser(String createUser) {
            this.createUser = createUser;
            return this;
        }

        public StreamTaskBuilder withCreatedOn(Long createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public StreamTaskBuilder withUpdateUser(String updateUser) {
            this.updateUser = updateUser;
            return this;
        }

        public StreamTaskBuilder withUpdatedOn(Long updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }

        public StreamTaskBuilder withMinStreamId(Long minStreamId) {
            this.minStreamId = minStreamId;
            return this;
        }

        public StreamTaskBuilder withMinEventId(Long minEventId) {
            this.minEventId = minEventId;
            return this;
        }

        public StreamTask build() {
            StreamTask streamTask = new StreamTask();
            streamTask.streamCount = this.streamCount;
            streamTask.lastPollAge = this.lastPollAge;
            streamTask.updateUser = this.updateUser;
            streamTask.createUser = this.createUser;
            streamTask.eventCount = this.eventCount;
            streamTask.updatedOn = this.updatedOn;
            streamTask.pipelineId = this.pipelineId;
            streamTask.createdOn = this.createdOn;
            streamTask.status = this.status;
            streamTask.minStreamId = this.minStreamId;
            streamTask.filterId = this.filterId;
            streamTask.filterName = this.filterName;
            streamTask.pipelineName = this.pipelineName;
            streamTask.enabled = this.enabled;
            streamTask.trackerPercent = this.trackerPercent;
            streamTask.minEventId = this.minEventId;
            streamTask.trackerMs = this.trackerMs;
            streamTask.taskCount = this.taskCount;
            streamTask.priority = this.priority;
            return streamTask;
        }
    }
}
