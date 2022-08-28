package stroom.proxy.app.forwarder;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.StreamHandler;
import stroom.receive.common.StreamHandlers;
import stroom.receive.common.StroomStreamException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import javax.net.ssl.SSLSocketFactory;

public class ForwardHttpPostHandlers implements StreamHandlers {

    private final LogStream logStream;
    private final String userAgentString;
    private final ForwardHttpPostConfig config;
    private final SSLSocketFactory sslSocketFactory;

    public ForwardHttpPostHandlers(final LogStream logStream,
                                   final ForwardHttpPostConfig config,
                                   final String userAgentString,
                                   final SSLSocketFactory sslSocketFactory) {
        this.logStream = logStream;
        this.userAgentString = userAgentString;
        this.sslSocketFactory = sslSocketFactory;
        this.config = config;
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

        ForwardStreamHandler streamHandler = null;
        try {
            streamHandler = new ForwardStreamHandler(
                    logStream,
                    config,
                    sslSocketFactory,
                    userAgentString,
                    attributeMap);
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

    ForwardHttpPostConfig getConfig() {
        return config;
    }

    SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }
}
