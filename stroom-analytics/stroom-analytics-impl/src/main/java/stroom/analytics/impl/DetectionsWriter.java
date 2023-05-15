package stroom.analytics.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.pipeline.writer.StreamAppender;
import stroom.pipeline.writer.XMLWriter;

import java.util.List;
import javax.inject.Inject;

public class DetectionsWriter implements RecordConsumer, ProcessLifecycleAware {

    private final RecordWriter recordWriter;
    private final StreamAppender streamAppender;


    @Inject
    public DetectionsWriter(final RecordWriter recordWriter,
                            final XMLWriter xmlWriter,
                            final StreamAppender streamAppender) {
        this.recordWriter = recordWriter;
        this.streamAppender = streamAppender;

        recordWriter.setHandler(xmlWriter);
        xmlWriter.setTarget(streamAppender);
        xmlWriter.setIndentOutput(true);
        streamAppender.setStreamType(StreamTypeNames.DETECTIONS);
    }

    public void setFeed(final DocRef feed) {
        streamAppender.setFeed(feed);
    }

    @Override
    public void start() {
        recordWriter.start();
    }

    @Override
    public void end() {
        recordWriter.end();
    }

    @Override
    public void accept(final Record record) {
        recordWriter.accept(record);
    }
}
