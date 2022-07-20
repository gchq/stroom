package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.event.ReceiveDataHelper;
import stroom.proxy.repo.CSVFormatter;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamProcessor;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Main entry point to handling proxy requests.
 * <p>
 * This class used the main context and forwards the request on to our
 * dynamic mini proxy.
 */
public class ProxyRequestHandler implements RequestHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyRequestHandler.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private final ReceiveStreamHandlers receiveStreamHandlerProvider;
    private final LogStream logStream;
    private final ProxyId proxyId;
    private final ReceiveDataHelper receiveDataHelper;

    @Inject
    public ProxyRequestHandler(final ReceiveStreamHandlers receiveStreamHandlerProvider,
                               final LogStream logStream,
                               final ProxyId proxyId,
                               final ReceiveDataHelper receiveDataHelper) {
        this.receiveStreamHandlerProvider = receiveStreamHandlerProvider;
        this.logStream = logStream;
        this.proxyId = proxyId;
        this.receiveDataHelper = receiveDataHelper;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        try {
            final String proxyId = receiveDataHelper.process(
                    request,
                    this::readInputStream,
                    this::readAndDropInputStream);
            response.setStatus(HttpStatus.SC_OK);

            LOGGER.debug(() -> "Writing proxy id attribute to response: " + proxyId);
            try (final PrintWriter writer = response.getWriter()) {
                writer.println(proxyId);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

        } catch (final StroomStreamException e) {
            e.sendErrorResponse(response);
        }
    }

    private void readInputStream(final HttpServletRequest request,
                                 final AttributeMap attributeMap,
                                 final String requestUuid) {
        final String proxyIdString = proxyId.getId();
        LOGGER.debug(() -> "Adding proxy id attribute: " + proxyIdString + ": " + requestUuid);
        attributeMap.put(proxyIdString, requestUuid);

        final long startTimeMs = System.currentTimeMillis();
        try (final ByteCountInputStream inputStream =
                new ByteCountInputStream(request.getInputStream())) {
            // Consume the data
            Metrics.measure("ProxyRequestHandler - handle", () -> {
                final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
                final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);
                receiveStreamHandlerProvider.handle(feedName, typeName, attributeMap, handler -> {
                    final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                            attributeMap,
                            handler,
                            new ProgressHandler("Receiving data"));
                    stroomStreamProcessor.processRequestHeader(request);
                    stroomStreamProcessor.processInputStream(inputStream, "");
                });
            });

            final long duration = System.currentTimeMillis() - startTimeMs;
            logStream.log(
                    RECEIVE_LOG,
                    attributeMap,
                    "RECEIVE",
                    request.getRequestURI(),
                    HttpStatus.SC_OK,
                    inputStream.getCount(),
                    duration);
        } catch (final IOException e) {
            throw StroomStreamException.create(e, attributeMap);
        }
    }

    private void readAndDropInputStream(final HttpServletRequest request,
                                        final AttributeMap attributeMap,
                                        final String requestUuid) {
        final long startTimeMs = System.currentTimeMillis();
        try (final ByteCountInputStream inputStream =
                new ByteCountInputStream(request.getInputStream())) {
            // Just read the stream in and ignore it
            final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];
            while (inputStream.read(buffer) >= 0) {
                // Ignore data.
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(new String(buffer));
                }
            }
            LOGGER.warn("\"Dropped\",{}", CSVFormatter.format(attributeMap));

            final long duration = System.currentTimeMillis() - startTimeMs;
            logStream.log(
                    RECEIVE_LOG,
                    attributeMap,
                    "DROP",
                    request.getRequestURI(),
                    HttpStatus.SC_OK,
                    inputStream.getCount(),
                    duration);
        } catch (final IOException e) {
            throw StroomStreamException.create(e, attributeMap);
        }
    }
}
