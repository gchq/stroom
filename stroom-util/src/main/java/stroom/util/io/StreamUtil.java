/*
 * Copyright 2016 Crown Copyright
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.channels.ByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Helper class for resources.
 */
public final class StreamUtil {
    /**
     * Buffer size to use.
     */
    public static final int BUFFER_SIZE = 8192;
    // TODO 2016-04-20: Replace all references to "UTF-8" (throughout the
    // code-base) with a reference to StandardCharsets.UTF_8. Possibly remove
    // the default charset name just leaving default charset.
    public static final String DEFAULT_CHARSET_NAME = "UTF-8";
    public static final Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_CHARSET_NAME);
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

    public static byte[] streamToBytes(final InputStream stream) {
        return doStreamToBuffer(stream, true).toByteArray();
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
            throw new RuntimeException(ioEx);
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
            Files.write(file, string.getBytes(charset));
        } catch (final IOException ioEx) {
            // Wrap it
            throw new RuntimeException(ioEx);
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
            throw new RuntimeException(ioEx);
        }
    }

    public static String fileToString(final Path file) {
        return fileToString(file, DEFAULT_CHARSET);
    }

    /**
     * Reads a file and returns it as a string.
     */
    public static String fileToString(final Path file, final Charset charset) {
        try {
            final byte[] bytes = Files.readAllBytes(file);
            return new String(bytes, charset);
        } catch (final IOException ioEx) {
            // Wrap it
            throw new RuntimeException(ioEx);
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
//            throw new RuntimeException(ioEx);
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

            try (final OutputStream fos = Files.newOutputStream(file)) {
                streamToStream(inputStream, fos);
            }
        } catch (final IOException ioEx) {
            // Wrap it
            throw new RuntimeException(ioEx);
        }
    }

    /**
     * Copy file to file.
     *
     * @param fromFile the Path to copy (readable, non-null file)
     * @param toFile   the Path to copy to (non-null, parent dir exists)
     * @throws IOException
     */
    public static void copyFile(final Path fromFile, final Path toFile) throws IOException {
        Files.copy(fromFile, toFile);
//        try (final InputStream in = Files.newInputStream(fromFile); final OutputStream out = Files.newOutputStream(toFile)) {
//            streamToStream(in, out);
//        }
    }

    /**
     * Take a stream to another stream (AND CLOSE BOTH).
     */
    public static void streamToStream(final InputStream inputStream, final OutputStream outputStream) {
        streamToStream(inputStream, outputStream, true);
    }

    /**
     * Take a stream to another stream.
     */
    public static long streamToStream(final InputStream inputStream, final OutputStream outputStream,
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
        int read = 0;
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
        return new String(byteArrayOutputStream.toByteArray(), StreamUtil.DEFAULT_CHARSET);

    }

    public static void close(final OutputStream outputStream) {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void close(final InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
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
