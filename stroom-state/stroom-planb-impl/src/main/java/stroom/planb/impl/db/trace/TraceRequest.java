package stroom.planb.impl.db.trace;

import stroom.planb.impl.data.TraceKey;

public record TraceRequest(TraceKey key) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private TraceKey key;

        public Builder() {
        }

        public Builder(final TraceRequest request) {
            this.key = request.key;
        }

        public Builder key(final TraceKey key) {
            this.key = key;
            return this;
        }

        public TraceRequest build() {
            return new TraceRequest(key);
        }
    }
}
