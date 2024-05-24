package stroom.pipeline.writer;

import java.io.IOException;
import java.io.OutputStream;

public interface Output {

    OutputStream getOutputStream();

    default void insertSegmentMarker() throws IOException {
    }

    default void startZipEntry() throws IOException {
    }

    default void endZipEntry() throws IOException {
    }

    default boolean isZip() {
        return false;
    }

    long getCurrentOutputSize();

    boolean getHasBytesWritten();

    void write(final byte[] bytes) throws IOException;

    void close() throws IOException;
}
