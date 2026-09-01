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

package stroom.proxy.app.handler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-filesystem moves are what {@link DirUtil#moveDir} exists for, and every other fixture in this
 * module puts everything under one JUnit temp directory - so the hazard cannot be arranged there and
 * has to be either injected or found. These tests do both: the fallback is exercised directly on one
 * filesystem, and the genuine cross-filesystem case runs against a directory found by
 * {@link OtherFileSystem} and is skipped with a reason when the machine has no second filesystem.
 */
class TestDirUtilMoveDir {

    @Test
    void testAcrossFileStoresCopiesTheWholeTreeAndRemovesTheSource(@TempDir final Path tempDir)
            throws IOException {
        final Path source = tempDir.resolve("source");
        final Path target = tempDir.resolve("target");
        writeTree(source);

        DirUtil.moveDirAcrossFileStores(source, target);

        assertTreeAt(target);
        assertThat(source)
                .doesNotExist();

        // No staging dir may be left behind, whatever it was called.
        assertThat(stagingResidueIn(tempDir))
                .isEmpty();
    }

    @Test
    void testAcrossFileStoresLeavesTheSourceIntactWhenTheCopyFails(@TempDir final Path tempDir)
            throws IOException {
        final Path source = tempDir.resolve("source");
        writeTree(source);

        // A target whose parent does not exist, so the rename into place cannot succeed.
        final Path target = tempDir.resolve("missing").resolve("target");

        assertThatThrownBy(() -> DirUtil.moveDirAcrossFileStores(source, target))
                .isInstanceOf(IOException.class);

        // The one thing that must never happen: the source gone with nothing to show for it.
        assertTreeAt(source);
    }

    @Test
    void testAnUnfinishedStagingDirFromAnEarlierAttemptIsNotMergedIntoTheResult(
            @TempDir final Path tempDir) throws IOException {
        final Path source = tempDir.resolve("source");
        final Path target = tempDir.resolve("target");
        writeTree(source);

        // Simulate a previous attempt interrupted after it had copied part of the tree.
        final Path staging = DirUtil.stagingPathFor(target);
        Files.createDirectories(staging);
        Files.writeString(staging.resolve("stale.txt"), "from an earlier attempt");

        DirUtil.moveDirAcrossFileStores(source, target);

        assertTreeAt(target);
        assertThat(target.resolve("stale.txt"))
                .doesNotExist();

        // It is orphaned rather than adopted: this attempt staged elsewhere and never touched it.
        // Nothing reclaims it, which is the cost of the uniqueness the test below requires.
        assertThat(staging.resolve("stale.txt"))
                .exists();
    }

    /**
     * Two movers can legitimately be handed the same target - see the test below for why - so the
     * staging path must not be derived from the target alone. When it was, the second mover deleted
     * or merged into the first's half-copied tree.
     */
    @Test
    void testTheStagingPathIsUniqueToEachAttempt(@TempDir final Path tempDir) {
        final Path target = tempDir.resolve("group").resolve("0000000005");

        final Path first = DirUtil.stagingPathFor(target);
        final Path second = DirUtil.stagingPathFor(target);

        assertThat(first)
                .isNotEqualTo(second);
        // Beside the target, so the rename that publishes it stays within one filesystem.
        assertThat(first.getParent())
                .isEqualTo(target.getParent());
        assertThat(first.getFileName().toString())
                .startsWith(DirUtil.CROSS_DEVICE_PREFIX)
                .endsWith(DirUtil.CROSS_DEVICE_SUFFIX);
    }

    /**
     * Why two movers can be handed the same target: commit ids come from a counter reloaded by
     * scanning for the highest numeric directory name, and a file group still in staging is not
     * numeric, so it does not raise the count.
     */
    @Test
    void testTheCommitIdScanCannotSeeAStagingDir(@TempDir final Path tempDir) throws IOException {
        for (int id = 1; id <= 4; id++) {
            Files.createDirectories(DirUtil.createPath(tempDir, id));
        }
        final Path fifth = DirUtil.createPath(tempDir, 5);
        Files.createDirectories(DirUtil.stagingPathFor(fifth));

        assertThat(DirUtil.getMaxDirId(tempDir))
                .isEqualTo(4L);
    }

    private static List<Path> stagingResidueIn(final Path dir) throws IOException {
        try (final Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(DirUtil.CROSS_DEVICE_SUFFIX))
                    .toList();
        }
    }

    @Test
    void testMoveDirFallsBackWhenTheTargetIsOnAnotherFileSystem(@TempDir final Path tempDir)
            throws IOException {
        final Path otherFileSystemDir = OtherFileSystem.find(tempDir);
        Assumptions.assumeTrue(otherFileSystemDir != null, OtherFileSystem.skipReason());

        final Path source = otherFileSystemDir.resolve("source");
        final Path target = tempDir.resolve("target");
        try {
            writeTree(source);

            // The bare ATOMIC_MOVE these call sites used to do cannot cross a filesystem boundary.
            DirUtil.moveDir(source, target);

            assertTreeAt(target);
            assertThat(source)
                    .doesNotExist();
        } finally {
            stroom.util.io.FileUtil.deleteDir(otherFileSystemDir);
        }
    }

    private static void writeTree(final Path root) throws IOException {
        Files.createDirectories(root.resolve("nested").resolve("deeper"));
        Files.writeString(root.resolve("proxy.meta"), "meta-content");
        Files.writeString(root.resolve("nested").resolve("proxy.zip"), "zip-content");
        Files.writeString(root.resolve("nested").resolve("deeper").resolve("proxy.entries"), "entries");
    }

    private static void assertTreeAt(final Path root) {
        assertThat(root.resolve("proxy.meta"))
                .hasContent("meta-content");
        assertThat(root.resolve("nested").resolve("proxy.zip"))
                .hasContent("zip-content");
        assertThat(root.resolve("nested").resolve("deeper").resolve("proxy.entries"))
                .hasContent("entries");
    }
}
