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
import stroom.pipeline.reader.ByteStreamDecoder.DecodedChar;
import stroom.pipeline.stepping.Recorder;
import stroom.util.shared.TextRange;

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
import java.util.function.Supplier;

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
                throw new PipelineFactoryException(
                        "Attempt to link to an element that does not accept input or reader: "
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
    public Object getData(final TextRange textRange) {
        if (buffer == null) {
            return null;
        }
        return buffer.getData(textRange);
    }

    @Override
    public void clear(final TextRange textRange) {
        if (buffer != null) {
            buffer.clear(textRange);
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

    /**
     * Buffer for reading character data
     */
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
        public Object getData(final TextRange textRange) {
            if (textRange != null) {
                final StringBuilder sb = new StringBuilder();
                consumeHighlightedSection(textRange, sb::append);
                return sb.toString();
            }

            return null;
        }

        @Override
        public void clear(final TextRange textRange) {
            if (textRange == null) {
                clear();
            } else {
                consumeHighlightedSection(textRange, c -> {
                });
            }
        }

        @Override
        public void reset() {
            lineNo = BASE_LINE_NO;
            colNo = BASE_COL_NO;
            clear();
        }

        private void consumeHighlightedSection(final TextRange textRange,
                                               final Consumer<Character> consumer) {
            // range is inclusive at both ends
            final int lineFrom = textRange.getFrom().getLineNo();
            final int colFrom = textRange.getFrom().getColNo();
            final int lineTo = textRange.getTo().getLineNo();
            final int colTo = textRange.getTo().getColNo();

            boolean found = false;
            boolean inRecord = false;

            int advance = 0;
            int i = 0;
            for (; i < length() && !found; i++) {
                final char c = charAt(i);

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


    /**
     * Buffer for reading byte data with a provided encoding
     */
    private static class InputBuffer extends FilterInputStream implements Buffer {
        private static final int MAX_BUFFER_SIZE = 1000000;
        private final String encoding;
        private final ByteBuffer byteBuffer = new ByteBuffer();
        private final ByteStreamDecoder byteStreamDecoder;

        private int lineNo = BASE_LINE_NO;
        private int colNo = BASE_COL_NO;

        InputBuffer(final InputStream in, final String encoding) {
            super(in);
            this.encoding = encoding;
            this.byteStreamDecoder = new ByteStreamDecoder(encoding);
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
        public Object getData(final TextRange textRange) {
            if (textRange != null) {
                return getHighlightedSection(textRange);
            }

            return null;
        }

        private String getHighlightedSection(final TextRange textRange) {
            try {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                consumeHighlightedSection(textRange, baos::write);
                final String str = baos.toString(encoding);
                LOGGER.debug("str: [{}]", str);
                return str;
            } catch (final UnsupportedEncodingException e) {
                LOGGER.error(e.getMessage(), e);
                return e.getMessage();
            }
        }

        @Override
        public void clear(final TextRange textRange) {
            if (textRange == null) {
                clear();
            } else {
                consumeHighlightedSection(textRange, c -> {
                });
            }
        }

        private void consumeHighlightedSection(final TextRange textRange,
                                               final Consumer<Byte> consumer) {
            // range is inclusive at both ends
            final int lineFrom = textRange.getFrom().getLineNo();
            final int colFrom = textRange.getFrom().getColNo();
            final int lineTo = textRange.getTo().getLineNo();
            final int colTo = textRange.getTo().getColNo();

            boolean found = false;
            boolean inRecord = false;

            int advance = 0;
            final AtomicInteger offset = new AtomicInteger(0);

            final Supplier<Byte> byteSupplier = () ->
                    byteBuffer.getByte(offset.getAndIncrement());

            // TODO This could be made more efficient if scans the stream to look for the
            //  byte value (which may differ for different encodings) of the \n 'chars' until
            //  we get to the line of interest.  From that point we need to decode each char to
            //  see how may bytes it occupies.
            //  Need a kind of jumpToLine method
            while (offset.get() < length() && !found) {

                // The offset where our potentially multi-byte char starts
                final int startOffset = offset.get();

                // This will move offset by the number of bytes in the 'character', i.e. 1-4
                final DecodedChar decodedChar = byteStreamDecoder.decodeNextChar(byteSupplier);

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
                        advance = startOffset + decodedChar.getByteCount() - 1;
                    }
                }

                if (inRecord) {
                    // Pass all the bytes that make up our char onto the consumer
                    for (int j = 0; j < decodedChar.getByteCount(); j++) {
                        final byte b = byteAt(startOffset + j);
                        consumer.accept(b);
                    }
                }

                // Advance the line or column number if we haven't found the record.
                if (!found) {
                    if (decodedChar.isLineBreak()) {
                        lineNo++;
                        colNo = BASE_COL_NO;
                    } else if (decodedChar.isByteOrderMark() || decodedChar.isNonVisibleCharacter()) {
                        // We don't want to advance the line/col position if it is a non visual char
                        // but we still need to pass the non visual char on to the consumer
                        // as they might want to do something with it.
                        LOGGER.debug("BOM found at [{}:{}]", lineNo, colNo);
                    } else {
                        final int charCount = decodedChar.getCharCount();
                        if (LOGGER.isDebugEnabled() && charCount > 1) {
                            LOGGER.debug("Found multi-char 'character' [{}] with char count {} at [{}:{}]",
                                    decodedChar.getStr(),
                                    charCount,
                                    lineNo,
                                    colNo);
                        }
                        // Some 'characters', e.g. emoji are not only mult-byte but are
                        // represented as more than one char so we need
                        // to increment the colNo by the right number of chars
                        colNo += decodedChar.getCharCount();
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
        Object getData(TextRange textRange);

        void clear(TextRange textRange);

        void reset();
    }
}
