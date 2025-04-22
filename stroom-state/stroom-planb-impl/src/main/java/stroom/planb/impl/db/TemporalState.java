package stroom.planb.impl.db;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.TemporalState.Key;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class TemporalState extends KV<Key, StateValue> implements PlanBValue {

    public TemporalState(final Key key, final StateValue value) {
        super(key, value);
    }

    public record Key(byte[] bytes, long effectiveTime) {

        @Override
        public String toString() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private byte[] bytes;
            private long effectiveTime;

            public Builder() {
            }

            public Builder(final Key key) {
                this.bytes = key.bytes;
                this.effectiveTime = key.effectiveTime;
            }

            public Builder name(final byte[] bytes) {
                this.bytes = bytes;
                return this;
            }

            public Builder name(final String name) {
                this.bytes = name.getBytes(StandardCharsets.UTF_8);
                return this;
            }

            public Builder effectiveTime(final long effectiveTime) {
                this.effectiveTime = effectiveTime;
                return this;
            }

            public Builder effectiveTime(final Instant effectiveTime) {
                this.effectiveTime = effectiveTime.toEpochMilli();
                return this;
            }

            public Key build() {
                return new Key(bytes, effectiveTime);
            }
        }
    }
}
