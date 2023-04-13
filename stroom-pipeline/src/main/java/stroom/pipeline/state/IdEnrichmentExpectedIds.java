package stroom.pipeline.state;

import stroom.util.pipeline.scope.PipelineScoped;

@PipelineScoped
public class IdEnrichmentExpectedIds {

    private Long streamId;
    private long[] eventIds;

    public Long getStreamId() {
        return streamId;
    }

    public void setStreamId(final Long streamId) {
        this.streamId = streamId;
    }

    public long[] getEventIds() {
        return eventIds;
    }

    public void setEventIds(final long[] eventIds) {
        this.eventIds = eventIds;
    }
}
