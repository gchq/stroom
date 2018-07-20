package stroom.proxy.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datafeed.AttributeMapFilter;
import stroom.datafeed.AttributeMapFilterFactory;
import stroom.datafeed.RequestHandler;
import stroom.data.meta.api.AttributeMap;
import stroom.feed.AttributeMapUtil;
import stroom.datafeed.StroomStatusCode;
import stroom.datafeed.StroomStreamException;
import stroom.proxy.repo.StroomStreamProcessor;
import stroom.docref.DocRef;
import stroom.util.io.ByteCountInputStream;
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
    private final AttributeMapFilter attributeMapFilter;
    private final LogStream logStream;

    @Inject
    public ProxyRequestHandler(final ProxyRequestConfig proxyRequestConfig,
                               final MasterStreamHandlerFactory streamHandlerFactory,
                               final AttributeMapFilterFactory attributeMapFilterFactory,
                               final LogStream logStream) {
        this.streamHandlerFactory = streamHandlerFactory;
        this.attributeMapFilter = attributeMapFilterFactory.create(new DocRef("RuleSet", proxyRequestConfig.getReceiptPolicyUuid()));
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

        final long startTimeMs = System.currentTimeMillis();
        final AttributeMap attributeMap = AttributeMapUtil.create(request);

        try {
            try (final ByteCountInputStream inputStream = new ByteCountInputStream(request.getInputStream())) {
                // Test to see if we are going to accept this stream or drop the data.
                if (attributeMapFilter.filter(attributeMap)) {
                    // Send the data
                    final List<StreamHandler> handlers = streamHandlerFactory.addReceiveHandlers(new ArrayList<>());

                    try {
                        // Set the meta map for all handlers.
                        for (final StreamHandler streamHandler : handlers) {
                            streamHandler.setAttributeMap(attributeMap);
                        }

                        final byte[] buffer = BufferFactory.create();
                        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(attributeMap, handlers, buffer, "DataFeedServlet");

                        stroomStreamProcessor.processRequestHeader(request);

                        for (final StreamHandler streamHandler : handlers) {
                            streamHandler.handleHeader();
                        }

                        stroomStreamProcessor.process(request.getInputStream(), "");

                        for (final StreamHandler streamHandler : handlers) {
                            streamHandler.handleFooter();
                        }

                    } catch (final RuntimeException e) {
                        for (final StreamHandler streamHandler : handlers) {
                            try {
                                streamHandler.handleError();
                            } catch (final IOException | RuntimeException ex) {
                                LOGGER.error(ex.getMessage(), ex);
                            }
                        }

                        throw e;
                    }

                    final long duration = System.currentTimeMillis() - startTimeMs;
                    logStream.log(RECEIVE_LOG, attributeMap, "RECEIVE", request.getRequestURI(), returnCode, inputStream.getByteCount(), duration);

                } else {
                    // Just read the stream in and ignore it
                    final byte[] buffer = BufferFactory.create();
                    while (inputStream.read(buffer) >= 0) {
                        // Ignore data.
                    }
                    returnCode = HttpServletResponse.SC_OK;
                    LOGGER.warn("\"Dropped stream\",{}", CSVFormatter.format(attributeMap));

                    final long duration = System.currentTimeMillis() - startTimeMs;
                    logStream.log(RECEIVE_LOG, attributeMap, "DROP", request.getRequestURI(), returnCode, inputStream.getByteCount(), duration);
                }
            }
        } catch (final StroomStreamException e) {
            StroomStreamException.sendErrorResponse(response, e);
            returnCode = e.getStroomStatusCode().getCode();

            LOGGER.warn("\"handleException()\",{},\"{}\"", CSVFormatter.format(attributeMap), CSVFormatter.escape(e.getMessage()));

            final long duration = System.currentTimeMillis() - startTimeMs;
            if (StroomStatusCode.RECEIPT_POLICY_SET_TO_REJECT_DATA.equals(e.getStroomStatusCode())) {
                logStream.log(RECEIVE_LOG, attributeMap, "REJECT", request.getRequestURI(), returnCode, -1, duration);
            } else {
                logStream.log(RECEIVE_LOG, attributeMap, "ERROR", request.getRequestURI(), returnCode, -1, duration);
            }

        } catch (final IOException | RuntimeException e) {
            StroomStreamException.sendErrorResponse(response, e);
            returnCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

            LOGGER.error("\"handleException()\",{}", CSVFormatter.format(attributeMap), e);
            final long duration = System.currentTimeMillis() - startTimeMs;
            logStream.log(RECEIVE_LOG, attributeMap, "ERROR", request.getRequestURI(), returnCode, -1, duration);
        }

        response.setStatus(returnCode);
    }
}
