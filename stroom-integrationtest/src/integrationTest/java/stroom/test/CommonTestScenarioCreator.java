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
 */

package stroom.test;

import org.junit.Assert;
import org.springframework.stereotype.Component;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.Feed.FeedStatus;
import stroom.feed.shared.FeedService;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexService;
import stroom.node.server.NodeCache;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Volume;
import stroom.node.shared.Volume.VolumeUseStatus;
import stroom.node.shared.VolumeService;
import stroom.query.api.v2.DocRef;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.server.fs.serializable.RawInputSegmentWriter;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilterService;
import stroom.streamtask.shared.StreamProcessorService;
import stroom.util.io.StreamUtil;
import stroom.util.test.FileSystemTestUtil;
import stroom.feed.StroomHeaderArguments;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Help class to create some basic scenarios for testing.
 */
@Component
public class CommonTestScenarioCreator {
    private final FeedService feedService;
    private final StreamStore streamStore;
    private final FolderService folderService;
    private final StreamProcessorService streamProcessorService;
    private final StreamProcessorFilterService streamProcessorFilterService;
    private final IndexService indexService;
    private final VolumeService volumeService;
    private final NodeCache nodeCache;

    @Inject
    CommonTestScenarioCreator(final FeedService feedService, final StreamStore streamStore, final FolderService folderService, final StreamProcessorService streamProcessorService, final StreamProcessorFilterService streamProcessorFilterService, final IndexService indexService, final VolumeService volumeService, final NodeCache nodeCache) {
        this.feedService = feedService;
        this.streamStore = streamStore;
        this.folderService = folderService;
        this.streamProcessorService = streamProcessorService;
        this.streamProcessorFilterService = streamProcessorFilterService;
        this.indexService = indexService;
        this.volumeService = volumeService;
        this.nodeCache = nodeCache;
    }

    public DocRef getTestFolder() {
        Folder globalGroup = null;
        globalGroup = folderService.loadByName(null, "GlobalGroup");
        if (globalGroup == null) {
            globalGroup = folderService.create(null, "GlobalGroup");
        }
        return DocRefUtil.create(globalGroup);
    }

    public Feed createSimpleFeed() {
        return createSimpleFeed("Junit");
    }

    /**
     * @return a basic feed
     */
    public Feed createSimpleFeed(final String name) {
        final DocRef folder = getTestFolder();
        Feed feed = feedService.create(folder, FileSystemTestUtil.getUniqueTestString());
        feed.setDescription(name);
        feed.setStatus(FeedStatus.RECEIVE);
        feed.setStreamType(StreamType.RAW_EVENTS);
        feed = feedService.save(feed);

        return feed;
    }

    public void createBasicTranslateStreamProcessor(final Feed feed) {
        final FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainStreamTypeIdSet().add(StreamType.RAW_EVENTS.getId());
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed.getId());

        createStreamProcessor(findStreamCriteria);
    }

    public void createStreamProcessor(final FindStreamCriteria findStreamCriteria) {
        StreamProcessor streamProcessor = new StreamProcessor();
        streamProcessor.setEnabled(true);
        streamProcessor = streamProcessorService.save(streamProcessor);

        streamProcessorFilterService.addFindStreamCriteria(streamProcessor, 1, findStreamCriteria);
    }

    public Index createIndex(final String name) {
        return createIndex(name, createIndexFields(), Index.DEFAULT_MAX_DOCS_PER_SHARD);
    }

    public Index createIndex(final String name, final IndexFields indexFields) {
        return createIndex(name, indexFields, Index.DEFAULT_MAX_DOCS_PER_SHARD);
    }

    public Index createIndex(final String name, final IndexFields indexFields, final int maxDocsPerShard) {
        final DocRef folder = getTestFolder();

        // Create a test index.
        Index index = indexService.create(folder, name);
        index.setMaxDocsPerShard(maxDocsPerShard);
        index.setIndexFieldsObject(indexFields);

        final FindVolumeCriteria findVolumeCriteria = new FindVolumeCriteria();
        findVolumeCriteria.getIndexStatusSet().add(VolumeUseStatus.ACTIVE);
        findVolumeCriteria.getNodeIdSet().add(nodeCache.getDefaultNode());
        final List<Volume> volumes = volumeService.find(findVolumeCriteria);
        index.getVolumes().addAll(volumes);

        index = indexService.save(index);
        Assert.assertNotNull(index);
        return index;
    }

    public IndexFields createIndexFields() {
        final IndexFields indexFields = IndexFields.createStreamIndexFields();
        indexFields.add(IndexField.createField("test"));
        return indexFields;
    }

    /**
     * @param feed related
     * @return a basic raw file
     * @throws IOException
     */
    public Stream createSample2LineRawFile(final Feed feed, final StreamType streamType) {
        final Stream stream = Stream.createStream(streamType, feed, null);
        final StreamTarget target = streamStore.openStreamTarget(stream);

        final InputStream inputStream = new ByteArrayInputStream("line1\nline2".getBytes(StreamUtil.DEFAULT_CHARSET));

        final RawInputSegmentWriter writer = new RawInputSegmentWriter();
        try {
            writer.write(inputStream, new RASegmentOutputStream(target));
        } catch (final IOException ioEx) {
            throw new RuntimeException(ioEx);
        }

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
        try {
            writer.write(inputStream, new RASegmentOutputStream(target));
        } catch (final IOException ioEx) {
            throw new RuntimeException(ioEx);
        }
        streamStore.closeStreamTarget(target);
        return target.getStream();
    }
}
