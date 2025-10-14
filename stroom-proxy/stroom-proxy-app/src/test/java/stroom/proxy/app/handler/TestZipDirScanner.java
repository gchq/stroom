package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.proxy.app.DirScannerConfig;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.PathFlag;
import stroom.test.common.DirectorySnapshot.PathSnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.test.common.TestUtil;
import stroom.util.io.SimplePathCreator;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestZipDirScanner {

    @Mock
    private ZipReceiver mockZipReceiver;

    @TempDir
    Path testDir;

    @Captor
    ArgumentCaptor<Path> zipFileCaptor;
    @Captor
    ArgumentCaptor<AttributeMap> attributeMapCaptor;

    @Test
    void testScan() {
        final Path ingestDir1 = testDir.resolve("ingest1");
        final Path failureDir1 = testDir.resolve("failure1");
        final DirScannerConfig config = new DirScannerConfig(
                List.of(ingestDir1.toString()),
                failureDir1.toString(),
                true,
                StroomDuration.ofSeconds(1));

        final ZipDirScanner zipDirScanner = createZipDirScanner(config);

        final Path file1 = ingestDir1.resolve("file1.txt");
        final Path file2 = ingestDir1.resolve("file2.zip");
        final Path file3 = ingestDir1.resolve("file3.ZIP");
        final Path file4 = ingestDir1.resolve("file4");
        final Path file5 = ingestDir1.resolve("dir1/subDir1/file5");
        final Path file6 = ingestDir1.resolve("dir2/subDir2/file6.Zip");

        TestUtil.createFiles(file1, file2, file3, file4, file5, file6);

        Mockito.doNothing()
                .when(mockZipReceiver)
                .receive(zipFileCaptor.capture(), attributeMapCaptor.capture());

        zipDirScanner.scan();

        assertThat(ingestDir1)
                .exists()
                .isDirectory();

        final List<Path> zipFilesProcessed = zipFileCaptor.getAllValues();
        assertThat(zipFilesProcessed)
                .containsExactlyInAnyOrder(
                        file2,
                        file3,
                        file6);

        final Snapshot snapshot = DirectorySnapshot.of(ingestDir1);
        assertThat(snapshot.stream()
                .map(PathSnapshot::path)
                .map(ingestDir1::resolve)
                .toList())
                .containsExactlyInAnyOrder(
                        file1,
                        file4,
                        file5,
                        file5.getParent(),
                        file5.getParent().getParent());
    }

    @Test
    void testScan_multipleDirs() {
        final Path ingestDir1 = testDir.resolve("ingest1");
        final Path ingestDir2 = testDir.resolve("ingest2");
        final Path failureDir1 = testDir.resolve("failure1");
        final DirScannerConfig config = new DirScannerConfig(
                List.of(ingestDir1.toString(),
                        ingestDir2.toString()),
                failureDir1.toString(),
                true,
                StroomDuration.ofSeconds(1));

        final ZipDirScanner zipDirScanner = createZipDirScanner(config);

        final Path file11 = ingestDir1.resolve("file1.txt");
        final Path file12 = ingestDir1.resolve("file2.zip");
        final Path file13 = ingestDir1.resolve("file3.ZIP");
        final Path file14 = ingestDir1.resolve("file4");
        final Path file15 = ingestDir1.resolve("dir1/subDir1/file5");
        final Path file16 = ingestDir1.resolve("dir2/subDir2/file6.Zip");

        final Path file21 = ingestDir2.resolve("file1.txt");
        final Path file22 = ingestDir2.resolve("file2.zip");
        final Path file23 = ingestDir2.resolve("file3.ZIP");
        final Path file24 = ingestDir2.resolve("file4");
        final Path file25 = ingestDir2.resolve("dir1/subDir1/file5");
        final Path file26 = ingestDir2.resolve("dir2/subDir2/file6.Zip");

        TestUtil.createFiles(
                file11, file12, file13, file14, file15, file16,
                file21, file22, file23, file24, file25, file26);

        Mockito.doNothing()
                .when(mockZipReceiver)
                .receive(zipFileCaptor.capture(), attributeMapCaptor.capture());

        zipDirScanner.scan();

        assertThat(ingestDir1)
                .exists()
                .isDirectory();
        assertThat(ingestDir2)
                .exists()
                .isDirectory();

        final List<Path> zipFilesProcessed = zipFileCaptor.getAllValues();
        assertThat(zipFilesProcessed)
                .containsExactlyInAnyOrder(
                        file12,
                        file13,
                        file16,
                        file22,
                        file23,
                        file26);

        Snapshot snapshot = DirectorySnapshot.of(ingestDir1);
        assertThat(snapshot.stream()
                .map(PathSnapshot::path)
                .map(ingestDir1::resolve)
                .toList())
                .containsExactlyInAnyOrder(
                        file11,
                        file14,
                        file15,
                        file15.getParent(),
                        file15.getParent().getParent());

        snapshot = DirectorySnapshot.of(ingestDir2);
        assertThat(snapshot.stream()
                .map(PathSnapshot::path)
                .map(ingestDir2::resolve)
                .toList())
                .containsExactlyInAnyOrder(
                        file21,
                        file24,
                        file25,
                        file25.getParent(),
                        file25.getParent().getParent());
    }

    @Test
    void testScan_badZip() {
        final Path ingestDir1 = testDir.resolve("ingest1");
        final Path ingestDir2 = testDir.resolve("ingest2");
        final Path failureDir1 = testDir.resolve("failure1");
        final DirScannerConfig config = new DirScannerConfig(
                List.of(ingestDir1.toString(),
                        ingestDir2.toString()),
                failureDir1.toString(),
                true,
                StroomDuration.ofSeconds(1));

        final ZipDirScanner zipDirScanner = createZipDirScanner(config);

        final Path badFile = ingestDir1.resolve("bad.zip");
        final Path file11 = ingestDir1.resolve("file1.txt");
        final Path file12 = ingestDir1.resolve("file2.zip");
        final Path file13 = ingestDir1.resolve("file3.ZIP");
        final Path file14 = ingestDir1.resolve("file4");
        final Path file15 = ingestDir1.resolve("dir1/subDir1/file5");
        final Path file16 = ingestDir1.resolve("dir2/subDir2/file6.Zip");

        final Path file21 = ingestDir2.resolve("file1.txt");
        final Path file22 = ingestDir2.resolve("file2.zip");
        final Path file23 = ingestDir2.resolve("file3.ZIP");
        final Path file24 = ingestDir2.resolve("file4");
        final Path file25 = ingestDir2.resolve("dir1/subDir1/file5");
        final Path file26 = ingestDir2.resolve("dir2/subDir2/file6.Zip");

        TestUtil.createFiles(
                badFile,
                file11, file12, file13, file14, file15, file16,
                file21, file22, file23, file24, file25, file26);

        final List<Path> zipFiles = new ArrayList<>();
        Mockito.doAnswer(
                        invocation -> {
                            final Path zipFile = invocation.getArgument(0, Path.class);
                            zipFiles.add(zipFile);
                            if (zipFile.getFileName().toString().contains("bad")) {
                                throw new RuntimeException("bad zip");
                            }
                            return null;
                        })
                .when(mockZipReceiver)
                .receive(Mockito.any(), Mockito.any());

        zipDirScanner.scan();

        assertThat(ingestDir1)
                .exists()
                .isDirectory();
        assertThat(ingestDir2)
                .exists()
                .isDirectory();

        assertThat(zipFiles)
                .containsExactlyInAnyOrder(
                        badFile,
                        file12,
                        file13,
                        file16,
                        file22,
                        file23,
                        file26);

        Snapshot snapshot = DirectorySnapshot.of(ingestDir1);
        assertThat(snapshot.stream()
                .map(PathSnapshot::path)
                .map(ingestDir1::resolve)
                .toList())
                .containsExactlyInAnyOrder(
                        file11,
                        file14,
                        file15,
                        file15.getParent(),
                        file15.getParent().getParent());

        snapshot = DirectorySnapshot.of(ingestDir2);
        assertThat(snapshot.stream()
                .map(PathSnapshot::path)
                .map(ingestDir2::resolve)
                .toList())
                .containsExactlyInAnyOrder(
                        file21,
                        file24,
                        file25,
                        file25.getParent(),
                        file25.getParent().getParent());

        snapshot = DirectorySnapshot.of(failureDir1);
        assertThat(snapshot.stream()
                .filter(pathSnapshot -> pathSnapshot.flags().contains(PathFlag.REGULAR_FILE))
                .map(PathSnapshot::path)
                .toList())
                .containsExactlyInAnyOrder(Path.of("0/001/bad.zip"));
    }

    private ZipDirScanner createZipDirScanner(final DirScannerConfig config) {
        final Path homeDir = testDir.resolve("home");
        final Path stroomTempDir = testDir.resolve("temp");
        final SimplePathCreator pathCreator = new SimplePathCreator(() -> homeDir, () -> stroomTempDir);
        return new ZipDirScanner(
                () -> config,
                pathCreator,
                mockZipReceiver,
                new ProxyReceiptIdGenerator(() -> "test-node"));
    }
}
