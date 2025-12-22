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

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestOrphanFileFinder extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestOrphanFileFinder.class);

    private static final int NEG_SIXTY = -60;
    private static final int NEG_FOUR = -4;

    @Inject
    private FsFileFinder fileFinder;
    @Inject
    private DataVolumeService dataVolumeService;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private DataStoreServiceConfig config;
    @Inject
    private FsVolumeService volumeService;
    @Inject
    private Store streamStore;
    @Inject
    private MetaService metaService;
    @Inject
    private PathCreator pathCreator;
    // Use provider so we can set up the config before this guice create this
    @Inject
    private Provider<FsOrphanFileFinderExecutor> fsOrphanFileFinderExecutorProvider;

    @BeforeEach
    void setup() {
        metaService.find(new FindMetaCriteria())
                .forEach(meta -> {
                    LOGGER.info("Deleting meta with id: {}, volume: {}",
                            meta.getId(),
                            FileUtil.getCanonicalPath(
                                    pathCreator.toAppPath(dataVolumeService
                                            .findDataVolume(meta.getId()).getVolume().getPath())));
                    metaService.delete(meta.getId());
                });

        final List<FsVolume> volumeList = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        volumeList.forEach(fsVolume -> {
            final String pathStr = fsVolume.getPath();
            final Path path = Path.of(pathStr);
            LOGGER.info("Clearing contents of {}", path);
            FileUtil.deleteContents(path);
        });
        listAllVolsContent();
    }

    @AfterEach
    void unsetProperties() {
        clearConfigValueMapper();
    }

    private void listAllVolsContent() {
        final List<FsVolume> volumeList = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        volumeList.forEach(fsVolume -> {
            final String pathStr = fsVolume.getPath();
            final Path path = Path.of(pathStr);
            listContents(path);
        });
    }

    private static void listContents(final Path path) {
        final StringBuilder sb = new StringBuilder();
        try (final Stream<Path> stream = Files.walk(path)) {
            stream.forEach(path2 -> sb.append("\n  ")
                    .append(path2.toString()));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        LOGGER.info("Listing contents of {}{}", path, sb);
    }

    @Test
    void testSimple() throws IOException {
        setConfigValueMapper(DataStoreServiceConfig.class, config -> config
                .withFileSystemCleanOldAge(StroomDuration.ZERO));

        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Meta md = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        final String date = ZonedDateTime
                .ofInstant(Instant.ofEpochMilli(md.getCreateMs()), ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH));

        commonTestScenarioCreator.createSampleBlankProcessedFile(feedName, md);

        final List<Path> files = fileFinder.findAllStreamFile(md);

        assertThat(files.size() > 0).isTrue();

        final FindDataVolumeCriteria findStreamVolumeCriteria = FindDataVolumeCriteria.create(md);
        assertThat(dataVolumeService.find(findStreamVolumeCriteria).size() > 0).isTrue();

        final Path dir = files.iterator().next().getParent();

        final Path test1 = dir.resolve("badfile.dat");

        Files.createFile(test1);

        assertThat(Files.exists(test1)).isTrue();

        final FsOrphanFileFinderSummary summary = new FsOrphanFileFinderSummary();
        final List<String> fileList = scan(summary);

        final String expected = LogUtil.message("""
                Summary:

                | Type       | File/Directory | Feed (if present) | Date       | Orphan Count |
                |------------|----------------|-------------------|------------|--------------|
                | RAW_EVENTS | Dir            |                   | {} |            1 |""", date);

        assertThat(summary.toString().trim())
                .isEqualTo(expected);

        final List<FsVolume> volumeList = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        assertThat(volumeList.size()).isEqualTo(1);
        assertThat(fileList).contains(FileUtil.getCanonicalPath(test1));
    }

    @Test
    void testSimple_withInvalidPaths() throws IOException {
        setConfigValueMapper(DataStoreServiceConfig.class, config -> config
                .withFileSystemCleanOldAge(StroomDuration.ZERO));

        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Meta md = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        final String date = ZonedDateTime
                .ofInstant(Instant.ofEpochMilli(md.getCreateMs()), ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH));

        commonTestScenarioCreator.createSampleBlankProcessedFile(feedName, md);

        final List<Path> files = fileFinder.findAllStreamFile(md);

        assertThat(files.size() > 0).isTrue();

        final FindDataVolumeCriteria findStreamVolumeCriteria = FindDataVolumeCriteria.create(md);
        assertThat(dataVolumeService.find(findStreamVolumeCriteria).size() > 0).isTrue();

        final Path validDir = files.iterator()
                .next()
                .getParent();
        final Path test1 = validDir.resolve("badfile1.dat");
        Files.createFile(test1);
        assertThat(test1).isRegularFile();

        final Path invalidDirBase = files.iterator()
                .next()
                .getParent()
                .getParent()
                .getParent()
                .getParent();
        final Path invalidDir1 = invalidDirBase.resolve("empty_dir");
        FileUtil.ensureDirExists(invalidDir1);
        assertThat(invalidDir1).isDirectory();

        final Path invalidDir2 = invalidDirBase.resolve("contains_file");
        FileUtil.ensureDirExists(invalidDir2);
        final Path test2 = invalidDir2.resolve("badfile2.dat");
        Files.createFile(test2);
        assertThat(test2).isRegularFile();

        final FsOrphanFileFinderSummary summary = new FsOrphanFileFinderSummary();
        final List<String> fileList = scan(summary);

        LOGGER.info("Summary:\n{}", summary);

        assertThat(summary.toString().trim())
                .contains("Summary:")
                .containsPattern("badfile2.dat *\\| File")
                .containsPattern("empty_dir *\\| Empty directory");

        final List<FsVolume> volumeList = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
        assertThat(volumeList.size()).isEqualTo(1);
        assertThat(fileList).contains(FileUtil.getCanonicalPath(test1));
    }

    @Test
    void testScan() throws IOException {
        final FsOrphanFileFinderSummary summary = new FsOrphanFileFinderSummary();
        scan(summary);
        assertThat(summary.toString()).isEqualTo("");

        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        final ZonedDateTime oldDate = now.plusDays(NEG_SIXTY);
        final String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH));

        // Write a file 2 files ... on we leave locked and the other not locked
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final MetaProperties lockfile1 = MetaProperties.builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        final MetaProperties nolockfile1 = MetaProperties.builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        //
        // FILE1 LOCKED
        //
        // Write some data
        try (final Target lockstreamTarget1 = streamStore.openTarget(lockfile1)) {
            TargetUtil.write(lockstreamTarget1, "MyTest");

            final Collection<Path> lockedFiles = fileFinder.findAllStreamFile(
                    lockstreamTarget1.getMeta());
            FileSystemUtil.updateLastModified(lockedFiles, oldDate.toInstant().toEpochMilli());
            dataVolumeService.find(FindDataVolumeCriteria.create(lockstreamTarget1.getMeta()));
            // // Hack making the last access time quite old
            // for (StreamVolume volume : volumeList) {
            // volume.setLastAccessMs(oldDate.toDate().getTime());
            // streamMaintenanceService.save(volume);
            // }

            //
            // FILE2 UNLOCKED
            //
            final Meta meta;
            try (final Target nolockstreamTarget1 = streamStore.openTarget(nolockfile1)) {
                meta = nolockstreamTarget1.getMeta();
                TargetUtil.write(nolockstreamTarget1, "MyTest");
            }
            final Collection<Path> unlockedFiles = fileFinder
                    .findAllStreamFile(meta);
            final Path directory = unlockedFiles.iterator().next().getParent();
            // Create some other files on the file system

            // Copy something that is quite old into the same directory.
            final Path oldfile = directory.resolve("oldfile.txt");
            Files.createFile(oldfile);
            FileUtil.setLastModified(oldfile, oldDate.toInstant().toEpochMilli());

            // Create a old sub directory;
            final Path olddir = directory.resolve("olddir");
            FileUtil.mkdirs(olddir);
            FileUtil.setLastModified(olddir,
                    now.plusDays(NEG_SIXTY).toInstant().toEpochMilli());

            final Path newdir = directory.resolve("newdir");
            FileUtil.mkdirs(newdir);
            FileUtil.setLastModified(newdir,
                    now.plusDays(NEG_SIXTY).toInstant().toEpochMilli());

            final Path oldfileinnewdir = newdir.resolve("oldfileinnewdir.txt");
            Files.createFile(oldfileinnewdir);
            FileUtil.setLastModified(oldfileinnewdir,
                    now.plusDays(NEG_FOUR).toInstant().toEpochMilli());

            // Run the clean
            final FsOrphanFileFinderSummary summary2 = new FsOrphanFileFinderSummary();
            final List<String> fileList = scan(summary2);

            assertThat(fileList).as("expected orphan old file " + oldfile)
                    .contains(FileUtil.getCanonicalPath(oldfile));
            assertThat(fileList).as("expected orphan old dir " + olddir)
                    .contains(FileUtil.getCanonicalPath(olddir));
            assertThat(fileList).as("unexpected orphan new dir " + newdir)
                    .doesNotContain(FileUtil.getCanonicalPath(newdir));
            assertThat(fileList).as("old file in new dir")
                    .contains(FileUtil.getCanonicalPath(oldfileinnewdir));

            final String expected = LogUtil.message("""
                    Summary:

                    | Type       | File/Directory | Feed (if present) | Date       | Orphan Count |
                    |------------|----------------|-------------------|------------|--------------|
                    | RAW_EVENTS | Dir            |                   | {} |            3 |""", date);

            assertThat(summary2.toString().trim())
                    .isEqualTo(expected);

            final List<FsVolume> volumeList = volumeService.find(FindFsVolumeCriteria.matchAll()).getValues();
            assertThat(volumeList.size()).isEqualTo(1);

            assertThat(FileSystemUtil.isAllFile(lockedFiles)).as("Locked files should still exist").isTrue();
            assertThat(FileSystemUtil.isAllFile(unlockedFiles)).as("Unlocked files should still exist").isTrue();
        }
    }

    /**
     * NOTE ERROR LOGGING "processDirectory() - Missing Files for..." is expected.
     */
    @Test
    void testArchiveRemovedFile() {
        setConfigValueMapper(DataStoreServiceConfig.class, config -> config
                .withFileSystemCleanOldAge(StroomDuration.ofDays(1)));
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Meta meta = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);

        Collection<Path> files = fileFinder.findAllStreamFile(meta);

        for (final Path file : files) {
            listContents(file.getParent());
            assertThat(FileUtil.delete(file)).isTrue();
        }

        final FindDataVolumeCriteria streamVolumeCriteria = FindDataVolumeCriteria.create(meta);

        assertThat(dataVolumeService.find(streamVolumeCriteria).size() >= 1)
                .as("Must be saved to at least one volume")
                .isTrue();

        final FsOrphanFileFinderSummary summary = new FsOrphanFileFinderSummary();
        scan(summary);
        assertThat(summary.toString()).isEqualTo("");

        files = fileFinder.findAllStreamFile(meta);

        assertThat(files.size())
                .as("Files have been deleted above")
                .isEqualTo(0);

        assertThat(dataVolumeService.find(streamVolumeCriteria).size() >= 1)
                .as("Volumes should still exist as they are new")
                .isTrue();

        final FsOrphanFileFinderSummary summary2 = new FsOrphanFileFinderSummary();
        scan(summary2);
        assertThat(summary2.toString()).isEqualTo("");
    }

    @Test
    void testScanLotsOfFiles() throws IOException {
        setConfigValueMapper(DataStoreServiceConfig.class, config -> config
                .withFileSystemCleanOldAge(StroomDuration.ofDays(1)));
        final FsOrphanFileFinderSummary summary = new FsOrphanFileFinderSummary();
        scan(summary, false);
        assertThat(summary.toString()).isEqualTo("");

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final long endTime = System.currentTimeMillis();
        final long twoDaysTime = 1000 * 60 * 60 * 24 * 2;
        final long tenMin = 1000 * 60 * 10;
        final long startTime = endTime - twoDaysTime;
        for (long time = startTime; time < endTime; time += tenMin) {
            final MetaProperties metaProperties = MetaProperties.builder()
                    .feedName(feedName)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .createMs(time)
                    .build();
            try (final Target target = streamStore.openTarget(metaProperties)) {
                TargetUtil.write(target, "TEST");
            }
        }

        final FsOrphanFileFinderSummary summary2 = new FsOrphanFileFinderSummary();
        scan(summary2, false);
        assertThat(summary2.toString()).isEqualTo("");
    }

    private List<String> scan(final FsOrphanFileFinderSummary summary) {
        return scan(summary, true);
    }

    private List<String> scan(final FsOrphanFileFinderSummary summary, final boolean logContents) {
        if (logContents) {
            metaService.find(new FindMetaCriteria())
                    .forEach(meta -> {
                        LOGGER.info("Found meta with id: {}, volume: {}",
                                meta.getId(),
                                FileUtil.getCanonicalPath(
                                        pathCreator
                                                .toAppPath(dataVolumeService
                                                        .findDataVolume(meta.getId()).getVolume().getPath())));
                    });

            listAllVolsContent();
        }

        final List<String> fileList = new ArrayList<>();
        final Consumer<Path> orphanConsumer = path -> {
            fileList.add(FileUtil.getCanonicalPath(path));
            LOGGER.info("Found orphan: {}", path);
            summary.addPath(path);
        };
        fsOrphanFileFinderExecutorProvider.get()
                .scan(orphanConsumer, new SimpleTaskContext());
        LOGGER.info("summary:\n{}", summary.toString());
        return fileList;
    }
}
