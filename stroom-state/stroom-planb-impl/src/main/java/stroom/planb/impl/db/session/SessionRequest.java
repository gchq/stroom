package stroom.planb.impl.db.session;

import stroom.planb.impl.serde.keyprefix.KeyPrefix;

import java.time.Instant;

public record SessionRequest(KeyPrefix prefix, Instant time) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private KeyPrefix prefix;
        private Instant time;

        public Builder() {
        }

        public Builder(final SessionRequest request) {
            this.prefix = request.prefix;
            this.time = request.time;
        }

        public Builder prefix(final KeyPrefix prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder time(final Instant time) {
            this.time = time;
            return this;
        }

        public SessionRequest build() {
            return new SessionRequest(prefix, time);
        }
    }
}
