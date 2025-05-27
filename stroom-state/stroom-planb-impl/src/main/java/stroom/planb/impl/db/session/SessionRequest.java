package stroom.planb.impl.db.session;

import stroom.query.language.functions.Val;

import java.time.Instant;

public record SessionRequest(Val key, Instant time) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Val key;
        private Instant time;

        public Builder() {
        }

        public Builder(final SessionRequest request) {
            this.key = request.key;
            this.time = request.time;
        }

        public Builder key(final Val key) {
            this.key = key;
            return this;
        }

        public Builder time(final Instant time) {
            this.time = time;
            return this;
        }

        public SessionRequest build() {
            return new SessionRequest(key, time);
        }
    }
}
