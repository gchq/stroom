package stroom.meta.impl.mock;

import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;

class MockMeta implements Meta {
    private long id;
    private String feedName;
    private String typeName;
    private String pipelineUuid;
    private Long parentDataId;
    private Long processorTaskId;
    private Integer processorId;
    Status status;
    Long statusMs;
    private long createMs;
    private Long effectiveMs;

    MockMeta() {
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
    public Status getStatus() {
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
        private final MockMeta data = new MockMeta();

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

        public Builder status(final Status status) {
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

        public Meta build() {
            return data;
        }
    }
}
