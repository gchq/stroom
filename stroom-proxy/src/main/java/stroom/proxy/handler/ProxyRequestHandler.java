package stroom.proxy.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datafeed.server.RequestHandler;
import stroom.feed.MetaMap;
import stroom.feed.MetaMapFactory;
import stroom.feed.StroomStreamException;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.util.io.CloseableUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.thread.BufferFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
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
    private static final AtomicInteger concurrentRequestCount = new AtomicInteger(0);

    private final StreamHandlerFactoryImpl proxyHandlerFactory;

    @Inject
    public ProxyRequestHandler(final StreamHandlerFactoryImpl proxyHandlerFactory) {
        this.proxyHandlerFactory = proxyHandlerFactory;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        concurrentRequestCount.incrementAndGet();
        try {
            int returnCode = HttpServletResponse.SC_OK;

            // Send the data
            final List<StreamHandler> handlers = proxyHandlerFactory.createIncomingHandlers();

            final long startTime = System.currentTimeMillis();

            final MetaMap metaMap = MetaMapFactory.create(request);
            try {
                final byte[] buffer = BufferFactory.create();
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(metaMap, handlers,
                        buffer, "DataFeedServlet");

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
                        final InputStream inputStream = request.getInputStream();
                        final byte[] buffer = BufferFactory.create();
                        while (inputStream.read(buffer) >= 0) ;
                        CloseableUtil.close(inputStream);
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
                        response.setStatus(returnCode);
                    }
                } catch (final IOException ioEx) {
                    throw new RuntimeException(ioEx);
                }
            } finally {
                if (LOGGER.isInfoEnabled()) {
                    final long time = System.currentTimeMillis() - startTime;
                    LOGGER.info("\"doPost() - Took {} to process (concurrentRequestCount={}) {}\",{}",
                            ModelStringUtil.formatDurationString(time), concurrentRequestCount, returnCode,
                            CSVFormatter.format(metaMap));
                }
            }
        } finally {
            concurrentRequestCount.decrementAndGet();
        }
    }
}
