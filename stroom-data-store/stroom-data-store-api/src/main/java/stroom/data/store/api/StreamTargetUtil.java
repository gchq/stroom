package stroom.data.store.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

public final class StreamTargetUtil {
    private static final int BUFFER_SIZE = 8192;
    private static final String DEFAULT_CHARSET_NAME = "UTF-8";
    private static final Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_CHARSET_NAME);

    public static void write(final StreamTarget streamTarget, final String string) {
        write(streamTarget, string.getBytes(DEFAULT_CHARSET));
    }

    private static void write(final StreamTarget streamTarget, final byte[] bytes) {
        try (final OutputStreamProvider outputStreamProvider = streamTarget.getOutputStreamProvider()) {
            try (final OutputStream outputStream = outputStreamProvider.next()) {
                outputStream.write(bytes);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static long write(final StreamTarget streamTarget, final InputStream inputStream) {
        try (final OutputStreamProvider outputStreamProvider = streamTarget.getOutputStreamProvider()) {
            try (final OutputStream outputStream = outputStreamProvider.next()) {
                return write(inputStream, outputStream, false);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    //    private static long write(final InputStream inputStream,
//                              final OutputStream segmentOutputStream,
//                              final boolean close) {
//        long bytesWritten;
//        try {
//            try {
//                bytesWritten = streamToStream(inputStream, segmentOutputStream, close);
//            } finally {
//                try {
//                    // Ensure all streams are closed.
//                    if (segmentOutputStream != null) {
//                        segmentOutputStream.flush();
//                        if (close) {
//                            segmentOutputStream.close();
//                        }
//                    }
//
//                } finally {
//                    if (close && inputStream != null) {
//                        inputStream.close();
//                    }
//                }
//            }
//            return bytesWritten;
//        } catch (final IOException e) {
//            throw new UncheckedIOException(e);
//        }
//    }
//
//    /**
//     * Take a stream to another stream.
//     */
    public static long write(final InputStream inputStream,
                             final OutputStream outputStream,
                             final boolean close) {
        long bytesWritten = 0;
        try {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
                bytesWritten += len;
            }
            if (close) {
                outputStream.close();
                inputStream.close();
            }
            return bytesWritten;
        } catch (final IOException ioEx) {
            // Wrap it
            throw new RuntimeException(ioEx);
        }
    }
}
