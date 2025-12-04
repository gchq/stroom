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

package stroom.util.shared.string;

import stroom.util.shared.Range;

public class HexDumpLine {

    private final String line;
    // The number (1 based) of this line in a complete hex dump
    private final int lineNo;
    // The range of bytes covered by this line
    private final Range<Long> byteOffsetRange;

    public HexDumpLine(final String line,
                       final int lineNo,
                       final Range<Long> byteOffsetRange) {
        this.line = line;
        this.lineNo = lineNo;
        this.byteOffsetRange = byteOffsetRange;
    }

    public String getLine() {
        return line;
    }

    public int getLineNo() {
        return lineNo;
    }

    public long getByteCount() {
        return byteOffsetRange.size().longValue();
    }

    public Range<Long> getByteOffsetRange() {
        return byteOffsetRange;
    }

    /**
     * @return The size of the hex dump line when output as a string. Not the
     * number of chars rendered by the hex dump.
     */
    public int getDumpLineCharCount() {
        return line.length();
    }

    @Override
    public String toString() {
        return "HexDumpLine{" +
                "line='" + line + '\'' +
                ", lineNo=" + lineNo +
                ", byteOffsetRange=" + byteOffsetRange +
                '}';
    }
}
