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
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataProperties;
import stroom.data.store.api.StreamStore;
import stroom.data.store.api.StreamTarget;
import stroom.data.store.api.StreamTargetUtil;
import stroom.jobsystem.MockTask;
import stroom.node.NodeService;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.task.api.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.FileUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileSystemCleanTask extends AbstractCoreIntegrationTest {
    private static final int NEG_SIXTY = -60;
    private static final int NEG_FOUR = -4;

    private static Logger LOGGER = LoggerFactory.getLogger(TestFileSystemCleanTask.class);

    @Inject
    private StreamStore streamStore;
    @Inject
    private FileSystemStreamMaintenanceService streamMaintenanceService;
    @Inject
    private DataVolumeService streamVolumeService;
    @Inject
    private FileSystemCleanExecutor fileSystemCleanTaskExecutor;
    @Inject
    private TaskManager taskManager;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private NodeService nodeService;

    @Test
    void testCheckCleaning() throws IOException {
        final List<Node> nodeList = nodeService.find(new FindNodeCriteria());
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();

        final ZonedDateTime oldDate = ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_SIXTY);

        // Write a file 2 files ... on we leave locked and the other not locked
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final DataProperties lockfile1 = new DataProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        final DataProperties nolockfile1 = new DataProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        //
        // FILE1 LOCKED
        //
        // Write some data
        final StreamTarget lockstreamTarget1 = streamStore.openStreamTarget(lockfile1);
        StreamTargetUtil.write(lockstreamTarget1, "MyTest");
        // Close the file but not the stream (you should use the closeStream
        // API)
        lockstreamTarget1.close();
        final Collection<Path> lockedFiles = streamMaintenanceService.findAllStreamFile(lockstreamTarget1.getStream());
        FileSystemUtil.updateLastModified(lockedFiles, oldDate.toInstant().toEpochMilli());
        streamVolumeService.find(FindDataVolumeCriteria.create(lockstreamTarget1.getStream()));
        // // Hack making the last access time quite old
        // for (StreamVolume volume : volumeList) {
        // volume.setLastAccessMs(oldDate.toDate().getTime());
        // streamMaintenanceService.save(volume);
        // }

        //
        // FILE2 UNLOCKED
        //
        final StreamTarget nolockstreamTarget1 = streamStore.openStreamTarget(nolockfile1);
        StreamTargetUtil.write(nolockstreamTarget1, "MyTest");
        // Close the file but not the stream (you should use the closeStream
        // API)
        streamStore.closeStreamTarget(nolockstreamTarget1);

        final Collection<Path> unlockedFiles = streamMaintenanceService
                .findAllStreamFile(nolockstreamTarget1.getStream());
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
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();

        assertThat(FileSystemUtil.isAllFile(lockedFiles)).as("Locked files should still exist").isTrue();
        assertThat(FileSystemUtil.isAllFile(unlockedFiles)).as("Unlocked files should still exist").isTrue();

        assertThat(Files.isRegularFile(oldfile)).as("expected deleted " + oldfile).isFalse();
        assertThat(Files.isDirectory(olddir)).as("deleted deleted " + olddir).isFalse();
        assertThat(Files.isDirectory(newdir)).as("not deleted new dir").isTrue();
        assertThat(Files.isRegularFile(oldfileinnewdir)).as("deleted old file in new dir").isFalse();

    }

    @Test
    void testArchiveRemovedFile() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final Data stream = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);

        Collection<Path> files = streamMaintenanceService.findAllStreamFile(stream);

        for (final Path file : files) {
            assertThat(FileUtil.delete(file)).isTrue();
        }

        final FindDataVolumeCriteria streamVolumeCriteria = new FindDataVolumeCriteria();
        streamVolumeCriteria.obtainStreamIdSet().add(stream.getId());

        assertThat(streamVolumeService.find(streamVolumeCriteria).size() >= 1).as("Must be saved to at least one volume").isTrue();

        final List<Node> nodeList = nodeService.find(new FindNodeCriteria());
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        files = streamMaintenanceService.findAllStreamFile(stream);

        assertThat(files.size()).as("Files have been deleted above").isEqualTo(0);

        assertThat(streamVolumeService.find(streamVolumeCriteria).size() >= 1).as("Volumes should still exist as they are new").isTrue();

        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();
    }

    @Test
    void testCheckCleaningLotsOfFiles() throws IOException {
        final List<Node> nodeList = nodeService.find(new FindNodeCriteria());
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();

        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final long endTime = System.currentTimeMillis();
        final long twoDaysTime = 1000 * 60 * 60 * 24 * 2;
        final long tenMin = 1000 * 60 * 10;
        final long startTime = endTime - twoDaysTime;
        for (long time = startTime; time < endTime; time += tenMin) {
            final DataProperties streamProperties = new DataProperties.Builder()
                    .feedName(feedName)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .createMs(time)
                    .build();
            final StreamTarget t = streamStore.openStreamTarget(streamProperties);
            StreamTargetUtil.write(t, "TEST");
            streamStore.closeStreamTarget(t);
        }

        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();

    }

    private void waitForTaskManagerToComplete() {
        while (taskManager.getCurrentTaskCount() > 0) {
            Thread.yield();
        }
        LOGGER.info("waitForTaskManagerToComplete() - done");
    }
}
