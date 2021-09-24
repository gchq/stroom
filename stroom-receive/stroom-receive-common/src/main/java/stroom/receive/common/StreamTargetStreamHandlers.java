package stroom.receive.common;

import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.proxy.StroomStatusCode;

import java.util.function.Consumer;
import javax.inject.Inject;

public class StreamTargetStreamHandlers implements StreamHandlers {

    private final Store store;
    private final FeedProperties feedProperties;
    private final MetaService metaService;
    private final MetaStatistics metaDataStatistics;

    @Inject
    public StreamTargetStreamHandlers(final Store store,
                                      final FeedProperties feedProperties,
                                      final MetaService metaService,
                                      final MetaStatistics metaDataStatistics) {
        this.store = store;
        this.feedProperties = feedProperties;
        this.metaService = metaService;
        this.metaDataStatistics = metaDataStatistics;
    }

    @Override
    public void handle(final String feedName,
                       final String typeName,
                       final AttributeMap attributeMap,
                       final Consumer<StreamHandler> consumer) {
        StreamTargetStreamHandler streamHandler = null;
        try {
            if (feedName == null || feedName.isEmpty()) {
                throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
            }

            String type = typeName;
            if (type == null || type.isEmpty()) {
                // If no type name is supplied then get the default for the feed.
                type = feedProperties.getStreamTypeName(feedName);
            }

            // Validate the data type name.
            if (!metaService.getTypes().contains(type)) {
                throw new StroomStreamException(StroomStatusCode.UNEXPECTED_DATA_TYPE, attributeMap);
            }

            streamHandler = new StreamTargetStreamHandler(
                    store,
                    feedProperties,
                    metaDataStatistics,
                    feedName,
                    type,
                    attributeMap);
            consumer.accept(streamHandler);
            streamHandler.close();
        } catch (final RuntimeException e) {
            if (streamHandler != null) {
                streamHandler.error();
            }
            throw e;
        }
    }
}
