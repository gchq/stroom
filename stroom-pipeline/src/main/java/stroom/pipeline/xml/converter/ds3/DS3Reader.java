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

package stroom.pipeline.xml.converter.ds3;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;

import org.xml.sax.Locator;

import java.io.IOException;
import java.io.Reader;

public class DS3Reader extends CharBuffer implements DSLocator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DS3Reader.class);
    private static final int LINE_NO_BASE = 1;
    private static final int COL_NO_BASE = 1;
    private static final char EMPTY_LAST_CHAR = 0; // Anything except a \n
    private final int initialSize;
    private final int capacity;
    private final int halfCapacity;

    private Reader reader;
    private boolean eof;
    private int lineNo = LINE_NO_BASE;
    private int colNo = COL_NO_BASE;


    private char lastCharRead = EMPTY_LAST_CHAR;
    private boolean isFirstChar = true;
    private int currentLineNo = LINE_NO_BASE;
    private int currentColNo = COL_NO_BASE;

    public DS3Reader(final Reader reader, final int initialSize, final int capacity) {
        super(initialSize);
        setReader(reader);

        if (initialSize < 8) {
            throw new IllegalStateException("The initial size must be greater than or equal to 8");
        }
        if (capacity < initialSize) {
            throw new IllegalStateException("Capacity must be greater or equal to " + initialSize);
        }
        if (initialSize > capacity) {
            throw new IllegalStateException("The initial size cannot be greater than the capacity");
        }

        this.capacity = capacity;
        this.initialSize = initialSize;
        this.halfCapacity = capacity / 2;
    }

    public void setReader(final Reader reader) {
        this.reader = reader;
        offset = 0;
        length = 0;
        lineNo = LINE_NO_BASE;
        colNo = COL_NO_BASE;
        currentLineNo = LINE_NO_BASE;
        currentColNo = COL_NO_BASE;
        eof = false;
        lastCharRead = EMPTY_LAST_CHAR;
        isFirstChar = true;
    }

    public void fillBuffer() throws IOException {
        // Only fill the buffer if we haven't reached the end of the file.
        if (!eof) {
            int i = 0;

            // Only fill the buffer if the length of remaining characters is
            // less than half the capacity.
            if (length < halfCapacity) {
                // If we processed more than half of the capacity then we need
                // to shift the buffer up so that the offset becomes 0. This
                // will give us
                // room to fill more of the buffer.
                if (offset >= halfCapacity) {
                    System.arraycopy(buffer, offset, buffer, 0, length);
                    offset = 0;
                }

                // Now fill any remaining capacity.
                int maxLength = capacity - offset;
                while (maxLength > length && (i = reader.read()) != -1) {
                    final char c = (char) i;

                    // Strip out carriage returns and other control characters.
                    if (c >= ' ' || c == '\n' || c == '\t') {
                        // If we have filled the buffer then double the size of
                        // it up to the maximum capacity.
                        if (buffer.length == offset + length) {
                            int newLen = buffer.length * 2;
                            if (newLen == 0) {
                                newLen = initialSize;
                            } else if (newLen > capacity) {
                                newLen = capacity;
                            }
                            final char[] tmp = new char[newLen];
                            System.arraycopy(buffer, offset, tmp, 0, length);
                            offset = 0;
                            buffer = tmp;

                            // Now the offset has been reset to 0 we should set
                            // the max length to be the maximum capacity.
                            maxLength = capacity;
                        }

                        buffer[offset + length] = c;
                        length++;
                    }
                }
            }

            // Determine if we reached the end of the file.
            eof = i == -1;
        }
    }

    public void close() throws IOException {
        reader.close();
    }

    public boolean isEof() {
        return eof;
    }

    @Override
    public void move(final int increment) {
        // The position of the line break is not included in our location/highlight
        // range but is counted for advancing our current position in the input.

        //     000000000111111
        //   | 123456789012345
        //   +----------------
        //  1| hello↵        - 1:1=>1:5 (inc.)
        //  2|
        //  3| multi-↵
        //  4| line↵         - 3:1=>4:4 (inc.)
        //  5|
        //  6| with↵
        //  7| ↵
        //  8| gap↵          - 6:1=>8:3 (inc.)
        //  9|
        // 10| hello|world   - 1:1=>1:6, 1:7=>1:11 (pipe delim records)
        // 11|
        // 12| MonTueWed     - 1:1=>1:3, 1:4=>1:6, 1:7=>1:9 regex records: ([A-Z][a-z]{2})

        for (int i = 0; i < increment; i++) {
            final int pos = offset + i;
            final char c = buffer[pos];
            if (isFirstChar) {
                isFirstChar = false;
                // No need to advance as our pos is already 1:1
            } else if (lastCharRead == '\n') {
                // char before this one was a line break so this char is
                // now on the next line at the base col
                currentLineNo++;
                currentColNo = COL_NO_BASE;
            } else if (c == '\n') {
                // This char is a line break so leave position as it was so
                // our range is up to the last non \n char
                LOGGER.trace("Found \\n, currentLineNo: {}, currentColNo: {}", currentLineNo, currentColNo);
            } else {
                currentColNo++;
            }
//            LOGGER.info("offset: {}, char [{}], lastChar: [{}], currentLineNo: {}, currentColNo: {}",
//                    offset + i,
//                    toString(buffer[pos]),
//                    toString(lastCharRead),
//                    currentLineNo,
//                    currentColNo);

            lastCharRead = c;
        }
        super.move(increment);
    }

    /**
     * Changes the current location of the reader so that any errors or warnings
     * can be linked to the current location.
     */
    public void changeLocation() {
        logLocationInfo("changeLocation() ");
        if (lastCharRead == '\n') {
            // We don't want our start pos to be the end of the prev line so advance it
            lineNo = currentLineNo + 1;
            colNo = COL_NO_BASE;
        } else {
            lineNo = currentLineNo;
            // After an inline record delimiter add one to set the start pos after the delimiter
            colNo = currentColNo + 1;
        }
    }

    public Location getCurrentLocationAsStart() {
        final int startLineNo;
        final int startColNo;

        // The current(Line|Col)No does not advance on a line break so to get the location
        // as a start position we need to respect the line break so our start is on the next
        // line
        if (lastCharRead == '\n') {
            // We don't want our start pos to be the end of the prev line so advance it
            startLineNo = currentLineNo + 1;
            startColNo = COL_NO_BASE;
        } else {
            startLineNo = currentLineNo;
            // After an inline record delimiter add one to set the start pos after the delimiter
            startColNo = currentColNo + 1;
        }

        return DefaultLocation.of(startLineNo, startColNo);
    }

    public Location getCurrentLocationAsEnd() {
        return DefaultLocation.of(currentLineNo, currentColNo);
    }

    @Override
    public int getLineNumber() {
        return lineNo;
    }

    @Override
    public int getColumnNumber() {
        return colNo;
    }

    public Locator getRecordStartLocator() {
        return new DefaultLocator() {
            @Override
            public int getLineNumber() {
                logLocationInfo("getRecordStartLocator getLineNumber()  ");
                return lineNo;
            }

            @Override
            public int getColumnNumber() {
                logLocationInfo("getRecordStartLocator getColumnNumber()");
                return colNo;
            }
        };
    }

    public Locator getRecordEndLocator() {
        return new DefaultLocator() {
            @Override
            public int getLineNumber() {
                logLocationInfo("getRecordEndLocator getLineNumber()  ");
                return getCurrentLocationAsEnd().getLineNo();
            }

            @Override
            public int getColumnNumber() {
                logLocationInfo("getRecordEndLocator getColumnNumber()");
                return getCurrentLocationAsEnd().getColNo();
            }
        };
    }

    private void logLocationInfo(final String name) {
        LOGGER.trace(() -> LogUtil.message(
                "{} offset: {}, char [{}], lastChar: [{}], lineNo: {}, colNo: {}, currentLineNo: {}, currentColNo: {}",
                name,
                offset,
                charToVisible(buffer[offset]),
                charToVisible(lastCharRead),
                lineNo,
                colNo,
                currentLineNo,
                currentColNo));
    }

    private String charToVisible(final char c) {
        if (c == 0) {
            return "";
        } else if (c == '\n') {
            return "↵";
        } else if (c == '\t') {
            return "↹";
        } else {
            return String.valueOf(c);
        }
    }
}
