/*
 * Copyright 2016-2026 Crown Copyright
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


import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Range;

import org.jspecify.annotations.NullMarked;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * A contiguous range of frames (inclusive-inclusive).
 */
@NullMarked
public record FrameRange(FrameLocation startFrame, FrameLocation endFrame) implements Comparable<FrameRange> {

    /**
     * @param startFrame The frame at the start of the range, inclusive.
     * @param endFrame   The frame at the end of the range, inclusive.
     */
    public FrameRange {
        Objects.requireNonNull(startFrame, "FrameRange must have a from value");
        Objects.requireNonNull(endFrame, "FrameRange must have a to value");
        final int endFrameIdx = endFrame.frameIdx();
        if (endFrameIdx < startFrame.frameIdx()) {
            throw new IllegalArgumentException(LogUtil.message("End frame index {} is before start frame index {}",
                    endFrameIdx, startFrame.frameIdx()));
        }
        final long startPos = startFrame.position();
        final long endPosInc = startFrame.getToInc();
        if (endPosInc < startPos) {
            throw new IllegalArgumentException(LogUtil.message(
                    "End frame position (inc) {} is before start frame position {}, {} -> {}",
                    endPosInc, startPos, startFrame, endFrame));
        }
    }

    /**
     * A {@link FrameRange} for a single frame.
     */
    public static FrameRange singleFrame(final FrameLocation frameLocation) {
        return new FrameRange(frameLocation, frameLocation);
    }

    /**
     * @return This {@link FrameRange} in the form of a {@link Range} of the compressed bytes.
     */
    public Range<Long> asCompressedByteRange() {
        return new Range<>(
                startFrame.position(),
                endFrame.getToExc());
    }

    /**
     * @return The position/offset of the first compressed byte of the first frame in this range.
     */
    public long position() {
        return startFrame.position();
    }

    /**
     * @return The size in compressed bytes of this range of contiguous frames.
     */
    public long compressedSize() {
        return endFrame.getToExc() - startFrame.position();
    }

    /**
     * Creates a {@link MemorySegment} that is a slice of memorySegment using this {@link FrameRange}'s
     * position and size.
     */
    public MemorySegment asSlice(final MemorySegment memorySegment) {
        Objects.requireNonNull(memorySegment);
        return memorySegment.asSlice(position(), compressedSize());
    }

    /**
     * @return The number of frames in this contiguous {@link FrameRange}.
     */
    public int frameCount() {
        // +1 as frameIdx is inclusive
        return endFrame.frameIdx() - startFrame.frameIdx() + 1;
    }

    @Override
    public String toString() {
        return LogUtil.message(
                "FrameRange{frameIdx range: {} -> {} (inc-inc), count: {}, byte position range: {} -> {} (inc-exc)",
                startFrame.frameIdx(),
                endFrame.frameIdx(),
                frameCount(),
                ModelStringUtil.formatCsv(startFrame.position()),
                ModelStringUtil.formatCsv(endFrame.getToExc()));
    }

    @Override
    public int compareTo(final FrameRange o) {
        return Integer.compare(
                this.startFrame().frameIdx(),
                Objects.requireNonNull(o).startFrame().frameIdx());
    }
}
