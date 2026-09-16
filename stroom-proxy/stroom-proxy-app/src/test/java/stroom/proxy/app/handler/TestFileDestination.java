/*
 * Copyright 2025 Crown Copyright
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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.ForwardFileConfig.LivenessCheckMode;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFileDestination {

    public static final String NAME = "my file dest";

    private Dirs dirs;
    private PathCreator pathCreator;

    @BeforeEach
    void setUp(@TempDir final Path homeDir) {
        dirs = new Dirs(homeDir);
        pathCreator = new SimplePathCreator(dirs::getHomeDir, dirs::getTempDir);
    }

    /**
     * A file group is a directory, and {@code Files.move} can only rename a directory, so before
     * {@link DirUtil#moveDirAcrossFileStores} was used here both branches of the move failed with
     * {@code DirectoryNotEmptyException} across filesystems - the very case the fallback exists for.
     */
    @Test
    void testDeliverMovesTheFileGroupWhenTheTargetIsOnAnotherFileSystem() throws IOException {
        assertDeliverMovesAcrossFileSystems(true);
    }

    /**
     * The documented remedy for the warning the atomic attempt logs. It used to reach the same plain
     * {@code Files.move}, so following the advice did not help.
     */
    @Test
    void testDeliverMovesTheFileGroupAcrossFileSystemsWhenTheAtomicMoveIsDisabled() throws IOException {
        assertDeliverMovesAcrossFileSystems(false);
    }

    /**
     * Disabling the atomic move now goes straight to the copy rather than to a rename, so pin that it
     * still delivers the whole file group. Unlike the two above, this one needs no second filesystem.
     */
    @Test
    void testDeliverWithTheAtomicMoveDisabledDeliversTheWholeFileGroup() throws IOException {
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                pathCreator,
                false);

        final Path source = createSourceDir(1);
        final Snapshot sourceSnapshot = DirectorySnapshot.of(source);

        forwardFileDest.deliver(source);

        assertThat(source)
                .doesNotExist();
        assertThat(DirectorySnapshot.of(DirUtil.createPath(dirs.getStoreDir(), 1)))
                .isEqualTo(sourceSnapshot);
    }

    private void assertDeliverMovesAcrossFileSystems(final boolean isAtomicMoveEnabled) throws IOException {
        final Path otherFileSystemDir = OtherFileSystem.find(dirs.getHomeDir());
        Assumptions.assumeTrue(otherFileSystemDir != null, OtherFileSystem.skipReason());

        try {
            final Path storeDir = otherFileSystemDir.resolve("store");
            final FileDestination forwardFileDest = new FileDestination(
                    storeDir,
                    NAME,
                    pathCreator,
                    isAtomicMoveEnabled);

            final Path source = createSourceDir(1);
            final Snapshot sourceSnapshot = DirectorySnapshot.of(source);

            forwardFileDest.deliver(source);

            assertThat(source)
                    .doesNotExist();
            assertThat(DirectorySnapshot.of(DirUtil.createPath(storeDir, 1)))
                    .isEqualTo(sourceSnapshot);
        } finally {
            FileUtil.deleteDir(otherFileSystemDir);
        }
    }

    @Test
    void testDeliverSingle() throws IOException {
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                pathCreator,
                true);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Snapshot source1Snapshot = DirectorySnapshot.of(source1);

        forwardFileDest.deliver(source1);

        assertThat(source1)
                .doesNotExist();

        final Path destPath = DirUtil.createPath(dirs.getStoreDir(), 1);
        final Snapshot destSnapshot = DirectorySnapshot.of(destPath);
        assertThat(deepListContent(dirs.getStoreDir()))
                .extracting(TypedFile::path)
                .contains(destPath);

        assertThat(destSnapshot)
                .isEqualTo(source1Snapshot);
    }

    private FileDestination createForwardFileDest() {
        return new FileDestination(dirs.getStoreDir(), NAME, pathCreator, true);
    }

    @Test
    void testDeliverMultiple() throws IOException {
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                pathCreator,
                true);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Path source2 = createSourceDir(2);
        final Path source3 = createSourceDir(3);

        Stream.of(source1, source2, source3)
                .forEach(source -> {
                });

        forwardFileDest.deliver(source1);

        assertThat(source1)
                .doesNotExist();

        assertThat(deepListContent(dirs.getStoreDir()))
                .extracting(TypedFile::path)
                .contains(DirUtil.createPath(dirs.getStoreDir(), 1));

        forwardFileDest.deliver(source2);
        forwardFileDest.deliver(source3);

        assertThat(dirs.getSourcesDir())
                .isEmptyDirectory();

        assertThat(deepListContent(dirs.getStoreDir()))
                .extracting(TypedFile::path)
                .contains(
                        DirUtil.createPath(dirs.getStoreDir(), 1),
                        DirUtil.createPath(dirs.getStoreDir(), 2),
                        DirUtil.createPath(dirs.getStoreDir(), 3));
    }

    /**
     * The commit-id counter is seeded from the directory once and never re-read, so two processes
     * delivering into one directory - two nodes on a shared mount - hand out the same ids. The
     * rename onto an existing non-empty directory fails rather than overwrites, but every collision
     * was a failed delivery. With shared writers each process delivers under a writer root of its own
     * and the two never meet; the configured directory is still what {@link FileDestination#getStoreDir()}
     * reports.
     */
    @Test
    void testTwoWritersOnOneDirectoryCollideUnlessEachHasItsOwnWriterRoot() throws IOException {
        final Path shared = dirs.getStoreDir();

        // Two single-writer destinations on one directory, as two nodes would be today.
        final FileDestination nodeA = new FileDestination(shared, NAME, pathCreator, true);
        final FileDestination nodeB = new FileDestination(shared, NAME, pathCreator, true);
        final Path sourceA = createSourceDir(1);
        final Snapshot snapshotA = DirectorySnapshot.of(sourceA);
        nodeA.deliver(sourceA);
        final Path sourceB = createSourceDir(2);
        assertThatThrownBy(() -> nodeB.deliver(sourceB))
                .as("B seeded from the same empty directory and picked A's id")
                .isInstanceOf(IOException.class);
        assertThat(sourceB).as("a losing rename leaves the source where it was").isDirectory();
        assertThat(DirectorySnapshot.of(DirUtil.createPath(shared, 1)))
                .as("and never overwrites what A delivered")
                .isEqualTo(snapshotA);

        // The same two, each with a writer root of its own.
        final Path mount = dirs.getStoreDir().resolve("mount");
        final FileDestination sharedA = new FileDestination(mount, NAME, null, null, null, pathCreator, true, true);
        final FileDestination sharedB = new FileDestination(mount, NAME, null, null, null, pathCreator, true, true);
        assertThat(sharedA.getStoreDir()).isEqualTo(mount);
        assertThat(sharedA.getWriterRoot().getParent()).isEqualTo(mount);
        assertThat(sharedA.getWriterRoot()).isNotEqualTo(sharedB.getWriterRoot());

        final Path source3 = createSourceDir(3);
        final Snapshot snapshot3 = DirectorySnapshot.of(source3);
        final Path source4 = createSourceDir(4);
        final Snapshot snapshot4 = DirectorySnapshot.of(source4);
        sharedA.deliver(source3);
        sharedB.deliver(source4);

        assertThat(DirectorySnapshot.of(DirUtil.createPath(sharedA.getWriterRoot(), 1))).isEqualTo(snapshot3);
        assertThat(DirectorySnapshot.of(DirUtil.createPath(sharedB.getWriterRoot(), 1))).isEqualTo(snapshot4);
    }

    @Test
    void testDeliverStaticSubPath() throws IOException {
        final String subPathStr = "staticSubPath";
        final Path subPath = dirs.getStoreDir().resolve(subPathStr);
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                new PathTemplateConfig(subPathStr),
                null,
                null,
                pathCreator,
                true);

        assertThat(subPath)
                .exists()
                .isDirectory()
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Snapshot source1Snapshot = DirectorySnapshot.of(source1);

        forwardFileDest.deliver(source1);

        assertThat(source1)
                .doesNotExist();

        final Path destPath = DirUtil.createPath(subPath, 1);
        final Snapshot destSnapshot = DirectorySnapshot.of(destPath);
        assertThat(deepListContent(subPath))
                .extracting(TypedFile::path)
                .contains(destPath);

        assertThat(destSnapshot)
                .isEqualTo(source1Snapshot);
    }

    @Test
    void testDeliverTemplated() throws IOException {
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                new PathTemplateConfig("${feed}/${year}", TemplatingMode.REPLACE_UNKNOWN_PARAMS),
                null,
                null,
                pathCreator,
                true);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1, Map.of(
                StandardHeaderArguments.FEED, "FEED1"
        ));
        final Path source2 = createSourceDir(2, Map.of(
                StandardHeaderArguments.FEED, "FEED2"
        ));
        final Path source3 = createSourceDir(3, Map.of(
                StandardHeaderArguments.FEED, "FEED1"
        ));
        final Path source4 = createSourceDir(4, Map.of(
                StandardHeaderArguments.FEED, "FEED2"
        ));

        final Snapshot source1Snapshot = DirectorySnapshot.of(source1);
        final Snapshot source2Snapshot = DirectorySnapshot.of(source2);
        final Snapshot source3Snapshot = DirectorySnapshot.of(source3);
        final Snapshot source4Snapshot = DirectorySnapshot.of(source4);

        forwardFileDest.deliver(source1);
        forwardFileDest.deliver(source2);
        forwardFileDest.deliver(source3);
        forwardFileDest.deliver(source4);

        assertThat(source1)
                .doesNotExist();

        final String year = String.valueOf(ZonedDateTime.now().getYear());

        // Each feed has its own id number set
        final Path dest1 = DirUtil.createPath(dirs.getStoreDir().resolve("FEED1/" + year), 1);
        final Snapshot dest1Snapshot = DirectorySnapshot.of(dest1);
        final Path dest2 = DirUtil.createPath(dirs.getStoreDir().resolve("FEED2/" + year), 1);
        final Snapshot dest2Snapshot = DirectorySnapshot.of(dest2);
        final Path dest3 = DirUtil.createPath(dirs.getStoreDir().resolve("FEED1/" + year), 2);
        final Snapshot dest3Snapshot = DirectorySnapshot.of(dest3);
        final Path dest4 = DirUtil.createPath(dirs.getStoreDir().resolve("FEED2/" + year), 2);
        final Snapshot dest4Snapshot = DirectorySnapshot.of(dest4);

        assertThat(deepListContent(dirs.getStoreDir()))
                .extracting(TypedFile::path)
                .contains(dest1,
                        dest2,
                        dest3,
                        dest4);

        assertThat(dest1Snapshot)
                .isEqualTo(source1Snapshot);
        assertThat(dest2Snapshot)
                .isEqualTo(source2Snapshot);
        assertThat(dest3Snapshot)
                .isEqualTo(source3Snapshot);
        assertThat(dest4Snapshot)
                .isEqualTo(source4Snapshot);
    }

    @Test
    void testDeliverTemplateDisabled() throws IOException {
        final String subPathStr = "${feed}";
        // subPathStr is ignored as TemplatingMode is DISABLED
        final Path subPath = dirs.getStoreDir();
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                PathTemplateConfig.DISABLED,
                null,
                null,
                pathCreator,
                true);

        assertThat(subPath)
                .exists()
                .isDirectory()
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Snapshot source1Snapshot = DirectorySnapshot.of(source1);

        forwardFileDest.deliver(source1);

        assertThat(source1)
                .doesNotExist();

        final Path destPath = DirUtil.createPath(subPath, 1);
        final Snapshot destSnapshot = DirectorySnapshot.of(destPath);
        assertThat(deepListContent(subPath))
                .extracting(TypedFile::path)
                .contains(destPath);

        assertThat(destSnapshot)
                .isEqualTo(source1Snapshot);
    }

    @Test
    void testDeliverTemplateBadParamReplace() throws IOException {
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                new PathTemplateConfig("${foo}/${year}", TemplatingMode.REPLACE_UNKNOWN_PARAMS),
                null,
                null,
                pathCreator,
                true);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Path source2 = createSourceDir(2);
        final Path source3 = createSourceDir(3);
        final Path source4 = createSourceDir(4);

        forwardFileDest.deliver(source1);
        forwardFileDest.deliver(source2);
        forwardFileDest.deliver(source3);
        forwardFileDest.deliver(source4);

        assertThat(source1)
                .doesNotExist();

        final String year = String.valueOf(ZonedDateTime.now().getYear());

        assertThat(deepListContent(dirs.getStoreDir()))
                .extracting(TypedFile::path)
                .contains(
                        DirUtil.createPath(dirs.getStoreDir().resolve("XXX/" + year), 1),
                        DirUtil.createPath(dirs.getStoreDir().resolve("XXX/" + year), 2),
                        DirUtil.createPath(dirs.getStoreDir().resolve("XXX/" + year), 3),
                        DirUtil.createPath(dirs.getStoreDir().resolve("XXX/" + year), 4));
    }

    @Test
    void testDeliverTemplateBadParamRemove() throws IOException {
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                new PathTemplateConfig("${foo}/${year}", TemplatingMode.REMOVE_UNKNOWN_PARAMS),
                null,
                null,
                pathCreator,
                true);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Path source2 = createSourceDir(2);
        final Path source3 = createSourceDir(3);
        final Path source4 = createSourceDir(4);

        forwardFileDest.deliver(source1);
        forwardFileDest.deliver(source2);
        forwardFileDest.deliver(source3);
        forwardFileDest.deliver(source4);

        assertThat(source1)
                .doesNotExist();

        final String year = String.valueOf(ZonedDateTime.now().getYear());

        assertThat(deepListContent(dirs.getStoreDir()))
                .extracting(TypedFile::path)
                .contains(
                        DirUtil.createPath(dirs.getStoreDir().resolve(year), 1),
                        DirUtil.createPath(dirs.getStoreDir().resolve(year), 2),
                        DirUtil.createPath(dirs.getStoreDir().resolve(year), 3),
                        DirUtil.createPath(dirs.getStoreDir().resolve(year), 4));
    }

    @Test
    void testDeliverTemplateBadParamIgnore() throws IOException {
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                new PathTemplateConfig("${foo}/${year}", TemplatingMode.IGNORE_UNKNOWN_PARAMS),
                null,
                null,
                pathCreator,
                true);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Path source2 = createSourceDir(2);
        final Path source3 = createSourceDir(3);
        final Path source4 = createSourceDir(4);

        forwardFileDest.deliver(source1);
        forwardFileDest.deliver(source2);
        forwardFileDest.deliver(source3);
        forwardFileDest.deliver(source4);

        assertThat(source1)
                .doesNotExist();

        final String year = String.valueOf(ZonedDateTime.now().getYear());

        assertThat(deepListContent(dirs.getStoreDir()))
                .extracting(TypedFile::path)
                .contains(
                        DirUtil.createPath(dirs.getStoreDir().resolve("${foo}/" + year), 1),
                        DirUtil.createPath(dirs.getStoreDir().resolve("${foo}/" + year), 2),
                        DirUtil.createPath(dirs.getStoreDir().resolve("${foo}/" + year), 3),
                        DirUtil.createPath(dirs.getStoreDir().resolve("${foo}/" + year), 4));
    }

    @Test
    void testDeliverTemplateBadPath() throws IOException {
        Assertions.assertThatThrownBy(() -> {
            final FileDestination forwardFileDest = new FileDestination(
                    dirs.getStoreDir(),
                    NAME,
                    new PathTemplateConfig("../sibling", // Outside the store dir, so no allowed
                            TemplatingMode.REPLACE_UNKNOWN_PARAMS),
                    null,
                    null,
                    pathCreator,
                    true);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testATargetThatHasGoneIsATransientFailureAndTheSourceStays() throws IOException {
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                pathCreator,
                true);
        final Path source = createSourceDir(1);
        // The mount goes: the store directory is replaced by something that cannot hold a directory.
        FileUtil.deleteDir(dirs.getStoreDir());
        Files.writeString(dirs.getStoreDir(), "not a directory any more");

        Assertions.assertThatThrownBy(() -> forwardFileDest.deliver(source))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(Refused.class);

        assertThat(source).as("nothing was taken").isDirectory();
        assertThat(source.resolve("proxy.zip")).exists();
    }

    @Test
    void testDeliverMissingSource() throws IOException {
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                pathCreator,
                true);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        FileUtil.deleteDir(source1);

        Assertions.assertThatThrownBy(
                        () -> {
                            forwardFileDest.deliver(source1);
                        })
                .isInstanceOf(NoSuchFileException.class);

        assertThat(source1)
                .doesNotExist();

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();
    }

    @Test
    void testLivenessWriteMode() throws IOException {
        final String livenessCheckPath = "health.check";
        final Path file = dirs.getStoreDir().resolve(livenessCheckPath);
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                null,
                "health.check",
                LivenessCheckMode.WRITE,
                pathCreator,
                true);

        assertThat(file)
                .doesNotExist();

        assertThat(forwardFileDest.livenessCheck())
                .isPresent();

        assertLivenessCheck(forwardFileDest, false);

        FileUtil.mkdirs(file.getParent());
        FileUtil.touch(file);
        assertThat(file)
                .exists()
                .isRegularFile();

        assertLivenessCheck(forwardFileDest, true);

        FileUtil.deleteFile(file);

        assertLivenessCheck(forwardFileDest, false);
    }

    @Test
    void testLivenessReadModeFile() throws IOException {
        final String livenessCheckPath = "health.check";
        final Path file = dirs.getStoreDir().resolve(livenessCheckPath);
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                null,
                "health.check",
                LivenessCheckMode.READ,
                pathCreator,
                true);

        assertThat(file)
                .doesNotExist();

        assertThat(forwardFileDest.livenessCheck())
                .isPresent();

        assertLivenessCheck(forwardFileDest, false);

        FileUtil.mkdirs(file.getParent());
        FileUtil.touch(file);
        assertThat(file)
                .exists()
                .isRegularFile();

        assertLivenessCheck(forwardFileDest, true);

        FileUtil.deleteFile(file);

        assertLivenessCheck(forwardFileDest, false);
    }

    @Test
    void testLivenessReadModeDir() throws IOException {
        final String livenessCheckPath = "health.check";
        final Path dir = dirs.getStoreDir().resolve(livenessCheckPath);
        final FileDestination forwardFileDest = new FileDestination(
                dirs.getStoreDir(),
                NAME,
                null,
                "health.check",
                LivenessCheckMode.READ,
                pathCreator,
                true);

        assertThat(dir)
                .doesNotExist();

        assertThat(forwardFileDest.livenessCheck())
                .isPresent();

        assertLivenessCheck(forwardFileDest, false);

        FileUtil.mkdirs(dir);
        assertThat(dir)
                .exists()
                .isDirectory();

        assertLivenessCheck(forwardFileDest, true);

        FileUtil.deleteDir(dir);

        assertLivenessCheck(forwardFileDest, false);
    }

    private List<Path> listContent(final Path path) {
        try (final Stream<Path> stream = Files.list(path)) {
            return stream.toList();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<TypedFile> deepListContent(final Path path) {
        return FileUtil.deepListContents(path, false)
                .stream()
                .map(fileWithAttributes -> {
                    final BasicFileAttributes attributes = fileWithAttributes.attributes();
                    final FileType fileType;
                    if (attributes.isDirectory()) {
                        fileType = FileType.DIRECTORY;
                    } else if (attributes.isRegularFile()) {
                        fileType = FileType.FILE;
                    } else {
                        // Only care about simple files/dirs in tests
                        throw new RuntimeException("Unexpected type");
                    }
                    return new TypedFile(fileType, fileWithAttributes.path());
                })
                .toList();
    }

    private Path createSourceDir(final int num) {
        return createSourceDir(num, null);
    }

    private Path createSourceDir(final int num, final Map<String, String> attrs) {
        final Path sourceDir = dirs.getSourcesDir().resolve("source_" + num);
        FileUtil.ensureDirExists(sourceDir);
        assertThat(sourceDir)
                .isDirectory()
                .exists();

        final FileGroup fileGroup = new FileGroup(sourceDir);
        fileGroup.items()
                .forEach(ThrowingConsumer.unchecked(FileUtil::touch));

        try {
            if (NullSafe.hasEntries(attrs)) {
                final Path meta = fileGroup.getMeta();
                final AttributeMap attributeMap = new AttributeMap(attrs);
                AttributeMapUtil.write(attributeMap, meta);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return sourceDir;
    }

    private void assertLivenessCheck(final Destination destination, final boolean isLive) {
        final LivenessCheck check = destination.livenessCheck().orElseThrow();
        try {
            check.check();
            if (!isLive) {
                Assertions.fail(LogUtil.message("Expecting {} not to be live", destination));
            }
        } catch (final Exception e) {
            if (isLive) {
                Assertions.fail(LogUtil.message("Expecting {} to be live: {}", destination, e.getMessage()));
            }
        }
    }

    // --------------------------------------------------------------------------------

    private enum FileType {
        DIRECTORY,
        FILE,
    }

    // --------------------------------------------------------------------------------

    private record TypedFile(FileType fileType, Path path) {

        @Override
        public String toString() {
            return switch (fileType) {
                case FILE -> "FILE";
                case DIRECTORY -> "DIR ";
            } + " " + path;
        }
    }

    // --------------------------------------------------------------------------------

    private record Dirs(Path homeDir) {

        Path getStoreDir() {
            return homeDir.resolve("store");
        }

        Path getHomeDir() {
            return homeDir;
        }

        Path getTempDir() {
            return homeDir.resolve("temp");
        }

        Path getSourcesDir() {
            return homeDir.resolve("sources");
        }
    }
}
