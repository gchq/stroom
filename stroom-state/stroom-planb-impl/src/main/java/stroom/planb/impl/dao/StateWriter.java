package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.dao.State.Key;
import stroom.planb.impl.dao.State.Value;

import java.nio.file.Path;

public class StateWriter extends AbstractLmdbWriter<Key, Value> {

    public StateWriter(final Path path,
                       final ByteBufferFactory byteBufferFactory,
                       final boolean keepFirst) {
        super(path, byteBufferFactory, new StateSerde(byteBufferFactory), keepFirst);
    }
}
