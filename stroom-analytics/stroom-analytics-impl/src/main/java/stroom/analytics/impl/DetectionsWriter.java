package stroom.analytics.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.pipeline.writer.StreamAppender;
import stroom.pipeline.writer.XMLWriter;

import javax.inject.Inject;

public class DetectionsWriter implements DetectionConsumer, ProcessLifecycleAware {

    private final DetectionWriter detectionWriter;
    private final StreamAppender streamAppender;


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
        streamAppender.setFeed(feed);
    }

    @Override
    public void start() {
        detectionWriter.start();
    }

    @Override
    public void end() {
        detectionWriter.end();
    }

    @Override
    public void accept(final Detection detection) {
        detectionWriter.accept(detection);
    }
}
