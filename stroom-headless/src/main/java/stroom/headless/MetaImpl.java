package stroom.headless;

import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;

import java.util.Objects;

class MetaImpl implements Meta {
    private long id;
    private String feedName;
    private String streamTypeName;
    private String pipelineUuid;
    private Long parentStreamId;
    private Long streamTaskId;
    private Integer streamProcessorId;
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
        return streamTypeName;
    }

    @Override
    public String getPipelineUuid() {
        return pipelineUuid;
    }

    @Override
    public Long getParentMetaId() {
        return parentStreamId;
    }

    @Override
    public Long getProcessTaskId() {
        return streamTaskId;
    }

    @Override
    public Integer getProcessorId() {
        return streamProcessorId;
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
        final MetaImpl stream = (MetaImpl) o;
        return id == stream.id;
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

        public Builder id(final long id) {
            meta.id = id;
            return this;
        }

        public Builder feedName(final String feedName) {
            meta.feedName = feedName;
            return this;
        }

        public Builder streamTypeName(final String streamTypeName) {
            meta.streamTypeName = streamTypeName;
            return this;
        }

        public Builder pipelineUuid(final String pipelineUuid) {
            meta.pipelineUuid = pipelineUuid;
            return this;
        }

        public Builder parentStreamId(final Long parentStreamId) {
            meta.parentStreamId = parentStreamId;
            return this;
        }

        public Builder streamTaskId(final Long streamTaskId) {
            meta.streamTaskId = streamTaskId;
            return this;
        }

        public Builder streamProcessorId(final Integer streamProcessorId) {
            meta.streamProcessorId = streamProcessorId;
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
