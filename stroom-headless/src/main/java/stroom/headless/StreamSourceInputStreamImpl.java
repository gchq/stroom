package stroom.headless;

import stroom.data.store.api.StreamSourceInputStream;

import java.io.IOException;
import java.io.InputStream;

class StreamSourceInputStreamImpl extends StreamSourceInputStream {
    private final InputStream inputStream;
    private final long size;

    StreamSourceInputStreamImpl(final InputStream inputStream, final long size) {
        this.inputStream = inputStream;
        this.size = size;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public void close() throws IOException {
        // Ignore.
    }

    @Override
    public synchronized void mark(int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return inputStream.read(b, off, len);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return inputStream.read(b);
    }

    @Override
    public synchronized void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        return inputStream.skip(n);
    }

    @Override
    public final boolean equals(Object obj) {
        return inputStream.equals(obj);
    }

    @Override
    public final int hashCode() {
        return inputStream.hashCode();
    }

    @Override
    public String toString() {
        return inputStream.toString();
    }
}
