package stroom.refdata;

import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.parser.Location;

import java.nio.ByteBuffer;

public interface RefDataValueByteBufferConsumer {

    void consumeBytes(final Receiver receiver, final ByteBuffer byteBuffer);

    static class NullLocation implements Location {
        @Override
        public String getSystemId() {
            return null;
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public int getLineNumber() {
            return 0;
        }

        @Override
        public int getColumnNumber() {
            return 0;
        }

        @Override
        public Location saveLocation() {
            return this;
        }
    }
}
