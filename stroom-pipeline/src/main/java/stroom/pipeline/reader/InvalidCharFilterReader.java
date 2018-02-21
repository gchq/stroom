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

package stroom.pipeline.reader;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

public class InvalidCharFilterReader extends FilterReader {
    private static final char SURROGATE1_MIN = 0xD800;
    private static final char SURROGATE1_MAX = 0xDBFF;

    private boolean modifiedContent;

    public InvalidCharFilterReader(final Reader reader) {
        super(reader);
    }

    @Override
    public int read(final char[] cbuf, final int offset, final int length) throws IOException {
        final int len = super.read(cbuf, offset, length);
        if (len < 0) {
            return len;
        }

        int i = offset;
        int j = offset;

        while (i < len) {
            final char c = cbuf[i++];

            if (c <= 31) {
                if (c == '\n' || c == '\r' || c == '\t') {
                    // Don't output any control characters other than new line,
                    // carriage return and tab.
                    cbuf[j++] = c;
                }
            } else if (c < 127) {
                cbuf[j++] = c;
            } else if (c < 160 || c == 0x2028) {
                // XML 1.0 and 1.1 requires these characters to be written as
                // character references.
            } else if (isHighSurrogate(c)) {
                // This is a 2 byte encoding.
                if (i < len - 1) {
                    cbuf[j++] = c;
                    cbuf[j++] = cbuf[i++];
                }
            } else if (c >= 0xfffe) {
                // Invalid characters in XML 1.0 and XML 1.1.
            } else {
                cbuf[j++] = c;
            }
        }

        final int newLen = j - offset;

        if (!modifiedContent && newLen != len) {
            modifiedContent = true;
        }

        return newLen;
    }

    /**
     * Test whether the given character is a high surrogate
     *
     * @param ch The character to test.
     * @return true if the character is the first character in a surrogate pair
     */
    private boolean isHighSurrogate(final int ch) {
        return (SURROGATE1_MIN <= ch && ch <= SURROGATE1_MAX);
    }

    public boolean hasModifiedContent() {
        return modifiedContent;
    }
}
