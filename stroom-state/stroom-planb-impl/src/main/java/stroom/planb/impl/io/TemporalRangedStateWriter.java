package stroom.planb.impl.io;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.io.TemporalRangedState.Key;

import java.nio.file.Path;

public class TemporalRangedStateWriter extends AbstractLmdbWriter<Key, StateValue> {

    public TemporalRangedStateWriter(final Path path,
                                     final ByteBufferFactory byteBufferFactory) {
        this(path, byteBufferFactory, true);
    }

    public TemporalRangedStateWriter(final Path path,
                                     final ByteBufferFactory byteBufferFactory,
                                     final boolean overwrite) {
        super(path, byteBufferFactory, new TemporalRangedStateSerde(byteBufferFactory), overwrite);
    }
}
