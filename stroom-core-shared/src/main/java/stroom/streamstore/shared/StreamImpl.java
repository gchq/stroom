package stroom.streamstore.shared;

public class StreamImpl implements Stream {
    private final long id;
    private final String feedName;
    private final Long legacyFeedId;
    private final String streamTypeName;
    private final String pipelineName;
    private final String pipelineUuid;
    private final Long parentStreamId;
    private final Long streamTaskId;
    private final Long streamProcessorId;
    private final StreamStatus status;
    private final Long statusMs;
    private final long createMs;
    private final Long effectiveMs;

    StreamImpl(final long id,
               final String feedName,
               final Long legacyFeedId,
               final String streamTypeName,
               final String pipelineName,
               final String pipelineUuid,
               final Long parentStreamId,
               final Long streamTaskId,
               final Long streamProcessorId,
               final StreamStatus status,
               final Long statusMs,
               final long createMs,
               final Long effectiveMs) {
        this.id = id;
        this.feedName = feedName;
        this.legacyFeedId = legacyFeedId;
        this.streamTypeName = streamTypeName;
        this.pipelineName = pipelineName;
        this.pipelineUuid = pipelineUuid;
        this.parentStreamId = parentStreamId;
        this.streamTaskId = streamTaskId;
        this.streamProcessorId = streamProcessorId;
        this.status = status;
        this.statusMs = statusMs;
        this.createMs = createMs;
        this.effectiveMs = effectiveMs;
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
    public Long getLegacyFeedId() {
        return legacyFeedId;
    }

    @Override
    public String getStreamTypeName() {
        return streamTypeName;
    }

    @Override
    public String getPipelineName() {
        return pipelineName;
    }

    @Override
    public String getPipelineUuid() {
        return pipelineUuid;
    }

    @Override
    public Long getParentStreamId() {
        return parentStreamId;
    }

    @Override
    public Long getStreamTaskId() {
        return streamTaskId;
    }

    @Override
    public Long getStreamProcessorId() {
        return streamProcessorId;
    }

    @Override
    public StreamStatus getStatus() {
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
}
