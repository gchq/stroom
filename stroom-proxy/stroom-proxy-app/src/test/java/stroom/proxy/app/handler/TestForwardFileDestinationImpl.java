package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.ForwardFileConfig.LivenessCheckMode;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.util.NullSafe;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestForwardFileDestinationImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestForwardFileDestinationImpl.class);
    public static final String NAME = "my file dest";

    private Dirs dirs;
    private PathCreator pathCreator;

    @BeforeEach
    void setUp(@TempDir Path homeDir) {
        dirs = new Dirs(homeDir);
        pathCreator = new SimplePathCreator(dirs::getHomeDir, dirs::getTempDir);
    }

    @Test
    void testAdd_single() {
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                pathCreator);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Snapshot source1Snapshot = DirectorySnapshot.of(source1);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(listContent(dirs.getSourcesDir()))
                .containsExactlyInAnyOrder(source1);

        assertThat(source1)
                .isDirectory()
                .exists();

        forwardFileDest.add(source1);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

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

    private ForwardFileDestination createForwardFileDest() {
        return new ForwardFileDestinationImpl(dirs.getStoreDir(), NAME, pathCreator);
    }

    @Test
    void testAdd_multiple() {
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                pathCreator);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Path source2 = createSourceDir(2);
        final Path source3 = createSourceDir(3);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(listContent(dirs.getSourcesDir()))
                .containsExactlyInAnyOrder(source1, source2, source3);

        Stream.of(source1, source2, source3)
                .forEach(source -> {
                    assertThat(source1)
                            .isDirectory()
                            .exists();
                });

        forwardFileDest.add(source1);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(source1)
                .doesNotExist();

        assertThat(listContent(dirs.getSourcesDir()))
                .containsExactlyInAnyOrder(source2, source3);

        assertThat(deepListContent(dirs.getStoreDir()))
                .extracting(TypedFile::path)
                .contains(DirUtil.createPath(dirs.getStoreDir(), 1));

        forwardFileDest.add(source2);
        forwardFileDest.add(source3);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(dirs.getSourcesDir())
                .isEmptyDirectory();

        assertThat(deepListContent(dirs.getStoreDir()))
                .extracting(TypedFile::path)
                .contains(
                        DirUtil.createPath(dirs.getStoreDir(), 1),
                        DirUtil.createPath(dirs.getStoreDir(), 2),
                        DirUtil.createPath(dirs.getStoreDir(), 3));
    }

    @Test
    void testAdd_staticSubPath() {
        final String subPathStr = "staticSubPath";
        final Path subPath = dirs.getStoreDir().resolve(subPathStr);
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                subPathStr,
                null,
                null,
                null,
                pathCreator);

        assertThat(subPath)
                .exists()
                .isDirectory()
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Snapshot source1Snapshot = DirectorySnapshot.of(source1);

        dumpContents(dirs.getSourcesDir());
        dumpContents(subPath);

        assertThat(listContent(dirs.getSourcesDir()))
                .containsExactlyInAnyOrder(source1);

        assertThat(source1)
                .isDirectory()
                .exists();

        forwardFileDest.add(source1);

        dumpContents(dirs.getSourcesDir());
        dumpContents(subPath);

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
    void testAdd_templated() {
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                "${feed}/${year}",
                TemplatingMode.REPLACE_UNKNOWN,
                null,
                null,
                pathCreator);

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

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(listContent(dirs.getSourcesDir()))
                .containsExactlyInAnyOrder(source1, source2, source3, source4);

        assertThat(source1)
                .isDirectory()
                .exists();

        final Snapshot source1Snapshot = DirectorySnapshot.of(source1);
        final Snapshot source2Snapshot = DirectorySnapshot.of(source2);
        final Snapshot source3Snapshot = DirectorySnapshot.of(source3);
        final Snapshot source4Snapshot = DirectorySnapshot.of(source4);

        forwardFileDest.add(source1);
        forwardFileDest.add(source2);
        forwardFileDest.add(source3);
        forwardFileDest.add(source4);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(source1)
                .doesNotExist();

        final String year = String.valueOf(ZonedDateTime.now().getYear());


        final Path dest1 = DirUtil.createPath(dirs.getStoreDir().resolve("FEED1/" + year), 1);
        final Snapshot dest1Snapshot = DirectorySnapshot.of(dest1);
        final Path dest2 = DirUtil.createPath(dirs.getStoreDir().resolve("FEED2/" + year), 2);
        final Snapshot dest2Snapshot = DirectorySnapshot.of(dest2);
        final Path dest3 = DirUtil.createPath(dirs.getStoreDir().resolve("FEED1/" + year), 3);
        final Snapshot dest3Snapshot = DirectorySnapshot.of(dest3);
        final Path dest4 = DirUtil.createPath(dirs.getStoreDir().resolve("FEED2/" + year), 4);
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
    void testAdd_template_badParam_replace() {
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                "${foo}/${year}",
                TemplatingMode.REPLACE_UNKNOWN,
                null,
                null,
                pathCreator);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Path source2 = createSourceDir(2);
        final Path source3 = createSourceDir(3);
        final Path source4 = createSourceDir(4);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(listContent(dirs.getSourcesDir()))
                .containsExactlyInAnyOrder(source1, source2, source3, source4);

        assertThat(source1)
                .isDirectory()
                .exists();

        forwardFileDest.add(source1);
        forwardFileDest.add(source2);
        forwardFileDest.add(source3);
        forwardFileDest.add(source4);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

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
    void testAdd_template_badParam_remove() {
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                "${foo}/${year}",
                TemplatingMode.REMOVE_UNKNOWN,
                null,
                null,
                pathCreator);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Path source2 = createSourceDir(2);
        final Path source3 = createSourceDir(3);
        final Path source4 = createSourceDir(4);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(listContent(dirs.getSourcesDir()))
                .containsExactlyInAnyOrder(source1, source2, source3, source4);

        assertThat(source1)
                .isDirectory()
                .exists();

        forwardFileDest.add(source1);
        forwardFileDest.add(source2);
        forwardFileDest.add(source3);
        forwardFileDest.add(source4);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

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
    void testAdd_template_badParam_ignore() {
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                "${foo}/${year}",
                TemplatingMode.IGNORE_UNKNOWN,
                null,
                null,
                pathCreator);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        final Path source2 = createSourceDir(2);
        final Path source3 = createSourceDir(3);
        final Path source4 = createSourceDir(4);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(listContent(dirs.getSourcesDir()))
                .containsExactlyInAnyOrder(source1, source2, source3, source4);

        assertThat(source1)
                .isDirectory()
                .exists();

        forwardFileDest.add(source1);
        forwardFileDest.add(source2);
        forwardFileDest.add(source3);
        forwardFileDest.add(source4);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

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
    void testAdd_template_badPath() {
        Assertions.assertThatThrownBy(() -> {
            final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                    dirs.getStoreDir(),
                    NAME,
                    "../sibling", // Outside the store dir, so no allowed
                    TemplatingMode.REPLACE_UNKNOWN,
                    null,
                    null,
                    pathCreator);
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAdd_missingSource() {
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                pathCreator);

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();

        final Path source1 = createSourceDir(1);
        FileUtil.deleteDir(source1);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(listContent(dirs.getSourcesDir()))
                .isEmpty();

        assertThat(source1)
                .doesNotExist();

        Assertions.assertThatThrownBy(
                        () -> {
                            forwardFileDest.add(source1);
                        })
                .isInstanceOf(UncheckedIOException.class)
                .extracting(Throwable::getCause)
                .isInstanceOf(NoSuchFileException.class);

        dumpContents(dirs.getSourcesDir());
        dumpContents(dirs.getStoreDir());

        assertThat(source1)
                .doesNotExist();

        assertThat(dirs.getStoreDir())
                .isEmptyDirectory();
    }

    @Test
    void testLiveness_writeMode() throws IOException {
        final String livenessCheckPath = "health.check";
        final Path file = dirs.getStoreDir().resolve(livenessCheckPath);
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                null,
                null,
                "health.check",
                LivenessCheckMode.WRITE,
                pathCreator);

        assertThat(file)
                .doesNotExist();

        assertThat(forwardFileDest.hasLivenessCheck())
                .isTrue();

        assertThat(forwardFileDest.performLivenessCheck())
                .isFalse();

        FileUtil.mkdirs(file.getParent());
        FileUtil.touch(file);
        assertThat(file)
                .exists()
                .isRegularFile();

        assertThat(forwardFileDest.performLivenessCheck())
                .isTrue();

        FileUtil.deleteFile(file);

        assertThat(forwardFileDest.performLivenessCheck())
                .isFalse();
    }

    @Test
    void testLiveness_readMode_file() throws IOException {
        final String livenessCheckPath = "health.check";
        final Path file = dirs.getStoreDir().resolve(livenessCheckPath);
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                null,
                null,
                "health.check",
                LivenessCheckMode.READ,
                pathCreator);

        assertThat(file)
                .doesNotExist();

        assertThat(forwardFileDest.hasLivenessCheck())
                .isTrue();

        assertThat(forwardFileDest.performLivenessCheck())
                .isFalse();

        FileUtil.mkdirs(file.getParent());
        FileUtil.touch(file);
        assertThat(file)
                .exists()
                .isRegularFile();

        assertThat(forwardFileDest.performLivenessCheck())
                .isTrue();

        FileUtil.deleteFile(file);

        assertThat(forwardFileDest.performLivenessCheck())
                .isFalse();
    }

    @Test
    void testLiveness_readMode_dir() throws IOException {
        final String livenessCheckPath = "health.check";
        final Path dir = dirs.getStoreDir().resolve(livenessCheckPath);
        final ForwardFileDestination forwardFileDest = new ForwardFileDestinationImpl(
                dirs.getStoreDir(),
                NAME,
                null,
                null,
                "health.check",
                LivenessCheckMode.READ,
                pathCreator);

        assertThat(dir)
                .doesNotExist();

        assertThat(forwardFileDest.hasLivenessCheck())
                .isTrue();

        assertThat(forwardFileDest.performLivenessCheck())
                .isFalse();

        FileUtil.mkdirs(dir);
        assertThat(dir)
                .exists()
                .isDirectory();

        assertThat(forwardFileDest.performLivenessCheck())
                .isTrue();

        FileUtil.deleteDir(dir);

        assertThat(forwardFileDest.performLivenessCheck())
                .isFalse();
    }

    private void dumpContents(final Path path) {
        LOGGER.debug("Contents of {}\n{}",
                path,
                deepListContent(path)
                        .stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining("\n")));
    }

    private List<Path> listContent(final Path path) {
        try (final Stream<Path> stream = Files.list(path)) {
            return stream.toList();
        } catch (IOException e) {
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sourceDir;
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
