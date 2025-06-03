package stroom.planb.impl.db.rangestate;

import stroom.planb.impl.db.rangestate.RangeState.Key;
import stroom.planb.impl.db.serde.KeySerde;

import java.nio.ByteBuffer;
import java.util.function.Function;

public interface RangeKeySerde extends KeySerde<Key> {

    <R> R toKeyStart(final long key,
                     final Function<ByteBuffer, R> function);
}
