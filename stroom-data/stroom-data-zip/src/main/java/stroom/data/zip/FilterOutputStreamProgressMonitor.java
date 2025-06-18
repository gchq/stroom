package stroom.data.zip;

import stroom.util.io.WrappedOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

public class FilterOutputStreamProgressMonitor extends WrappedOutputStream {

    private final Consumer<Long> progressHandler;

    public FilterOutputStreamProgressMonitor(final OutputStream outputStream, final Consumer<Long> progressHandler) {
        super(outputStream);
        this.progressHandler = progressHandler;
    }

    @Override
    public void write(final byte[] b) throws IOException {
        super.write(b);
        progressHandler.accept((long) b.length);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        super.write(b, off, len);
        progressHandler.accept((long) len);
    }

    @Override
    public void write(final int b) throws IOException {
        super.write(b);
        progressHandler.accept(1L);
    }
}
