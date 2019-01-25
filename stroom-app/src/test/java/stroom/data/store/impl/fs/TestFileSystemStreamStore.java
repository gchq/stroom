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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stroom.data.meta.shared.Data;
import stroom.data.meta.shared.DataMetaService;
import stroom.data.meta.shared.DataProperties;
import stroom.data.meta.shared.DataRow;
import stroom.data.meta.shared.DataStatus;
import stroom.data.meta.shared.EffectiveMetaDataCriteria;
import stroom.data.meta.shared.ExpressionUtil;
import stroom.data.meta.shared.FindDataCriteria;
import stroom.data.meta.shared.MetaDataSource;
import stroom.data.meta.impl.db.MetaValueConfig;
import stroom.data.store.StreamMaintenanceService;
import stroom.data.store.api.StreamSource;
import stroom.data.store.api.StreamStore;
import stroom.data.store.api.StreamTarget;
import stroom.data.store.api.StreamTargetUtil;
import stroom.data.store.impl.fs.DataVolumeService.DataVolume;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Period;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.volume.VolumeConfig;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class TestFileSystemStreamStore extends AbstractCoreIntegrationTest {
    private static final int N1 = 1;
    private static final int N13 = 13;

    private static final String FEED1 = "FEED1";
    private static final String FEED2 = "FEED2";

    @Inject
    private MetaValueConfig metaValueConfig;
    @Inject
    private VolumeConfig volumeConfig;
    @Inject
    private StreamStore streamStore;
    @Inject
    private DataMetaService streamMetaService;
    @Inject
    private DataVolumeService streamVolumeService;
    @Inject
    private StreamMaintenanceService streamMaintenanceService;
    @Inject
    private FileSystemStreamPathHelper fileSystemStreamPathHelper;

    @BeforeEach
    void setProperties() {
        // Make sure stream attributes get flushed straight away.
        metaValueConfig.setAddAsync(false);
        volumeConfig.setResilientReplicationCount(2);
    }

    @AfterEach
    void unsetProperties() {
        metaValueConfig.setAddAsync(true);
        volumeConfig.setResilientReplicationCount(1);
    }

    @Test
    void testBasic() throws IOException {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.CREATE_TIME, Condition.BETWEEN, createYearPeriod(2014))
                .addTerm(MetaDataSource.EFFECTIVE_TIME, Condition.BETWEEN, createYearPeriod(2014))
                .addTerm(MetaDataSource.FEED_NAME, Condition.EQUALS, FEED1)
                .addTerm(MetaDataSource.PARENT_STREAM_ID, Condition.EQUALS, "1")
                .addTerm(MetaDataSource.STREAM_ID, Condition.EQUALS, "1")
                .addTerm(MetaDataSource.STREAM_TYPE_NAME, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();
        testCriteria(new FindDataCriteria(expression), 0);
    }

    private String createYearPeriod(final int year) {
        return year + "-01-01T00:00:00.000Z," + (year + 1) + "-01-01T00:00:00.000Z";
    }

    private String createToDateWithOffset(long time, int offset) {
        final long from = time;
        final long to = time + (offset * 1000 * 60 * 60 * 24);
        return DateUtil.createNormalDateTimeString(from) + "," + DateUtil.createNormalDateTimeString(to);
    }

    @Test
    void testFeedFindAll() throws IOException {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addOperator(new ExpressionOperator.Builder(Op.OR)
                        .addTerm(MetaDataSource.FEED_NAME, Condition.EQUALS, FEED1)
                        .addTerm(MetaDataSource.FEED_NAME, Condition.EQUALS, FEED2)
                        .build())
                .addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();
        testCriteria(new FindDataCriteria(expression), 2);
    }

    @Test
    void testFeedFindSome() throws IOException {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addOperator(new ExpressionOperator.Builder(Op.OR)
                        .addTerm(MetaDataSource.FEED_NAME, Condition.EQUALS, FEED1)
                        .addTerm(MetaDataSource.FEED_NAME, Condition.EQUALS, FEED2)
                        .build())
                .addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();
        final FindDataCriteria findStreamCriteria = new FindDataCriteria(expression);
        findStreamCriteria.setPageRequest(new PageRequest(0L, 1));
        testCriteria(findStreamCriteria, 1);
    }

    @Test
    void testFeedFindNone() throws IOException {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.FEED_NAME, Condition.EQUALS, FEED1)
                .addOperator(new ExpressionOperator.Builder(Op.NOT)
                        .addTerm(MetaDataSource.FEED_NAME, Condition.EQUALS, FEED1)
                        .build())
                .addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();
        testCriteria(new FindDataCriteria(expression), 0);
    }

    @Test
    void testFeedFindOne() throws IOException {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.FEED_NAME, Condition.EQUALS, FEED2)
                .addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();
        testCriteria(new FindDataCriteria(expression), 1);
    }

    private void testCriteria(final FindDataCriteria criteria, final int expectedStreams) throws IOException {
        streamMetaService.updateStatus(new FindDataCriteria(), DataStatus.DELETED);

        createStream(FEED1, 1L, null);
        createStream(FEED2, 1L, null);
//        criteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        final BaseResultList<Data> streams = streamMetaService.find(criteria);
        assertThat(streams.size()).isEqualTo(expectedStreams);

        streamMetaService.updateStatus(new FindDataCriteria(), DataStatus.DELETED);
    }

    @Test
    void testParentChild() {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();
        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        StreamTargetUtil.write(streamTarget, testString);
        streamStore.closeStreamTarget(streamTarget);

        final DataProperties childProperties = new DataProperties.Builder()
                .feedName(streamTarget.getStream().getFeedName())
                .typeName(StreamTypeNames.RAW_EVENTS)
                .parent(streamTarget.getStream())
                .build();
        final StreamTarget childTarget = streamStore.openStreamTarget(childProperties);
        StreamTargetUtil.write(childTarget, testString);
        streamStore.closeStreamTarget(childTarget);

        final DataProperties grandChildProperties = new DataProperties.Builder()
                .feedName(childTarget.getStream().getFeedName())
                .typeName(StreamTypeNames.RAW_EVENTS)
                .parent(childTarget.getStream())
                .build();
        final StreamTarget grandChildTarget = streamStore.openStreamTarget(grandChildProperties);
        StreamTargetUtil.write(grandChildTarget, testString);
        streamStore.closeStreamTarget(grandChildTarget);

        List<DataRow> relationList = streamMetaService.findRelatedData(childTarget.getStream().getId(), true);

        assertThat(relationList.size()).isEqualTo(3);
        assertThat(relationList.get(0).getData()).isEqualTo(streamTarget.getStream());
        assertThat(relationList.get(1).getData()).isEqualTo(childTarget.getStream());
        assertThat(relationList.get(2).getData()).isEqualTo(grandChildTarget.getStream());

        relationList = streamMetaService.findRelatedData(grandChildTarget.getStream().getId(), true);

        assertThat(relationList.size()).isEqualTo(3);
        assertThat(relationList.get(0).getData()).isEqualTo(streamTarget.getStream());
        assertThat(relationList.get(1).getData()).isEqualTo(childTarget.getStream());
        assertThat(relationList.get(2).getData()).isEqualTo(grandChildTarget.getStream());
    }

    @Test
    void testFindDeleteAndUndelete() {
        final Data stream = createStream(FEED1, 1L, null);

        FindDataCriteria findStreamCriteria = new FindDataCriteria();
        findStreamCriteria.obtainSelectedIdSet().add(stream.getId());
        final long deleted = streamMetaService.updateStatus(findStreamCriteria, DataStatus.DELETED);

        assertThat(deleted).isEqualTo(1L);

        findStreamCriteria = new FindDataCriteria();
        findStreamCriteria.obtainSelectedIdSet().add(stream.getId());
        assertThat(streamMetaService.find(findStreamCriteria).size()).isEqualTo(1L);

        findStreamCriteria.setExpression(ExpressionUtil.createStatusExpression(DataStatus.UNLOCKED));
        assertThat(streamMetaService.find(findStreamCriteria).size()).isEqualTo(0L);

        findStreamCriteria.setExpression(ExpressionUtil.createStatusExpression(DataStatus.DELETED));
        assertThat(streamMetaService.find(findStreamCriteria).size()).isEqualTo(1L);

        findStreamCriteria.setExpression(ExpressionUtil.createStatusExpression(DataStatus.UNLOCKED));
        assertThat(streamMetaService.find(findStreamCriteria).size()).isEqualTo(0L);

        // This will undelete
        findStreamCriteria.setExpression(ExpressionUtil.createStatusExpression(DataStatus.DELETED));
        assertThat(streamMetaService.updateStatus(findStreamCriteria, DataStatus.UNLOCKED)).isEqualTo(1L);

        findStreamCriteria.setExpression(ExpressionUtil.createStatusExpression(DataStatus.UNLOCKED));
        assertThat(streamMetaService.find(findStreamCriteria).size()).isEqualTo(1L);
    }

    private Data createStream(final String feedName, final Long streamTaskId, final Long parentStreamId) {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .processorTaskId(streamTaskId)
                .parentId(parentStreamId)
                .build();

        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        StreamTargetUtil.write(streamTarget, testString);
        streamStore.closeStreamTarget(streamTarget);
        return streamTarget.getStream();
    }

    @Test
    void testFindWithAllCriteria() {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.CREATE_TIME, Condition.BETWEEN, createToDateWithOffset(System.currentTimeMillis(), 1))
                .addTerm(MetaDataSource.EFFECTIVE_TIME, Condition.BETWEEN, createToDateWithOffset(System.currentTimeMillis(), 1))
                .addTerm(MetaDataSource.STATUS_TIME, Condition.BETWEEN, createToDateWithOffset(System.currentTimeMillis(), 1))
                .addTerm(MetaDataSource.FEED_NAME, Condition.EQUALS, FEED1)
                .addTerm(MetaDataSource.PARENT_STREAM_ID, Condition.EQUALS, "1")
                .addTerm(MetaDataSource.STREAM_ID, Condition.EQUALS, "1")
