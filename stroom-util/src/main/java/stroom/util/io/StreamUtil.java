/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.io;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Helper class for resources.
 */
public final class StreamUtil {

    /**
     * Buffer size to use.
     */
    public static final int BUFFER_SIZE = 8192;
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    public static final String DEFAULT_CHARSET_NAME = DEFAULT_CHARSET.name();
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    private static final ByteSlice ZERO_BYTES = new ByteSlice(new byte[0]);

    private StreamUtil() {
        // NA Utility
    }

    /**
     * Convert a resource to a string.
     */
    public static String streamToString(final InputStream stream) {
        return streamToString(stream, DEFAULT_CHARSET);
    }

    /**
     * Convert a resource to a string.
     */
    public static String streamToString(final InputStream stream, final boolean close) {
        return streamToString(stream, DEFAULT_CHARSET, close);
    }

    /**
     * Convert a resource to a string.
     */
    public static String streamToString(final InputStream stream, final Charset charset) {
        return streamToString(stream, charset, true);
    }

    /**
     * Convert a resource to a string.
     */
    public static String streamToString(final InputStream stream, final Charset charset, final boolean close) {
        if (stream == null) {
            return null;
        }

        final MyByteArrayOutputStream baos = doStreamToBuffer(stream, close);
        return baos.toString(charset);
    }

    public static Stream<String> streamToLines(final InputStream stream) {
        return streamToLines(stream, DEFAULT_CHARSET, true);
    }

    public static Stream<String> streamToLines(final InputStream stream, final Charset charset, final boolean close) {
        return new BufferedReader(new StringReader(streamToString(stream, charset, close))).lines();
    }

    public static byte[] streamToBytes(final InputStream stream) {
        final MyByteArrayOutputStream byteArrayOutputStream = doStreamToBuffer(stream, true);
        if (byteArrayOutputStream == null) {
            return new byte[0];
        }
        return byteArrayOutputStream.toByteArray();
    }

    public static ByteArrayOutputStream streamToBuffer(final InputStream stream) {
        return doStreamToBuffer(stream, true);
    }

    public static ByteArrayOutputStream streamToBuffer(final InputStream stream, final boolean close) {
        return doStreamToBuffer(stream, close);
    }

    private static MyByteArrayOutputStream doStreamToBuffer(final InputStream stream, final boolean close) {
        if (stream == null) {
            return null;
        }
        final MyByteArrayOutputStream byteArrayOutputStream = new MyByteArrayOutputStream();

        try {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = stream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, len);
            }
            if (close) {
                stream.close();
            }
            return byteArrayOutputStream;
        } catch (final IOException ioEx) {
            // Wrap it
            throw new UncheckedIOException(ioEx);
        }
    }

    /**
     * Takes a string and writes it to a file.
     */
    public static void stringToFile(final String string, final Path file) {
        stringToFile(string, file, DEFAULT_CHARSET);
    }

    /**
     * Takes a string and writes it to a file.
     */
    public static void stringToFile(final String string, final Path file, final Charset charset) {
        try {
            if (Files.isRegularFile(file)) {
                FileUtil.deleteFile(file);
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, string, charset);
        } catch (final IOException ioEx) {
            // Wrap it
            throw new UncheckedIOException(ioEx);
        }
    }

    /**
     * Takes a string and writes it to an output stream.
     */
    public static void stringToStream(final String string, final OutputStream outputStream) {
        stringToStream(string, outputStream, DEFAULT_CHARSET);
    }

    /**
     * Takes a string and writes it to an output stream.
     */
    public static void stringToStream(final String string, final OutputStream outputStream, final Charset charset) {
        try {
            outputStream.write(string.getBytes(charset));
            outputStream.flush();
            outputStream.close();

        } catch (final IOException ioEx) {
            // Wrap it
            throw new UncheckedIOException(ioEx);
        }
    }

    /**
     * Read file into a String, wrapping any {@link IOException} with an
     * {@link UncheckedIOException}.
     *
     * @return The contents of the file as a string using {@link StreamUtil#DEFAULT_CHARSET}.
     */
    public static String fileToString(final Path file) {
        try {
            return Files.readString(file, DEFAULT_CHARSET);
        } catch (final IOException ioEx) {
            // Wrap it
            throw new UncheckedIOException(ioEx);
        }
    }

    /**
     * Read file into a String, wrapping any {@link IOException} with an
     * {@link UncheckedIOException}.
     *
     * @return The contents of the file as a string using the supplied charset.
     */
    public static String fileToString(final Path file, final Charset charset) {
        try {
            return Files.readString(file, charset);
        } catch (final IOException ioEx) {
            // Wrap it
            throw new UncheckedIOException(ioEx);
        }
    }

