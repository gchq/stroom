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
    private final long streamId;
    @JsonProperty
    private final long eventId;
    @JsonProperty
    private final long lastEventTime;

    @JsonCreator
    public CurrentDbState(@JsonProperty("streamId") final long streamId,
                          @JsonProperty("eventId") final long eventId,
                          @JsonProperty("lastEventTime") final long lastEventTime) {
        this.streamId = streamId;
        this.eventId = eventId;
        this.lastEventTime = lastEventTime;
    }

    public long getStreamId() {
        return streamId;
    }

    public long getEventId() {
        return eventId;
    }

    public long getLastEventTime() {
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
