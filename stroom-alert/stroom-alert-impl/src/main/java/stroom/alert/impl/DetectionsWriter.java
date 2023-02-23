package stroom.alert.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.pipeline.writer.StreamAppender;
import stroom.pipeline.writer.XMLWriter;

import java.util.List;
import javax.inject.Inject;

public class DetectionsWriter implements RecordConsumer {

    private final RecordWriter recordWriter;
    private final StreamAppender streamAppender;
    private final FeedStore feedStore;


    @Inject
    public DetectionsWriter(final RecordWriter recordWriter,
                            final XMLWriter xmlWriter,
                            final StreamAppender streamAppender,
                            final FeedStore feedStore) {
        this.recordWriter = recordWriter;
        this.streamAppender = streamAppender;
        this.feedStore = feedStore;

        recordWriter.setHandler(xmlWriter);
        xmlWriter.setTarget(streamAppender);
        streamAppender.setStreamType(StreamTypeNames.DETECTIONS);
    }

    public void setFeedName(final String feedName) {
        final List<DocRef> list = feedStore.findByName(feedName);
        if (list == null || list.size() == 0) {
            throw new RuntimeException("Feed not found: " + feedName);
        } else if (list.size() > 1) {
            throw new RuntimeException("Too many feeds found for name: " + feedName);
        }
        streamAppender.setFeed(list.get(0));
    }

    public void start() {
        recordWriter.start();
    }

    public void end() {
        recordWriter.end();
    }

    @Override
    public void accept(final Record record) {
        recordWriter.accept(record);
    }
}
