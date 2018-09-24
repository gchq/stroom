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
import java.io.StringReader;
import java.util.List;

public class ReaderRecorder extends AbstractReaderElement implements Recorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReaderRecorder.class);

    private Filter filter;

    @Override
    protected Reader insertFilter(final Reader reader) {
        filter = new Filter(reader);
        return filter;
    }

    @Override
    public Object getData(final List<Highlight> highlights) {
        if (filter == null) {
            return null;
        }

        if (highlights != null && highlights.size() > 0) {
            final String data = filter.toString();
            final StringReader reader = new StringReader(data);
            return getHighlightedSection(reader, highlights);
        }

        return null;
    }

    private String getHighlightedSection(final Reader reader, final List<Highlight> highlights) {
        final StringBuilder sb = new StringBuilder();

        try {
            int i;
            boolean found = false;
            int lineNo = 1;
            int colNo = 0;
            boolean inRecord = false;

            while ((i = reader.read()) != -1 && !found) {
                final char c = (char) i;

                if (c == '\n') {
                    lineNo++;
                    colNo = 0;
                } else {
                    colNo++;
                }

                for (final Highlight highlight : highlights) {
                    if (!inRecord) {
                        if (lineNo > highlight.getLineFrom() || (lineNo >= highlight.getLineFrom()
                                && colNo >= highlight.getColFrom())) {
                            inRecord = true;
                            break;
                        }
                    } else if (lineNo > highlight.getLineTo()
                            || (lineNo >= highlight.getLineTo() && colNo >= highlight.getColTo())) {
                        inRecord = false;
                        found = true;
                        break;
                    }
                }

                if (inRecord) {
                    sb.append(c);
                }
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return sb.toString();
    }

    @Override
    public void clear() {
//        if (filter != null) {
//            filter.clear();
//        }
    }

    @Override
    public void startStream() {
        super.startStream();
        if (filter != null) {
            filter.clear();
        }
    }

    private static class Filter extends FilterReader {
        private static final int MAX_BUFFER_SIZE = 1000000;
        private final StringBuilder stringBuilder = new StringBuilder();

        public Filter(final Reader in) {
            super(in);
        }

        @Override
        public int read(final char[] cbuf, final int off, final int len) throws IOException {
            final int length =  super.read(cbuf, off, len);
            if (length > 0 && stringBuilder.length() < MAX_BUFFER_SIZE) {
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
                if (stringBuilder.length() < MAX_BUFFER_SIZE) {
                    stringBuilder.append((char) result);
                }
            }
            return result;
        }

        public void clear() {
            stringBuilder.setLength(0);
        }

        @Override
        public String toString() {
            return stringBuilder.toString();
        }
    }
}
