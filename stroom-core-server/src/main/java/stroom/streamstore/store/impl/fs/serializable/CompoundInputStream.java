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

package stroom.streamstore.store.impl.fs.serializable;

import stroom.streamstore.store.api.StreamSource;
import stroom.streamstore.shared.StreamTypeNames;

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

    public CompoundInputStream(InputStream data, InputStream bdyIndex, InputStream segIndex) {
        super(data, bdyIndex);
        this.segIndex = segIndex;
    }

    public CompoundInputStream(final StreamSource streamSource) {
        this(streamSource.getInputStream(), streamSource.getChildStream(StreamTypeNames.BOUNDARY_INDEX).getInputStream(),
                streamSource.getChildStream(StreamTypeNames.SEGMENT_INDEX).getInputStream());
    }

    public RASegmentInputStream getNextInputStream(final long skipCount) throws IOException {
        getNextEntry(skipCount);

        final long segmentByteOffsetStart = entryByteOffsetStart();
        final long segmentByteOffsetEnd = entryByteOffsetEnd();

        closeEntry();

        return new RASegmentInputStream(data, segIndex, segmentByteOffsetStart, segmentByteOffsetEnd);
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
