package stroom.analytics.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.pipeline.writer.StreamAppender;
import stroom.pipeline.writer.XMLWriter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

public class DetectionsWriter implements DetectionConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DetectionsWriter.class);

    private final DetectionWriter detectionWriter;
    private final StreamAppender streamAppender;
    private DocRef feed = null;

    @Inject
    public DetectionsWriter(final DetectionWriter detectionWriter,
                            final XMLWriter xmlWriter,
                            final StreamAppender streamAppender) {
        this.detectionWriter = detectionWriter;
        this.streamAppender = streamAppender;

        detectionWriter.setHandler(xmlWriter);
        xmlWriter.setTarget(streamAppender);
        xmlWriter.setIndentOutput(true);
        streamAppender.setStreamType(StreamTypeNames.DETECTIONS);
    }

    public void setFeed(final DocRef feed) {
        this.feed = feed;
        streamAppender.setFeed(feed);
    }

    @Override
    public void start() {
        LOGGER.debug("Starting to write detections to feed: {}", feed);
        detectionWriter.start();
    }

    @Override
    public void end() {
        detectionWriter.end();
        LOGGER.debug("Finished writing detections to feed: {}", feed);
    }

    @Override
    public void accept(final Detection detection) {
        LOGGER.debug("Accepting detection to feed: {}", feed);
        detectionWriter.accept(detection);
    }
}
