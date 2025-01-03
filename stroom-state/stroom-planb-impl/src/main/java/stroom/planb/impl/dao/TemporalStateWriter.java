package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.dao.TemporalState.Key;
import stroom.planb.impl.dao.TemporalState.Value;

import java.nio.file.Path;

public class TemporalStateWriter extends AbstractLmdbWriter<Key, Value> {

    public TemporalStateWriter(final Path path,
                               final ByteBufferFactory byteBufferFactory,
                               final boolean keepFirst) {
        super(path, byteBufferFactory, new TemporalStateSerde(byteBufferFactory), keepFirst);
    }
}
