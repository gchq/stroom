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

package stroom.pipeline.server.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.server.task.Recorder;
import stroom.util.shared.Highlight;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.function.Consumer;

public class ReaderRecorder extends AbstractReaderElement implements Recorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderRecorder.class);

    private Buffer buffer;
    private int lineNo = 1;
    private int colNo = 0;

    @Override
    protected Reader insertFilter(final Reader reader) {
        buffer = new Buffer(reader);
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
        final StringBuilder sb = new StringBuilder();
        consumeHighlightedSection(highlight, sb::append);
        return sb.toString();
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

    private void consumeHighlightedSection(final Highlight highlight, final Consumer<Character> consumer) {
        final int lineFrom = highlight.getLineFrom();
        final int colFrom = highlight.getColFrom();
        final int lineTo = highlight.getLineTo();
        final int colTo = highlight.getColTo();

        boolean found = false;
        boolean inRecord = false;

        int advance = 0;
        int i = 0;
        for (; i < buffer.length() && !found; i++) {
            final char c = buffer.charAt(i);

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

    private static class Buffer extends FilterReader {
        private static final int MAX_BUFFER_SIZE = 1000000;
        private final StringBuilder stringBuilder = new StringBuilder();

        public Buffer(final Reader in) {
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

        char charAt(int index) {
            return stringBuilder.charAt(index);
        }

        int length() {
            return stringBuilder.length();
        }

        void move(final int len) {
            if (len > 0) {
                if (len >= stringBuilder.length()) {
                    stringBuilder.setLength(0);
                } else {
                    stringBuilder.delete(0, len);
                }
            }
        }

        void clear() {
            stringBuilder.setLength(0);
        }
    }
}
