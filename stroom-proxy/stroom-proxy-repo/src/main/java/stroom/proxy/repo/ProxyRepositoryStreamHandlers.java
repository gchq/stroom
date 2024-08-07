package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.receive.common.StreamHandler;
import stroom.receive.common.StreamHandlers;
import stroom.receive.common.StroomStreamException;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

public class ProxyRepositoryStreamHandlers implements StreamHandlers {

    private final SequentialFileStore sequentialFileStore;

    @Inject
    public ProxyRepositoryStreamHandlers(final SequentialFileStore sequentialFileStore) {
        this.sequentialFileStore = sequentialFileStore;
    }

    @Override
    public void handle(final String feedName,
                       final String typeName,
                       final AttributeMap attributeMap,
                       final Consumer<StreamHandler> consumer) {
        if (feedName.isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }
        AttributeMapUtil.addFeedAndType(attributeMap, feedName, typeName);

        ProxyRepositoryStreamHandler streamHandler = null;
        try {
            streamHandler = new ProxyRepositoryStreamHandler(sequentialFileStore, attributeMap);
            consumer.accept(streamHandler);
            streamHandler.close();
        } catch (final RuntimeException e) {
            if (streamHandler != null) {
                streamHandler.error();
            }
            throw e;
        } catch (final IOException e) {
            if (streamHandler != null) {
                streamHandler.error();
            }
            throw new UncheckedIOException(e);
        }
    }
}
