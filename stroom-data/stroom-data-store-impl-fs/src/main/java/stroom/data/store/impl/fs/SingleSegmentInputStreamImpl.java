package stroom.data.store.impl.fs;

import stroom.data.store.api.SegmentInputStream;

import java.io.IOException;
import java.io.InputStream;

class SingleSegmentInputStreamImpl extends SegmentInputStream {
    private final InputStream inputStream;
    private final long size;

    private boolean excludeAll;

    SingleSegmentInputStreamImpl(final InputStream inputStream, final long size) {
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
    public void close() {
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
        if (excludeAll) {
            return -1;
        }
        return inputStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (excludeAll) {
            return -1;
        }
        return inputStream.read(b, off, len);
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (excludeAll) {
            return -1;
        }
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

    @Override
    public long count() {
        return 1;
    }

    @Override
    public void include(final long segment) {
        check(segment);
    }

    @Override
    public void includeAll() {
        check(0);
    }

    @Override
    public void exclude(final long segment) {
        check(0);
        excludeAll = true;
    }

    @Override
    public void excludeAll() {
        check(0);
        excludeAll = true;
    }

    /**
     * Checks that no includes or excludes are added once the stream is being
     * read.
     */
    private void check(final long segment) {
        if (segment < 0 || segment >= 1) {
            throw new RuntimeException(
                    "Segment number " + segment + " is not within bounds [0-" + 1 + "]");
        }
    }
}
