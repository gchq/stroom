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

package stroom.pipeline.server.writer;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.node.server.NodeCache;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingDestinationFactory;
import stroom.pipeline.destination.RollingStreamDestination;
import stroom.pipeline.destination.StreamKey;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.StreamHolder;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamTypeService;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Joins text instances into a single text instance.
 */
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "RollingStreamAppender", category = Category.DESTINATION, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_DESTINATION,
        PipelineElementType.VISABILITY_STEPPING}, icon = ElementIcons.STREAM)
public class RollingStreamAppender extends AbstractRollingAppender implements RollingDestinationFactory {

    private final StreamStore streamStore;
    private final StreamHolder streamHolder;
    private final FeedService feedService;
    private final StreamTypeService streamTypeService;
    private final NodeCache nodeCache;

    private Feed feed;
    private String streamType;
    private boolean segmentOutput = true;

    private StreamKey key;

    @Inject
    RollingStreamAppender(final StreamStore streamStore, final StreamHolder streamHolder, final FeedService feedService, final StreamTypeService streamTypeService, final NodeCache nodeCache) {
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
    Object getKey() throws IOException {
        if (key == null) {
            key = new StreamKey(feed, streamType, segmentOutput);
        }

        return key;
    }

    @Override
    void validateSpecificSettings() {
        if (feed == null) {
            // Use current feed if none other has been specified.
            feed = feedService.load(streamHolder.getStream().getFeed());
        }

        if (streamType == null) {
            throw new ProcessException("Stream type not specified");
        }
    }

    @PipelineProperty(description = "The feed that output stream should be written to. If not specified the feed the input stream belongs to will be used.")
    public void setFeed(final Feed feed) {
        this.feed = feed;
    }

    @PipelineProperty(description = "The stream type that the output stream should be written as. This must be specified.")
    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    @PipelineProperty(description = "Shoud the output stream be marked with indexed segments to allow fast access to individual records?", defaultValue = "true")
    public void setSegmentOutput(final boolean segmentOutput) {
        this.segmentOutput = segmentOutput;
    }
}
