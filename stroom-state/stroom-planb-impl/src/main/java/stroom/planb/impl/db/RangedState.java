package stroom.planb.impl.db;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.RangedState.Key;

public class RangedState extends KV<Key, StateValue> implements PlanBValue  {

    public RangedState(final Key key, final StateValue value) {
        super(key, value);
    }

    public record Key(long keyStart, long keyEnd) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private long keyStart;
            private long keyEnd;

            public Builder() {
            }

            public Builder(final Key key) {
                this.keyStart = key.keyStart;
                this.keyEnd = key.keyEnd;
            }

            public Builder keyStart(final long keyStart) {
                this.keyStart = keyStart;
                return this;
            }

            public Builder keyEnd(final long keyEnd) {
                this.keyEnd = keyEnd;
                return this;
            }

            public Key build() {
                return new Key(keyStart, keyEnd);
            }
        }
    }
}
