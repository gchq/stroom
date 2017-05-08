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

package stroom.streamstore.server.fs.serializable;

import stroom.io.SeekableInputStream;
import stroom.io.StreamCloser;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.shared.StreamType;

import java.io.IOException;
import java.io.InputStream;

/**
 * Wrapper for a nested input stream.
 *
 * You must call getNextEntry and closeEntry like the ZIP API.
 */
public class StreamSourceInputStreamProviderImpl implements StreamSourceInputStreamProvider {
    private Long segmentCount = null;

    private final StreamSource streamSource;
    private InputStream data;
    private InputStream boundaryIndex;
    private InputStream segmentIndex;
    private StreamCloser streamCloser;

    public StreamSourceInputStreamProviderImpl(final StreamSource streamSource) throws IOException {
        this.streamSource = streamSource;
    }

    private InputStream getData() {
        if (data == null) {
            data = streamSource.getInputStream();
            getStreamCloser().add(data);
        }
        return data;
    }

    private InputStream getBoundaryIndex() {
        if (boundaryIndex == null) {
            boundaryIndex = streamSource.getChildStream(StreamType.BOUNDARY_INDEX).getInputStream();
            getStreamCloser().add(boundaryIndex);
        }
        return boundaryIndex;
    }

    private InputStream getSegmentIndex() {
        if (segmentIndex == null) {
            segmentIndex = streamSource.getChildStream(StreamType.SEGMENT_INDEX).getInputStream();
            getStreamCloser().add(segmentIndex);
        }
        return segmentIndex;
    }

    private StreamCloser getStreamCloser() {
        if (streamCloser == null) {
            streamCloser = new StreamCloser();
        }
        return streamCloser;
    }

    @Override
    public long getStreamCount() throws IOException {
        return getSegmentCount();
    }

    @Override
    public StreamSourceInputStream getStream(final long streamNo) throws IOException {
        // Check bounds.
        final long segmentCount = getSegmentCount();
        if (streamNo < 0 || streamNo >= segmentCount) {
            return null;
        }

        final RASegmentInputStream segmentInputStream = new RASegmentInputStream(getData(), getBoundaryIndex());

        // If this stream has segments, include the requested segment
        // otherwise we will use the whole stream.
        if (segmentCount > 0) {
            segmentInputStream.include(streamNo);
        }

        // Calculate the size of this stream.
        final long entryByteOffsetStart = entryByteOffsetStart(segmentInputStream, streamNo);
        final long entryByteOffsetEnd = entryByteOffsetEnd(segmentInputStream, streamNo);
        final long size = entryByteOffsetEnd - entryByteOffsetStart;

        // Create the wrapped input stream.
        final StreamSourceInputStream streamSourceInputStream = new StreamSourceInputStream(segmentInputStream, size);

        return streamSourceInputStream;
    }

    @Override
    public RASegmentInputStream getSegmentInputStream(final long streamNo) throws IOException {
        // Check bounds.
        final long segmentCount = getSegmentCount();
        if (streamNo < 0 || streamNo >= segmentCount) {
            return null;
        }

        final RASegmentInputStream segmentInputStream = new RASegmentInputStream(getData(), getBoundaryIndex());

        // If this stream has segments, include the requested segment
        // otherwise we will use the whole stream.
        if (segmentCount > 0) {
            segmentInputStream.include(streamNo);
        }

        // Calculate the size of this stream.
        final long entryByteOffsetStart = entryByteOffsetStart(segmentInputStream, streamNo);
        final long entryByteOffsetEnd = entryByteOffsetEnd(segmentInputStream, streamNo);
        // final long size = entryByteOffsetEnd - entryByteOffsetStart;

        final RASegmentInputStream child = new RASegmentInputStream(getData(), getSegmentIndex(), entryByteOffsetStart,
                entryByteOffsetEnd);
        return child;
    }

    private long entryByteOffsetStart(final RASegmentInputStream segmentInputStream, final long streamNo)
            throws IOException {
        return segmentInputStream.byteOffset(streamNo);
    }

    private long entryByteOffsetEnd(final RASegmentInputStream segmentInputStream, final long streamNo)
            throws IOException {
        long offset = segmentInputStream.byteOffset(streamNo + 1);
        if (offset == -1) {
            offset = ((SeekableInputStream) getData()).getSize();
        }
        return offset;
    }

    private long getSegmentCount() throws IOException {
        if (segmentCount == null) {
            final RASegmentInputStream segmentInputStream = new RASegmentInputStream(getData(), getBoundaryIndex());
            segmentCount = segmentInputStream.count();
        }
        return segmentCount;
    }

    @Override
    public void close() throws IOException {
        try {
            if (streamCloser != null) {
                streamCloser.close();
            }
        } catch (final IOException e) {
            throw e;
        }
    }
}