//    /**
//     * Reads a file and returns it as a stream.
//     */
//    public static InputStream fileToStream(final Path file) {
//        try (final InputStream is = new BufferedInputStream(Files.newInputStream(file)) ){
//            return new BufferedInputStream(Files.newInputStream(file));
//        } catch (final IOException ioEx) {
//            // Wrap it
//            throw new UncheckedIOException(ioEx);
//        }
//    }

    /**
     * Take a stream to a file.
     *
     * @param inputStream    to read and close
     * @param outputFileName to (over)write and close
     */
    public static void streamToFile(final InputStream inputStream, final String outputFileName) {
        streamToFile(inputStream, Paths.get(outputFileName));
    }

    public static void streamToFile(final InputStream inputStream, final Path file) {
        try {
            if (Files.isRegularFile(file)) {
                FileUtil.deleteFile(file);
            }
            Files.createDirectories(file.getParent());

            try (final OutputStream fos = new BufferedOutputStream(Files.newOutputStream(file))) {
                streamToStream(inputStream, fos);
            }
        } catch (final IOException ioEx) {
            // Wrap it
            throw new UncheckedIOException(ioEx);
        }
    }

    /**
     * Copy file to file.
     *
     * @param fromFile the Path to copy (readable, non-null file)
     * @param toFile   the Path to copy to (non-null, parent dir exists)
     */
    public static void copyFile(final Path fromFile, final Path toFile) throws IOException {
        Files.copy(fromFile, toFile);
    }

    /**
     * Take a stream to another stream.
     */
    public static long streamToStream(final InputStream inputStream, final OutputStream outputStream) {
        return streamToStream(inputStream, outputStream, new byte[BUFFER_SIZE], bytesWritten -> {
        });
    }

    /**
     * Take a stream to another stream.
     */
    public static long streamToStream(final InputStream inputStream,
                                      final OutputStream outputStream,
                                      final byte[] buffer,
                                      final Consumer<Long> progressConsumer) {
        long bytesWritten = 0;
        try {
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
                bytesWritten += len;
                progressConsumer.accept((long) len);
            }
            return bytesWritten;
        } catch (final IOException ioEx) {
            // Wrap it
            throw new UncheckedIOException(ioEx);
        }
    }

    /**
     * Convert a string to a stream.
     *
     * @return the stream or null
     */
    public static InputStream stringToStream(final String string) {
        return stringToStream(string, DEFAULT_CHARSET);
    }

    /**
     * Convert a string to a stream.
     *
     * @param string to convert
     * @return the stream or null
     */
    public static InputStream stringToStream(final String string, final Charset charset) {
        if (string != null) {
            return new ByteArrayInputStream(string.getBytes(charset));
        }
        return null;
    }

    /**
     * Try a read a full buffer from a stream.
     *
     * @param stream to read from
     * @param buffer to read into
     * @throws IOException if error
     */
    public static int eagerRead(final InputStream stream, final byte[] buffer) throws IOException {
        int read;
        int offset = 0;
        int remaining = buffer.length;
        while (remaining > 0 && (read = stream.read(buffer, offset, remaining)) != -1) {
            remaining = remaining - read;
            offset = offset + read;
        }

        // Did not read anything ... must be finished
        if (offset == 0) {
            return -1;
        }

        // Length read
        return offset;
    }

    public static long skip(final InputStream inputStream, final long totalBytesToSkip) throws IOException {
        long bytesToSkip = totalBytesToSkip;
        while (bytesToSkip > 0) {
            final long skip = inputStream.skip(bytesToSkip);
            if (skip < 0) {
                break;
            }
            bytesToSkip -= skip;
        }
        return totalBytesToSkip - bytesToSkip;
    }

    /**
     * Read an exact number of bytes into a buffer. Throws an exception if the
     * number of bytes are not available.
     *
     * @param stream to read from
     * @param buffer to read into
     * @throws IOException if error
     */
    public static void fillBuffer(final InputStream stream, final byte[] buffer) throws IOException {
        fillBuffer(stream, buffer, 0, buffer.length);
    }

    /**
     * Read an exact number of bytes into a buffer. Throws an exception if the
     * number of bytes are not available.
     *
     * @param stream to read from
     * @param buffer to read into
     * @param offset to use
     * @param len    length
     * @throws IOException if error
     */
    public static void fillBuffer(final InputStream stream, final byte[] buffer, final int offset, final int len)
            throws IOException {
        final int realLen = stream.read(buffer, offset, len);

        if (realLen == -1) {
            throw new IOException("Unable to fill buffer");
        }
        if (realLen != len) {
            // Try Again
            fillBuffer(stream, buffer, offset + realLen, len - realLen);
        }
    }

    /**
     * Wrap a stream and don't let it close.
     */
    public static OutputStream ignoreClose(final OutputStream outputStream) {
        return new FilterOutputStream(outputStream) {
            @Override
            public void close() throws IOException {
                flush();
                // Ignore Close
            }
        };
    }

    @SuppressWarnings(value = "DM_DEFAULT_ENCODING")
    public static String exceptionCallStack(final Throwable throwable) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final PrintWriter printWriter = new PrintWriter(byteArrayOutputStream);
        throwable.printStackTrace(printWriter);
        printWriter.close();
        return byteArrayOutputStream.toString(StreamUtil.DEFAULT_CHARSET);

    }

    public static void close(final OutputStream outputStream) {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void close(final InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ByteSlice getByteSlice(final String string) {
        if (string == null) {
            return ZERO_BYTES;
        }
        return new ByteSlice(string.getBytes(DEFAULT_CHARSET));
    }

    /**
     * A wrapper on ByteArrayOutputStream to add direct use of charset without
     * charset name.
     */
    private static class MyByteArrayOutputStream extends ByteArrayOutputStream {

        public synchronized String toString(final Charset charset) {
            return new String(buf, 0, count, charset);
        }
    }
}
