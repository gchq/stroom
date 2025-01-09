package stroom.planb.impl.io;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.io.RangedState.Key;

import java.nio.file.Path;

public class RangedStateWriter extends AbstractLmdbWriter<Key, StateValue> {

    public RangedStateWriter(final Path path,
                             final ByteBufferFactory byteBufferFactory,
                             final boolean keepFirst) {
        super(path, byteBufferFactory, new RangedStateSerde(byteBufferFactory), keepFirst);
    }
}
