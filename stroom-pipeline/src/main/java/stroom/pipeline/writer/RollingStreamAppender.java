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

package stroom.pipeline.writer;

import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.node.NodeCache;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingDestinationFactory;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.destination.RollingStreamDestination;
import stroom.pipeline.destination.StreamKey;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.StreamHolder;
import stroom.docref.DocRef;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTarget;
import stroom.streamstore.StreamTypeService;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.task.TaskContext;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Joins text instances into a single text instance.
 */
@ConfigurableElement(type = "RollingStreamAppender", category = Category.DESTINATION, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_DESTINATION,
        PipelineElementType.VISABILITY_STEPPING}, icon = ElementIcons.STREAM)
public class RollingStreamAppender extends AbstractRollingAppender implements RollingDestinationFactory {

    private final StreamStore streamStore;
    private final StreamHolder streamHolder;
    private final FeedService feedService;
    private final StreamTypeService streamTypeService;
    private final NodeCache nodeCache;

    private DocRef feedRef;
    private Feed feed;
    private String streamType;
    private boolean segmentOutput = true;

    private StreamKey key;

    @Inject
    RollingStreamAppender(final RollingDestinations destinations,
                          final TaskContext taskContext,
                          final StreamStore streamStore,
                          final StreamHolder streamHolder,
                          final FeedService feedService,
                          final StreamTypeService streamTypeService,
                          final NodeCache nodeCache) {
        super(destinations, taskContext);
        this.streamStore = streamStore;
        this.streamHolder = streamHolder;
        this.feedService = feedService;
        this.streamTypeService = streamTypeService;
        this.nodeCache = nodeCache;
    }

    @Override
    public RollingDestination createDestination() throws IOException {
        if (key.getStreamType() == null) {
            throw new ProcessException("Stream type not specified");
        }
        final StreamType st = streamTypeService.loadByName(key.getStreamType());
        if (st == null) {
            throw new ProcessException("Stream type not specified");
        }

        // Don't set the processor or the task or else this rolling stream will be deleted automatically because the
        // system will think it is superseded output.
        final Stream stream = Stream.createProcessedStream(streamHolder.getStream(), key.getFeed(), st,
                null, null);

        final String nodeName = nodeCache.getDefaultNode().getName();
        final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
        return new RollingStreamDestination(key,
                getFrequency(),
                getMaxSize(),
                System.currentTimeMillis(),
                streamStore,
                streamTarget,
                nodeName);
    }

    @Override
    protected Object getKey() {
        if (key == null) {
            key = new StreamKey(feed, streamType, segmentOutput);
        }

        return key;
    }

    @Override
    protected void validateSpecificSettings() {
        if (feed == null) {
            if (feedRef != null) {
                feed = feedService.loadByUuid(feedRef.getUuid());
            } else {
                final Stream parentStream = streamHolder.getStream();
                if (parentStream == null) {
                    throw new ProcessException("Unable to determine feed as no parent stream set");
                }

                // Use current feed if none other has been specified.
                feed = feedService.load(parentStream.getFeed());
            }
        }

        if (streamType == null) {
            throw new ProcessException("Stream type not specified");
        }
    }

    @PipelineProperty(
            description = "The feed that output stream should be written to. If not specified the feed the input stream belongs to will be used.",
            displayPriority = 2)
    @PipelinePropertyDocRef(types = Feed.ENTITY_TYPE)
    public void setFeed(final DocRef feedRef) {
        this.feedRef = feedRef;
    }

    @PipelineProperty(
            description = "The stream type that the output stream should be written as. This must be specified.",
            displayPriority = 1)
    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    @PipelineProperty(
            description = "Shoud the output stream be marked with indexed segments to allow fast access to individual records?",
            defaultValue = "true",
            displayPriority = 6)
    public void setSegmentOutput(final boolean segmentOutput) {
        this.segmentOutput = segmentOutput;
    }
}
