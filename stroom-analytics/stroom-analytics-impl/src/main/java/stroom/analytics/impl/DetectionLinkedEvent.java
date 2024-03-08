package stroom.analytics.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"stroom", "streamId", "eventId"})
@JsonInclude(Include.NON_NULL)
public class DetectionLinkedEvent {

    @JsonProperty
    private final String stroom;
    @JsonProperty
    private final Long streamId;
    @JsonProperty
    private final Long eventId;

    @JsonCreator
    public DetectionLinkedEvent(@JsonProperty("stroom") final String stroom,
                                @JsonProperty("streamId") final Long streamId,
                                @JsonProperty("eventId") final Long eventId) {
        this.stroom = stroom;
        this.streamId = streamId;
        this.eventId = eventId;
    }

    public String getStroom() {
        return stroom;
    }

    public Long getStreamId() {
        return streamId;
    }

    public Long getEventId() {
        return eventId;
    }
}
