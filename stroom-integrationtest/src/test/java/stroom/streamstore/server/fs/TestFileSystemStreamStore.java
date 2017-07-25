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

package stroom.streamstore.server.fs;

import org.hibernate.LazyInitializationException;
import org.junit.Assert;
import org.junit.Test;
import stroom.entity.server.util.PeriodUtil;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.entity.shared.IdRange;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Period;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.streamstore.server.EffectiveMetaDataCriteria;
import stroom.streamstore.server.FindStreamAttributeValueCriteria;
import stroom.streamstore.server.FindStreamVolumeCriteria;
import stroom.streamstore.server.StreamAttributeValueFlush;
import stroom.streamstore.server.StreamAttributeValueService;
import stroom.streamstore.server.StreamException;
import stroom.streamstore.server.StreamMaintenanceService;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.shared.FindStreamAttributeMapCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamAttributeConstants;
import stroom.streamstore.shared.StreamAttributeMap;
import stroom.streamstore.shared.StreamAttributeMapService;
import stroom.streamstore.shared.StreamAttributeValue;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamVolume;
import stroom.streamtask.server.StreamTaskCreator;
import stroom.task.server.TaskMonitorImpl;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.config.StroomProperties;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.test.StroomExpectedException;
import stroom.volume.server.VolumeServiceImpl;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestFileSystemStreamStore extends AbstractCoreIntegrationTest {
    private static final int N1 = 1;
    private static final int N13 = 13;
    @Resource
    private StreamStore streamStore;
    @Resource
    private StreamMaintenanceService streamMaintenanceService;
    @Resource
    private StreamAttributeValueService streamAttributeValueService;
    @Resource
    private StreamAttributeValueFlush streamAttributeValueFlush;
    @Resource
    private FeedService feedService;
    @Resource
    private FolderService folderService;
    @Resource
    private StreamTaskCreator streamTaskCreator;
    @Resource
    private StreamAttributeMapService streamMDService;
    private Feed feed1;
    private Feed feed2;
    private int initialReplicationCount = 1;

    @Override
    protected void onBefore() {
        feed1 = setupFeed("FEED1");
        feed2 = setupFeed("FEED2");
        initialReplicationCount = StroomProperties.getIntProperty(VolumeServiceImpl.PROP_RESILIENT_REPLICATION_COUNT, 1);
        StroomProperties.setIntProperty(VolumeServiceImpl.PROP_RESILIENT_REPLICATION_COUNT, 2, StroomProperties.Source.TEST);
    }

    @Override
    protected void onAfter() {
        StroomProperties.setIntProperty(VolumeServiceImpl.PROP_RESILIENT_REPLICATION_COUNT, initialReplicationCount,
                StroomProperties.Source.TEST);
    }

    /**
     * Setup some test data.
     */
    private Feed setupFeed(final String feedName) {
        Feed sample = feedService.loadByName(feedName);
        if (sample == null) {
            Folder folder = folderService.create(null, FileSystemTestUtil.getUniqueTestString());
            folder = folderService.save(folder);

            sample = feedService.create(DocRefUtil.create(folder), feedName);
            sample.setDescription("Junit");
            sample = feedService.save(sample);
        }
        return sample;
    }

    @Test
    public void testBasic() throws Exception {
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.setCreatePeriod(PeriodUtil.createYearPeriod(2014));
        findStreamCriteria.setEffectivePeriod(PeriodUtil.createYearPeriod(2014));
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed1);
        findStreamCriteria.obtainParentStreamIdSet().add(1L);
        findStreamCriteria.obtainPipelineIdSet().add(1L);
        findStreamCriteria.obtainStreamIdSet().add(1L);
        findStreamCriteria.obtainStreamProcessorIdSet().add(1L);
        findStreamCriteria.obtainStreamTypeIdSet().add(1L);
        findStreamCriteria.obtainFolderIdSet().add(1L);
        testCriteria(findStreamCriteria, 0);
    }

	@Test
	public void testFeedFindAll() throws Exception {
		final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
		findStreamCriteria.obtainFeeds().obtainInclude().add(feed1);
		findStreamCriteria.obtainFeeds().obtainInclude().add(feed2);
		testCriteria(findStreamCriteria, 2);
	}

	@Test
	public void testFeedFindSome() throws Exception {
		final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
		findStreamCriteria.setPageRequest(new PageRequest(0L, 1));
		findStreamCriteria.obtainFeeds().obtainInclude().add(feed1);
		findStreamCriteria.obtainFeeds().obtainInclude().add(feed2);
		testCriteria(findStreamCriteria, 1);
	}

    @Test
    public void testFeedFindNone() throws Exception {
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed1);
        findStreamCriteria.obtainFeeds().obtainExclude().add(feed1);
        testCriteria(findStreamCriteria, 0);
    }

    @Test
    public void testFeedFindOne() throws Exception {
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainExclude().add(feed2);
        testCriteria(findStreamCriteria, 1);
    }

    private void testCriteria(final FindStreamCriteria criteria, final int expectedStreams) throws Exception {
        streamStore.findDelete(new FindStreamCriteria());

        createStream(feed1, 1L, null);
        createStream(feed2, 1L, null);
        criteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        final BaseResultList<Stream> streams = streamStore.find(criteria);
        Assert.assertEquals(expectedStreams, streams.size());

        streamStore.findDelete(new FindStreamCriteria());
    }

    @Test
    public void testParentChild() throws Exception {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);
        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamStore.closeStreamTarget(streamTarget);

        final Stream child = Stream.createProcessedStream(streamTarget.getStream(), streamTarget.getStream().getFeed(),
                StreamType.RAW_EVENTS, null, null);
        final StreamTarget childTarget = streamStore.openStreamTarget(child);
        childTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamStore.closeStreamTarget(childTarget);

        final Stream grandChild = Stream.createProcessedStream(childTarget.getStream(),
                childTarget.getStream().getFeed(), StreamType.RAW_EVENTS, null, null);
        final StreamTarget grandChildTarget = streamStore.openStreamTarget(grandChild);
        grandChildTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamStore.closeStreamTarget(grandChildTarget);

        FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainStreamIdSet().add(childTarget.getStream().getId());
        findStreamCriteria.getFetchSet().add(Stream.ENTITY_TYPE);
        List<Stream> relationList = streamStore.find(findStreamCriteria);

        Assert.assertEquals(stream, relationList.get(0));
        Assert.assertEquals(child, relationList.get(1));
        Assert.assertEquals(grandChild, relationList.get(2));

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainStreamIdSet().add(grandChildTarget.getStream().getId());
        findStreamCriteria.getFetchSet().add(Stream.ENTITY_TYPE);

        relationList = streamStore.find(findStreamCriteria);

        Assert.assertEquals(stream, relationList.get(0));
        Assert.assertEquals(child, relationList.get(1));
        Assert.assertEquals(grandChild, relationList.get(2));
    }

    @Test
    public void testFindDeleteAndUndelete() throws Exception {
        final Stream stream = createStream(feed1, 1L, null);

        FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainStreamIdSet().add(stream);
        final long deleted = streamStore.findDelete(findStreamCriteria);

        Assert.assertEquals(1L, deleted);

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainStreamIdSet().add(stream);
        Assert.assertEquals(1L, streamStore.find(findStreamCriteria).size());

        findStreamCriteria.obtainStatusSet().clear();
        findStreamCriteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        Assert.assertEquals(0L, streamStore.find(findStreamCriteria).size());

        findStreamCriteria.obtainStatusSet().clear();
        findStreamCriteria.obtainStatusSet().add(StreamStatus.DELETED);
        Assert.assertEquals(1L, streamStore.find(findStreamCriteria).size());

        findStreamCriteria.obtainStatusSet().clear();
        findStreamCriteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        Assert.assertEquals(0L, streamStore.find(findStreamCriteria).size());

        // This will undelete
        findStreamCriteria.obtainStatusSet().clear();
        findStreamCriteria.obtainStatusSet().add(StreamStatus.DELETED);
        Assert.assertEquals(1L, streamStore.findDelete(findStreamCriteria).longValue());

        findStreamCriteria.obtainStatusSet().clear();
        findStreamCriteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        Assert.assertEquals(1L, streamStore.find(findStreamCriteria).size());
    }

    private Stream createStream(final Feed feed, final Long streamTaskId, final Long parentStreamId)
            throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed, null);
        stream.setStreamTaskId(streamTaskId);
        stream.setParentStreamId(parentStreamId);

        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamStore.closeStreamTarget(streamTarget);
        stream = streamTarget.getStream();
        return stream;
    }

    @Test
    public void testFindWithAllCriteria() throws Exception {
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.setPageRequest(new PageRequest(0L, 100));
        findStreamCriteria.obtainStatusSet().clear();
        findStreamCriteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        findStreamCriteria.setSort(FindStreamCriteria.FIELD_CREATE_MS);

        findStreamCriteria.setCreatePeriod(PeriodUtil.createToDateWithOffset(System.currentTimeMillis(), 1));
        findStreamCriteria.setEffectivePeriod(PeriodUtil.createToDateWithOffset(System.currentTimeMillis(), 1));
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed1);
        findStreamCriteria.setStatusPeriod(PeriodUtil.createToDateWithOffset(System.currentTimeMillis(), 1));
        findStreamCriteria.obtainParentStreamIdSet().add(1L);
        findStreamCriteria.obtainPipelineIdSet().add(1L);
        findStreamCriteria.setStreamIdRange(new IdRange(0L, 1L));
        findStreamCriteria.obtainStreamProcessorIdSet().add(1L);
        findStreamCriteria.obtainStreamTypeIdSet().add(1L);

        Assert.assertEquals(0L, streamStore.find(findStreamCriteria).size());
    }

    @Test
    @StroomExpectedException(exception = LazyInitializationException.class)
    public void testBasicImportExportList() throws Exception {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);

        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        streamTarget.getStream().getFeed().getName();
        Stream exactMetaData = streamTarget.getStream();
        streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamStore.closeStreamTarget(streamTarget);

        // Refresh
        final StreamSource streamSource = streamStore.openStreamSource(exactMetaData.getId());
        exactMetaData = streamSource.getStream();

        Assert.assertSame(StreamStatus.UNLOCKED, exactMetaData.getStatus());

        // Check we can read it back in

        Assert.assertNotNull(streamSource);
        // Must be a proxy
        Assert.assertNotNull(streamSource.getStream().getFeed());
        Assert.assertNotNull(streamSource.getStream().getFeed().getName());

        // Finished
        streamStore.closeStreamSource(streamSource);

        final List<Stream> list = streamStore.find(FindStreamCriteria.createWithStreamType(StreamType.RAW_EVENTS));

        boolean foundOne = false;
        for (final Stream result : list) {
            Assert.assertNotNull(FileSystemStreamTypeUtil.getDirectory(result, StreamType.RAW_EVENTS));
            Assert.assertNotNull(FileSystemStreamTypeUtil.getBaseName(result));
            if (FileSystemStreamTypeUtil.getBaseName(result)
                    .equals(FileSystemStreamTypeUtil.getBaseName(exactMetaData))) {
                foundOne = true;
            }
        }

        Assert.assertTrue("Expecting to find at least that one file " + stream, foundOne);

        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();

        Assert.assertTrue("Expecting to find at least 1 with no criteria",
                streamStore.find(findStreamCriteria).size() >= 1);

        findStreamCriteria.obtainStatusSet().clear();
        findStreamCriteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        Assert.assertTrue("Expecting to find at least 1 with UNLOCKED criteria",
                streamStore.find(findStreamCriteria).size() >= 1);

        final FindStreamVolumeCriteria volumeCriteria = new FindStreamVolumeCriteria();
        volumeCriteria.obtainStreamStatusSet().add(StreamStatus.UNLOCKED);
        volumeCriteria.obtainStreamIdSet().add(exactMetaData);
        Assert.assertTrue("Expecting to find at least 1 with day old criteria",
                streamMaintenanceService.find(volumeCriteria).size() >= 1);
    }

    private void doTestDeleteSource(final DeleteTestStyle style) throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);

        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        stream = streamTarget.getStream();
        streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamStore.closeStreamTarget(streamTarget);

        // Check we can read it back in
        StreamSource streamSource = streamStore.openStreamSource(stream.getId());
        Assert.assertNotNull(streamSource);

        if (DeleteTestStyle.META.equals(style)) {
            // Finished
            streamStore.closeStreamSource(streamSource);
            // This should delete it
            streamStore.deleteStream(stream);
        }
        if (DeleteTestStyle.OPEN.equals(style)) {
            streamStore.deleteStream(streamSource.getStream());
        }
        if (DeleteTestStyle.OPEN_TOUCHED_CLOSED.equals(style)) {
            streamSource.getInputStream().read();
            streamSource.getInputStream().close();
            streamSource.close();
            streamStore.deleteStream(streamSource.getStream());
        }

        streamSource = streamStore.openStreamSource(stream.getId(), true);
        Assert.assertEquals(StreamStatus.DELETED, streamSource.getStream().getStatus());
    }

    private void doTestDeleteTarget(final DeleteTestStyle style) throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);

        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        stream = streamTarget.getStream();

        if (DeleteTestStyle.META.equals(style)) {
            // Finished
            streamStore.closeStreamTarget(streamTarget);
            // This should delete it
            streamStore.deleteStream(stream);
        }
        if (DeleteTestStyle.OPEN.equals(style)) {
            streamStore.deleteStreamTarget(streamTarget);
        }
        if (DeleteTestStyle.OPEN_TOUCHED.equals(style)) {
            streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
            streamStore.deleteStreamTarget(streamTarget);
        }
        if (DeleteTestStyle.OPEN_TOUCHED_CLOSED.equals(style)) {
            streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
            streamStore.closeStreamTarget(streamTarget);
            streamStore.deleteStreamTarget(streamTarget);
        }

        final StreamSource streamSource = streamStore.openStreamSource(stream.getId(), true);
        Assert.assertEquals(StreamStatus.DELETED, streamSource.getStream().getStatus());
    }

    @Test
    public void testDelete1() throws IOException {
        doTestDeleteSource(DeleteTestStyle.META);
    }

    @Test
    public void testDelete2() throws IOException {
        doTestDeleteSource(DeleteTestStyle.OPEN);
    }

    @Test
    public void testDelete4() throws IOException {
        doTestDeleteTarget(DeleteTestStyle.META);
    }

    @Test
    public void testDelete5() throws IOException {
        doTestDeleteTarget(DeleteTestStyle.OPEN);
    }

    @Test
    public void testDelete6() throws IOException {
        doTestDeleteTarget(DeleteTestStyle.OPEN_TOUCHED);
    }

    @Test
    public void testDelete7() throws IOException {
        doTestDeleteSource(DeleteTestStyle.OPEN_TOUCHED_CLOSED);
    }

    @Test
    public void testDelete8() throws IOException {
        doTestDeleteTarget(DeleteTestStyle.OPEN_TOUCHED_CLOSED);
    }

    @Test
    public void testDeletePipleineFilters() throws IOException {
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainPipelineIdSet().add(1L);
        findStreamCriteria.obtainStreamProcessorIdSet().add(1L);
        streamStore.findDelete(findStreamCriteria);
    }

