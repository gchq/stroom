package stroom.refdata.offheapstore;

import net.sf.saxon.event.Receiver;

import java.nio.ByteBuffer;

public interface RefDataValueByteBufferConsumer {

    void consumeBytes(final Receiver receiver, final ByteBuffer byteBuffer);
}
