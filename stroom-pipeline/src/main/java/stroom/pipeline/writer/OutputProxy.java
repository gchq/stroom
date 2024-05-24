package stroom.pipeline.writer;

import java.io.IOException;
import java.io.OutputStream;

public class OutputProxy implements Output {

    private final Output output;

    public OutputProxy(final Output output) {
        this.output = output;
    }

    @Override
    public OutputStream getOutputStream() {
        return output.getOutputStream();
    }

    @Override
    public void insertSegmentMarker() throws IOException {
        output.insertSegmentMarker();
    }

    @Override
    public void startZipEntry() throws IOException {
        output.startZipEntry();
    }

    @Override
    public void endZipEntry() throws IOException {
        output.endZipEntry();
    }

    @Override
    public boolean isZip() {
        return output.isZip();
    }

    @Override
    public long getCurrentOutputSize() {
        return output.getCurrentOutputSize();
    }

    @Override
    public boolean getHasBytesWritten() {
        return output.getHasBytesWritten();
    }

    @Override
    public void write(final byte[] bytes) throws IOException {
        output.write(bytes);
    }

    @Override
    public void close() throws IOException {
        output.close();
    }
}
