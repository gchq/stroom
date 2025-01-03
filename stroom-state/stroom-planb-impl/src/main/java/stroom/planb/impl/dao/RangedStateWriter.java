package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.dao.RangedState.Key;
import stroom.planb.impl.dao.RangedState.Value;

import java.nio.file.Path;

public class RangedStateWriter extends AbstractLmdbWriter<Key, Value> {

    public RangedStateWriter(final Path path,
                             final ByteBufferFactory byteBufferFactory,
                             final boolean keepFirst) {
        super(path, byteBufferFactory, new RangedStateSerde(byteBufferFactory), keepFirst);
    }
}
