package stroom.planb.impl.db.session;

import stroom.planb.impl.db.PlanBValue;
import stroom.query.language.functions.Val;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({"key", "start", "end"})
@JsonInclude(Include.NON_NULL)
public class Session implements PlanBValue {

    @JsonProperty
    private final Val key;
    @JsonProperty
    private final Instant start;
    @JsonProperty
    private final Instant end;

    @JsonCreator
    public Session(@JsonProperty("key") final Val key,
                   @JsonProperty("start") final Instant start,
                   @JsonProperty("end") final Instant end) {
        this.key = key;
        this.start = start;
        this.end = end;
    }

    public Val getKey() {
        return key;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Val key;
        private Instant start;
        private Instant end;

        private Builder() {
        }

        private Builder(final Session session) {
            this.key = session.key;
            this.start = session.start;
            this.end = session.end;
        }

        public Builder key(final Val key) {
            this.key = key;
            return this;
        }

        public Builder start(final Instant start) {
            this.start = start;
            return this;
        }

        public Builder end(final Instant end) {
            this.end = end;
            return this;
        }

        public Session build() {
            return new Session(key, start, end);
        }
    }
}
