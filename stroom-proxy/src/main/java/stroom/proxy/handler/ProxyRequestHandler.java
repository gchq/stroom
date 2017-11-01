package stroom.proxy.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datafeed.server.RequestHandler;
import stroom.feed.MetaMap;
import stroom.feed.MetaMapFactory;
import stroom.feed.StroomStreamException;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.util.io.ByteCountInputStream;
import stroom.util.shared.ModelStringUtil;
import stroom.util.thread.BufferFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main entry point to handling proxy requests.
 * <p>
 * This class used the main context and forwards the request on to our
 * dynamic mini proxy.
 */
public class ProxyRequestHandler implements RequestHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRequestHandler.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");
    private static final AtomicInteger concurrentRequestCount = new AtomicInteger(0);

    private final MasterStreamHandlerFactory streamHandlerFactory;
    private final LogStream logStream;

    @Inject
    public ProxyRequestHandler(final MasterStreamHandlerFactory streamHandlerFactory,
                               final LogStream logStream) {
        this.streamHandlerFactory = streamHandlerFactory;
        this.logStream = logStream;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        concurrentRequestCount.incrementAndGet();
        try {
            stream(request, response);
        } finally {
            concurrentRequestCount.decrementAndGet();
        }
    }

    private void stream(final HttpServletRequest request, final HttpServletResponse response) {
        int returnCode = HttpServletResponse.SC_OK;

        try (final ByteCountInputStream inputStream = new ByteCountInputStream(request.getInputStream())) {
            final long startTime = System.currentTimeMillis();

            // Send the data
            final List<StreamHandler> handlers = streamHandlerFactory.addReceiveHandlers(new ArrayList<>());

            final MetaMap metaMap = MetaMapFactory.create(request);

            // Set the meta map for all handlers.
            for (final StreamHandler streamHandler : handlers) {
                streamHandler.setMetaMap(metaMap);
            }
            try {
                final byte[] buffer = BufferFactory.create();
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(metaMap, handlers, buffer, "DataFeedServlet");

                stroomStreamProcessor.processRequestHeader(request);

                for (final StreamHandler streamHandler : handlers) {
                    streamHandler.handleHeader();
                }

                stroomStreamProcessor.process(request.getInputStream(), "");

                for (final StreamHandler streamHandler : handlers) {
                    streamHandler.handleFooter();
                }
                response.setStatus(returnCode);

            } catch (final Exception ex) {
                try {
                    boolean error = true;
                    if (ex instanceof DropStreamException) {
                        // Just read the stream in and ignore it

                        final byte[] buffer = BufferFactory.create();
                        while (inputStream.read(buffer) >= 0) ;
                        response.setStatus(HttpServletResponse.SC_OK);
                        LOGGER.warn("\"handleException() - Dropped stream\",{}", CSVFormatter.format(metaMap));
                        error = false;
                    } else {
                        if (ex instanceof StroomStreamException) {
                            LOGGER.warn("\"handleException()\",{},\"{}\"", CSVFormatter.format(metaMap),
                                    CSVFormatter.escape(ex.getMessage()));
                        } else {
                            LOGGER.error("\"handleException()\",{}", CSVFormatter.format(metaMap), ex);
                        }
                    }
                    for (final StreamHandler streamHandler : handlers) {
                        try {
                            streamHandler.handleError();
                        } catch (final Exception ex1) {
                            LOGGER.error("\"handleException\"", ex1);
                        }
                    }
                    // Dropped stream errors are handled like all OK
                    if (error) {
                        returnCode = StroomStreamException.sendErrorResponse(response, ex);
                    } else {

                    }
                } catch (final IOException ioEx) {
                    throw new RuntimeException(ioEx);
                }
            } finally {
                if (LOGGER.isInfoEnabled()) {
                    final long duration = System.currentTimeMillis() - startTime;
                    logStream.log(RECEIVE_LOG, metaMap, "RECEIVE", request.getRemoteAddr(), returnCode, -1, duration);


                    LOGGER.info("\"doPost() - Took {} to process (concurrentRequestCount={}) {}\",{}",
                            ModelStringUtil.formatDurationString(duration), concurrentRequestCount, returnCode,
                            CSVFormatter.format(metaMap));
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } finally {
            response.setStatus(returnCode);
        }
    }
}
