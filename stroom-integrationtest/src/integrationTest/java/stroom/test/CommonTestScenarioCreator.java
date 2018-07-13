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

package stroom.test;

import org.junit.Assert;
import stroom.docref.DocRef;
import stroom.feed.FeedService;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.shared.Feed;
import stroom.feed.shared.Feed.FeedStatus;
import stroom.index.IndexStore;
import stroom.index.IndexVolumeService;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFields;
import stroom.node.NodeCache;
import stroom.node.VolumeService;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeUseStatus;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTarget;
import stroom.streamstore.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.fs.serializable.RawInputSegmentWriter;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.StreamProcessorFilterService;
import stroom.streamtask.StreamProcessorService;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Help class to create some basic scenarios for testing.
 */
public class CommonTestScenarioCreator {
    private final FeedService feedService;
    private final StreamStore streamStore;
    private final StreamProcessorService streamProcessorService;
    private final StreamProcessorFilterService streamProcessorFilterService;
    private final IndexStore indexStore;
    private final VolumeService volumeService;
    private final IndexVolumeService indexVolumeService;
    private final NodeCache nodeCache;

    @Inject
    CommonTestScenarioCreator(final FeedService feedService,
                              final StreamStore streamStore,
                              final StreamProcessorService streamProcessorService,
                              final StreamProcessorFilterService streamProcessorFilterService,
                              final IndexStore indexStore,
                              final VolumeService volumeService,
                              final IndexVolumeService indexVolumeService,
                              final NodeCache nodeCache) {
        this.feedService = feedService;
        this.streamStore = streamStore;
        this.streamProcessorService = streamProcessorService;
        this.streamProcessorFilterService = streamProcessorFilterService;
        this.indexStore = indexStore;
        this.volumeService = volumeService;
        this.indexVolumeService = indexVolumeService;
        this.nodeCache = nodeCache;
    }

//    public DocRef getTestFolder() {
////        Folder globalGroup = null;
////        globalGroup = folderService.loadByName(null, "GlobalGroup");
////        if (globalGroup == null) {
////            globalGroup = folderService.create(null, "GlobalGroup");
////        }
////        return DocRef.create(globalGroup);
//
//        return null;
//    }

    public Feed createSimpleFeed() {
        return createSimpleFeed("Junit");
    }

    /**
     * @return a basic feed
     */
    public Feed createSimpleFeed(final String name) {
        Feed feed = feedService.create(FileSystemTestUtil.getUniqueTestString());
        feed.setDescription(name);
        feed.setStatus(FeedStatus.RECEIVE);
        feed.setStreamType(StreamType.RAW_EVENTS);
        feed = feedService.save(feed);

        return feed;
    }

    public Feed createSimpleFeed(final String name, final String uuid) {
        Feed feed = feedService.create(FileSystemTestUtil.getUniqueTestString());
        feed.setUuid(uuid);
        feed.setDescription(name);
        feed.setStatus(FeedStatus.RECEIVE);
        feed.setStreamType(StreamType.RAW_EVENTS);
        feed = feedService.save(feed);

        return feed;
    }

    public void createBasicTranslateStreamProcessor(final Feed feed) {

        final QueryData findStreamQueryData = new QueryData.Builder()
                .dataSource(StreamDataSource.STREAM_STORE_DOC_REF)
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                    .addTerm(StreamDataSource.FEED_NAME, ExpressionTerm.Condition.EQUALS, feed.getName())
                    .addTerm(StreamDataSource.STREAM_TYPE_NAME, ExpressionTerm.Condition.EQUALS, StreamType.RAW_EVENTS.getName())
                    .build())
                .build();

        createStreamProcessor(findStreamQueryData);
    }

    public void createStreamProcessor(final QueryData queryData) {
        StreamProcessor streamProcessor = new StreamProcessor();
        streamProcessor.setEnabled(true);
        streamProcessor = streamProcessorService.save(streamProcessor);

        streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, queryData);
    }

    public DocRef createIndex(final String name) {
        return createIndex(name, createIndexFields(), IndexDoc.DEFAULT_MAX_DOCS_PER_SHARD);
    }

    public DocRef createIndex(final String name, final List<IndexField> indexFields) {
        return createIndex(name, indexFields, IndexDoc.DEFAULT_MAX_DOCS_PER_SHARD);
    }

    public DocRef createIndex(final String name, final List<IndexField> indexFields, final int maxDocsPerShard) {
        // Create a test index.
        final DocRef indexRef = indexStore.createDocument(name);
        final IndexDoc index = indexStore.readDocument(indexRef);
        index.setMaxDocsPerShard(maxDocsPerShard);
        index.setIndexFields(indexFields);
        indexStore.writeDocument(index);
        Assert.assertNotNull(index);

        final FindVolumeCriteria findVolumeCriteria = new FindVolumeCriteria();
        findVolumeCriteria.getIndexStatusSet().add(VolumeUseStatus.ACTIVE);
        findVolumeCriteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        final Set<Volume> volumes = new HashSet<>(volumeService.find(findVolumeCriteria));
        indexVolumeService.setVolumesForIndex(indexRef, volumes);

        return indexRef;
    }

    public List<IndexField> createIndexFields() {
        final List<IndexField> indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("test"));
        return indexFields;
    }

    /**
     * @param feed related
     * @return a basic raw file
     */
    public Stream createSample2LineRawFile(final Feed feed, final StreamType streamType) {
        final Stream stream = Stream.createStream(streamType, feed, null);
        final StreamTarget target = streamStore.openStreamTarget(stream);

        final InputStream inputStream = new ByteArrayInputStream("line1\nline2".getBytes(StreamUtil.DEFAULT_CHARSET));

        final RawInputSegmentWriter writer = new RawInputSegmentWriter();
        writer.write(inputStream, new RASegmentOutputStream(target));

        target.getAttributeMap().put(StroomHeaderArguments.FEED, feed.getName());

        streamStore.closeStreamTarget(target);
        return target.getStream();
    }

    public Stream createSampleBlankProcessedFile(final Feed feed, final Stream sourceStream) {
        final Stream stream = Stream.createProcessedStream(sourceStream, feed, StreamType.EVENTS, null, null);

        final StreamTarget target = streamStore.openStreamTarget(stream);

        final InputStream inputStream = new ByteArrayInputStream(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Events xpath-default-namespace=\"records:2\" "
                + "xmlns:stroom=\"stroom\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns=\"event-logging:3\" "
                + "xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.0.0.xsd\" "
                + "Version=\"3.0.0\"/>").getBytes(StreamUtil.DEFAULT_CHARSET));

        final RawInputSegmentWriter writer = new RawInputSegmentWriter();
        writer.write(inputStream, new RASegmentOutputStream(target));
        streamStore.closeStreamTarget(target);
        return target.getStream();
    }
}
