package stroom.planb.impl.io;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public record SessionRequest(byte[] name, long time) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private byte[] name;
        private long time;

        public Builder() {
        }

        public Builder(final SessionRequest request) {
            this.name = request.name;
            this.time = request.time;
        }

        public Builder name(final byte[] name) {
            this.name = name;
            return this;
        }

        public Builder name(final String name) {
            this.name = name.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        public Builder time(final long time) {
            this.time = time;
            return this;
        }

        public Builder time(final Instant time) {
            this.time = time.toEpochMilli();
            return this;
        }

        public SessionRequest build() {
            return new SessionRequest(name, time);
        }
    }
}
