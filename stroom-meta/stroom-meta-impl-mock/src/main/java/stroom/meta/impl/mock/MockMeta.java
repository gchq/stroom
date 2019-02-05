package stroom.meta.impl.mock;

import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;

class MockMeta implements Meta {
    private long id;
    private String feedName;
    private String typeName;
    private String processorUuid;
    private String processorFilterUuid;
    private String pipelineUuid;
    private Long parentDataId;
    private Long processorTaskId;
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
    public String getProcessorUuid() {
        return processorUuid;
    }

    @Override
    public String getProcessorFilterUuid() {
        return processorFilterUuid;
    }

    @Override
    public String getPipelineUuid() {
        return pipelineUuid;
    }

    @Override
    public Long getParentMetaId() {
        return parentDataId;
    }

    @Override
    public Long getProcessTaskId() {
        return processorTaskId;
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
        private final MockMeta meta = new MockMeta();

        public Builder id(final long id) {
            meta.id = id;
            return this;
        }

        public Builder feedName(final String feedName) {
            meta.feedName = feedName;
            return this;
        }

        public Builder typeName(final String typeName) {
            meta.typeName = typeName;
            return this;
        }

        public Builder processorUuid(final String processorUuid) {
            meta.processorUuid = processorUuid;
            return this;
        }

        public Builder processorFilterUuid(final String processorFilterUuid) {
            meta.processorFilterUuid = processorFilterUuid;
            return this;
        }

        public Builder pipelineUuid(final String pipelineUuid) {
            meta.pipelineUuid = pipelineUuid;
            return this;
        }

        public Builder parentDataId(final Long parentDataId) {
            meta.parentDataId = parentDataId;
            return this;
        }

        public Builder processorTaskId(final Long processorTaskId) {
            meta.processorTaskId = processorTaskId;
            return this;
        }

        public Builder status(final Status status) {
            meta.status = status;
            return this;
        }

        public Builder statusMs(final Long statusMs) {
            meta.statusMs = statusMs;
            return this;
        }

        public Builder createMs(final long createMs) {
            meta.createMs = createMs;
            return this;
        }

        public Builder effectiveMs(final Long effectiveMs) {
            meta.effectiveMs = effectiveMs;
            return this;
        }

        public Meta build() {
            return meta;
        }
    }
}
