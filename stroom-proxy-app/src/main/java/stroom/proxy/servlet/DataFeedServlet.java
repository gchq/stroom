package stroom.proxy.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.feed.StroomStreamException;
import stroom.proxy.datafeed.CSVFormatter;
import stroom.proxy.datafeed.ProxyHandlerFactory;
import stroom.proxy.handler.DropStreamException;
import stroom.proxy.handler.RequestHandler;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.util.io.CloseableUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.thread.BufferFactory;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main entry point to handling proxy requests.
 * <p>
 * This class used the main spring context and forwards the request on to our
 * dynamic mini proxy.
 */
public class DataFeedServlet extends HttpServlet {
    private static Logger LOGGER = LoggerFactory.getLogger(DataFeedServlet.class);

    private final ProxyHandlerFactory proxyHandlerFactory;

    private static AtomicInteger concurrentRequestCount = new AtomicInteger(0);

    private int returnCode = HttpServletResponse.SC_OK;

    @Inject
    public DataFeedServlet(final ProxyHandlerFactory proxyHandlerFactory) {
        this.proxyHandlerFactory = proxyHandlerFactory;
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        // Send the data
        final List<RequestHandler> handlers = proxyHandlerFactory.getIncomingRequestHandlerList();

        concurrentRequestCount.incrementAndGet();
        final long startTime = System.currentTimeMillis();

        final MetaMap metaMap = new MetaMap();
        try {
            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(metaMap, handlers,
                    BufferFactory.create(), "DataFeedRequestHandler");

            stroomStreamProcessor.processRequestHeader(req);

            for (final RequestHandler requestHandler : handlers) {
                requestHandler.handleHeader();
            }

            stroomStreamProcessor.process(req.getInputStream(), "");

            for (final RequestHandler requestHandler : handlers) {
                requestHandler.handleFooter();
            }
            resp.setStatus(HttpServletResponse.SC_OK);
        } catch (final Exception ex) {
            try {
                boolean error = true;
                if (ex instanceof DropStreamException) {
                    // Just read the stream in and ignore it
                    final InputStream inputStream = req.getInputStream();
                    final byte[] buffer = BufferFactory.create();
                    while (inputStream.read(buffer) >= 0) ;
                    CloseableUtil.close(inputStream);
                    resp.setStatus(HttpServletResponse.SC_OK);
                    LOGGER.warn("\"handleException() - Dropped stream\",%s", CSVFormatter.format(metaMap));
                    error = false;
                } else {
                    if (ex instanceof StroomStreamException) {
                        LOGGER.warn("\"handleException()\",%s,\"%s\"", CSVFormatter.format(metaMap),
                                CSVFormatter.escape(ex.getMessage()));
                    } else {
                        LOGGER.error("\"handleException()\",%s", CSVFormatter.format(metaMap), ex);
                    }
                }
                for (final RequestHandler requestHandler : handlers) {
                    try {
                        requestHandler.handleError();
                    } catch (final Exception ex1) {
                        LOGGER.error("\"handleException\"", ex1);
                    }
                }
                // Dropped stream errors are handled like all OK
                if (error) {
                    returnCode = StroomStreamException.sendErrorResponse(resp, ex);
                } else {
                    resp.setStatus(HttpServletResponse.SC_OK);
                }
            } catch (final IOException ioEx) {
                throw new RuntimeException(ioEx);
            }
        }

        if (LOGGER.isInfoEnabled()) {
            final long time = System.currentTimeMillis() - startTime;
            LOGGER.info("\"doPost() - Took %s to process (concurrentRequestCount=%s) %s\",%s",
                    ModelStringUtil.formatDurationString(time), concurrentRequestCount, returnCode,
                    CSVFormatter.format(metaMap));
        }
        concurrentRequestCount.decrementAndGet();
    }
}
