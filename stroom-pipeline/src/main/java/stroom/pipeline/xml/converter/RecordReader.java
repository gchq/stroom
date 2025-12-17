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

package stroom.pipeline.xml.converter;

import stroom.util.io.WrappedReader;

import java.io.IOException;
import java.io.Reader;

/**
 * <p>
 * Class used to chunk up records from a flat file.
 * </p>
 * <p>
 * <p>
 * By default delimiters are new lines '\n' but can be set to any sequence of
 * characters, i.e. the input stream can have all of it's content on a single
 * line with record delimiters at numerous points along the line.
 * </p>
 */
public final class RecordReader extends WrappedReader {
    private static final char[] NEW_LINE = {'\n'};
    private char[] delimiter = NEW_LINE;
    private long recordCount = 0;

    private StringBuilder sb;
    private int c;
    private int lastIndex;
    private int delimIndex;
    private char firstChar;
    private char delimChar;

    private boolean foundRecord;

    public RecordReader(final Reader reader) {
        this(reader, null);
    }

    /**
     * Pass the reader that is to be wrapped plus the delimiter string to be
     * used. If a null delimiter is supplied then new line '\n' shall be used by
     * default. Delimiters can be any string of characters, e.g. "----".
     *
     * @param reader input stream
     */
    public RecordReader(final Reader reader, final String recordDelimiter) {
        super(reader);
        if (recordDelimiter != null) {
            delimiter = recordDelimiter.toCharArray();
        }

        sb = new StringBuilder(1000);
        lastIndex = delimiter.length - 1;
        firstChar = delimiter[0];
    }

    public String readRecord() throws IOException {
        // Clear the buffer and reset fields.
        sb.setLength(0);
        delimIndex = 0;
        delimChar = firstChar;
        foundRecord = false;

        while (!foundRecord && (c = read()) != -1) {
            // Strip carriage return characters.
            if (c != '\r') {
                sb.append((char) c);

                if (c == delimChar) {
                    if (delimIndex == lastIndex) {
                        delimIndex = 0;
                        delimChar = firstChar;
                        foundRecord = true;
                    } else {
                        delimIndex++;
                        delimChar = delimiter[delimIndex];
                    }
                } else {
                    delimIndex = 0;
                    delimChar = firstChar;
                }

                if (foundRecord) {
                    // Strip the delimiter off the end of the buffer.
                    sb.setLength(sb.length() - delimiter.length);

                    // Hit a delimiter ... only break if we have some content
                    // (i.e. skip empty tokens)
                    if (sb.length() > 0) {
                        recordCount++;
                    } else {
                        foundRecord = false;
                    }
                }
            }
        }

        // Hit EOF? return null if we have no token
        if (sb.length() == 0) {
            return null;
        }

        return sb.toString();
    }

    public long getRecordCount() {
        return recordCount;
    }
}
