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

package stroom.util.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFileSyncUtil {

    @TempDir
    private Path tempDir;

    @Test
    void testSyncFile_leavesContentIntact() throws IOException {
        final Path file = tempDir.resolve("data.txt");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);

        FileSyncUtil.syncFile(file);

        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .isEqualTo("hello");
    }

    @Test
    void testSyncFile_emptyFile() throws IOException {
        final Path file = tempDir.resolve("empty.txt");
        Files.createFile(file);

        assertThatCode(() -> FileSyncUtil.syncFile(file))
                .doesNotThrowAnyException();

        assertThat(file).isEmptyFile();
    }

    @Test
    void testSyncFile_missingFileThrows() {
        final Path file = tempDir.resolve("missing.txt");

        assertThatThrownBy(() -> FileSyncUtil.syncFile(file))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void testSyncFileIfExists_missingFileIsIgnored() {
        final Path file = tempDir.resolve("missing.txt");

        assertThatCode(() -> FileSyncUtil.syncFileIfExists(file))
                .doesNotThrowAnyException();
    }

    @Test
    void testSyncFileIfExists_dirIsIgnored() throws IOException {
        // A dir is not a regular file so must be skipped rather than opened for write.
        final Path dir = Files.createDirectory(tempDir.resolve("aDir"));

        assertThatCode(() -> FileSyncUtil.syncFileIfExists(dir))
                .doesNotThrowAnyException();
    }

    @Test
    void testSyncDirContents_syncsFilesAndSkipsSubDirs() throws IOException {
        final Path dir = Files.createDirectory(tempDir.resolve("group"));
        Files.writeString(dir.resolve("one.txt"), "1", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("two.txt"), "2", StandardCharsets.UTF_8);
        Files.createDirectory(dir.resolve("subDir"));

        assertThatCode(() -> FileSyncUtil.syncDirContents(dir))
                .doesNotThrowAnyException();

        assertThat(Files.readString(dir.resolve("one.txt"), StandardCharsets.UTF_8)).isEqualTo("1");
        assertThat(Files.readString(dir.resolve("two.txt"), StandardCharsets.UTF_8)).isEqualTo("2");
    }

    @Test
    void testSyncDirContents_emptyDir() throws IOException {
        final Path dir = Files.createDirectory(tempDir.resolve("empty"));

        assertThatCode(() -> FileSyncUtil.syncDirContents(dir))
                .doesNotThrowAnyException();
    }

    @Test
    void testSyncDir() throws IOException {
        final Path dir = Files.createDirectory(tempDir.resolve("aDir"));
        Files.writeString(dir.resolve("file.txt"), "data", StandardCharsets.UTF_8);

        assertThatCode(() -> FileSyncUtil.syncDir(dir))
                .doesNotThrowAnyException();
    }

    @Test
    void testSyncDirTree_syncsWholeNewBranch() throws IOException {
        // DirUtil.createPath builds nested dirs like <root>/2/333/555, so forcing only the leaf
        // would leave the new intermediate dirs unsynced.
        final Path root = Files.createDirectory(tempDir.resolve("root"));
        final Path leaf = Files.createDirectories(root.resolve("2/333/555"));

        assertThatCode(() -> FileSyncUtil.syncDirTree(leaf, root))
                .doesNotThrowAnyException();

        assertThat(leaf).exists();
        assertThat(root).exists();
    }

    @Test
    void testSyncDirTree_stopsAtStopAtAndDoesNotEscapeIt() throws IOException {
        // Walking must terminate at stopAt rather than continuing to the filesystem root.
        final Path root = Files.createDirectory(tempDir.resolve("stopRoot"));
        final Path leaf = Files.createDirectories(root.resolve("a/b"));

        assertThatCode(() -> FileSyncUtil.syncDirTree(leaf, root))
                .doesNotThrowAnyException();
    }

    @Test
    void testSyncDirTree_dirEqualToStopAt() throws IOException {
        final Path root = Files.createDirectory(tempDir.resolve("single"));

        assertThatCode(() -> FileSyncUtil.syncDirTree(root, root))
                .doesNotThrowAnyException();
    }

    @Test
    void testSyncDirTree_unrelatedStopAtStillTerminates() throws IOException {
        // If stopAt is not an ancestor the walk must still end at the filesystem root
        // rather than looping forever.
        final Path dir = Files.createDirectories(tempDir.resolve("x/y"));
        final Path unrelated = Files.createDirectory(tempDir.resolve("elsewhere"));

        assertThatCode(() -> FileSyncUtil.syncDirTree(dir, unrelated))
                .doesNotThrowAnyException();
    }

    @Test
    void testSyncDir_missingDirDoesNotThrow() {
        // syncDir swallows IO problems as some platforms cannot open a dir as a channel,
        // so a missing dir must not bring down a receipt either.
        final Path dir = tempDir.resolve("missingDir");

        assertThatCode(() -> FileSyncUtil.syncDir(dir))
                .doesNotThrowAnyException();
    }
}
