/*
 * Copyright 2016 Crown Copyright
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class LineReader {
    /** How big our buffers are. This should always be a multiple of 8. */
    public static final int STREAM_BUFFER_SIZE = 1024 * 100;

    private final Reader reader;
    private final char[] buffer;
    private final StringBuilder lineBuffer;
    private int len;
    private int pos;

    public LineReader(final InputStream inputStream, final String charsetName) throws IOException {
        reader = new InputStreamReader(inputStream, charsetName);
        buffer = new char[STREAM_BUFFER_SIZE];
        lineBuffer = new StringBuilder();
    }

    public String nextLine() throws IOException {
        String line = null;

        // Append more of the previous buffer.
        for (; line == null && pos < len; pos++) {
            char c = buffer[pos];
            if (c == '\n') {
                line = lineBuffer.toString();
                lineBuffer.setLength(0);
            } else {
                lineBuffer.append(c);
            }
        }

        // If we haven't get a line yet then add more to the buffer and try and
        // produce the next line.
        if (line == null && len != -1) {
            while (line == null && (len = reader.read(buffer)) != -1) {
                for (pos = 0; line == null && pos < len; pos++) {
                    char c = buffer[pos];
                    if (c == '\n') {
                        line = lineBuffer.toString();
                        lineBuffer.setLength(0);
                    } else {
                        lineBuffer.append(c);
                    }
                }
            }

            if (len == -1) {
                reader.close();
            }
        }

        return line;
    }
}
