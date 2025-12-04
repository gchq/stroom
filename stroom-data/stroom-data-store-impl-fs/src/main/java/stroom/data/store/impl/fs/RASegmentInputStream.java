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

package stroom.data.store.impl.fs;

import stroom.data.store.api.SegmentInputStream;
import stroom.util.io.SeekableInputStream;
import stroom.util.io.StreamUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * This is an implementation of <code>SegmentInputStream</code> that uses random
 * access files for the data and index.
 * <p>
 * Also new handles working within a windows on the underlying data. With this
 * mode the segments are logical (i.e. they start at 0 regardless of the
 * window).
 */
public class RASegmentInputStream extends SegmentInputStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(RASegmentInputStream.class);
    private static final int INT8 = 8;
    private final byte[] eightBytes = new byte[INT8];
    private final byte[] singleByte = new byte[1];
    private final LongBuffer longBuffer = ByteBuffer.wrap(eightBytes).asLongBuffer();
    private InputStream data;
    private InputStream indexInputStream;
    private Set<Long> included;
    private Iterator<Long> includedIterator;
    private Set<Long> excluded;
    private Iterator<Long> excludedIterator;
    private boolean includeAll = true;
    private ByteRange range;
    private long windowPos = 0;
    private long windowByteStart = 0;
    private long windowByteEnd = 0;
    // The segment we start including data at
    private long windowSegmentStart = 0;
    private long windowSegmentCount = 0;
    private long totalSegmentCount;

