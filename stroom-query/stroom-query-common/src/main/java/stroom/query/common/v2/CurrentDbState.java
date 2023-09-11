package stroom.query.common.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@JsonInclude(Include.NON_NULL)
public class CurrentDbState {

    @JsonProperty
    private final Long streamId;
    @JsonProperty
    private final Long eventId;
    @JsonProperty
    private final Long lastEventTime;

    @JsonCreator
    public CurrentDbState(@JsonProperty("streamId") final Long streamId,
                          @JsonProperty("eventId") final Long eventId,
                          @JsonProperty("lastEventTime") final Long lastEventTime) {
        this.streamId = streamId;
        this.eventId = eventId;
        this.lastEventTime = lastEventTime;
    }

    public Long getStreamId() {
        return streamId;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getLastEventTime() {
        return lastEventTime;
    }

    @Override
    public String toString() {
        return "CurrentDbState{" +
                "streamId=" + streamId +
                ", eventId=" + eventId +
                ", lastEventTime=" + LocalDateTime.ofInstant(Instant.ofEpochMilli(lastEventTime), ZoneOffset.UTC) +
                '}';
    }
}
