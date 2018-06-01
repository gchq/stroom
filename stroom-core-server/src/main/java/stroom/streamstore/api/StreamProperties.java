package stroom.streamstore.api;

import stroom.streamstore.shared.Stream;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamTask;

public class StreamProperties {
    private Stream parent;
    private String streamTypeName;
    private String feedName;
    private StreamProcessor streamProcessor;
    private StreamTask streamTask;
    private Long createMs;
    private Long effectiveMs;
    private Long statusMs;

    public Stream getParent() {
        return parent;
    }

    public String getStreamTypeName() {
        return streamTypeName;
    }

    public String getFeedName() {
        return feedName;
    }

    public StreamProcessor getStreamProcessor() {
        return streamProcessor;
    }

    public StreamTask getStreamTask() {
        return streamTask;
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

        public Builder streamProcessor(final StreamProcessor streamProcessor) {
            sp.streamProcessor = streamProcessor;
            return this;
        }

        public Builder streamTask(final StreamTask streamTask) {
            sp.streamTask = streamTask;
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
            streamProperties.streamProcessor = sp.streamProcessor;
            streamProperties.streamTask = sp.streamTask;
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
