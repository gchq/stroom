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

import stroom.data.retention.impl.DataRetentionPolicyExecutor;
import stroom.data.retention.impl.DataRetentionRulesService;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.docref.DocRef;
import stroom.index.impl.selection.VolumeConfig;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.node.api.NodeInfo;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the archiving stuff.
 * <p>
 * Create some old files and make sure they get archived.
 */
class TestDataRetentionPolicyExecutor extends AbstractCoreIntegrationTest {

    private static final int REPLICATION_COUNT = 1;
    private static final int SIXTY = 60;
    private static final int FIFTY_FIVE = 55;
    private static final int FIFTY = 50;

    @Inject
    private VolumeConfig volumeConfig;
    @Inject
    private Store streamStore;
    @Inject
    private DataVolumeService dataVolumeService;
    @Inject
    private FsOrphanFileFinderExecutor fileSystemCleanTaskExecutor;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private DataRetentionRulesService dataRetentionRulesService;
    @Inject
    private DataRetentionPolicyExecutor dataRetentionPolicyExecutor;
    @Inject
    private PhysicalDeleteExecutor physicalDeleteExecutor;
    @Inject
    private DataStoreServiceConfig dataStoreServiceConfig;

    @AfterEach
    void unsetProperties() {
        clearConfigValueMapper();
    }

    @Test
    void testCheckArchive() throws IOException {
        setConfigValueMapper(DataStoreServiceConfig.class, config -> config
                .withDeletePurgeAge(StroomDuration.ZERO)
                .withFileSystemCleanOldAge(StroomDuration.ZERO));

        fileSystemCleanTaskExecutor.scan();

        final ZonedDateTime oldDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(SIXTY);
        final ZonedDateTime newDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(FIFTY);

        // Write a file 2 files ... on we leave locked and the other not locked
        final String feedName = FileSystemTestUtil.getUniqueTestString();
//        final DocRef feedRef = feedStore.createDocument(feedName);

        setupDataRetentionRules(feedName);

        final MetaProperties oldFile = MetaProperties.builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .createMs(oldDate.toInstant().toEpochMilli())
                .build();
        final MetaProperties newFile = MetaProperties.builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .createMs(newDate.toInstant().toEpochMilli())
                .build();

        final Meta oldFileMeta;
        try (final Target oldFileTarget = streamStore.openTarget(oldFile)) {
            oldFileMeta = oldFileTarget.getMeta();
            TargetUtil.write(oldFileTarget, "MyTest");
        }

        final Meta newFileMeta;
        try (final Target newFileTarget = streamStore.openTarget(newFile)) {
            newFileMeta = newFileTarget.getMeta();
            TargetUtil.write(newFileTarget, "MyTest");
        }

        List<DataVolume> oldVolumeList = dataVolumeService
                .find(FindDataVolumeCriteria.create(oldFileMeta)).getValues();
        assertThat(oldVolumeList.size()).as("Expecting 1 stream volumes").isEqualTo(REPLICATION_COUNT);

        List<DataVolume> newVolumeList = dataVolumeService
                .find(FindDataVolumeCriteria.create(newFileMeta)).getValues();
        assertThat(newVolumeList.size()).as("Expecting 1 stream volumes").isEqualTo(REPLICATION_COUNT);

        dataRetentionPolicyExecutor.exec();
        physicalDeleteExecutor.exec();

        // Test Again
        oldVolumeList = dataVolumeService.find(FindDataVolumeCriteria.create(oldFileMeta)).getValues();
        assertThat(oldVolumeList.size()).as("Expecting 0 stream volumes").isEqualTo(0);

        newVolumeList = dataVolumeService.find(FindDataVolumeCriteria.create(newFileMeta)).getValues();
        assertThat(newVolumeList.size()).as("Expecting 1 stream volumes").isEqualTo(REPLICATION_COUNT);

        // Test they are
        oldVolumeList = dataVolumeService.find(FindDataVolumeCriteria.create(oldFileMeta)).getValues();
        assertThat(oldVolumeList.size()).as("Expecting 0 stream volumes").isEqualTo(0);
        newVolumeList = dataVolumeService.find(FindDataVolumeCriteria.create(newFileMeta)).getValues();
        assertThat(newVolumeList.size()).as("Expecting 1 stream volumes").isEqualTo(REPLICATION_COUNT);
    }

    private void setupDataRetentionRules(final String feedName) {
        final DocRef docRef = dataRetentionRulesService.createDocument("test");
        final DataRetentionRules dataRetentionRules = dataRetentionRulesService.readDocument(docRef);

        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        builder.addTextTerm(MetaFields.FEED, Condition.EQUALS, feedName);
        final DataRetentionRule rule = createRule(1, builder.build(), FIFTY_FIVE, TimeUnit.DAYS);
        dataRetentionRules.setRules(Collections.singletonList(rule));
        dataRetentionRulesService.writeDocument(dataRetentionRules);
    }

    private DataRetentionRule createRule(final int num,
                                         final ExpressionOperator expression,
                                         final int age,
                                         final TimeUnit timeUnit) {
        return new DataRetentionRule(num,
                System.currentTimeMillis(),
                "rule " + num,
                true,
                expression,
                age,
                timeUnit,
                false);
    }
}
