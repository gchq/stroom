package stroom.streamstore.meta.api;

public class StreamProperties {
    private Long parentId;
    private String streamTypeName;
    private String feedName;
    private Integer streamProcessorId;
    private String pipelineUuid;
    private Long streamTaskId;
    private Long createMs;
    private Long effectiveMs;
    private Long statusMs;

    public Long getParentId() {
        return parentId;
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

        /**
         * This is a utility method to perform common parent association behaviour, e.g. setting the effective time from the parent.
         *
         * @param parent The parent to set.
         * @return The builder.
         */
        public Builder parent(final Stream parent) {
            // Set effective time from the parent stream.
            if (parent != null) {
                sp.parentId = parent.getId();
                if (sp.effectiveMs == null) {
                    if (parent.getEffectiveMs() != null) {
                        sp.effectiveMs = parent.getEffectiveMs();
                    } else {
                        sp.effectiveMs = parent.getCreateMs();
                    }
                }
            } else {
                sp.parentId = null;
            }

            return this;
        }

        public Builder parentId(final Long parentId) {
            sp.parentId = parentId;
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
            streamProperties.parentId = sp.parentId;
            streamProperties.streamTypeName = sp.streamTypeName;
            streamProperties.feedName = sp.feedName;
            streamProperties.streamProcessorId = sp.streamProcessorId;
            streamProperties.pipelineUuid = sp.pipelineUuid;
            streamProperties.streamTaskId = sp.streamTaskId;
            streamProperties.createMs = sp.createMs;
            streamProperties.effectiveMs = sp.effectiveMs;
            streamProperties.statusMs = sp.statusMs;

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
