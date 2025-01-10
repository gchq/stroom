package stroom.planb.impl.io;

import stroom.bytebuffer.impl6.ByteBufferFactory;

import java.nio.file.Path;

public class SessionWriter extends AbstractLmdbWriter<Session, Session> {

    public SessionWriter(final Path path,
                         final ByteBufferFactory byteBufferFactory) {
        this(path, byteBufferFactory, true);
    }

    public SessionWriter(final Path path,
                         final ByteBufferFactory byteBufferFactory,
                         final boolean overwrite) {
        super(path, byteBufferFactory, new SessionSerde(byteBufferFactory), overwrite);
    }
}
