package stroom.streamstore.meta.api;

public class StreamProperties {
    private Stream parent;
    private String streamTypeName;
    private String feedName;
    private Integer streamProcessorId;
    private String pipelineUuid;
    private Long streamTaskId;
    private Long createMs;
    private Long effectiveMs;
    private Long statusMs;

    public Stream getParent() {
        return parent;
    }

    public Long getParentId() {
        if (parent == null) {
            return null;
        }
        return parent.getId();
    }

    public String getStreamTypeName() {
        return streamTypeName;
    }

    public String getFeedName() {
        return feedName;
    }

    public Integer getStreamProcessorId() {
        return streamProcessorId;
    }

    public String getPipelineUuid() {
        return pipelineUuid;
    }

    public Long getStreamTaskId() {
        return streamTaskId;
    }

    public Long getCreateMs() {
        return createMs;
    }

    public Long getEffectiveMs() {
        return effectiveMs;
    }

    public Long getStatusMs() {
        return statusMs;
    }

    public static class Builder {
        private StreamProperties sp = new StreamProperties();

        public Builder parent(final Stream parent) {
            sp.parent = parent;
            return this;
        }

        public Builder streamTypeName(final String streamTypeName) {
            sp.streamTypeName = streamTypeName;
            return this;
        }

        public Builder feedName(final String feedName) {
            sp.feedName = feedName;
            return this;
        }

        public Builder streamProcessorId(final Integer streamProcessorId) {
            sp.streamProcessorId = streamProcessorId;
            return this;
        }

        public Builder pipelineUuid(final String pipelineUuid) {
            sp.pipelineUuid = pipelineUuid;
            return this;
        }

        public Builder streamTaskId(final Long streamTaskId) {
            sp.streamTaskId = streamTaskId;
            return this;
        }

        public Builder createMs(final Long createMs) {
            sp.createMs = createMs;
            return this;
        }

        public Builder effectiveMs(final Long effectiveMs) {
            sp.effectiveMs = effectiveMs;
            return this;
        }

        public Builder statusMs(final Long statusMs) {
            sp.statusMs = statusMs;
            return this;
        }

        public StreamProperties build() {
            final StreamProperties streamProperties = new StreamProperties();
            streamProperties.parent = sp.parent;
            streamProperties.streamTypeName = sp.streamTypeName;
            streamProperties.feedName = sp.feedName;
            streamProperties.streamProcessorId = sp.streamProcessorId;
            streamProperties.pipelineUuid = sp.pipelineUuid;
            streamProperties.streamTaskId = sp.streamTaskId;
            streamProperties.createMs = sp.createMs;
            streamProperties.effectiveMs = sp.effectiveMs;
            streamProperties.statusMs = sp.statusMs;

            // Set effective time from the parent stream.
            if (streamProperties.getParent() != null) {
                if (streamProperties.getParent().getEffectiveMs() != null) {
                    streamProperties.effectiveMs = streamProperties.getParent().getEffectiveMs();
                } else {
                    streamProperties.effectiveMs = streamProperties.getParent().getCreateMs();
                }
            }

            // When were we created
            if (streamProperties.createMs == null) {
                streamProperties.createMs = System.currentTimeMillis();
            }

            // Ensure an effective time
            if (streamProperties.effectiveMs == null) {
                streamProperties.effectiveMs = streamProperties.createMs;
            }

            return streamProperties;
        }
    }
}
