package stroom.planb.impl.db;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@JsonPropertyOrder({"key", "start", "end"})
@JsonInclude(Include.NON_NULL)
public class Session implements PlanBValue {

    @JsonProperty
    private final byte[] key;
    @JsonProperty
    private final long start;
    @JsonProperty
    private final long end;

    @JsonCreator
    public Session(@JsonProperty("key") final byte[] key,
                   @JsonProperty("start") final long start,
                   @JsonProperty("end") final long end) {
        this.key = key;
        this.start = start;
        this.end = end;
    }

    public byte[] getKey() {
        return key;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private byte[] key;
        private long start;
        private long end;

        private Builder() {
        }

        private Builder(final Session session) {
            this.key = session.key;
            this.start = session.start;
            this.end = session.end;
        }

        public Builder key(final byte[] key) {
            this.key = key;
            return this;
        }

        public Builder key(final String key) {
            this.key = key.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        public Builder start(final long start) {
            this.start = start;
            return this;
        }

        public Builder start(final Instant start) {
            this.start = start.toEpochMilli();
            return this;
        }

        public Builder end(final long end) {
            this.end = end;
            return this;
        }

        public Builder end(final Instant end) {
            this.end = end.toEpochMilli();
            return this;
        }

        public Session build() {
            return new Session(key, start, end);
        }
    }
}
