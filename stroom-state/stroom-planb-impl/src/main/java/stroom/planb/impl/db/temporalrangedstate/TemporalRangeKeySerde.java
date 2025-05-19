package stroom.planb.impl.db.temporalrangedstate;

import stroom.planb.impl.db.serde.KeySerde;
import stroom.planb.impl.db.temporalrangedstate.TemporalRangedState.Key;

import java.nio.ByteBuffer;
import java.util.function.Function;

public interface TemporalRangeKeySerde extends KeySerde<Key> {

    <R> R toKeyStart(final long key,
                     final Function<ByteBuffer, R> function);
}
