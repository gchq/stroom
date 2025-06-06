package stroom.planb.impl.serde.rangestate;

import stroom.planb.impl.data.RangeState.Key;
import stroom.planb.impl.serde.KeySerde;

import java.nio.ByteBuffer;
import java.util.function.Function;

public interface RangeKeySerde extends KeySerde<Key> {

    <R> R toKeyStart(final long key,
                     final Function<ByteBuffer, R> function);
}
