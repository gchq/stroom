package stroom.planb.impl.io;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.io.TemporalState.Key;

import java.nio.file.Path;

public class TemporalStateWriter extends AbstractLmdbWriter<Key, StateValue> {

    public TemporalStateWriter(final Path path,
                               final ByteBufferFactory byteBufferFactory) {
        this(path, byteBufferFactory, true);
    }

    public TemporalStateWriter(final Path path,
                               final ByteBufferFactory byteBufferFactory,
                               final boolean overwrite) {
        super(path, byteBufferFactory, new TemporalStateSerde(byteBufferFactory), overwrite);
    }
}
