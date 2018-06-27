package stroom.data.meta.impl.mock;

import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataStatus;

class MockData implements Data {
    private long id;
    private String feedName;
    private String typeName;
    private String pipelineUuid;
    private Long parentDataId;
    private Long processorTaskId;
    private Integer processorId;
    DataStatus status;
    Long statusMs;
    private long createMs;
    private Long effectiveMs;

    MockData() {
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getFeedName() {
        return feedName;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }

    @Override
    public String getPipelineUuid() {
        return pipelineUuid;
    }

    @Override
    public Long getParentDataId() {
        return parentDataId;
    }

    @Override
    public Long getProcessTaskId() {
        return processorTaskId;
    }

    @Override
    public Integer getProcessorId() {
        return processorId;
    }

    @Override
    public DataStatus getStatus() {
        return status;
    }

    @Override
    public Long getStatusMs() {
        return statusMs;
    }

    @Override
    public long getCreateMs() {
        return createMs;
    }

    @Override
    public Long getEffectiveMs() {
        return effectiveMs;
    }

    public static class Builder {
        private final MockData data = new MockData();

        public Builder id(final long id) {
            data.id = id;
            return this;
        }

        public Builder feedName(final String feedName) {
            data.feedName = feedName;
            return this;
        }

        public Builder typeName(final String typeName) {
            data.typeName = typeName;
            return this;
        }

        public Builder pipelineUuid(final String pipelineUuid) {
            data.pipelineUuid = pipelineUuid;
            return this;
        }

        public Builder parentDataId(final Long parentDataId) {
            data.parentDataId = parentDataId;
            return this;
        }

        public Builder processorTaskId(final Long processorTaskId) {
            data.processorTaskId = processorTaskId;
            return this;
        }

        public Builder processorId(final Integer processorId) {
            data.processorId = processorId;
            return this;
        }

        public Builder status(final DataStatus status) {
            data.status = status;
            return this;
        }

        public Builder statusMs(final Long statusMs) {
            data.statusMs = statusMs;
            return this;
        }

        public Builder createMs(final long createMs) {
            data.createMs = createMs;
            return this;
        }

        public Builder effectiveMs(final Long effectiveMs) {
            data.effectiveMs = effectiveMs;
            return this;
        }

        public Data build() {
            return data;
        }
    }
}
