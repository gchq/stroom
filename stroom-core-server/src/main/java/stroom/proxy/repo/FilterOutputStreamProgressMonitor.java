package stroom.proxy.repo;

import stroom.data.store.StreamProgressMonitor;

import java.io.IOException;
import java.io.OutputStream;

class FilterOutputStreamProgressMonitor extends WrappedOutputStream {
    private final StreamProgressMonitor streamProgressMonitor;

    FilterOutputStreamProgressMonitor(OutputStream outputStream, StreamProgressMonitor streamProgressMonitor) {
        super(outputStream);
        this.streamProgressMonitor = streamProgressMonitor;
    }

    @Override
    public void write(byte[] b) throws IOException {
        super.write(b);
        streamProgressMonitor.progress(b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        streamProgressMonitor.progress(len);
    }

    @Override
    public void write(int b) throws IOException {
        super.write(b);
        streamProgressMonitor.progress(1);
    }

}
