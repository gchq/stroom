package stroom.planb.impl.db.rangedstate;

import stroom.planb.impl.db.rangedstate.RangedState.Key;
import stroom.planb.impl.db.serde.KeySerde;

import java.nio.ByteBuffer;
import java.util.function.Function;

public interface RangeKeySerde extends KeySerde<Key> {

    <R> R toKeyStart(final long key,
                     final Function<ByteBuffer, R> function);
}
