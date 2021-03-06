package stroom.proxy.app.forwarder;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.StreamHandler;
import stroom.receive.common.StreamHandlers;
import stroom.receive.common.StroomStreamException;
import stroom.util.cert.SSLUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import javax.net.ssl.SSLSocketFactory;

public class ForwardStreamHandlers implements StreamHandlers {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForwardStreamHandlers.class);

    private final LogStream logStream;
    private final String userAgentString;
    private final ForwardDestinationConfig config;
    private final SSLSocketFactory sslSocketFactory;

    public ForwardStreamHandlers(final LogStream logStream,
                                 final String userAgentString,
                                 final ForwardDestinationConfig config) {
        this.logStream = logStream;
        this.userAgentString = userAgentString;

        LOGGER.info("Configuring SSLSocketFactory for URL {}", config.getForwardUrl());
        if (config.getSslConfig() != null) {
            sslSocketFactory = SSLUtil.createSslSocketFactory(config.getSslConfig());
        } else {
            sslSocketFactory = null;
        }

        this.config = config;
    }

    @Override
    public void handle(final String feedName,
                       final String typeName,
                       final AttributeMap attributeMap,
                       final Consumer<StreamHandler> consumer) {
        if (feedName.isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED);
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

    ForwardDestinationConfig getConfig() {
        return config;
    }

    SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }

}
