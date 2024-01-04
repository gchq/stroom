package stroom.proxy.app.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public class TransferUtil {

    /**
     * Take a stream to another stream.
     */
    public static long transfer(final InputStream inputStream,
                                final OutputStream outputStream,
                                final byte[] buffer) {
        long bytesWritten = 0;
        try {
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
                bytesWritten += len;
            }
            return bytesWritten;
        } catch (final IOException ioEx) {
            // Wrap it
            throw new UncheckedIOException(ioEx);
        }
    }
}
