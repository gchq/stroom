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

package stroom.streamstore.fs.serializable;

import stroom.streamstore.StreamSource;
import stroom.streamstore.shared.StreamType;

import java.io.IOException;
import java.io.InputStream;

/**
 * Class that wraps 3 streams (data, boundary, and segment) and provides handy
 * access to the nested segments with it.
 * <p>
 * You can call getNextInputStream() multiple times to get each segment stream.
 */
public class CompoundInputStream extends RANestedInputStream {
    InputStream segIndex;

    public CompoundInputStream(InputStream data, InputStream bdyIndex, InputStream segIndex) throws IOException {
        super(data, bdyIndex);
        this.segIndex = segIndex;
    }

    public CompoundInputStream(final StreamSource streamSource) throws IOException {
        this(streamSource.getInputStream(), streamSource.getChildStream(StreamType.BOUNDARY_INDEX).getInputStream(),
                streamSource.getChildStream(StreamType.SEGMENT_INDEX).getInputStream());
    }

    public RASegmentInputStream getNextInputStream(final long skipCount) throws IOException {
        getNextEntry(skipCount);

        final long segmentByteOffsetStart = entryByteOffsetStart();
        final long segmentByteOffsetEnd = entryByteOffsetEnd();

        closeEntry();

        RASegmentInputStream child = new RASegmentInputStream(data, segIndex, segmentByteOffsetStart,
                segmentByteOffsetEnd);
        return child;
    }

    public long getInputStreamCount() throws IOException {
        return getEntryCount();
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            segIndex.close();
        }
    }
}
