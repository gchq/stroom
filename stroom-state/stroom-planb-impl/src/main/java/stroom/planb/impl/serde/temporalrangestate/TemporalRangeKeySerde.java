package stroom.planb.impl.serde.temporalrangestate;

import stroom.planb.impl.data.TemporalRangeState.Key;
import stroom.planb.impl.serde.KeySerde;

import java.nio.ByteBuffer;
import java.util.function.Function;

public interface TemporalRangeKeySerde extends KeySerde<Key> {

    <R> R toKeyStart(final long key,
                     final Function<ByteBuffer, R> function);
}
