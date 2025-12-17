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
import stroom.data.store.api.WrappedSegmentInputStream;
import stroom.util.io.SeekableInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class SegmentInputStreamProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentInputStreamProvider.class);

    private final InternalSource source;
    //        private long index = -1;
    private final String dataTypeName;
//        private final SegmentInputStream nestedInputStream;
//        private final SegmentInputStream inputStream;

    private InputStream data;
    private InputStream boundaryIndex;
    private InputStream segmentIndex;

//        private Long segmentCount = null;

    public SegmentInputStreamProvider(final InternalSource source, final String dataTypeName) {
        this.source = source;
        this.dataTypeName = dataTypeName;

//            nestedInputStream = new RASegmentInputStream(nestedInputStreamFactory.getInputStream(),
//                    () -> nestedInputStreamFactory.getChild(InternalStreamTypeNames.BOUNDARY_INDEX).getInputStream());
//            inputStream = new RASegmentInputStream(nestedInputStream,
//                    () -> nestedInputStreamFactory.getChild(InternalStreamTypeNames.SEGMENT_INDEX).getInputStream());
    }

    public SegmentInputStream get(final long index) {
        try {
            final InputStream boundaryIndex = getBoundaryIndex();
            final InputStream segmentIndex = getSegmentIndex();

            final RASegmentInputStream segmentInputStream = new RASegmentInputStream(getData(), boundaryIndex);

            // Check bounds.
            final long segmentCount = segmentInputStream.count();
            if (index < 0 || index >= segmentCount) {
                throw new IOException("Index " + index + " is out of bounds (segment count=" + segmentCount + ")");
            }

            // If this stream has segments, include the requested segment
            // otherwise we will use the whole stream.
            if (segmentCount > 1) {
                segmentInputStream.include(index);
            }

            // Calculate the size of this stream.
            final long entryByteOffsetStart = entryByteOffsetStart(segmentInputStream, index);
            final long entryByteOffsetEnd = entryByteOffsetEnd(segmentInputStream, index);
            final long size = entryByteOffsetEnd - entryByteOffsetStart;

            if (segmentIndex == null) {
                return new SingleSegmentInputStreamImpl(segmentInputStream, size);
            }

            final SegmentInputStream inputStream = new RASegmentInputStream(
                    getData(), segmentIndex, entryByteOffsetStart, entryByteOffsetEnd);

            return new WrappedSegmentInputStream(inputStream) {
                @Override
                public void close() {
                    // Do nothing
                }
            };
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void close() {
        if (boundaryIndex != null) {
            tryClose(boundaryIndex);
            boundaryIndex = null;
        }
        if (segmentIndex != null) {
            tryClose(segmentIndex);
            segmentIndex = null;
        }
        if (data != null) {
            tryClose(data);
            data = null;
        }
    }

    void tryClose(final InputStream inputStream) {
        try {
            inputStream.close();
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private long entryByteOffsetStart(final RASegmentInputStream segmentInputStream, final long partIndex)
            throws IOException {
        return segmentInputStream.byteOffset(partIndex);
    }

    private long entryByteOffsetEnd(final RASegmentInputStream segmentInputStream, final long partIndex)
            throws IOException {
        long offset = segmentInputStream.byteOffset(partIndex + 1);
        if (offset == -1) {
            offset = ((SeekableInputStream) getData()).getSize();
        }
        return offset;
    }

    private InputStream getData() {
        if (data == null) {
            data = source.getInputStream();
        }
        return data;
    }

    private InputStream getBoundaryIndex() {
        if (boundaryIndex == null) {
            boundaryIndex = source.getChildInputStream(InternalStreamTypeNames.BOUNDARY_INDEX);
        }
        return boundaryIndex;
    }

    private InputStream getSegmentIndex() {
        if (segmentIndex == null) {
            segmentIndex = source.getChildInputStream(InternalStreamTypeNames.SEGMENT_INDEX);
        }
        return segmentIndex;
    }
}
