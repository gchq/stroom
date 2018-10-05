/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.stepping.Recorder;
import stroom.util.shared.Highlight;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.function.Consumer;

public class InputRecorder extends AbstractInputElement implements Recorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(InputRecorder.class);

    private String encoding;

    private Buffer buffer;
    private int lineNo = 1;
    private int colNo = 0;

    @Override
    protected InputStream insertFilter(final InputStream inputStream, final String encoding) {
        this.encoding = encoding;
        buffer = new Buffer(inputStream);
        return buffer;
    }

    @Override
    public Object getData(final Highlight highlight) {
        if (buffer == null) {
            return null;
        }

        if (highlight != null) {
            return getHighlightedSection(highlight);
        }

        return null;
    }

    private String getHighlightedSection(final Highlight highlight) {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            consumeHighlightedSection(highlight, baos::write);
            return baos.toString(encoding);
        } catch (final UnsupportedEncodingException e) {
            LOGGER.error(e.getMessage(), e);
            return e.getMessage();
        }
    }

    @Override
    public void clear(final Highlight highlight) {
        if (buffer != null) {
            if (highlight == null) {
                buffer.clear();
            } else {
                consumeHighlightedSection(highlight, c -> {
                });
            }
        }
    }

    private void consumeHighlightedSection(final Highlight highlight, final Consumer<Byte> consumer) {
        final int lineFrom = highlight.getLineFrom();
        final int colFrom = highlight.getColFrom();
        final int lineTo = highlight.getLineTo();
        final int colTo = highlight.getColTo();

        boolean found = false;
        boolean inRecord = false;

        int advance = 0;
        int i = 0;
        for (; i < buffer.length() && !found; i++) {
            final byte c = buffer.byteAt(i);

            // Remember the previous line and column numbers in case we need to go back to them.
            final int previousLineNo = lineNo;
            final int previousColNo = colNo;

            // Advance the line or column number.
            if (c == '\n') {
                lineNo++;
                colNo = 0;
            } else {
                colNo++;
            }

            if (!inRecord) {
                if (lineNo > lineFrom ||
                        (lineNo >= lineFrom && colNo >= colFrom)) {
                    inRecord = true;
                }
            }

            if (inRecord) {
                if (lineNo > lineTo ||
                        (lineNo >= lineTo && colNo >= colTo)) {
                    inRecord = false;
                    found = true;
                    advance = i;

                    // We won't be consuming the current char so revert to the previous line and column numbers.
                    lineNo = previousLineNo;
                    colNo = previousColNo;
                }
            }

            if (inRecord) {
                consumer.accept(c);
            }
        }

        // If we went through the whole buffer and didn't find what we are looking for then clear it.
        if (!found) {
            buffer.clear();
        } else {
            // Move the filter buffer forward and discard any content we no longer need.
            buffer.move(advance);
        }
    }

    @Override
    public void startStream() {
        super.startStream();
        lineNo = 1;
        colNo = 0;
        if (buffer != null) {
            buffer.clear();
        }
    }

    private static class Buffer extends FilterInputStream {
        private static final int MAX_BUFFER_SIZE = 1000000;
        private final ByteBuffer byteBuffer = new ByteBuffer();

        public Buffer(final InputStream in) {
            super(in);
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final int length = super.read(b, off, len);
            if (length > 0) {
                if (LOGGER.isDebugEnabled() && byteBuffer.size() > MAX_BUFFER_SIZE) {
                    LOGGER.debug("Exceeding buffer length");
                }

                byteBuffer.write(b, off, length);
            }
            return length;
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return this.read(b, 0, b.length);
        }

        @Override
        public int read() throws IOException {
            final int result = super.read();
            if (result != -1) {
                if (LOGGER.isDebugEnabled() && byteBuffer.size() > MAX_BUFFER_SIZE) {
                    LOGGER.debug("Exceeding buffer length");
                }

                byteBuffer.write(result);
            }
            return result;
        }

        byte byteAt(int index) {
            return byteBuffer.getByte(index);
        }

        int length() {
            return byteBuffer.size();
        }

        void move(final int len) {
            if (len > 0) {
                if (len >= byteBuffer.size()) {
                    byteBuffer.reset();
                } else {
                    byteBuffer.move(len);
                }
            }
        }

        void clear() {
            byteBuffer.reset();
        }
    }

    private static class ByteBuffer extends ByteArrayOutputStream {
        byte getByte(final int index) {
            return buf[index];
        }

        void move(final int len) {
            if (count >= len) {
                for (int i = 0; i < count - len; i++) {
                    buf[i] = buf[i + len];
                }
                count -= len;
            }
        }
    }
}
