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

import stroom.util.shared.Range;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestZstdSegmentUtil {

    private final byte[] fourByteArray = new byte[Integer.BYTES];
    private final ByteBuffer fourByteBuffer = ByteBuffer.wrap(fourByteArray);
    private final byte[] eightByteArray = new byte[Long.BYTES];
    private final ByteBuffer eightByteBuffer = ByteBuffer.wrap(eightByteArray);

    @Test
    void calculateSeekTableSize() {
        assertThat(ZstdSegmentUtil.calculateSeekTableSize(0))
                .isEqualTo(0);
        assertThat(ZstdSegmentUtil.calculateSeekTableSize(1))
                .isEqualTo(16);
        assertThat(ZstdSegmentUtil.calculateSeekTableSize(10))
                .isEqualTo(160);
    }

    @Test
    void calculateSeekTableFramePayloadSize() {
        assertThat(ZstdSegmentUtil.calculateSeekTableFramePayloadSize(0))
                .isEqualTo(9);
        assertThat(ZstdSegmentUtil.calculateSeekTableFramePayloadSize(1))
                .isEqualTo(16 + 9);
        assertThat(ZstdSegmentUtil.calculateSeekTableFramePayloadSize(10))
                .isEqualTo(160 + 9);
    }

    @Test
    void calculateSeekTableFrameSize() {
        assertThat(ZstdSegmentUtil.calculateSeekTableFrameSize(0))
                .isEqualTo(8 + 9);
        assertThat(ZstdSegmentUtil.calculateSeekTableFrameSize(1))
                .isEqualTo(8 + 16 + 9);
        assertThat(ZstdSegmentUtil.calculateSeekTableFrameSize(10))
                .isEqualTo(8 + 160 + 9);
    }

    @Test
    void isSeekable() {
        final ByteBuffer buffer = ByteBuffer.allocate(20);
        assertThat(ZstdSegmentUtil.isSeekable(buffer))
                .isFalse();
        // Put the magic number at the end of the buffer
        buffer.put(
                buffer.capacity() - ZstdConstants.SEEKABLE_MAGIC_NUMBER_SIZE,
                ZstdConstants.SEEKABLE_MAGIC_NUMBER);
        assertThat(ZstdSegmentUtil.isSeekable(buffer))
                .isTrue();
    }

    @Test
    void getLongLE() throws IOException {
        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(20)) {
            ZstdSegmentUtil.writeLELong(203984234L, eightByteBuffer, byteArrayOutputStream);

            final ByteBuffer buffer = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
            final long val = ZstdSegmentUtil.getLongLE(buffer, 0);
            assertThat(val)
                    .isEqualTo(203984234L);
        }
    }

    @Test
    void getIntegerLE() throws IOException {
        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(20)) {
            ZstdSegmentUtil.writeLEInteger(203984234, eightByteBuffer, byteArrayOutputStream);

            final ByteBuffer buffer = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
            final long val = ZstdSegmentUtil.getUnsignedIntLE(buffer, 0);
            assertThat(val)
                    .isEqualTo(203984234);
        }
    }

    @Test
    void getLastNRange() {
        final Range<Long> range = ZstdSegmentUtil.getLastNRange(10, 4);
        assertThat(range.getFrom())
                .isEqualTo(6);
        assertThat(range.getTo())
                .isEqualTo(10);
    }

    @Test
    void getLastNRange_invalid() {
        Assertions.assertThatThrownBy(
                () -> ZstdSegmentUtil.getLastNRange(10, 12)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
