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
