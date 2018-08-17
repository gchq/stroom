package stroom.headless;

import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.StreamSourceInputStream;
import stroom.data.store.api.StreamSourceInputStreamProvider;

import java.io.IOException;
import java.io.InputStream;

class BasicInputStreamProvider implements StreamSourceInputStreamProvider {
    private final StreamSourceInputStream inputStream;

    BasicInputStreamProvider(final InputStream inputStream, final long size) {
        this.inputStream = new StreamSourceInputStreamImpl(inputStream, size);
    }

    @Override
    public long getStreamCount() {
        return 1;
    }

    @Override
    public StreamSourceInputStream getStream(final long streamNo) {
        return inputStream;
    }

    @Override
    public SegmentInputStream getSegmentInputStream(final long streamNo) {
        return null;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}