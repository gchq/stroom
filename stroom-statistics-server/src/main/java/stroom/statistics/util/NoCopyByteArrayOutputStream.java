package stroom.statistics.util;

import java.io.ByteArrayOutputStream;

public class NoCopyByteArrayOutputStream extends ByteArrayOutputStream {

    /**
     * Overrides the normal behaviour of {@link ByteArrayOutputStream} to allow
     * access to the underlying bytes.  Any mutation of the return byte[] or the
     * output stream after this call will affect both. You have been warned.
     * @return The internal byte[] from the outputstream without an array copy.
     */
    @Override
    public synchronized byte[] toByteArray() {
        return super.buf;
    }
}