//                .addTerm(StreamDataSource.PIPELINE, Condition.EQUALS, "1")
//                .addTerm(StreamDataSource.STREAM_PROCESSOR_ID, Condition.EQUALS, "1")
                .addTerm(MetaDataSource.STREAM_TYPE_NAME, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();
        final FindDataCriteria findStreamCriteria = new FindDataCriteria(expression);
        findStreamCriteria.setPageRequest(new PageRequest(0L, 100));
        findStreamCriteria.setSort(MetaDataSource.CREATE_TIME);
//        findStreamCriteria.setStreamIdRange(new IdRange(0L, 1L));

        assertThat(streamMetaService.find(findStreamCriteria).size()).isEqualTo(0L);
    }

    @Test
    void testBasicImportExportList() {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        streamTarget.getStream().getFeedName();
        Data exactMetaData = streamTarget.getStream();
        StreamTargetUtil.write(streamTarget, testString);
        streamStore.closeStreamTarget(streamTarget);

        // Refresh
        final StreamSource streamSource = streamStore.openStreamSource(exactMetaData.getId());
        exactMetaData = streamSource.getStream();

        assertThat(exactMetaData.getStatus()).isSameAs(DataStatus.UNLOCKED);

        // Check we can read it back in

        assertThat(streamSource).isNotNull();
        // Must be a proxy
        assertThat(streamSource.getStream().getFeedName()).isNotNull();

        // Finished
        streamStore.closeStreamSource(streamSource);

        final List<Data> list = streamMetaService.find(FindDataCriteria.createWithType(StreamTypeNames.RAW_EVENTS));

        boolean foundOne = false;
        for (final Data result : list) {
            assertThat(fileSystemStreamPathHelper.getDirectory(result, StreamTypeNames.RAW_EVENTS)).isNotNull();
            assertThat(fileSystemStreamPathHelper.getBaseName(result)).isNotNull();
            if (fileSystemStreamPathHelper.getBaseName(result)
                    .equals(fileSystemStreamPathHelper.getBaseName(exactMetaData))) {
                foundOne = true;
            }
        }

        assertThat(foundOne).as("Expecting to find at least that one file " + streamTarget.getStream()).isTrue();

        assertThat(streamMetaService.find(new FindDataCriteria()).size() >= 1).as("Expecting to find at least 1 with no criteria").isTrue();

        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.STATUS, Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();
        assertThat(streamMetaService.find(new FindDataCriteria(expression)).size() >= 1).as("Expecting to find at least 1 with UNLOCKED criteria").isTrue();

        final FindDataVolumeCriteria volumeCriteria = new FindDataVolumeCriteria();
//        volumeCriteria.obtainStreamStatusSet().add(StreamStatus.UNLOCKED);
        volumeCriteria.obtainStreamIdSet().add(exactMetaData.getId());
        assertThat(streamVolumeService.find(volumeCriteria).size() >= 1).as("Expecting to find at least 1 with day old criteria").isTrue();
    }

    private void doTestDeleteSource(final DeleteTestStyle style) throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        final Data stream = streamTarget.getStream();
        StreamTargetUtil.write(streamTarget, testString);
        streamStore.closeStreamTarget(streamTarget);

        // Check we can read it back in
        StreamSource streamSource = streamStore.openStreamSource(stream.getId());
        assertThat(streamSource).isNotNull();

        if (DeleteTestStyle.META.equals(style)) {
            // Finished
            streamStore.closeStreamSource(streamSource);
            // This should delete it
            streamMetaService.delete(stream.getId());
        }
        if (DeleteTestStyle.OPEN.equals(style)) {
            streamMetaService.delete(streamSource.getStream().getId());
        }
        if (DeleteTestStyle.OPEN_TOUCHED_CLOSED.equals(style)) {
            streamSource.getInputStream().read();
            streamSource.getInputStream().close();
            streamSource.close();
            streamMetaService.delete(streamSource.getStream().getId());
        }

        streamSource = streamStore.openStreamSource(stream.getId(), true);
        assertThat(streamSource.getStream().getStatus()).isEqualTo(DataStatus.DELETED);
    }

    private void doTestDeleteTarget(final DeleteTestStyle style) {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        final Data stream = streamTarget.getStream();

        if (DeleteTestStyle.META.equals(style)) {
            // Finished
            streamStore.closeStreamTarget(streamTarget);
            // This should delete it
            streamMetaService.delete(stream.getId());
        }
        if (DeleteTestStyle.OPEN.equals(style)) {
            streamStore.deleteStreamTarget(streamTarget);
        }
        if (DeleteTestStyle.OPEN_TOUCHED.equals(style)) {
            StreamTargetUtil.write(streamTarget, testString);
            streamStore.deleteStreamTarget(streamTarget);
        }
        if (DeleteTestStyle.OPEN_TOUCHED_CLOSED.equals(style)) {
            StreamTargetUtil.write(streamTarget, testString);
            streamStore.closeStreamTarget(streamTarget);
            streamStore.deleteStreamTarget(streamTarget);
        }

        final StreamSource streamSource = streamStore.openStreamSource(stream.getId(), true);
        assertThat(streamSource.getStream().getStatus()).isEqualTo(DataStatus.DELETED);
    }

    @Test
    void testDelete1() throws IOException {
        doTestDeleteSource(DeleteTestStyle.META);
    }

    @Test
    void testDelete2() throws IOException {
        doTestDeleteSource(DeleteTestStyle.OPEN);
    }

    @Test
    void testDelete4() {
        doTestDeleteTarget(DeleteTestStyle.META);
    }

    @Test
    void testDelete5() {
        doTestDeleteTarget(DeleteTestStyle.OPEN);
    }

    @Test
    void testDelete6() {
        doTestDeleteTarget(DeleteTestStyle.OPEN_TOUCHED);
    }

    @Test
    void testDelete7() throws IOException {
        doTestDeleteSource(DeleteTestStyle.OPEN_TOUCHED_CLOSED);
    }

    @Test
    void testDelete8() {
        doTestDeleteTarget(DeleteTestStyle.OPEN_TOUCHED_CLOSED);
    }

    // TODO : FIX PIPELINE FILTERING
