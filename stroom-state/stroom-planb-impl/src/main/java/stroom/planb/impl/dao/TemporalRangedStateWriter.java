package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.dao.TemporalRangedState.Key;
import stroom.planb.impl.dao.TemporalRangedState.Value;

import java.nio.file.Path;

public class TemporalRangedStateWriter extends AbstractLmdbWriter<Key, Value> {

    public TemporalRangedStateWriter(final Path path,
                                     final ByteBufferFactory byteBufferFactory,
                                     final boolean keepFirst) {
        super(path, byteBufferFactory, new TemporalRangedStateSerde(byteBufferFactory), keepFirst);
    }
}
