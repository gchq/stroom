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


import java.nio.ByteBuffer;

public class ZstdConstants {

    /**
     * Identifies the frame as one that zstd will skip over.
     * See <a href="https://github.com/facebook/zstd/blob/release/doc/zstd_compression_format.md#skippable-frames">
     * skippable-frames</a>
     */
    public static final byte[] SKIPPABLE_FRAME_HEADER = new byte[]{0x5E, 0x2A, 0x4D, 0x18};

    /**
     * Identifies a file as being a seekable Zstd file.
     * See <a href="https://github.com/facebook/zstd/blob/dev/contrib/seekable_format/zstd_seekable_compression_format.md">
     * zstd_seekable_compression_format</a>
     */
    public static final byte[] SEEKABLE_MAGIC_NUMBER = new byte[]{(byte) 0xB1, (byte) 0xEA, (byte) 0x92, (byte) 0x8F};

    /**
     * A read-only {@link ByteBuffer} that wraps {@link ZstdConstants#SEEKABLE_MAGIC_NUMBER}
     */
    public static final ByteBuffer SEEKABLE_MAGIC_NUMBER_BUFFER = ByteBuffer.wrap(SEEKABLE_MAGIC_NUMBER)
            .asReadOnlyBuffer();

    /**
     * Number of bytes in a seekable frame footer
     * <pre>
     * <4b frame count LE><1b table descriptor><4b seekable magic number LE>
     * </pre>
     */
    public static final int SEEKABLE_FOOTER_BYTES = 9;

    /**
     * Number of bytes in an entry in the seek table within a seekable frame.
     */
    public static final int SEEK_TABLE_ENTRY_BYTES = Long.BYTES + Long.BYTES;

    private ZstdConstants() {
        // Constants only
    }
}
