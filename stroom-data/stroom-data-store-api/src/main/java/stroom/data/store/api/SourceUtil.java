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

public final class SourceUtil {

    private static final int BUFFER_SIZE = 8192;
    private static final String DEFAULT_CHARSET_NAME = "UTF-8";
    private static final Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_CHARSET_NAME);

    public static byte[] read(final Source source) {
        try (final InputStreamProvider inputStreamProvider = source.get(0)) {
            try (final InputStream inputStream = inputStreamProvider.get()) {
                return inputStream.readAllBytes();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void read(final Source source, final OutputStream outputStream) {
        try (final InputStreamProvider inputStreamProvider = source.get(0)) {
            try (final InputStream inputStream = inputStreamProvider.get()) {
                copy(inputStream, outputStream);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readString(final Source source) {
        return new String(read(source), DEFAULT_CHARSET);
    }

    /**
     * Take a stream to another stream.
     */
    private static long copy(final InputStream inputStream,
                             final OutputStream outputStream) {
        long bytesWritten = 0;
        try {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
                bytesWritten += len;
            }
            return bytesWritten;
        } catch (final IOException ioEx) {
            // Wrap it
            throw new RuntimeException(ioEx);
        }
    }
}
