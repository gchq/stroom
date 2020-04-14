/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store.impl.fs;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.Meta;
import stroom.task.impl.ExecutorProviderImpl;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileSystemCleanTask extends AbstractCoreIntegrationTest {
    private static final int NEG_SIXTY = -60;
    private static final int NEG_FOUR = -4;

    private static Logger LOGGER = LoggerFactory.getLogger(TestFileSystemCleanTask.class);

    @Inject
    private Store streamStore;
    @Inject
    private FsDataStoreMaintenanceService streamMaintenanceService;
    @Inject
    private DataVolumeService dataVolumeService;
    @Inject
    private FsCleanExecutor fileSystemCleanTaskExecutor;
    @Inject
    private ExecutorProviderImpl executorProvider;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;

    @Test
    void testCheckCleaning() throws IOException {
        fileSystemCleanTaskExecutor.clean();

        waitForTaskManagerToComplete();

        final ZonedDateTime oldDate = ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_SIXTY);

        // Write a file 2 files ... on we leave locked and the other not locked
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final MetaProperties lockfile1 = new MetaProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        final MetaProperties nolockfile1 = new MetaProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        //
        // FILE1 LOCKED
        //
        // Write some data
        try (final Target lockstreamTarget1 = streamStore.openTarget(lockfile1)) {
            TargetUtil.write(lockstreamTarget1, "MyTest");

            final Collection<Path> lockedFiles = streamMaintenanceService.findAllStreamFile(lockstreamTarget1.getMeta());
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
            Meta meta;
            try (final Target nolockstreamTarget1 = streamStore.openTarget(nolockfile1)) {
                meta = nolockstreamTarget1.getMeta();
                TargetUtil.write(nolockstreamTarget1, "MyTest");
            }
            final Collection<Path> unlockedFiles = streamMaintenanceService
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
            FileUtil.setLastModified(olddir, ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_SIXTY).toInstant().toEpochMilli());

            final Path newdir = directory.resolve("newdir");
            FileUtil.mkdirs(newdir);
            FileUtil.setLastModified(newdir, ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_SIXTY).toInstant().toEpochMilli());

            final Path oldfileinnewdir = newdir.resolve("oldfileinnewdir.txt");
            Files.createFile(oldfileinnewdir);
            FileUtil.setLastModified(oldfileinnewdir, ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_FOUR).toInstant().toEpochMilli());

            // Run the clean
            fileSystemCleanTaskExecutor.clean();

            waitForTaskManagerToComplete();

            assertThat(FileSystemUtil.isAllFile(lockedFiles)).as("Locked files should still exist").isTrue();
            assertThat(FileSystemUtil.isAllFile(unlockedFiles)).as("Unlocked files should still exist").isTrue();

            assertThat(Files.isRegularFile(oldfile)).as("expected deleted " + oldfile).isFalse();
            assertThat(Files.isDirectory(olddir)).as("deleted deleted " + olddir).isFalse();
            assertThat(Files.isDirectory(newdir)).as("not deleted new dir").isTrue();
            assertThat(Files.isRegularFile(oldfileinnewdir)).as("deleted old file in new dir").isFalse();
        }
    }

    @Test
    void testArchiveRemovedFile() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Meta meta = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);

        Collection<Path> files = streamMaintenanceService.findAllStreamFile(meta);

        for (final Path file : files) {
            assertThat(FileUtil.delete(file)).isTrue();
        }

        final FindDataVolumeCriteria streamVolumeCriteria = new FindDataVolumeCriteria();
        streamVolumeCriteria.obtainMetaIdSet().add(meta.getId());

        assertThat(dataVolumeService.find(streamVolumeCriteria).size() >= 1).as("Must be saved to at least one volume").isTrue();

        fileSystemCleanTaskExecutor.clean();

        files = streamMaintenanceService.findAllStreamFile(meta);

        assertThat(files.size()).as("Files have been deleted above").isEqualTo(0);

        assertThat(dataVolumeService.find(streamVolumeCriteria).size() >= 1).as("Volumes should still exist as they are new").isTrue();

        fileSystemCleanTaskExecutor.clean();

        waitForTaskManagerToComplete();
    }

    @Test
    void testCheckCleaningLotsOfFiles() throws IOException {
        fileSystemCleanTaskExecutor.clean();

        waitForTaskManagerToComplete();

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final long endTime = System.currentTimeMillis();
        final long twoDaysTime = 1000 * 60 * 60 * 24 * 2;
        final long tenMin = 1000 * 60 * 10;
        final long startTime = endTime - twoDaysTime;
        for (long time = startTime; time < endTime; time += tenMin) {
            final MetaProperties metaProperties = new MetaProperties.Builder()
                    .feedName(feedName)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .createMs(time)
                    .build();
            try (final Target target = streamStore.openTarget(metaProperties)) {
                TargetUtil.write(target, "TEST");
            }
        }

        fileSystemCleanTaskExecutor.clean();

        waitForTaskManagerToComplete();

    }

    private void waitForTaskManagerToComplete() {
        while (executorProvider.getCurrentTaskCount() > 0) {
            Thread.yield();
        }
        LOGGER.info("waitForTaskManagerToComplete() - done");
    }
}
