/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamtask.server;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.feed.shared.Feed;
import stroom.jobsystem.server.MockTask;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeService;
import stroom.streamstore.server.FindStreamVolumeCriteria;
import stroom.streamstore.server.StreamMaintenanceService;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.server.TaskManager;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import javax.annotation.Resource;
import java.io.File;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

public class TestFileSystemCleanTask extends AbstractCoreIntegrationTest {
    private static final int NEG_SIXTY = -60;
    private static final int NEG_FOUR = -4;

    private static Logger LOGGER = LoggerFactory.getLogger(TestFileSystemCleanTask.class);

    @Resource
    private StreamStore streamStore;
    @Resource
    private StreamMaintenanceService streamMaintenanceService;
    @Resource
    private FileSystemCleanExecutor fileSystemCleanTaskExecutor;
    @Resource
    private TaskManager taskManager;
    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private NodeService nodeService;

    @Test
    public void testCheckCleaning() throws Exception {
        final List<Node> nodeList = nodeService.find(new FindNodeCriteria());
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();

        final ZonedDateTime oldDate = ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_SIXTY);

        // Write a file 2 files ... on we leave locked and the other not locked
        final Feed feed = commonTestScenarioCreator.createSimpleFeed();
        final Stream lockfile1 = Stream.createStream(StreamType.RAW_EVENTS, feed, null);
        final Stream nolockfile1 = Stream.createStream(StreamType.RAW_EVENTS, feed, null);

        //
        // FILE1 LOCKED
        //
        // Write some data
        final StreamTarget lockstreamTarget1 = streamStore.openStreamTarget(lockfile1);
        lockstreamTarget1.getOutputStream().write("MyTest".getBytes(StreamUtil.DEFAULT_CHARSET));
        // Close the file but not the stream (you should use the closeStream
        // API)
        lockstreamTarget1.close();
        final Collection<File> lockedFiles = streamMaintenanceService.findAllStreamFile(lockstreamTarget1.getStream());
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

        final Collection<File> unlockedFiles = streamMaintenanceService
                .findAllStreamFile(nolockstreamTarget1.getStream());
        final File directory = unlockedFiles.iterator().next().getParentFile();
        // Create some other files on the file system

        // Copy something that is quite old into the same directory.
        final File oldfile = new File(directory, "oldfile.txt");
        FileUtil.createNewFile(oldfile);
        FileUtil.setLastModified(oldfile, oldDate.toInstant().toEpochMilli());

        // Create a old sub directory;
        final File olddir = new File(directory, "olddir");
        FileUtil.mkdirs(olddir);
        FileUtil.setLastModified(olddir, ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_SIXTY).toInstant().toEpochMilli());

        final File newdir = new File(directory, "newdir");
        FileUtil.mkdirs(newdir);
        FileUtil.setLastModified(newdir, ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_SIXTY).toInstant().toEpochMilli());

        final File oldfileinnewdir = new File(newdir, "oldfileinnewdir.txt");
        FileUtil.createNewFile(oldfileinnewdir);
        FileUtil.setLastModified(oldfileinnewdir, ZonedDateTime.now(ZoneOffset.UTC).plusDays(NEG_FOUR).toInstant().toEpochMilli());

        // Run the clean
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();

        Assert.assertTrue("Locked files should still exist", FileSystemUtil.isAllFile(lockedFiles));
        Assert.assertTrue("Unlocked files should still exist", FileSystemUtil.isAllFile(unlockedFiles));

        Assert.assertFalse("expected deleted " + oldfile, oldfile.isFile());
        Assert.assertFalse("deleted deleted " + olddir, olddir.isDirectory());
        Assert.assertTrue("not deleted new dir", newdir.isDirectory());
        Assert.assertFalse("deleted old file in new dir", oldfileinnewdir.isFile());

    }

    @Test
    public void testArchiveRemovedFile() {
        final Feed feed = commonTestScenarioCreator.createSimpleFeed();

        final Stream data = commonTestScenarioCreator.createSample2LineRawFile(feed, StreamType.RAW_EVENTS);

        Collection<File> files = streamMaintenanceService.findAllStreamFile(data);

        for (final File file : files) {
            Assert.assertTrue(file.delete());
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
    public void testCheckCleaningLotsOfFiles() throws Exception {
        final List<Node> nodeList = nodeService.find(new FindNodeCriteria());
        for (final Node node : nodeList) {
            fileSystemCleanTaskExecutor.clean(new MockTask("Test"), node.getId());
        }

        waitForTaskManagerToComplete();

        final Feed feed = commonTestScenarioCreator.createSimpleFeed();
        final long endTime = System.currentTimeMillis();
        final long twoDaysTime = 1000 * 60 * 60 * 24 * 2;
        final long tenMin = 1000 * 60 * 10;
        final long startTime = endTime - twoDaysTime;
        for (long time = startTime; time < endTime; time += tenMin) {
            final Stream stream = Stream.createStreamForTesting(StreamType.RAW_EVENTS, feed, null, time);
            final StreamTarget t = streamStore.openStreamTarget(stream);
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
