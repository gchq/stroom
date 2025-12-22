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


import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Range;

/**
 * Defines the location of a compressed frame in a segmented Zstd file.
 *
 * @param frameIdx
 * @param position       The position of the compressed frame (in byte terms) within the file/stream.
 *                       Zero based. <strong>Not</strong> the same as the frameIdx.
 * @param compressedSize The length of the compressed frame in bytes.
 * @param originalSize   The un-compressed size of the compressed frame.
 */
public record FrameLocation(int frameIdx, long position, long compressedSize, long originalSize) {

    long getToInc() {
        return position + compressedSize - 1;
    }

    long getToExc() {
        return position + compressedSize;
    }

    Range<Long> asRange() {
        return new Range<>(position, getToExc());
    }

    /**
     * @return The compressed size as a percentage of the original size.
     */
    double getCompressionPct() {
        return compressedSize / (double) originalSize * 100;
    }

    @Override
    public String toString() {
        return "FrameLocation{" +
               "frameIdx=" + frameIdx +
               ", position=" + position +
               ", compressedSize=" + compressedSize +
               ", originalSize=" + originalSize +
               ", compressionPct=" + ModelStringUtil.formatCsv(getCompressionPct(), 1) + "%" +
               '}';
    }
}
