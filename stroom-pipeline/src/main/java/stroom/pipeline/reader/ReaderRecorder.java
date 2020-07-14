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

import stroom.pipeline.destination.DestinationProvider;
import stroom.pipeline.factory.PipelineFactoryException;
import stroom.pipeline.factory.TakesInput;
import stroom.pipeline.factory.TakesReader;
import stroom.pipeline.factory.Target;
import stroom.pipeline.reader.ByteStreamDecoder.SizedString;
import stroom.pipeline.stepping.Recorder;
import stroom.util.shared.Highlight;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ReaderRecorder extends AbstractIOElement implements TakesInput, TakesReader, Target, Recorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderRecorder.class);

    private static final int BASE_LINE_NO = 1;
    private static final int BASE_COL_NO = 1;

    private Buffer buffer;

    @Override
    public void addTarget(final Target target) {
        if (target != null) {
            if (!(target instanceof DestinationProvider)
                    && !(target instanceof TakesInput)
                    && !(target instanceof TakesReader)) {
                throw new PipelineFactoryException("Attempt to link to an element that does not accept input or reader: "
                        + getElementId() + " > " + target.getElementId());
            }
            super.addTarget(target);
        }
    }

    @Override
    public void setInputStream(final InputStream inputStream, final String encoding) throws IOException {
        final InputBuffer inputBuffer = new InputBuffer(inputStream, encoding);
        buffer = inputBuffer;
        super.setInputStream(inputBuffer, encoding);
    }

    @Override
    public void setReader(final Reader reader) throws IOException {
        final ReaderBuffer readerBuffer = new ReaderBuffer(reader);
        buffer = readerBuffer;
        super.setReader(readerBuffer);
    }

    @Override
    public Object getData(final Highlight highlight) {
        if (buffer == null) {
            return null;
        }
        return buffer.getData(highlight);
    }

    @Override
    public void clear(final Highlight highlight) {
        if (buffer != null) {
            buffer.clear(highlight);
        }
    }

    @Override
    public void startStream() {
        super.startStream();
        if (buffer != null) {
            buffer.reset();
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class ReaderBuffer extends FilterReader implements Buffer {
        private static final int MAX_BUFFER_SIZE = 1000000;
        private final StringBuilder stringBuilder = new StringBuilder();

        private int lineNo = BASE_LINE_NO;
        private int colNo = BASE_COL_NO;

        ReaderBuffer(final Reader in) {
            super(in);
        }

        @Override
        public int read(final char[] cbuf, final int off, final int len) throws IOException {
            final int length = super.read(cbuf, off, len);
            if (length > 0) {
                if (LOGGER.isDebugEnabled() && stringBuilder.length() > MAX_BUFFER_SIZE) {
                    LOGGER.debug("Exceeding buffer length");
                }

                stringBuilder.append(cbuf, off, length);
            }
            return length;
        }

        @Override
        public int read(final char[] cbuf) throws IOException {
            return this.read(cbuf, 0, cbuf.length);
        }

        @Override
        public int read() throws IOException {
            final int result = super.read();
            if (result != -1) {
                if (LOGGER.isDebugEnabled() && stringBuilder.length() > MAX_BUFFER_SIZE) {
                    LOGGER.debug("Exceeding buffer length");
                }

                stringBuilder.append((char) result);
            }
            return result;
        }

        @Override
        public Object getData(final Highlight highlight) {
            if (highlight != null) {
                final StringBuilder sb = new StringBuilder();
                consumeHighlightedSection(highlight, sb::append);
                return sb.toString();
            }

            return null;
        }

        @Override
        public void clear(final Highlight highlight) {
            if (highlight == null) {
                clear();
            } else {
                consumeHighlightedSection(highlight, c -> {
                });
            }
        }

        @Override
        public void reset() {
            lineNo = BASE_LINE_NO;
            colNo = BASE_COL_NO;
            clear();
        }

        private void consumeHighlightedSection(final Highlight highlight, final Consumer<Character> consumer) {
            // range is inclusive at both ends
            final int lineFrom = highlight.getFrom().getLineNo();
            final int colFrom = highlight.getFrom().getColNo();
            final int lineTo = highlight.getTo().getLineNo();
            final int colTo = highlight.getTo().getColNo();

            boolean found = false;
            boolean inRecord = false;

            int advance = 0;
            int i = 0;
            for (; i < length() && !found; i++) {
                final char c = charAt(i);

//                // Remember the previous line and column numbers in case we need to go back to them.
//                final int previousLineNo = lineNo;
//                final int previousColNo = colNo;

                // Advance the line or column number.
//                if (c == '\n') {
//                    lineNo++;
//                    colNo = BASE_COL_NO;
//                } else {
//                    colNo++;
//                }

                if (!inRecord) {
                    // Inclusive from
                    if (lineNo > lineFrom ||
                            (lineNo == lineFrom && colNo >= colFrom)) {
                        inRecord = true;
                    }
                }

                if (inRecord) {
                    // Inclusive to
                    if (lineNo > lineTo ||
                            (lineNo == lineTo && colNo > colTo)) {
                        // Gone past the desired range
                        inRecord = false;
                        found = true;
                        advance = i; // offset of the first char outside the range

                        // We won't be consuming the current char so revert to the previous line and column numbers.
//                        lineNo = previousLineNo;
//                        colNo = previousColNo;
                    }
                }

                if (inRecord) {
                    consumer.accept(c);
                }

                // Advance the line or column number if we haven't found the record.
                if (!found) {
                    if (c == '\n') {
                        lineNo++;
                        colNo = BASE_COL_NO;
                    } else {
                        colNo++;
                    }
                }
            }

            // If we went through the whole buffer and didn't find what we are looking for then clear it.
            if (!found) {
                clear();
            } else {
                // Move the filter buffer forward and discard any content we no longer need.
                move(advance);
            }
        }

        private char charAt(int index) {
            return stringBuilder.charAt(index);
        }

        private int length() {
            return stringBuilder.length();
        }

        private void move(final int len) {
            if (len > 0) {
                if (len >= stringBuilder.length()) {
                    stringBuilder.setLength(0);
                } else {
                    stringBuilder.delete(0, len);
                }
            }
        }

        private void clear() {
            stringBuilder.setLength(0);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    private static class InputBuffer extends FilterInputStream implements Buffer {
        private static final int MAX_BUFFER_SIZE = 1000000;
        private final String encoding;
        private final ByteBuffer byteBuffer = new ByteBuffer();
        private final ByteStreamDecoder byteStreamDecoder;
        private final AtomicInteger offset = new AtomicInteger(0);

        private int lineNo = BASE_LINE_NO;
        private int colNo = BASE_COL_NO;

        InputBuffer(final InputStream in, final String encoding) {
            super(in);
            this.encoding = encoding;
            this.byteStreamDecoder = new ByteStreamDecoder(
                    encoding,
                    () -> byteBuffer.getByte(offset.getAndIncrement()));
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

        @Override
        public Object getData(final Highlight highlight) {
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
            if (highlight == null) {
                clear();
            } else {
                consumeHighlightedSection(highlight, c -> {
                });
            }
        }

        private void consumeHighlightedSection(final Highlight highlight, final Consumer<Byte> consumer) {
            // range is inclusive at both ends
            final int lineFrom = highlight.getFrom().getLineNo();
            final int colFrom = highlight.getFrom().getColNo();
            final int lineTo = highlight.getTo().getLineNo();
            final int colTo = highlight.getTo().getColNo();

            boolean found = false;
            boolean inRecord = false;

            int advance = 0;
            int i = 0;
            offset.set(0);

            for (; offset.get() < length() && !found; i++) {
                final byte c = byteAt(i);

                // The offset where our potentially multi-byte char starts
                final int startOffset = offset.get();

                // This will move offset by the number of bytes in the 'character', i.e. 1-4
                final SizedString sizedString = byteStreamDecoder.decodeNextChar();

                // Inclusive
                if (!inRecord) {
                    if (lineNo > lineFrom ||
                            (lineNo == lineFrom && colNo >= colFrom)) {
                        inRecord = true;
                    }
                }

                // Inclusive
                if (inRecord) {
                    if (lineNo > lineTo ||
                            (lineNo == lineTo && colNo > colTo)) {
                        // Gone past the desired range
                        inRecord = false;
                        found = true;
                        // Work out offset of the first char outside the range
                        advance = startOffset + sizedString.getByteCount() - 1;
                    }
                }

                if (inRecord) {
                    // Pass all the bytes that make up our char onto the consumer
                    for (int j = 0; j < sizedString.getByteCount(); j++) {
                        final byte b = byteAt(startOffset + j);
                        consumer.accept(b);
                    }
                }

                // Advance the line or column number if we haven't found the record.
                if (!found) {
                    if (c == '\n') {
                        lineNo++;
                        colNo = BASE_COL_NO;
                    } else {
                        colNo++;
                    }
                }
            }

            // If we went through the whole buffer and didn't find what we are looking for then clear it.
            if (!found) {
                clear();
            } else {
                // Move the filter buffer forward and discard any content we no longer need.
                move(advance);
            }
        }

        @Override
        public void reset() {
            lineNo = BASE_LINE_NO;
            colNo = BASE_COL_NO;
            clear();
        }

        private byte byteAt(int index) {
            return byteBuffer.getByte(index);
        }

        private int length() {
            return byteBuffer.size();
        }

        private void move(final int len) {
            if (len > 0) {
                if (len >= byteBuffer.size()) {
                    byteBuffer.reset();
                } else {
                    byteBuffer.move(len);
                }
            }
        }

        private void clear() {
            byteBuffer.reset();
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class ByteBuffer extends ByteArrayOutputStream {

        byte getByte(final int index) {
            return buf[index];
        }

        void move(final int len) {
            if (count >= len) {
                System.arraycopy(buf, len, buf, 0, count - len);
                count -= len;
            }
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    private interface Buffer {
        Object getData(Highlight highlight);

        void clear(Highlight highlight);

        void reset();
    }
}
