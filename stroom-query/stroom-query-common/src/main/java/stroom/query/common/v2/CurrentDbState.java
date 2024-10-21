package stroom.query.common.v2;

import stroom.util.shared.GwtNullSafe;

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
    private final Long eventId;
    @JsonProperty
    private final Long lastEventTime;

    @JsonCreator
    public CurrentDbState(@JsonProperty("streamId") final long streamId,
                          @JsonProperty("eventId") final Long eventId,
                          @JsonProperty("lastEventTime") final Long lastEventTime) {
        this.streamId = streamId;
        this.eventId = eventId;
        this.lastEventTime = lastEventTime;
    }

    public long getStreamId() {
        return streamId;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getLastEventTime() {
        return lastEventTime;
    }

    public boolean hasLastEventTime() {
        return lastEventTime != null;
    }

    @Override
    public String toString() {
        return "CurrentDbState{" +
                "streamId=" + streamId +
                ", eventId=" + eventId +
                ", lastEventTime=" + LocalDateTime.ofInstant(Instant.ofEpochMilli(lastEventTime), ZoneOffset.UTC) +
                '}';
    }

    /**
     * Merges existingCurrentDbState with this to create a new state.
     */
    public CurrentDbState mergeExisting(final CurrentDbState existingCurrentDbState) {
        final Long lastEventTime = GwtNullSafe.requireNonNullElseGet(
                this.lastEventTime,
                () -> GwtNullSafe.get(existingCurrentDbState, CurrentDbState::getLastEventTime));

        return new CurrentDbState(
                streamId,
                eventId,
                lastEventTime);
    }
}
