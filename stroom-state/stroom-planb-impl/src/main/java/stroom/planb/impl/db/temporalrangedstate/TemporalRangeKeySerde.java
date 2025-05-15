package stroom.planb.impl.db.temporalrangedstate;

import stroom.planb.impl.db.serde.Serde;
import stroom.planb.impl.db.temporalrangedstate.TemporalRangedState.Key;

import java.nio.ByteBuffer;
import java.util.function.Function;

public interface TemporalRangeKeySerde extends Serde<Key> {

    <R> R toKeyStart(final long key,
                     final Function<ByteBuffer, R> function);
}