//    RASegmentInputStream(final InputStream data,
//                         final InputStream inputStream) {
//        this(data, inputStream);
//    }
//
//    RASegmentInputStream(final InputStream data,
//                         final InputStream inputStream,
//                         final long byteStart,
//                         final long byteEnd) {
//        this(data, () -> inputStream, byteStart, byteEnd);
//    }

    public RASegmentInputStream(final InputStream data, final InputStream indexInputStream) {
        try {
            this.data = data;
            this.indexInputStream = indexInputStream;

            initWindow(0, ((SeekableInputStream) data).getSize());
        } catch (final IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    RASegmentInputStream(final InputStream data,
                         final InputStream indexInputStream,
                         final long byteStart,
                         final long byteEnd) {
        try {
            this.data = data;
            this.indexInputStream = indexInputStream;

            initWindow(byteStart, byteEnd);
        } catch (final IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    private long getDataSize() throws IOException {
        return ((SeekableInputStream) data).getSize();
    }

    private void initWindow(final long byteStart, final long byteEnd) throws IOException {
        totalSegmentCount = (((SeekableInputStream) indexInputStream).getSize() / INT8) + 1;

        // If the window starts at 0 we start at segment 0 otherwise we need to
        // look at which segment includes byteStart
        long windowSegmentStart = 0;
        if (byteStart > 0) {
            windowSegmentStart = segmentAtByteOffset(byteStart, true);
        }
        // If the windows ends before the size of the file we end at
        // totalSegmentCount - 1,
        // otherwise find the segment including byteEnd
        long windowSegmentEnd = totalSegmentCount - 1;
        if (byteEnd < getDataSize()) {
            windowSegmentEnd = segmentAtByteOffset(byteEnd, false);
        }

        windowSegmentCount = windowSegmentEnd - windowSegmentStart + 1;

        this.windowSegmentStart = windowSegmentStart;

        windowByteStart = byteStart;
        windowByteEnd = byteEnd;

        if (windowByteStart > 0) {
            data.skip(windowByteStart);
        }

        windowPos = windowByteStart;

        // Make sure we are at the start of the window.
        doSeek(windowPos);
    }

    /**
     * This method returns the total number of segments that can be read from
     * this input stream.
     *
     * @return The total number of segments that can be read from this input
     * stream.
     */
    @Override
    public long count() {
        return windowSegmentCount;
    }

    /**
     * Includes a specific segment number when reading from this input stream.
     *
     * @param segment The segment to include.
     */
    @Override
    public void include(final long segment) {
        check(segment);
        includeAll = false;

        if (included == null) {
            included = new TreeSet<>();
        }

        included.add(segment);
    }

    /**
     * Includes all segments when reading from this input stream. This is the
     * default behaviour if no segments are specifically included or excluded.
     */
    @Override
    public void includeAll() {
        check(0);
        includeAll = true;
        included = null;
        excluded = null;
    }

    /**
     * Excludes a specific segment number when reading from this input stream.
     * Initially all segments are included so setting this will exclude only the
     * specified segment.
     *
     * @param segment The segment to exclude.
     */
    @Override
    public void exclude(final long segment) {
        check(segment);
        includeAll = true;

        if (excluded == null) {
            excluded = new TreeSet<>();
        }

        excluded.add(segment);
    }

    /**
     * Excludes all segments when reading from this input stream. It is unlikely
     * that all input should be excluded, instead this method should be used to
     * clear all includes that have been specifically set.
     */
    @Override
    public void excludeAll() {
        check(0);
        includeAll = false;
        included = null;
        excluded = null;
    }

    /**
     * Indicates if there are more bytes available to read. This implementation
     * returns 1 if there are more bytes to read or 0 if not. This method will
     * throw an IOException if the data and index files are closed.
     *
     * @return 1 if there are bytes to read, 0 otherwise.
     * @throws IOException Thrown if either the data or index streams are closed.
     * @see java.io.InputStream#available()
     */
    @Override
    public int available() throws IOException {
        if (data == null || indexInputStream == null) {
            throw new IOException("Stream closed");
        }

        if (windowPos < windowByteEnd - 1) {
            return 1;
        }

        return 0;
    }

    /**
     * Closes the data and index input streams.
     *
     * @throws IOException Could be thrown while closing the data or index.
     * @see java.io.InputStream#close()
     */
    @Override
    public void close() throws IOException {
        if (data != null) {
            try {
                data.close();
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to close data stream!", e);
            }
        }
        if (indexInputStream != null) {
            try {
                indexInputStream.close();
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to close index stream!", e);
            }
        }

        data = null;
        indexInputStream = null;

        super.close();
    }

    /**
     * Do a read keeping a watch on the end window.
     */
    private int doRead(final byte[] b, final int off, final int len) throws IOException {
        int readLen = len;

        if ((windowPos + len) > windowByteEnd) {
            readLen = (int) (windowByteEnd - windowPos);
            if (readLen == 0) {
                return -1;
            }
        }

        final int totalBytesRead = data.read(b, off, readLen);
        windowPos += totalBytesRead;
        return totalBytesRead;
    }

    private void doSeek(final long pos) throws IOException {
        if (pos < windowByteStart) {
            ((SeekableInputStream) data).seek(windowByteStart);
        } else {
            ((SeekableInputStream) data).seek(pos);
        }
    }

    /**
     * Reads up to <code>len</code> bytes of data from the input stream into an
     * array of bytes. An attempt is made to read as many as <code>len</code>
     * bytes, but a smaller number may be read. The number of bytes actually
     * read is returned as an integer.
     * <p>
     * <p>
     * This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     * <p>
     * <p>
     * If <code>len</code> is zero, then no bytes are read and <code>0</code> is
     * returned; otherwise, there is an attempt to read at least one byte. If no
     * byte is available because the stream is at end of file, the value
     * <code>-1</code> is returned; otherwise, at least one byte is read and
     * stored into <code>b</code>.
     * <p>
     * <p>
     * The first byte read is stored into element <code>b[off]</code>, the next
     * one into <code>b[off+1]</code>, and so on. The number of bytes read is,
     * at most, equal to <code>len</code>. Let <i>k</i> be the number of bytes
     * actually read; these bytes will be stored in elements <code>b[off]</code>
     * through <code>b[off+</code><i>k</i><code>-1]</code>, leaving elements
     * <code>b[off+</code><i>k</i><code>]</code> through
     * <code>b[off+len-1]</code> unaffected.
     * <p>
     * <p>
     * In every case, elements <code>b[0]</code> through <code>b[off]</code> and
     * elements <code>b[off+len]</code> through <code>b[b.length-1]</code> are
     * unaffected.
     * <p>
     * <p>
     * The <code>read(b,</code> <code>off,</code> <code>len)</code> method for
     * class <code>InputStream</code> simply calls the method
     * <code>read()</code> repeatedly. If the first such call results in an
     * <code>IOException</code>, that exception is returned from the call to the
     * <code>read(b,</code> <code>off,</code> <code>len)</code> method. If any
     * subsequent call to <code>read()</code> results in a
     * <code>IOException</code>, the exception is caught and treated as if it
     * were end of file; the bytes read up to that point are stored into
     * <code>b</code> and the number of bytes read before the exception occurred
     * is returned. The default implementation of this method blocks until the
     * requested amount of input data <code>len</code> has been read, end of
     * file is detected, or an exception is thrown. Subclasses are encouraged to
     * provide a more efficient implementation of this method.
     *
     * @param b   the buffer into which the data is read.
     * @param off the start offset in array <code>b</code> at which the data is
     *            written.
     * @param len the maximum number of bytes to read.
     * @return the total number of bytes read into the buffer, or
     * <code>-1</code> if there is no more data because the end of the
     * stream has been reached.
     * @throws IOException If the first byte cannot be read for any reason other than
     *                     end of file, or if the input stream has been closed, or if
     *                     some other I/O error occurs.
     * @see java.io.InputStream#read()
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        int totalBytesRead = 0;

        if (data == null || indexInputStream == null) {
            throw new IOException("Stream closed");
        }

        if (includeAll) {
            // If we are including all and haven't specified specific excludes
            // then just read the data stream sequentially.
            if (excluded == null || excluded.size() == 0) {
                totalBytesRead = doRead(b, off, len);

            } else {
                // Otherwise we are assuming all segments are included except
                // ones that are specifically excluded.

                // Get the initial excluded range.
                if (windowPos == windowByteStart) {
                    excludedIterator = excluded.iterator();
                    range = getNextExcludedRange();
                }

                int bytesRead = 0;
                int remaining = len;
                int offset = off;
                while (remaining > 0 && bytesRead != -1) {
                    while (range != null && windowPos == range.getStart()) {
                        // Jump over the excluded data.
                        windowPos += range.getEnd() - range.getStart();
                        doSeek(windowPos);

                        // Get the next excluded range.
                        range = getNextExcludedRange();
                    }

                    // Find out how much data we can read before the start of
                    // the next range.
                    int available = remaining;
                    if (range != null) {
                        final long beforeRangeStart = range.getStart() - windowPos;
                        if (available > beforeRangeStart) {
                            available = (int) beforeRangeStart;
                        }
                    }

                    // Read what is available before the next range.
                    // We need to cater here for windows ranges of 0 bytes long
                    if (available > 0) {
                        // +ve range
                        bytesRead = doRead(b, offset, available);
                        if (bytesRead != -1) {
                            totalBytesRead += bytesRead;
                            offset += bytesRead;
                            remaining -= bytesRead;
                        }
                    } else {
                        // empty range
                        remaining = 0;
                    }
                }
            }
        } else {
            // If we are excluding all and haven't specified specific includes
            // then just return -1.
            if (included == null || included.size() == 0) {
                totalBytesRead = -1;

            } else {
                // Otherwise we are assuming all segments are excluded except
                // ones that are specifically included.

                // Get the initial included range.
                if (windowPos == windowByteStart) {
                    includedIterator = included.iterator();
                    range = getNextIncludedRange();
                }

                int bytesRead = 0;
                int remaining = len;
                int offset = off;
                while (remaining > 0 && bytesRead != -1 && range != null) {
                    // Jump to the start of the included range if we aren't
                    // there already.
                    if (windowPos < range.getStart()) {
                        windowPos = range.getStart();
                        doSeek(windowPos);
                    }

                    // Find out how much data we can read before we need the
                    // next range.
                    int available = remaining;
                    long beforeRangeEnd = range.getEnd() - windowPos;
                    if (available > beforeRangeEnd) {
                        available = (int) beforeRangeEnd;
                    }

                    // Read what is available before the next range.
                    // We need to cater here for windows ranges of 0 bytes long
                    if (available > 0) {
                        // Read what is available before the range end.
                        bytesRead = doRead(b, offset, available);
                        if (bytesRead != -1) {
                            totalBytesRead += bytesRead;
                            offset += bytesRead;
                            remaining -= bytesRead;
                            beforeRangeEnd -= bytesRead;
                        }
                    } else {
                        // Empty range
                        beforeRangeEnd = 0;
                    }

                    if (beforeRangeEnd == 0) {
                        // Get the next included range for next time.
                        range = getNextIncludedRange();
                    }
                }
            }
        }

        // If we didn't read anything then we must be at the end of the file.
        if (totalBytesRead == 0) {
            return -1;
        }

//        LOGGER.info("bytes: {} {}", ByteArrayUtils.byteArrayToHex(b, off, len), b[off]);
        return totalBytesRead;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Reads a byte of data from this input stream. This method blocks if no
     * input is yet available.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the file
     * is reached.
     */
    @Override
    public int read() throws IOException {
        final int len = read(singleByte);
        if (len == -1) {
            return -1; // end of stream
        }
        // result of read must be 0-255 (unsigned) so we need to convert our
        // signed byte to unsigned.
        return singleByte[0] & 0xff;
    }

    /**
     * Gets the byte range for the next included segment.
     */
    private ByteRange getNextIncludedRange() throws IOException {
        if (includedIterator.hasNext()) {
            final long segment = includedIterator.next();
            return getSegmentRange(segment);
        }

        return null;
    }

    /**
     * Gets the byte range for the next excluded segment.
     */
    private ByteRange getNextExcludedRange() throws IOException {
        while (excludedIterator.hasNext()) {
            final long segment = excludedIterator.next();
            return getSegmentRange(segment);
        }

        return null;
    }

    /**
     * This method gets the range of bytes covered by a particualar segment.
     */
    private ByteRange getSegmentRange(final long segment) throws IOException {
        long from = windowByteStart;
        long to = windowByteEnd;

        if (segment > 0) {
            from = getOffset(segment - 1);
        }

        if (segment < count() - 1) {
            to = getOffset(segment);
        }

        return new ByteRange(from, to);
    }

    /**
     * Gets the byte offset in the data file for a specified position in the
     * index.
     *
     * @param pos The index position.
     * @return The byte offset in the data file.
     */
    private long getOffset(final long pos) throws IOException {
        final long seekPos = pos + windowSegmentStart;
        if (seekPos == -1) {
            // Implies start of file
            return 0;
        }

        ((SeekableInputStream) indexInputStream).seek((seekPos) * INT8);

        StreamUtil.eagerRead(indexInputStream, eightBytes);
        longBuffer.rewind();
        return longBuffer.get();
    }

    /**
     * Checks that no includes or excludes are added once the stream is being
     * read.
     */
    private void check(final long segment) {
        if (segment < 0 || segment >= windowSegmentCount) {
            throw new RuntimeException(
                    "Segment number " + segment + " is not within bounds [0-" + windowSegmentCount + "]");
        }

        if (windowPos > windowByteStart) {
            throw new RuntimeException("Cannot include a new segment as reading is in progress");
        }
    }

    public void setIncluded(final NavigableSet<Long> included) {
        this.included = included;
    }

    public void setExcluded(final NavigableSet<Long> excluded) {
        this.excluded = excluded;
    }

    /**
     * @return false ... we don't support this
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void mark(final int readlimit) {
        throw new IllegalStateException("Segmented Stream does not support mark and reset");
    }

    @Override
    public void reset() {
        throw new IllegalStateException("Segmented Stream does not support mark and reset");
    }

    /**
     * Return the byte offset in the underlying stream given a segment number
     */
    long byteOffset(final long segment) throws IOException {
        return byteOffset(segment, true);
    }

    /**
     * @param segment    to find the byte offset of
     * @param lowerBound if you want the start of the end byte pos
     */
    private long byteOffset(final long segment, final boolean lowerBound) throws IOException {
        // start pos ?
        if (lowerBound) {
            if (segment == 0) {
                return 0;
            } else if (segment == totalSegmentCount) {
                return windowByteEnd;
            } else {
                return getOffset(segment - 1);
            }
        } else {
            // end of block
            if (segment == totalSegmentCount) {
                return windowByteEnd;
            } else {
                return getOffset(segment);
            }
        }
    }

    /**
     * Return the segment number given a byte position
     */
    long segmentAtByteOffset(final long findBytePos) throws IOException {
        return segmentAtByteOffset(findBytePos, true);
    }

    /**
     * Look for the segment at a given offset ... if lower bound is true then it
     * will look for the lowest one skipping back over any empty segments
     * otherwise it will skip forward.
     */
    long segmentAtByteOffset(final long findBytePos, final boolean lowerBound) throws IOException {
        // Seek past EOF?
        if (findBytePos > getDataSize()) {
            return -1;
        }
        final long workingSegment = totalSegmentCount;
        return segmentAtByteOffset(findBytePos, 0, workingSegment, lowerBound);
    }

    /**
     * Perform a search for a segment given a byte offset.
     */
    private long segmentAtByteOffset(final long findBytePos, final long workingLowerSegment,
                                     final long workingUpperSegment, final boolean lowerBound) throws IOException {
        // Get the mid point
        long midPointSegment = (workingLowerSegment + workingUpperSegment) / 2;
        long midPointBytePos = byteOffset(midPointSegment);

        // Case 1 Match
        if (findBytePos == midPointBytePos) {
            // We need to test for empty segments here
            if (lowerBound) {
                // Skip back over any empty segments
                long prevMidPointBytePos;
                while ((midPointSegment > 0)
                       && (prevMidPointBytePos = byteOffset(midPointSegment - 1)) == midPointBytePos) {
                    midPointSegment--;
                    midPointBytePos = prevMidPointBytePos;
                }
            } else {
                // Skip forward over any empty segments
                long nextMidPointBytePos;
                while ((midPointSegment + 1 < totalSegmentCount)
                       && (nextMidPointBytePos = byteOffset(midPointSegment + 1, false)) == midPointBytePos) {
                    midPointSegment++;
                    midPointBytePos = nextMidPointBytePos;
                }
            }
            return midPointSegment;
        }

        // Case 2 Our mid point is too High
        if (findBytePos < midPointBytePos) {
            // Scan the lower half
            return segmentAtByteOffset(findBytePos, workingLowerSegment, midPointSegment, lowerBound);
        }

        // Case 3 Our mid point is too Low but we can't seek any more ... best
        // match we are going to get
        if (midPointSegment + 1 >= workingUpperSegment) {
            return midPointSegment;
        }
        return segmentAtByteOffset(findBytePos, midPointSegment, workingUpperSegment, lowerBound);

    }

    @Override
    public long size() {
        return windowByteEnd - windowByteStart;
    }

    private static class ByteRange {

        private final long start;
        private final long end;

        ByteRange(final long start, final long end) {
            this.start = start;
            this.end = end;

            assert start >= 0 && end >= 0;
            assert start <= end;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        @Override
        public String toString() {
            return start + ":" + end;
        }
    }
}
