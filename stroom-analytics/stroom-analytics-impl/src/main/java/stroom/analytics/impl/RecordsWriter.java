package stroom.analytics.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.pipeline.writer.StreamAppender;
import stroom.pipeline.writer.XMLWriter;

import javax.inject.Inject;

public class RecordsWriter implements RecordConsumer, ProcessLifecycleAware {

    private final RecordWriter recordWriter;
    private final StreamAppender streamAppender;


    @Inject
    public RecordsWriter(final RecordWriter recordWriter,
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
    public void accept(final Record detection) {
        recordWriter.accept(detection);
    }
}
