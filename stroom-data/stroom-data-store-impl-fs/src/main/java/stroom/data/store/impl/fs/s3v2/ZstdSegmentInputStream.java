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

package stroom.data.store.impl.fs.s3v2;


import stroom.data.store.api.SegmentInputStream;
import stroom.util.io.SeekableInputStream;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Currently supports one level of segmentation, either boundary or index, not both.
 */
public class ZstdSegmentInputStream extends SegmentInputStream {

    private final byte[] singleByte = new byte[1];
    private final SeekableInputStream seekableInputStream;
    private final ZstdSeekTable zstdSeekTable;
    private LongSortedSet includedSegments;
    private LongSortedSet excludedSegments;
    private boolean includeAllSegments = true;

    public ZstdSegmentInputStream(final SeekableInputStream compressedInputStream,
                                  final ZstdSeekTable zstdSeekTable) {
        this.seekableInputStream = compressedInputStream;
        this.zstdSeekTable = Objects.requireNonNull(zstdSeekTable);
    }

    @Override
    public long count() {
        return zstdSeekTable.getFrameCount();
    }

    @Override
    public void include(final long segment) {
        validateSegment(segment);
        includedSegments = Objects.requireNonNullElseGet(includedSegments, LongAVLTreeSet::new);
        includedSegments.add(segment);
    }

    @Override
    public void includeAll() {
        includeAllSegments = true;
    }

    @Override
    public void exclude(final long segment) {
        validateSegment(segment);
        excludedSegments = Objects.requireNonNullElseGet(excludedSegments, LongAVLTreeSet::new);
        excludedSegments.add(segment);

    }

    @Override
    public void excludeAll() {
        includeAllSegments = false;
        includedSegments = null;
        excludedSegments = null;
    }

    @Override
    public long size() {
        return 0;
    }

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

    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        // TODO

        return -1;
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

    private void validateSegment(final long segment) {
        if (segment < 0 || segment >= count()) {
            throw new IllegalArgumentException("Invalid segment " + segment);
        }
    }


    // --------------------------------------------------------------------------------


    private static class InternalSeekableInputStream extends InputStream implements SeekableInputStream {

        private final SeekableInputStream compressedInputStream;

        private InternalSeekableInputStream(final SeekableInputStream compressedInputStream,
                                            final List<FrameLocation> frameLocations) {
            this.compressedInputStream = compressedInputStream;
        }

        @Override
        public int read() throws IOException {
            return 0;
        }

        @Override
        public long getSize() throws IOException {
            return compressedInputStream.getSize();
        }

        @Override
        public long getPosition() throws IOException {
            return 0;
        }

        @Override
        public void seek(final long pos) throws IOException {

        }
    }
}
