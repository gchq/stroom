package stroom.data.meta.impl.db;

import stroom.data.meta.shared.Meta;
import stroom.data.meta.shared.Status;

import java.util.Objects;

class DataImpl implements Meta {
    private long id;
    private String feedName;
    private String typeName;
    private String pipelineUuid;
    private Long parentDataId;
    private Long processTaskId;
    private Integer processorId;
    private Status status;
    private Long statusMs;
    private long createMs;
    private Long effectiveMs;

    DataImpl() {
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
        return processTaskId;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DataImpl data = (DataImpl) o;
        return id == data.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.valueOf(id);
    }

    public static class Builder {
        private final DataImpl data = new DataImpl();

        Builder() {
        }

        Builder(final Meta data) {
            id(data.getId());
            feedName(data.getFeedName());
            typeName(data.getTypeName());
            pipelineUuid(data.getPipelineUuid());
            parentDataId(data.getParentDataId());
            processTaskId(data.getProcessTaskId());
            processorId(data.getProcessorId());
            status(data.getStatus());
            statusMs(data.getStatusMs());
            createMs(data.getCreateMs());
            effectiveMs(data.getEffectiveMs());
        }

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

        public Builder processTaskId(final Long processTaskId) {
            data.processTaskId = processTaskId;
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
