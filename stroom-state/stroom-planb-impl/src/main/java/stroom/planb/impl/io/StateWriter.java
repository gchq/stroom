package stroom.planb.impl.io;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.io.State.Key;

import java.nio.file.Path;

public class StateWriter extends AbstractLmdbWriter<Key, StateValue> {

    public StateWriter(final Path path,
                       final ByteBufferFactory byteBufferFactory,
                       final boolean keepFirst) {
        super(path, byteBufferFactory, new StateSerde(byteBufferFactory), keepFirst);
    }
}
