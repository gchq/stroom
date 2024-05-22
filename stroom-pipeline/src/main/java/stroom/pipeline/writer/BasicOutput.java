package stroom.pipeline.writer;

import stroom.util.io.ByteCountOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class BasicOutput implements Output {

    private final ByteCountOutputStream outputStream;

    public BasicOutput(final OutputStream innerOutputStream) {
        this.outputStream = new ByteCountOutputStream(innerOutputStream);
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public long getCurrentOutputSize() {
        return outputStream.getCount();
    }

    @Override
    public boolean getHasBytesWritten() {
        return outputStream.getHasBytesWritten();
    }

    @Override
    public void write(final byte[] bytes) throws IOException {
        outputStream.write(bytes);
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
