package stroom.receive.common;

import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.meta.statistics.api.MetaStatistics;
import stroom.proxy.StroomStatusCode;

import java.util.Optional;
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
    public void handle(final AttributeMap attributeMap, final Consumer<StreamHandler> consumer) {
        StreamTargetStreamHandler streamHandler = null;
        try {
            final String feedName = Optional.ofNullable(attributeMap.get(StandardHeaderArguments.FEED))
                    .map(String::trim)
                    .orElse("");
            if (feedName.isEmpty()) {
                throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED);
            }

            // Get the type name from the header arguments if supplied.
            String typeName = Optional.ofNullable(attributeMap.get(StandardHeaderArguments.TYPE))
                    .map(String::trim)
                    .orElse("");
            if (typeName.isEmpty()) {
                // If no type name is supplied then get the default for the feed.
                typeName = feedProperties.getStreamTypeName(feedName);
                attributeMap.put(StandardHeaderArguments.TYPE, typeName);
            }

            // Validate the data type name.
            if (!metaService.getTypes().contains(typeName)) {
                throw new StroomStreamException(StroomStatusCode.UNEXPECTED_DATA_TYPE);
            }

            streamHandler = new StreamTargetStreamHandler(
                    store,
                    feedProperties,
                    metaDataStatistics,
                    feedName,
                    typeName,
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
