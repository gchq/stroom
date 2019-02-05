package stroom.meta.impl.db;

import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;

import java.util.Objects;

class MetaImpl implements Meta {
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

    MetaImpl() {
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
    public Long getParentMetaId() {
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
        final MetaImpl meta = (MetaImpl) o;
        return id == meta.id;
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
        private final MetaImpl meta = new MetaImpl();

        Builder() {
        }

        Builder(final Meta meta) {
            id(meta.getId());
            feedName(meta.getFeedName());
            typeName(meta.getTypeName());
            pipelineUuid(meta.getPipelineUuid());
            parentDataId(meta.getParentMetaId());
            processTaskId(meta.getProcessTaskId());
            processorId(meta.getProcessorId());
            status(meta.getStatus());
            statusMs(meta.getStatusMs());
            createMs(meta.getCreateMs());
            effectiveMs(meta.getEffectiveMs());
        }

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

        public Builder pipelineUuid(final String pipelineUuid) {
            meta.pipelineUuid = pipelineUuid;
            return this;
        }

        public Builder parentDataId(final Long parentDataId) {
            meta.parentDataId = parentDataId;
            return this;
        }

        public Builder processTaskId(final Long processTaskId) {
            meta.processTaskId = processTaskId;
            return this;
        }

        public Builder processorId(final Integer processorId) {
            meta.processorId = processorId;
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
