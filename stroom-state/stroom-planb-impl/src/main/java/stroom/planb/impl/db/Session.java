package stroom.planb.impl.db;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public record Session(
        byte[] key,
        long start,
        long end) implements PlanBValue {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private byte[] key;
        private long start;
        private long end;

        public Builder() {
        }

        public Builder(final Session session) {
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
