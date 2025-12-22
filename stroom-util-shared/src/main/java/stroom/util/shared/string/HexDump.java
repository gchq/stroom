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

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class HexDump {

    public static final int MAX_BYTES_PER_LINE = 32;
    // This depends on how we render the line of MAX_BYTES_PER_LINE (includes the  at the end \n)
    public static final int MAX_CHARS_PER_DUMP_LINE = 149;

    private final List<HexDumpLine> lines;
    // The charset of the decoded bytes.
    private final Charset charset;
    // The range covered by all lines in this hex dump
    private final Range<Long> byteOffsetRange;

    public HexDump(final List<HexDumpLine> lines,
                   final Charset charset,
                   final Range<Long> byteOffsetRange) {
        this.lines = lines;
        this.charset = charset;
        this.byteOffsetRange = byteOffsetRange;
    }

    public List<HexDumpLine> getLines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public Charset getCharset() {
        return charset;
    }

    public Optional<HexDumpLine> getLine(final int lineNo) {
        if (lines.isEmpty()) {
            return Optional.empty();
        } else {
            final long firstLineNo = lines.get(0).getLineNo();
            if (lineNo < firstLineNo) {
                return Optional.empty();
            } else {
                final long idx = lineNo - firstLineNo;
                if (idx >= lines.size()) {
                    return Optional.empty();
                } else {
                    return Optional.ofNullable(lines.get((int) idx));
                }
            }
        }
    }

    public int getLineCount() {
        return lines.size();
    }

    public Optional<HexDumpLine> getFirstLine() {
        if (lines.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(lines.get(0));
        }
    }

    public Optional<HexDumpLine> getLastLine() {
        if (lines.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(lines.get(lines.size() - 1));
        }
    }

    /**
     * @return Number of bytes rendered by the hex dump.
     */
    public long getDumpByteCount() {
        return byteOffsetRange.size().longValue();
    }

    /**
     * @return The range of bytes rendered by the hex dump, e.g. if only a subset of
     * an {@link InputStream} is dumped.
     */
    public Range<Long> getByteOffsetRange() {
        return byteOffsetRange;
    }

    /**
     * @return The size of the hex dump when output as a string. Not the
     * number of chars rendered by the hex dump.
     */
    public long getDumpCharCount() {
        final int lineBreakCount = Math.max(0, lines.size() - 1); // No \n on last line
        return lines.stream()
                .mapToInt(HexDumpLine::getDumpLineCharCount) // ignore lack of \n at this point
                .sum()
                + lineBreakCount;
    }

    public String getHexDumpAsStr() {
        return lines.stream()
                .map(HexDumpLine::getLine)
                .collect(Collectors.joining("\n"));
    }

    @Override
    public String toString() {
        return "HexDump{" +
                "lines=" + lines +
                ", charset=" + charset +
                '}';
    }
}
