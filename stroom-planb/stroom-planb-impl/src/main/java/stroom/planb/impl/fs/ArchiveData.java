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

package stroom.planb.impl.fs;

import stroom.planb.impl.PlanBConstants;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * How an archive bucket's data file is written to and read from the shared store.
 *
 * <p>A bucket is immutable once published and is copied down to local disk before anything reads it,
 * so the copy can be a decompression and the read path above it is unchanged. Measured on this
 * installation, twelve buckets compress 11.1 to 1 at level 9, so an archive costs roughly a twelfth
 * of the disk and a read moves a twelfth of the bytes across the shared mount.
 *
 * <p>Only the archive is compressed. A live shard is copied down, merged into and pushed back every
 * cycle, so it would pay the cost again on each one and hold a fraction of the bytes.
 *
 * <p>A bucket holds its data in one of two forms and both are read: {@code data.mdb.zst} where it is
 * compressed, {@code data.mdb} where it is not. A bucket holding the plain form is served as it is
 * and takes the compressed form the next time anything merges into it, so the two can sit side by
 * side across a store indefinitely and no node has to agree with another about which it will find.
 */
final class ArchiveData {

    /**
     * Measured on twelve buckets from this installation: level 3 gives 10.1 to 1, level 9 gives 11.1,
     * level 19 gives 12.8. Level 9 is a tenth of the time of 19 for most of the gain. Decompression
     * runs at the same speed whatever this is, so it does not reach the read path.
     */
    private static final int LEVEL = 9;

    private ArchiveData() {
    }

    /**
     * Copies a bucket's data file down to {@code destFile}, decompressing it where it is compressed.
     * The destination is always a plain LMDB file, so every caller gets the same thing back whichever
     * form the bucket happens to be in.
     *
     * @return false where the bucket holds no data file at all, so a caller can treat that as a miss
     * rather than an error.
     */
    static boolean copyDown(final Path bucketDir, final Path destFile) throws IOException {
        final Path compressed = bucketDir.resolve(PlanBConstants.DATA_ZST_FILE_NAME);
        if (Files.exists(compressed)) {
            decompress(compressed, destFile);
            return true;
        }
        final Path plain = bucketDir.resolve(PlanBConstants.DATA_FILE_NAME);
        if (Files.exists(plain)) {
            Files.copy(plain, destFile, StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
        return false;
    }

    static void compress(final Path src, final Path dest) throws IOException {
        try (final OutputStream out = new ZstdOutputStream(
                new BufferedOutputStream(Files.newOutputStream(dest)), LEVEL)) {
            Files.copy(src, out);
        }
    }

    static void decompress(final Path src, final Path dest) throws IOException {
        try (final InputStream in = new ZstdInputStream(
                new BufferedInputStream(Files.newInputStream(src)))) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
