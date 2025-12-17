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

import stroom.util.io.StreamUtil;

/**
 * BlockGZIP has the following format:
 * <p>
 * [Block GZip Version Marker 'BGZ1'] [Un-Compressed Block Size] [Un-Compressed
 * Data Length] [Index Position] [EOF Position] [Magic Marker] [Block Size]
 * [Block Data] [Magic Marker] [Block Size] [Block Data] ... [Magic Marker] <-
 * (Index Position) [Un-Compressed Index Data] [EOF] <- (EOF)
 */
final class BlockGZIPConstants {

    /**
     * Lock name.
     */
    static final String LOCK_EXTENSION = ".lock";
    /**
     * Default Block we write.
     */
    static final int DEFAULT_BLOCK_SIZE = 1000000; // 1MB
    /**
     * Java IO Read Only.
     */
    static final String READ_ONLY = "r";
    /**
     * Java IO Read Write.
     */
    static final String READ_WRITE = "rw";
    /**
     * How long is a long.
     */
    static final int LONG_BYTES = 8;
    /**
     * Written at the start to identify .
     */
    static final byte[] BLOCK_GZIP_V1_IDENTIFIER = "BGZ1".getBytes(StreamUtil.DEFAULT_CHARSET);
    private static final byte MAGIC_HIGH = 127;
    private static final byte MAGIC_LOW = -128;
    /**
     * Made Up Stroom Marker .... used to help check we have not got a corrupt
     * stream.
     */
    static final byte[] MAGIC_MARKER = new byte[]{MAGIC_LOW, MAGIC_LOW, MAGIC_LOW, 0, 0, MAGIC_HIGH, MAGIC_HIGH,
            MAGIC_HIGH};

    private BlockGZIPConstants() {
        // NA
    }
}
