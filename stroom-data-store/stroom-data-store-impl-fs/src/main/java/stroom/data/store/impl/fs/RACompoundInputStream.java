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

package stroom.data.store.impl.fs;

import stroom.data.store.api.CompoundInputStream;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.StreamSource;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class that wraps 3 streams (data, boundary, and segment) and provides handy
 * access to the nested segments with it.
 * <p>
 * You can call getNextInputStream() multiple times to get each segment stream.
 */
class RACompoundInputStream implements CompoundInputStream {
    private final RANestedInputStream nestedInputStream;
    private InputStream segIndex;

    RACompoundInputStream(final RANestedInputStream nestedInputStream, final InputStream segIndex) {
        this.nestedInputStream = nestedInputStream;
        this.segIndex = segIndex;
    }

    @Override
    public SegmentInputStream getNextInputStream(final long skipCount) throws IOException {
        nestedInputStream.getNextEntry(skipCount);

        final long segmentByteOffsetStart = nestedInputStream.entryByteOffsetStart();
        final long segmentByteOffsetEnd =  nestedInputStream.entryByteOffsetEnd();

        nestedInputStream.closeEntry();

        return new RASegmentInputStream(nestedInputStream.data, segIndex, segmentByteOffsetStart, segmentByteOffsetEnd);
    }

    @Override
    public long getEntryCount() throws IOException {
        return nestedInputStream.getEntryCount();
    }

    @Override
    public void close() throws IOException {
        try {
            nestedInputStream.close();
        } finally {
            segIndex.close();
        }
    }
}
