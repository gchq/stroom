/*
 * Copyright 2026 Crown Copyright
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How an archive bucket's data file survives the trip to the shared store and back.
 *
 * <p>Every byte matters here: a bucket is the only copy of what it holds once retention has taken the
 * records from the live shard, so a round trip that is nearly right is data loss.
 */
class TestArchiveData {

    @TempDir
    Path dir;

    // Compressible the way an LMDB file is - long runs of repeated structure and zero-filled free
    // pages - rather than random bytes, which would prove the round trip but say nothing about size.
    private byte[] lmdbLike(final int size) {
        final byte[] bytes = new byte[size];
        final Random random = new Random(42);
        for (int i = 0; i < size; i += 512) {
            if (random.nextInt(4) > 0) {
                final byte value = (byte) random.nextInt(16);
                for (int j = i; j < Math.min(i + 512, size); j++) {
                    bytes[j] = value;
                }
            }
        }
        return bytes;
    }

    @Test
    void aFileSurvivesCompressionAndDecompression() throws IOException {
        final byte[] original = lmdbLike(1_000_000);
        final Path source = dir.resolve("source.mdb");
        Files.write(source, original);

        final Path compressed = dir.resolve("out.mdb.zst");
        ArchiveData.compress(source, compressed);
        final Path restored = dir.resolve("restored.mdb");
        ArchiveData.decompress(compressed, restored);

        assertThat(Files.readAllBytes(restored)).isEqualTo(original);
        assertThat(Files.size(compressed)).isLessThan(Files.size(source));
    }

    @Test
    void aCompressedBucketIsCopiedDownDecompressed() throws IOException {
        final byte[] original = lmdbLike(200_000);
        final Path bucket = Files.createDirectories(dir.resolve("2026-10-14_12"));
        final Path staged = dir.resolve("staged.mdb");
        Files.write(staged, original);
        ArchiveData.compress(staged, bucket.resolve(PlanBConstants.DATA_ZST_FILE_NAME));

        final Path destination = dir.resolve("down.mdb");
        assertThat(ArchiveData.copyDown(bucket, destination)).isTrue();
        assertThat(Files.readAllBytes(destination)).isEqualTo(original);
    }

    /**
     * A bucket written before buckets were compressed. It has to go on being read as it is, because
     * nothing converts one and retention is what eventually removes it.
     */
    @Test
    void anUncompressedBucketIsStillRead() throws IOException {
        final byte[] original = lmdbLike(50_000);
        final Path bucket = Files.createDirectories(dir.resolve("2026-10-08_09"));
        Files.write(bucket.resolve(PlanBConstants.DATA_FILE_NAME), original);

        final Path destination = dir.resolve("down.mdb");
        assertThat(ArchiveData.copyDown(bucket, destination)).isTrue();
        assertThat(Files.readAllBytes(destination)).isEqualTo(original);
    }

    /**
     * Mid-upgrade a bucket can hold both: the compressed file is published first and the plain one
     * dropped after. The compressed one is the newer, so it is the one to read.
     */
    @Test
    void theCompressedFileWinsWhereBothArePresent() throws IOException {
        final Path bucket = Files.createDirectories(dir.resolve("2026-10-14_13"));
        Files.write(bucket.resolve(PlanBConstants.DATA_FILE_NAME), "stale".getBytes());
        final Path staged = dir.resolve("staged.mdb");
        Files.write(staged, "current".getBytes());
        ArchiveData.compress(staged, bucket.resolve(PlanBConstants.DATA_ZST_FILE_NAME));

        final Path destination = dir.resolve("down.mdb");
        assertThat(ArchiveData.copyDown(bucket, destination)).isTrue();
        assertThat(Files.readString(destination)).isEqualTo("current");
    }

    /**
     * Retention can take a bucket at any moment, including between a locator listing it and a reader
     * copying it. Saying so rather than throwing lets the caller treat it as a miss.
     */
    @Test
    void aBucketWithNoDataFileSaysSo() throws IOException {
        final Path bucket = Files.createDirectories(dir.resolve("2026-10-14_14"));
        assertThat(ArchiveData.copyDown(bucket, dir.resolve("down.mdb"))).isFalse();
    }

    @Test
    void anEmptyFileSurvivesTheRoundTrip() throws IOException {
        final Path source = dir.resolve("empty.mdb");
        Files.write(source, new byte[0]);

        final Path compressed = dir.resolve("empty.mdb.zst");
        ArchiveData.compress(source, compressed);
        final Path restored = dir.resolve("restored.mdb");
        ArchiveData.decompress(compressed, restored);

        assertThat(Files.size(restored)).isZero();
    }
}
