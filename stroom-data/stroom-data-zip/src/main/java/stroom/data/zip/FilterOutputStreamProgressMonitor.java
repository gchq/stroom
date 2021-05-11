package stroom.data.zip;

import stroom.util.io.WrappedOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

class FilterOutputStreamProgressMonitor extends WrappedOutputStream {

    private final Consumer<Long> progressHandler;

    FilterOutputStreamProgressMonitor(OutputStream outputStream, final Consumer<Long> progressHandler) {
        super(outputStream);
        this.progressHandler = progressHandler;
    }

    @Override
    public void write(byte[] b) throws IOException {
        super.write(b);
        progressHandler.accept((long) b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        super.write(b, off, len);
        progressHandler.accept((long) len);
    }

    @Override
    public void write(int b) throws IOException {
        super.write(b);
        progressHandler.accept(1L);
    }
}