//    @Test
//    public void testDeletePipleineFilters() throws IOException {
//        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
//                .addTerm(StreamDataSource.PIPELINE, Condition.EQUALS, "Test")
////                .addTerm(StreamDataSource., Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue())
//                .build();
//
//        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
//        findStreamCriteria.setExpression(expression);
//        streamStore.updateStatus(findStreamCriteria);
//    }

    @Test
    void testFileSystem() throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        final Data exactMetaData = streamTarget.getStream();
        StreamTargetUtil.write(streamTarget, testString);
        streamTarget.getAttributes().put(MetaDataSource.REC_READ, "10");
        streamTarget.getAttributes().put(MetaDataSource.REC_WRITE, "20");
        streamStore.closeStreamTarget(streamTarget);

        final Data reloadMetaData = streamMetaService.find(FindDataCriteria.createWithData(exactMetaData)).get(0);

        final StreamSource streamSource = streamStore.openStreamSource(reloadMetaData.getId());

        final InputStream is = streamSource.getInputStream();
        assertThat(is).isNotNull();
        is.close();
        streamStore.closeStreamSource(streamSource);

        final FindDataCriteria criteria = new FindDataCriteria();
        criteria.obtainSelectedIdSet().add(reloadMetaData.getId());

//        streamAttributeValueFlush.flush();
        final DataRow streamMD = streamMetaService.findRows(criteria).getFirst();

        assertThat(streamMD.getAttributeValue(MetaDataSource.REC_READ)).isEqualTo("10");
        assertThat(streamMD.getAttributeValue(MetaDataSource.REC_WRITE)).isEqualTo("20");
    }

    @Test
    void testWriteNothing() throws IOException {
        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        final Data exactMetaData = streamTarget.getStream();
        streamStore.closeStreamTarget(streamTarget);

        final Data reloadMetaData = streamMetaService.find(FindDataCriteria.createWithData(exactMetaData)).get(0);

        final StreamSource streamSource = streamStore.openStreamSource(reloadMetaData.getId());
        final InputStream is = streamSource.getInputStream();
        assertThat(is).isNotNull();
        is.close();
        streamStore.closeStreamSource(streamSource);
    }

    @Test
    void testEffective() throws IOException {
        final String feed1 = FileSystemTestUtil.getUniqueTestString();
        final String feed2 = FileSystemTestUtil.getUniqueTestString();
        final String feed3 = FileSystemTestUtil.getUniqueTestString();

//        setupFeed(feed1);
        final Data refData1 = buildRefData(feed2, 2008, 2, StreamTypeNames.REFERENCE, false);
        final Data refData2 = buildRefData(feed2, 2009, 2, StreamTypeNames.REFERENCE, false);
        final Data refData3 = buildRefData(feed2, 2010, 2, StreamTypeNames.REFERENCE, false);

        // These 2 should get ignored as one is locked and the other is RAW
        final HashSet<Long> invalidFeeds = new HashSet<>();
        invalidFeeds.add(buildRefData(feed2, 2010, 2, StreamTypeNames.REFERENCE, true).getId());

        invalidFeeds.add(buildRefData(feed2, 2010, 2, StreamTypeNames.RAW_REFERENCE, false).getId());

        // Build some for another feed.
        buildRefData(feed3, 2008, 2, StreamTypeNames.REFERENCE, false);
        buildRefData(feed3, 2009, 2, StreamTypeNames.REFERENCE, false);
        buildRefData(feed3, 2010, 2, StreamTypeNames.REFERENCE, false);
        buildRefData(feed3, 2011, 2, StreamTypeNames.REFERENCE, false);

        final EffectiveMetaDataCriteria criteria = new EffectiveMetaDataCriteria();
        criteria.setType(StreamTypeNames.REFERENCE);

        // feed2 or feed1
        criteria.setFeed(feed2);
//        criteria.getFeedIdSet().add(feedService.loadByName(feed1));

        // 2009 to 2010
        criteria.setEffectivePeriod(new Period(DateUtil.parseNormalDateTimeString("2009-01-01T00:00:00.000Z"),
                DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z")));

        Set<Data> set = streamMetaService.findEffectiveData(criteria);

        // Make sure the list contains what it should.
        verifySet(set, refData1, refData2);

        // Try another test that picks up no tom within period but it should get
        // the last one as it would be the most effective.
        criteria.setEffectivePeriod(new Period(DateUtil.parseNormalDateTimeString("2013-01-01T00:00:00.000Z"),
                DateUtil.parseNormalDateTimeString("2014-01-01T00:00:00.000Z")));

        set = streamMetaService.findEffectiveData(criteria);

        // Make sure the list contains what it should.
        verifySet(set, refData3);

        assertThat(invalidFeeds.contains(set.iterator().next().getId())).isFalse();
    }

    /**
     * Check that the list of stream contains the items we expect.
     *
     * @param set
     * @param expected
     */
    private void verifySet(final Set<Data> set, final Data... expected) {
        assertThat(set).isNotNull();
        assertThat(set.size()).isEqualTo(expected.length);
        for (final Data stream : expected) {
            assertThat(set.contains(stream)).isTrue();
        }
    }

    private Data buildRefData(final String feed, final int year, final int month, final String type, final boolean lock) {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(feed)
                .typeName(type)
                .effectiveMs(ZonedDateTime.of(year, month, N1, N13, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli())
                .build();

        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        StreamTargetUtil.write(streamTarget, testString);
//        streamTarget.close();
        // Leave locked ?
        if (!lock) {
            streamStore.closeStreamTarget(streamTarget);
        }
        // commonTestControl.clearContext();

        return streamTarget.getStream();
    }

    @Test
    void testDeleteStreamTarget() {
        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final String testString = FileSystemTestUtil.getUniqueTestString();

        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        StreamTargetUtil.write(streamTarget, testString);
        streamStore.closeStreamTarget(streamTarget);

//        // Create tasks.
//        streamTaskCreator.createTasks(new SimpleTaskContext());

        Data reloadedStream = streamMetaService.getData(streamTarget.getStream().getId());
        assertThat(reloadedStream).isNotNull();

        assertThatThrownBy(() -> {
            // We shouldn't be able to close a stream target again.
            streamStore.closeStreamTarget(streamTarget);
        }).isInstanceOf(RuntimeException.class);

        streamStore.deleteStreamTarget(streamTarget);
        reloadedStream = streamMetaService.find(FindDataCriteria.createWithData(streamTarget.getStream())).getFirst();
        assertThat(reloadedStream).isNull();

        streamStore.deleteStreamTarget(streamTarget);

        reloadedStream = streamMetaService.find(FindDataCriteria.createWithData(streamTarget.getStream())).getFirst();
        assertThat(reloadedStream).isNull();
    }

    @Test
    void testAppendStream() {
        final String testString1 = FileSystemTestUtil.getUniqueTestString();
        final String testString2 = FileSystemTestUtil.getUniqueTestString();
        final String testString3 = FileSystemTestUtil.getUniqueTestString();
        final String testString4 = FileSystemTestUtil.getUniqueTestString();
        final String testString5 = FileSystemTestUtil.getUniqueTestString();
        final String testString6 = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);

        StreamTargetUtil.write(streamTarget, "xyz");
        streamTarget.getAttributes().put(testString1, testString2);
        streamTarget.getAttributes().put(MetaDataSource.REC_READ, "100");
        streamStore.closeStreamTarget(streamTarget);

        Data stream = streamTarget.getStream();

        final Set<DataVolume> streamVolumes = streamVolumeService.findStreamVolume(stream.getId());
        final Set<Path> rootFile = new HashSet<>();

        for (final DataVolume streamVolume : streamVolumes) {
            rootFile.add(fileSystemStreamPathHelper.createRootStreamFile(streamVolume.getVolumePath(), stream,
                    StreamTypeNames.RAW_EVENTS));
        }
        assertThat(FileSystemUtil.isAllFile(rootFile)).isTrue();

        StreamSource streamSource = streamStore.openStreamSource(stream.getId());
        stream = streamSource.getStream();
        assertThat(streamSource.getAttributes().get(testString1)).isEqualTo(testString2);
        streamStore.closeStreamSource(streamSource);

        final Set<Path> manifestFile = fileSystemStreamPathHelper.createChildStreamPath(rootFile, InternalStreamTypeNames.MANIFEST);

        assertThat(FileSystemUtil.isAllFile(manifestFile)).isTrue();

        streamTarget = streamStore.openExistingStreamTarget(stream);
        streamTarget.getAttributes().put(testString3, testString4);
        streamStore.closeStreamTarget(streamTarget);
        stream = streamTarget.getStream();

        streamSource = streamStore.openStreamSource(stream.getId());
        stream = streamSource.getStream();
        assertThat(streamSource.getAttributes().get(testString1)).isEqualTo(testString2);
        assertThat(streamSource.getAttributes().get(testString3)).isEqualTo(testString4);
        streamStore.closeStreamSource(streamSource);

        assertThat(FileSystemUtil.deleteAnyPath(manifestFile)).isTrue();
//        for (final StreamAttributeValue value : streamAttributeValueService
//                .find(FindStreamAttributeValueCriteria.create(stream))) {
//            assertThat(streamAttributeValueService.delete(value)).isTrue();
//        }

        streamTarget = streamStore.openExistingStreamTarget(stream);
        streamTarget.getAttributes().put(testString5, testString6);
        assertThat(streamTarget.getAttributes().get(testString3)).isNull();
        streamStore.closeStreamTarget(streamTarget);
        stream = streamTarget.getStream();

        streamSource = streamStore.openStreamSource(stream.getId());
        stream = streamSource.getStream();
        assertThat(streamSource.getAttributes().get(testString5)).isEqualTo(testString6);
        assertThat(streamSource.getAttributes().get(testString3)).isNull();
        streamStore.closeStreamSource(streamSource);
    }

    /**
     * Test.
     */
    @Test
    void testIOErrors() throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final DataProperties streamProperties = new DataProperties.Builder()
                .feedName(FEED1)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build();

        final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
        StreamTargetUtil.write(streamTarget, testString);
        final Set<Path> dirSet = new HashSet<>();
        for (final Path file : ((FileSystemStreamTarget) streamTarget).getFiles(true)) {
            dirSet.add(file.getParent());
        }

        try {
            for (final Path dir : dirSet) {
                FileUtil.removeFilePermision(dir, PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE);
            }

            streamStore.closeStreamTarget(streamTarget);
            fail("Expecting an error");
        } catch (final RuntimeException e) {
            // Expected.
        } finally {
            for (final Path dir : dirSet) {
                FileUtil.addFilePermision(dir, PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE);
            }
        }
    }

    private enum DeleteTestStyle {
        META, OPEN, OPEN_TOUCHED, OPEN_TOUCHED_CLOSED
    }
}
