package stroom.proxy.app.forwarder;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.ProxyRepositoryStreamHandler;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.receive.common.StreamHandler;
import stroom.receive.common.StreamHandlers;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.PathCreator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

public class ForwardFileHandlers implements StreamHandlers {

    private final SequentialFileStore sequentialFileStore;

    public ForwardFileHandlers(final ForwardFileConfig forwardFileConfig,
                               final PathCreator pathCreator,
                               final QueueMonitors queueMonitors,
                               final FileStores fileStores,
                               final int order,
                               final String name) {
        this.sequentialFileStore = new SequentialFileStore(() ->
                pathCreator.toAppPath(forwardFileConfig.getPath()), queueMonitors, fileStores, order, name);
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

    public SequentialFileStore getSequentialFileStore() {
        return sequentialFileStore;
    }
}
