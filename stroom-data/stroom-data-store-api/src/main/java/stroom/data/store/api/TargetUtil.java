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

package stroom.data.store.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

public final class TargetUtil {

    private static final int BUFFER_SIZE = 8192;
    private static final String DEFAULT_CHARSET_NAME = "UTF-8";
    private static final Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_CHARSET_NAME);

    public static void write(final Target streamTarget, final String string) {
        write(streamTarget, string.getBytes(DEFAULT_CHARSET));
    }

    private static void write(final Target streamTarget, final byte[] bytes) {
        try (final OutputStreamProvider outputStreamProvider = streamTarget.next()) {
            try (final OutputStream outputStream = outputStreamProvider.get()) {
                outputStream.write(bytes);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static long write(final Target streamTarget, final InputStream inputStream) {
        try (final OutputStreamProvider outputStreamProvider = streamTarget.next()) {
            try (final OutputStream outputStream = outputStreamProvider.get()) {
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