//    @Test
//    @StroomExpectedException(exception = LazyInitializationException.class)
//    public void testProxyHandling() throws IOException {
//        final String testString = FileSystemTestUtil.getUniqueTestString();
//
//        final Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);
//
//        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
//        final Stream exactMetaData = streamTarget.getStream();
//        streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
//        streamStore.closeStreamTarget(streamTarget);
//
//        final Stream reloadMetaData = streamStore.find(FindStreamCriteria.createWithStream(exactMetaData)).get(0);
//        // Proxy can handle this
//        reloadMetaData.getFeed().getId();
//
//        try {
//            // This will trigger a lazy load exception
//            reloadMetaData.getFeed().getName();
//            Assert.fail("Proxy Expected ... not the real thing");
//        } catch (final Exception ex) {
//            final Feed reloadFd = feedService.load(reloadMetaData.getFeed());
//            // Should be OK now
//            reloadFd.getName();
//        }
//    }

    @Test
    public void testFileSystem() throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);

        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        final Stream exactMetaData = streamTarget.getStream();
        streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamTarget.getAttributeMap().put(StreamAttributeConstants.REC_READ, "10");
        streamTarget.getAttributeMap().put(StreamAttributeConstants.REC_WRITE, "20");
        streamStore.closeStreamTarget(streamTarget);

        final Stream reloadMetaData = streamStore.find(FindStreamCriteria.createWithStream(exactMetaData)).get(0);

        final StreamSource streamSource = streamStore.openStreamSource(reloadMetaData.getId());

        final InputStream is = streamSource.getInputStream();
        Assert.assertNotNull(is);
        is.close();
        streamStore.closeStreamSource(streamSource);

        final FindStreamAttributeMapCriteria criteria = new FindStreamAttributeMapCriteria();
        criteria.getFindStreamCriteria().obtainStreamIdSet().add(reloadMetaData);

        streamAttributeValueFlush.flush();
        final StreamAttributeMap streamMD = streamMDService.find(criteria).getFirst();

        Assert.assertEquals("10", streamMD.getAttributeValue(StreamAttributeConstants.REC_READ));
        Assert.assertEquals("20", streamMD.getAttributeValue(StreamAttributeConstants.REC_WRITE));
    }

    @Test
    public void testWriteNothing() throws IOException {
        final Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);

        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        final Stream exactMetaData = streamTarget.getStream();
        streamStore.closeStreamTarget(streamTarget);

        final Stream reloadMetaData = streamStore.find(FindStreamCriteria.createWithStream(exactMetaData)).get(0);

        final StreamSource streamSource = streamStore.openStreamSource(reloadMetaData.getId());
        final InputStream is = streamSource.getInputStream();
        Assert.assertNotNull(is);
        is.close();
        streamStore.closeStreamSource(streamSource);
    }

    @Test
    public void testEffective() throws IOException {
        final String feed1 = FileSystemTestUtil.getUniqueTestString();
        final String feed2 = FileSystemTestUtil.getUniqueTestString();
        final String feed3 = FileSystemTestUtil.getUniqueTestString();

        setupFeed(feed1);
        final Stream refData1 = buildRefData(feed2, 2008, 2, StreamType.REFERENCE, false);
        final Stream refData2 = buildRefData(feed2, 2009, 2, StreamType.REFERENCE, false);
        final Stream refData3 = buildRefData(feed2, 2010, 2, StreamType.REFERENCE, false);

        // These 2 should get ignored as one is locked and the other is RAW
        final HashSet<Long> invalidFeeds = new HashSet<>();
        invalidFeeds.add(buildRefData(feed2, 2010, 2, StreamType.REFERENCE, true).getId());

        invalidFeeds.add(buildRefData(feed2, 2010, 2, StreamType.RAW_REFERENCE, false).getId());

        // Build some for another feed.
        buildRefData(feed3, 2008, 2, StreamType.REFERENCE, false);
        buildRefData(feed3, 2009, 2, StreamType.REFERENCE, false);
        buildRefData(feed3, 2010, 2, StreamType.REFERENCE, false);
        buildRefData(feed3, 2011, 2, StreamType.REFERENCE, false);

        final EffectiveMetaDataCriteria criteria = new EffectiveMetaDataCriteria();
        criteria.setStreamType(StreamType.REFERENCE.getName());

        // feed2 or feed1
        criteria.setFeed(DocRefUtil.create(feedService.loadByName(feed2)));
//        criteria.getFeedIdSet().add(feedService.loadByName(feed1));

        // 2009 to 2010
        criteria.setEffectivePeriod(new Period(DateUtil.parseNormalDateTimeString("2009-01-01T00:00:00.000Z"),
                DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z")));

        List<Stream> list = streamStore.findEffectiveStream(criteria);

        // Make sure the list contains what it should.
        verifyList(list, refData1, refData2);

        // Try another test that picks up no tom within period but it should get
        // the last one as it would be the most effective.
        criteria.setEffectivePeriod(new Period(DateUtil.parseNormalDateTimeString("2013-01-01T00:00:00.000Z"),
                DateUtil.parseNormalDateTimeString("2014-01-01T00:00:00.000Z")));

        list = streamStore.findEffectiveStream(criteria);

        // Make sure the list contains what it should.
        verifyList(list, refData3);

        Assert.assertFalse(invalidFeeds.contains(list.get(0).getId()));
    }

    /**
     * Check that the list of stream contains the items we expect.
     *
     * @param list
     * @param expected
     */
    private void verifyList(final List<Stream> list, final Stream... expected) {
        Assert.assertNotNull(list);
        Assert.assertEquals(expected.length, list.size());
        for (final Stream stream : expected) {
            Assert.assertTrue(list.contains(stream));
        }
    }

    private Stream buildRefData(final String feed, final int year, final int month, final StreamType type,
            final boolean lock) throws IOException {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final Feed sample = setupFeed(feed);
        final Stream stream = Stream.createStream(type, sample,
                ZonedDateTime.of(year, month, N1, N13, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli());

        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamTarget.close();
        // Leave locked ?
        if (!lock) {
            streamStore.closeStreamTarget(streamTarget);
        }
        // commonTestControl.clearContext();

        return streamTarget.getStream();
    }

    @Test
    public void testDeleteStreamTarget() throws Exception {
        final Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);
        doDeleteStreamTarget(stream);

        try {
            doDeleteStreamTarget(stream);
            Assert.fail();
        } catch (final Exception e) {
        }
    }

    @Test
    public void testAppendStream() throws Exception {
        final String testString1 = FileSystemTestUtil.getUniqueTestString();
        final String testString2 = FileSystemTestUtil.getUniqueTestString();
        final String testString3 = FileSystemTestUtil.getUniqueTestString();
        final String testString4 = FileSystemTestUtil.getUniqueTestString();
        final String testString5 = FileSystemTestUtil.getUniqueTestString();
        final String testString6 = FileSystemTestUtil.getUniqueTestString();

        Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);

        StreamTarget streamTarget = streamStore.openStreamTarget(stream);

        streamTarget.getOutputStream().write("xyz".getBytes(StreamUtil.DEFAULT_CHARSET));
        streamTarget.getAttributeMap().put(testString1, testString2);
        streamTarget.getAttributeMap().put(StreamAttributeConstants.REC_READ, "100");
        streamStore.closeStreamTarget(streamTarget);

        stream = streamTarget.getStream();

        final Set<StreamVolume> volumes = ((FileSystemStreamStore) streamStore).findStreamVolume(stream.getId());
        final Set<File> rootFile = new HashSet<>();

        for (final StreamVolume streamVolume : volumes) {
            rootFile.add(FileSystemStreamTypeUtil.createRootStreamFile(streamVolume.getVolume(), stream,
                    StreamType.RAW_EVENTS));
        }
        Assert.assertTrue(FileSystemUtil.isAllFile(rootFile));

        StreamSource streamSource = streamStore.openStreamSource(stream.getId());
        stream = streamSource.getStream();
        Assert.assertEquals(testString2, streamSource.getAttributeMap().get(testString1));
        streamStore.closeStreamSource(streamSource);

        final Set<File> manifestFile = FileSystemStreamTypeUtil.createChildStreamFile(rootFile, StreamType.MANIFEST);

        Assert.assertTrue(FileSystemUtil.isAllFile(manifestFile));

        streamTarget = streamStore.openStreamTarget(stream, true);
        streamTarget.getAttributeMap().put(testString3, testString4);
        streamStore.closeStreamTarget(streamTarget);
        stream = streamTarget.getStream();

        streamSource = streamStore.openStreamSource(stream.getId());
        stream = streamSource.getStream();
        Assert.assertEquals(testString2, streamSource.getAttributeMap().get(testString1));
        Assert.assertEquals(testString4, streamSource.getAttributeMap().get(testString3));
        streamStore.closeStreamSource(streamSource);

        Assert.assertTrue(FileSystemUtil.deleteAnyFile(manifestFile));
        for (final StreamAttributeValue value : streamAttributeValueService
                .find(FindStreamAttributeValueCriteria.create(stream))) {
            Assert.assertTrue(streamAttributeValueService.delete(value));
        }

        streamTarget = streamStore.openStreamTarget(stream, true);
        streamTarget.getAttributeMap().put(testString5, testString6);
        Assert.assertNull(streamTarget.getAttributeMap().get(testString3));
        streamStore.closeStreamTarget(streamTarget);
        stream = streamTarget.getStream();

        streamSource = streamStore.openStreamSource(stream.getId());
        stream = streamSource.getStream();
        Assert.assertEquals(testString6, streamSource.getAttributeMap().get(testString5));
        Assert.assertNull(streamSource.getAttributeMap().get(testString3));
        streamStore.closeStreamSource(streamSource);
    }

    private void doDeleteStreamTarget(final Stream stream) throws Exception {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        streamStore.closeStreamTarget(streamTarget);

        // Create tasks.
        streamTaskCreator.createTasks(new TaskMonitorImpl());

        Stream reloadedStream = streamStore.loadStreamById(streamTarget.getStream().getId());
        Assert.assertNotNull(reloadedStream);

        try {
            // We shouldn't be able to close a stream target again.
            streamStore.closeStreamTarget(streamTarget);
            Assert.fail();
        } catch (final Exception e) {
        }

        streamStore.deleteStreamTarget(streamTarget);
        reloadedStream = streamStore.find(FindStreamCriteria.createWithStream(streamTarget.getStream())).getFirst();
        Assert.assertNull(reloadedStream);

        try {
            // We shouldn't be able to delete a stream target again.
            streamStore.deleteStreamTarget(streamTarget);
            Assert.fail();
        } catch (final Exception e) {
        }

        reloadedStream = streamStore.find(FindStreamCriteria.createWithStream(streamTarget.getStream())).getFirst();
        Assert.assertNull(reloadedStream);
    }

    /**
     * Test.
     */
    @Test
    @StroomExpectedException(exception = { FileNotFoundException.class, StreamException.class, IOException.class,
            RuntimeException.class })
    public void testIOErrors() throws Exception {
        final String testString = FileSystemTestUtil.getUniqueTestString();

        final Stream stream = Stream.createStream(StreamType.RAW_EVENTS, feed1, null);
        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        streamTarget.getOutputStream().write(testString.getBytes(StreamUtil.DEFAULT_CHARSET));
        final Set<File> dirSet = new HashSet<>();
        for (final File file : ((FileSystemStreamTarget) streamTarget).getFiles(true)) {
            dirSet.add(file.getParentFile());
        }
        for (final File dir : dirSet) {
            dir.setWritable(false);
        }
        try {
            streamStore.closeStreamTarget(streamTarget);

            Assert.fail("Expecting an error");

        } catch (final Throwable th) {
            for (final File dir : dirSet) {
                dir.setWritable(true);
            }
        }
    }

    private enum DeleteTestStyle {
        META, OPEN, OPEN_TOUCHED, OPEN_TOUCHED_CLOSED
    }
}
