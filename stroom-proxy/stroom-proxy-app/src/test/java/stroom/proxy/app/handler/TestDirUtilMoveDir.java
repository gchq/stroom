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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cross-filesystem moves are what {@link DirUtil#moveDir} exists for, and every other fixture in this
 * module puts everything under one JUnit temp directory - so the hazard cannot be arranged there and
 * has to be either injected or found. These tests do both: the fallback is exercised directly on one
 * filesystem, and the genuine cross-filesystem case runs when the machine has a second filesystem to
 * hand and is skipped with a reason when it does not.
 */
class TestDirUtilMoveDir {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDirUtilMoveDir.class);

    /**
     * Candidate roots on a filesystem other than the build directory. Both are conventional on Linux
     * and are tmpfs, so they are a different {@code FileStore} from a disk-backed working directory.
     */
    private static final String[] OTHER_FILESYSTEM_ROOTS = {"/dev/shm", "/run/shm"};

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

        // The staging dir the fallback builds beside the target must not be left behind.
        assertThat(target.resolveSibling(target.getFileName().toString() + DirUtil.CROSS_DEVICE_SUFFIX))
                .doesNotExist();
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
        final Path staging = target.resolveSibling(
                target.getFileName().toString() + DirUtil.CROSS_DEVICE_SUFFIX);
        Files.createDirectories(staging);
        Files.writeString(staging.resolve("stale.txt"), "from an earlier attempt");

        DirUtil.moveDirAcrossFileStores(source, target);

        assertTreeAt(target);
        assertThat(target.resolve("stale.txt"))
                .doesNotExist();
    }

    @Test
    void testMoveDirFallsBackWhenTheTargetIsOnAnotherFileSystem(@TempDir final Path tempDir)
            throws IOException {
        final Path otherFileSystemDir = findDirOnAnotherFileSystem(tempDir);
        Assumptions.assumeTrue(otherFileSystemDir != null,
                "No second filesystem available on this machine, so a cross-filesystem move cannot "
                + "be arranged. Tried " + String.join(", ", OTHER_FILESYSTEM_ROOTS));

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

    private static Path findDirOnAnotherFileSystem(final Path reference) throws IOException {
        for (final String root : OTHER_FILESYSTEM_ROOTS) {
            final Path candidate = Path.of(root);
            if (Files.isDirectory(candidate) && Files.isWritable(candidate)) {
                if (!Files.getFileStore(candidate).equals(Files.getFileStore(reference))) {
                    final Path dir = candidate.resolve("stroom-test-xdev-" + reference.getFileName());
                    Files.createDirectories(dir);
                    LOGGER.info("Using {} for the cross-filesystem test", dir);
                    return dir;
                }
            }
        }
        return null;
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
