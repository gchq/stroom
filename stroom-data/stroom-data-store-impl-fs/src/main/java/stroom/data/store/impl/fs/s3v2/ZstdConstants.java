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


import stroom.util.UuidUtil;

import java.nio.ByteBuffer;
import java.util.UUID;

public class ZstdConstants {

    /**
     * Identifies the frame as one that zstd will skip over.
     * See <a href="https://github.com/facebook/zstd/blob/release/doc/zstd_compression_format.md#skippable-frames">
     * skippable-frames</a>
     */
    public static final byte[] SKIPPABLE_FRAME_MAGIC_NUMBER = new byte[]{0x5E, 0x2A, 0x4D, 0x18};
    public static final ByteBuffer SKIPPABLE_FRAME_MAGIC_NUMBER_BUFFER = ByteBuffer.wrap(SKIPPABLE_FRAME_MAGIC_NUMBER);

    /**
     * Number of bytes for {@link ZstdConstants#SKIPPABLE_FRAME_MAGIC_NUMBER}
     */
    public static final int SKIPPABLE_FRAME_MAGIC_NUMBER_SIZE = SKIPPABLE_FRAME_MAGIC_NUMBER.length;

    /**
     * The size of a skippable frame header in bytes.
     * <pre>
     * < skippable frame magic number > < frame payload size (int LE) >
     * </pre>
     */
    public static final int SKIPPABLE_FRAME_HEADER_SIZE = SKIPPABLE_FRAME_MAGIC_NUMBER_SIZE
                                                          + Integer.BYTES;

    /**
     * Identifies a file as being a seekable Zstd file.
     * See <a href="https://github.com/facebook/zstd/blob/dev/contrib/seekable_format/zstd_seekable_compression_format.md">
     * zstd_seekable_compression_format</a>
     */
    public static final byte[] SEEKABLE_MAGIC_NUMBER = new byte[]{(byte) 0xB1, (byte) 0xEA, (byte) 0x92, (byte) 0x8F};

    /**
     * Number of bytes for {@link ZstdConstants#SEEKABLE_MAGIC_NUMBER}
     */
    public static final int SEEKABLE_MAGIC_NUMBER_SIZE = SEEKABLE_MAGIC_NUMBER.length;

    /**
     * Number of bytes for the frame count field.
     */
    public static final int FRAME_COUNT_SIZE = Integer.BYTES;

    /**
     * Number of bytes for the bit field.
     */
    public static final int BIT_FIELD_SIZE = 1;

    /**
     * The position of the frame count value in the skippable frame
     * <strong>relative to the END of the byte[]/buffer/stream</strong>.
     * This is a negative number.
     */
    public static final int FRAME_COUNT_RELATIVE_POSITION = -1 * (SEEKABLE_MAGIC_NUMBER_SIZE
                                                                  + BIT_FIELD_SIZE
                                                                  + FRAME_COUNT_SIZE);

    /**
     * Number of bytes for the dictionary UUID field.
     */
    public static final int DICTIONARY_UUID_SIZE = UuidUtil.UUID_BYTES;

    /**
     * The position of the dictionary UUID value in the skippable frame
     * <strong>relative to the END of the byte[]/buffer/stream</strong>.
     * This is a negative number.
     */
    public static final int DICTIONARY_UUID_RELATIVE_POSITION = -1 * (SEEKABLE_MAGIC_NUMBER_SIZE
                                                                      + BIT_FIELD_SIZE
                                                                      + FRAME_COUNT_SIZE
                                                                      + DICTIONARY_UUID_SIZE);

    /**
     * A read-only {@link ByteBuffer} that wraps {@link ZstdConstants#SEEKABLE_MAGIC_NUMBER}
     */
    public static final ByteBuffer SEEKABLE_MAGIC_NUMBER_BUFFER = ByteBuffer.wrap(SEEKABLE_MAGIC_NUMBER)
            .asReadOnlyBuffer();

    /**
     * Number of bytes in a seekable frame footer
     * <pre>
     * <16b dict UUID BE><4b frame count LE><1b table descriptor><4b seekable magic number LE>
     * </pre>
     */
    public static final int SEEKABLE_FOOTER_SIZE = DICTIONARY_UUID_SIZE
                                                   + FRAME_COUNT_SIZE
                                                   + BIT_FIELD_SIZE
                                                   + SEEKABLE_MAGIC_NUMBER_SIZE;

    /**
     * Number of bytes in an entry in the seek table within a seekable frame.
     */
    public static final int SEEK_TABLE_ENTRY_SIZE = Long.BYTES + Long.BYTES;

    public static final UUID ZERO_UUID = new UUID(0, 0);

    public static final byte[] ZERO_UUID_BYTES = UuidUtil.toByteArray(ZERO_UUID);

    private ZstdConstants() {
        // Constants only
    }
}
