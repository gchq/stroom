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


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import stroom.data.retention.impl.DataRetentionExecutor;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.data.store.impl.fs.DataVolumeService.DataVolume;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.job.MockTask;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaProperties;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.test.FileSystemTestUtil;
import stroom.volume.VolumeConfig;

import javax.inject.Inject;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the archiving stuff.
 * <p>
 * Create some old files and make sure they get archived.
 */
// TODO : @66 Decide what needs to be done with deletion of old data rows

@Disabled
class TestStreamArchiveTask extends AbstractCoreIntegrationTest {
    private static final int HIGHER_REPLICATION_COUNT = 2;
    private static final int SIXTY = 60;
    private static final int FIFTY_FIVE = 55;
    private static final int FIFTY = 50;

    @Inject
    private VolumeConfig volumeConfig;
    @Inject
    private Store streamStore;
    @Inject
    private DataVolumeService streamVolumeService;
    @Inject
    private FeedStore feedStore;
    @Inject
    private FileSystemCleanExecutor fileSystemCleanTaskExecutor;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private NodeService nodeService;
    //    @Inject
//    private StreamTaskCreatorImpl streamTaskCreator;
    @Inject
    private DataRetentionExecutor streamRetentionExecutor;
    @Inject
    private StreamDeleteExecutor streamDeleteExecutor;

    private int initialReplicationCount = 1;

    @Override
    protected void onBefore() {
        initialReplicationCount = volumeConfig.getResilientReplicationCount();
        volumeConfig.setResilientReplicationCount(HIGHER_REPLICATION_COUNT);
    }

    @Override
    protected void onAfter() {
        volumeConfig.setResilientReplicationCount(initialReplicationCount);
    }

    @Test
    void testCheckArchive() throws IOException {
        fileSystemCleanTaskExecutor.clean(new MockTask("Test"));

        final ZonedDateTime oldDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(SIXTY);
        final ZonedDateTime newDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(FIFTY);

        // Write a file 2 files ... on we leave locked and the other not locked
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final DocRef feedRef = feedStore.createDocument(feedName);
        final FeedDoc feedDoc = feedStore.readDocument(feedRef);
        feedDoc.setRetentionDayAge(FIFTY_FIVE);
        feedStore.writeDocument(feedDoc);

        final MetaProperties oldFile = new MetaProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .createMs(oldDate.toInstant().toEpochMilli())
                .build();
        final MetaProperties newFile = new MetaProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .createMs(newDate.toInstant().toEpochMilli())
                .build();

        Meta oldFileMeta;
        try (final Target oldFileTarget = streamStore.openStreamTarget(oldFile)) {
            oldFileMeta = oldFileTarget.getMeta();
            TargetUtil.write(oldFileTarget, "MyTest");
        }

        Meta newFileMeta;
        try (final Target newFileTarget = streamStore.openStreamTarget(newFile)) {
            newFileMeta = newFileTarget.getMeta();
            TargetUtil.write(newFileTarget, "MyTest");
        }

//        // Now we have added some data create some associated stream tasks.
//        // TODO : At some point we need to change deletion to delete streams and
//        // not tasks. Tasks should be deleted if an associated source stream is
//        // deleted if they exist, however currently streams are only deleted if
//        // their associated task exists which would prevent us from deleting
//        // streams that have no task associated with them.
//        streamTaskCreator.createTasks(new SimpleTaskContext());

        List<DataVolume> oldVolumeList = streamVolumeService
                .find(FindDataVolumeCriteria.create(oldFileMeta));
        assertThat(oldVolumeList.size()).as("Expecting 2 stream volumes").isEqualTo(HIGHER_REPLICATION_COUNT);

        List<DataVolume> newVolumeList = streamVolumeService
                .find(FindDataVolumeCriteria.create(newFileMeta));
        assertThat(newVolumeList.size()).as("Expecting 2 stream volumes").isEqualTo(HIGHER_REPLICATION_COUNT);

        streamRetentionExecutor.exec();
//        streamDeleteExecutor.delete(System.currentTimeMillis());

        // Test Again
        oldVolumeList = streamVolumeService.find(FindDataVolumeCriteria.create(oldFileMeta));
        assertThat(oldVolumeList.size()).as("Expecting 0 stream volumes").isEqualTo(0);

        newVolumeList = streamVolumeService.find(FindDataVolumeCriteria.create(newFileMeta));
        assertThat(newVolumeList.size()).as("Expecting 2 stream volumes").isEqualTo(HIGHER_REPLICATION_COUNT);

        // Test they are
        oldVolumeList = streamVolumeService.find(FindDataVolumeCriteria.create(oldFileMeta));
        assertThat(oldVolumeList.size()).as("Expecting 0 stream volumes").isEqualTo(0);
        newVolumeList = streamVolumeService.find(FindDataVolumeCriteria.create(newFileMeta));
        assertThat(newVolumeList.size()).as("Expecting 2 stream volumes").isEqualTo(HIGHER_REPLICATION_COUNT);
    }
}
