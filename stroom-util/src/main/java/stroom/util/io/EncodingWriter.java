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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

public class EncodingWriter extends Writer {

    private final OutputStreamWriter writer;

    public EncodingWriter(final OutputStream out, final Charset cs) {
        final WrappedOutputStream wrappedOutputStream = new WrappedOutputStream(out) {
            @Override
            public void flush() throws IOException {
                // Ignore flushes to the output stream.
            }
        };

        writer = new OutputStreamWriter(wrappedOutputStream, cs);
    }

    @Override
    public void write(final int c) throws IOException {
        writer.write(c);
    }

    @Override
    public void write(final char[] cbuf, final int off, final int len) throws IOException {
        writer.write(cbuf, off, len);
    }

    @Override
    public void write(final String str, final int off, final int len) throws IOException {
        writer.write(str, off, len);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
