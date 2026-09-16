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
import stroom.proxy.app.DirScannerConfig;
import stroom.security.api.CommonSecurityContext;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.PathSnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.test.common.TestUtil;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestZipDirScanner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZipDirScanner.class);

    @Mock
    private Receiver mockReceiver;
    @Mock
    private CommonSecurityContext mockSecurityContext;

    /**
     * Set for the duration of a {@link CommonSecurityContext#asProcessingUser(Runnable)} call
     * so a test can assert what ran inside it.
     */
    private final AtomicBoolean inProcessingUser = new AtomicBoolean(false);

    @TempDir
    Path testDir;

    @Captor
    ArgumentCaptor<Path> zipFileCaptor;
    @Captor
    ArgumentCaptor<AttributeMap> attributeMapCaptor;

    /**
     * A file arriving on disk has no authenticated sender, so nothing establishes a user for
     * the receipt check to run as. Receipt-check modes that consult feed status
     * (FEED_STATUS, RECEIPT_POLICY, FEED_EXISTENCE) then fail with
     * "No user is currently logged in" and the bundle is quarantined. The datafeed path
     * already elevates to the processing user before filtering; this path must do the same.
     */
    @Test
    void testScanRunsReceiveAsTheProcessingUser() {
        final Path ingestDir = testDir.resolve("ingest");
        final Path failureDir = testDir.resolve("failure");
        final DirScannerConfig config = new DirScannerConfig(
                List.of(ingestDir.toString()),
                failureDir.toString(),
                true,
                StroomDuration.ofSeconds(1));

        final ZipDirScanner zipDirScanner = createZipDirScanner(config);

        final Path zipFile = ingestDir.resolve("file1.zip");
        TestUtil.createFiles(zipFile);

        final AtomicBoolean receiveSawProcessingUser = new AtomicBoolean(false);
        Mockito.doAnswer(invocation -> {
            receiveSawProcessingUser.set(inProcessingUser.get());
            return null;
        })
                .when(mockReceiver)
                .receiveZip(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        zipDirScanner.scan();

        Mockito.verify(mockReceiver).receiveZip(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        assertThat(receiveSawProcessingUser)
                .withFailMessage("receive() must be invoked as the processing user")
                .isTrue();
    }

    /**
     * Two defects together, which is why they belong in one test: the fixtures only ever created
     * {@code .zip}, {@code .txt} and extensionless files, so {@code isSidecarFile} could only take its
     * {@code return false} branch and the sidecar path was <strong>dead under test</strong>. That is
     * how the second survived — {@code SIDECAR_EXTENSIONS} held the FILE NAME "proxy.entries" in a set
     * compared against an extension, so it never matched, and nothing exercised it.
     * <p>
     * This is the replay case from {@code data-path.md}: an operator moves a failed group back into
     * the scan directory, sidecars and all. Its {@code proxy.entries} must be recognised as part of
     * the group, not counted as an unknown file and moved to quarantine while its own zip succeeds.
     * </p>
     */
    @Test
    void testAMovedInGroupsSidecarsAreNotCountedAsUnknownFiles() {
        final Path ingestDir = testDir.resolve("ingest-sidecars");
        final Path failureDir = testDir.resolve("failure-sidecars");
        final DirScannerConfig config = new DirScannerConfig(
                List.of(ingestDir.toString()),
                failureDir.toString(),
                true,
                StroomDuration.ofSeconds(1));

        final ZipDirScanner zipDirScanner = createZipDirScanner(config);

        // A group as it appears in 03_failure, moved back in for replay.
        TestUtil.createFiles(
                ingestDir.resolve("proxy.zip"),
                ingestDir.resolve("proxy.meta"),
                ingestDir.resolve("proxy.entries"),
                ingestDir.resolve("error.log"));

        zipDirScanner.scan();

        // An unknown file is moved to the failure directory by postVisitDirectory; a recognised
        // sidecar is consumed with its group. So what is left in the failure area is the observable
        // form of the classification. ScanResult itself is private, and asserting the effect is the
        // better test anyway - the count is a symptom, the quarantined file is the harm.
        assertThat(failureDir)
                .withFailMessage("a successful re-ingest must quarantine nothing; proxy.entries was "
                                 + "counted unknown because the extension set held a filename")
                .satisfiesAnyOf(
                        dir -> assertThat(dir).doesNotExist(),
                        dir -> assertThat(dir).isEmptyDirectory());
    }

    @Test
    void testScan() {
        final Path ingestDir1 = testDir.resolve("ingest1");
        final Path failureDir = testDir.resolve("failure");
        final DirScannerConfig config = new DirScannerConfig(
                List.of(ingestDir1.toString()),
                failureDir.toString(),
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
                .when(mockReceiver)
                .receiveZip(Mockito.any(), attributeMapCaptor.capture(), Mockito.any(), zipFileCaptor.capture());

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

        assertThat(DirectorySnapshot.of(ingestDir1).pathSnapshots())
                .isEmpty();

        final Snapshot snapshot = DirectorySnapshot.of(failureDir);
        LOGGER.debug("Snapshot of {}\n{}", failureDir, snapshot);

        assertThat(snapshot.stream()
                .map(PathSnapshot::path)
                .map(failureDir::resolve)
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .toList())
                .containsExactlyInAnyOrderElementsOf(Stream.of(
                                file1,
                                file4,
                                file5)
                        .map(Path::getFileName)
                        .toList());
    }

    @Test
    void testScanMultipleDirs() {
        final Path ingestDir1 = testDir.resolve("ingest1");
        final Path ingestDir2 = testDir.resolve("ingest2");
        final Path failureDir = testDir.resolve("failure");
        final DirScannerConfig config = new DirScannerConfig(
                List.of(ingestDir1.toString(),
                        ingestDir2.toString()),
                failureDir.toString(),
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
                .when(mockReceiver)
                .receiveZip(Mockito.any(), attributeMapCaptor.capture(), Mockito.any(), zipFileCaptor.capture());

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

        Snapshot snapshot;
        snapshot = DirectorySnapshot.of(ingestDir1);
        assertThat(snapshot.pathSnapshots())
                .isEmpty();

        snapshot = DirectorySnapshot.of(ingestDir2);
        assertThat(snapshot.pathSnapshots())
                .isEmpty();

        snapshot = DirectorySnapshot.of(failureDir);
        assertThat(snapshot.stream()
                .map(PathSnapshot::path)
                .map(failureDir::resolve)
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .toList())
                .containsExactlyInAnyOrderElementsOf(Stream.of(
                                file11,
                                file14,
                                file15,
                                file21,
                                file24,
                                file25)
                        .map(Path::getFileName)
                        .toList());
    }

    @Test
    void testScanBadZip() {
        final Path ingestDir1 = testDir.resolve("ingest1");
        final Path ingestDir2 = testDir.resolve("ingest2");
        final Path failureDir = testDir.resolve("failure");
        final DirScannerConfig config = new DirScannerConfig(
                List.of(ingestDir1.toString(),
                        ingestDir2.toString()),
                failureDir.toString(),
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
                            final Path zipFile = invocation.getArgument(3, Path.class);
                            zipFiles.add(zipFile);
                            if (zipFile.getFileName().toString().contains("bad")) {
                                throw new RuntimeException("bad zip");
                            }
                            return null;
                        })
                .when(mockReceiver)
                .receiveZip(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

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

        Snapshot snapshot;
        snapshot = DirectorySnapshot.of(ingestDir1);
        assertThat(snapshot.pathSnapshots())
                .isEmpty();

        snapshot = DirectorySnapshot.of(ingestDir2);
        assertThat(snapshot.pathSnapshots())
                .isEmpty();

        snapshot = DirectorySnapshot.of(failureDir);
        assertThat(snapshot.stream()
                .map(PathSnapshot::path)
                .map(failureDir::resolve)
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .toList())
                .containsExactlyInAnyOrderElementsOf(Stream.of(
                                file11,
                                file14,
                                file15,
                                file21,
                                file24,
                                file25,
                                badFile)
                        .map(Path::getFileName)
                        .toList());
    }

    private ZipDirScanner createZipDirScanner(final DirScannerConfig config) {
        final Path homeDir = testDir.resolve("home");
        final Path stroomTempDir = testDir.resolve("temp");
        final SimplePathCreator pathCreator = new SimplePathCreator(() -> homeDir, () -> stroomTempDir);

        // Run the supplied work, recording that it ran inside the processing user scope.
        Mockito.lenient()
                .doAnswer(invocation -> {
                    final Runnable runnable = invocation.getArgument(0);
                    inProcessingUser.set(true);
                    try {
                        runnable.run();
                    } finally {
                        inProcessingUser.set(false);
                    }
                    return null;
                })
                .when(mockSecurityContext)
                .asProcessingUser(Mockito.any());

        return new ZipDirScanner(
                () -> config,
                pathCreator,
                mockReceiver,
                new ProxyReceiptIdGenerator(() -> "test-node"),
                mockSecurityContext);
    }
}
