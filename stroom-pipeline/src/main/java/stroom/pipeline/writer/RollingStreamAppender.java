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

package stroom.pipeline.writer;

import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.Meta;
import stroom.node.api.NodeInfo;
import stroom.pipeline.destination.RollingDestination;
import stroom.pipeline.destination.RollingDestinationFactory;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.destination.RollingStreamDestination;
import stroom.pipeline.destination.StreamKey;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaHolder;
import stroom.svg.shared.SvgImage;

import com.google.common.base.Strings;
import jakarta.inject.Inject;

import java.time.Instant;

/**
 * Joins text instances into a single text instance.
 */
@ConfigurableElement(
        type = "RollingStreamAppender",
        description = """
                A destination used to write one or more output streams to a new stream which is then rolled \
                when it reaches a certain size or age.
                A new stream will be created after the size or age criteria has been met.
                On system shutdown all active streams will be rolled.
                """,
        category = Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_STREAM)
public class RollingStreamAppender extends AbstractRollingAppender implements RollingDestinationFactory {

    private final Store streamStore;
    private final MetaHolder metaHolder;
    private final NodeInfo nodeInfo;
    private final DocRefInfoService docRefInfoService;
    private final VolumeGroupNameProvider volumeGroupNameProvider;

    private DocRef feedRef;
    private String feed;
    private String streamType;
    private boolean segmentOutput = true;
    private String volumeGroup;

    private StreamKey key;

    @Inject
    RollingStreamAppender(final RollingDestinations destinations,
                          final Store streamStore,
                          final MetaHolder metaHolder,
                          final NodeInfo nodeInfo,
                          final DocRefInfoService docRefInfoService,
                          final VolumeGroupNameProvider volumeGroupNameProvider) {
        super(destinations);
        this.streamStore = streamStore;
        this.metaHolder = metaHolder;
        this.nodeInfo = nodeInfo;
        this.docRefInfoService = docRefInfoService;
        this.volumeGroupNameProvider = volumeGroupNameProvider;
    }

    @Override
    public RollingDestination createDestination() {
        if (key.getStreamType() == null) {
            throw ProcessException.create("Stream type not specified");
        }

        // Don't set the processor or the task or else this rolling stream will be deleted automatically because the
        // system will think it is superseded output.
        final MetaProperties metaProperties = MetaProperties.builder()
                .feedName(key.getFeed())
                .typeName(key.getStreamType())
                .parent(metaHolder.getMeta())
                .build();

        final String nodeName = nodeInfo.getThisNodeName();
        final String volumeGroupName = volumeGroupNameProvider
                .getVolumeGroupName(key.getFeed(), key.getStreamType(), volumeGroup);
        final Target streamTarget = streamStore.openTarget(metaProperties, volumeGroupName);
        return new RollingStreamDestination(key,
                getFrequencyTrigger(),
                getCronTrigger(),
                getRollSize(),
                Instant.now(),
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
                feed = docRefInfoService.name(feedRef).orElse(null);
                if (Strings.isNullOrEmpty(feed)) {
                    fatal("Feed not found");
                }

            } else {
                final Meta parentMeta = metaHolder.getMeta();
                if (parentMeta == null) {
                    fatal("Unable to determine feed as no parent set");
                } else if (Strings.isNullOrEmpty(parentMeta.getFeedName())) {
                    fatal("Parent has no feed name");
                } else {
                    // Use current feed if none other has been specified.
                    feed = parentMeta.getFeedName();
                }
            }
        }

        if (Strings.isNullOrEmpty(streamType)) {
            throw ProcessException.create("Stream type not specified");
        }
    }

    private void fatal(final String message) {
        throw ProcessException.create(message);
    }

    @PipelineProperty(
            description = "The stream type that the output stream should be written as. This must be specified.",
            displayPriority = 1)
    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    @PipelineProperty(
            description = "The feed that output stream should be written to. If not specified the feed the input " +
                    "stream belongs to will be used.",
            displayPriority = 2)
    @PipelinePropertyDocRef(types = FeedDoc.TYPE)
    public void setFeed(final DocRef feedRef) {
        this.feedRef = feedRef;
    }

    @PipelineProperty(description = "Choose the maximum size that a stream can be before it is rolled.",
            defaultValue = "100M",
            displayPriority = 3)
    public void setRollSize(final String rollSize) {
        super.setRollSize(rollSize);
    }

    @PipelineProperty(description = "Choose how frequently streams are rolled.",
            defaultValue = "1h",
            displayPriority = 4)
    public void setFrequency(final String frequency) {
        super.setFrequency(frequency);
    }

    @PipelineProperty(description = "Provide a cron expression to determine when streams are rolled.",
            displayPriority = 5)
    public void setSchedule(final String expression) {
        super.setSchedule(expression);
    }

    @PipelineProperty(
            description = "Should the output stream be marked with indexed segments to allow fast access to " +
                    "individual records?",
            defaultValue = "true",
            displayPriority = 6)
    public void setSegmentOutput(final boolean segmentOutput) {
        this.segmentOutput = segmentOutput;
    }

    @PipelineProperty(
            description = "Optionally override the default volume group of the destination feed.",
            displayPriority = 7)
    public void setVolumeGroup(final String volumeGroup) {
        this.volumeGroup = volumeGroup;
    }
}
