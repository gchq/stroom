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

package stroom.streamtask;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.jobsystem.MockTask;
import stroom.node.NodeService;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.streamstore.FindStreamVolumeCriteria;
import stroom.streamstore.api.StreamProperties;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.fs.FileSystemCleanExecutor;
import stroom.streamstore.fs.FileSystemStreamMaintenanceService;
import stroom.streamstore.fs.FileSystemUtil;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamTypeEntity;
import stroom.task.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

public class TestFileSystemCleanTask extends AbstractCoreIntegrationTest {
    private static final int NEG_SIXTY = -60;
    private static final int NEG_FOUR = -4;

    private static Logger LOGGER = LoggerFactory.getLogger(TestFileSystemCleanTask.class);

    @Inject
    private StreamStore streamStore;
    @Inject
    private FileSystemStreamMaintenanceService streamMaintenanceService;
    @Inject
    private FileSystemCleanExecutor fileSystemCleanTaskExecutor;
    @Inject
    private TaskManager taskManager;
    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private NodeService nodeService;

    @Test
    public void testCheckCleaning() throws IOException {
        final List<Node> nodeList = nodeService.find(new FindNodeCriteria());
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();

        final ZonedDateTime oldDate = ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_SIXTY);

        // Write a file 2 files ... on we leave locked and the other not locked
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final StreamProperties lockfile1 = new StreamProperties.Builder()
                .feedName(feedName)
                .streamTypeName(StreamTypeEntity.RAW_EVENTS.getName())
                .build();
        final StreamProperties nolockfile1 = new StreamProperties.Builder()
                .feedName(feedName)
                .streamTypeName(StreamTypeEntity.RAW_EVENTS.getName())
                .build();
        //
        // FILE1 LOCKED
        //
        // Write some data
        final StreamTarget lockstreamTarget1 = streamStore.openStreamTarget(lockfile1);
        lockstreamTarget1.getOutputStream().write("MyTest".getBytes(StreamUtil.DEFAULT_CHARSET));
        // Close the file but not the stream (you should use the closeStream
        // API)
        lockstreamTarget1.close();
        final Collection<Path> lockedFiles = streamMaintenanceService.findAllStreamFile(lockstreamTarget1.getStream());
        FileSystemUtil.updateLastModified(lockedFiles, oldDate.toInstant().toEpochMilli());
        streamMaintenanceService.find(FindStreamVolumeCriteria.create(lockstreamTarget1.getStream()));
        // // Hack making the last access time quite old
        // for (StreamVolume volume : volumeList) {
        // volume.setLastAccessMs(oldDate.toDate().getTime());
        // streamMaintenanceService.save(volume);
        // }

        //
        // FILE2 UNLOCKED
        //
        final StreamTarget nolockstreamTarget1 = streamStore.openStreamTarget(nolockfile1);
        nolockstreamTarget1.getOutputStream().write("MyTest".getBytes(StreamUtil.DEFAULT_CHARSET));
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

        Assert.assertTrue("Locked files should still exist", FileSystemUtil.isAllFile(lockedFiles));
        Assert.assertTrue("Unlocked files should still exist", FileSystemUtil.isAllFile(unlockedFiles));

        Assert.assertFalse("expected deleted " + oldfile, Files.isRegularFile(oldfile));
        Assert.assertFalse("deleted deleted " + olddir, Files.isDirectory(olddir));
        Assert.assertTrue("not deleted new dir", Files.isDirectory(newdir));
        Assert.assertFalse("deleted old file in new dir", Files.isRegularFile(oldfileinnewdir));

    }

    @Test
    public void testArchiveRemovedFile() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();

        final StreamEntity data = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeEntity.RAW_EVENTS.getName());

        Collection<Path> files = streamMaintenanceService.findAllStreamFile(data);

        for (final Path file : files) {
            Assert.assertTrue(FileUtil.delete(file));
        }

        final FindStreamVolumeCriteria streamVolumeCriteria = new FindStreamVolumeCriteria();
        streamVolumeCriteria.obtainStreamIdSet().add(data);

        Assert.assertTrue("Must be saved to at least one volume",
                streamMaintenanceService.find(streamVolumeCriteria).size() >= 1);

        final List<Node> nodeList = nodeService.find(new FindNodeCriteria());
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        files = streamMaintenanceService.findAllStreamFile(data);

        Assert.assertEquals("Files have been deleted above", 0, files.size());

        Assert.assertTrue("Volumes should still exist as they are new",
                streamMaintenanceService.find(streamVolumeCriteria).size() >= 1);

        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();
    }

    @Test
    public void testCheckCleaningLotsOfFiles() throws IOException {
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
            final StreamProperties streamProperties = new StreamProperties.Builder()
                    .feedName(feedName)
                    .streamTypeName(StreamTypeEntity.RAW_EVENTS.getName())
                    .createMs(time)
                    .build();
            final StreamTarget t = streamStore.openStreamTarget(streamProperties);
            t.getOutputStream().write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));
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
