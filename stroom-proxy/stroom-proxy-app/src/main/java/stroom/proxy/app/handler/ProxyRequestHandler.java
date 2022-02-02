package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.StreamHandler;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapValidator;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamProcessor;
import stroom.security.api.RequestAuthenticator;
import stroom.security.api.UserIdentity;
import stroom.util.io.BufferFactory;
import stroom.util.io.ByteCountInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

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
    private final BufferFactory bufferFactory;
    private final RequestAuthenticator requestAuthenticator;
    private final Provider<ProxyRequestConfig> proxyRequestConfigProvider;

    @Inject
    public ProxyRequestHandler(final MasterStreamHandlerFactory streamHandlerFactory,
                               final AttributeMapFilterFactory attributeMapFilterFactory,
                               final LogStream logStream,
                               final BufferFactory bufferFactory,
                               final RequestAuthenticator requestAuthenticator,
                               final Provider<ProxyRequestConfig> proxyRequestConfigProvider) {
        this.streamHandlerFactory = streamHandlerFactory;
        this.logStream = logStream;
        this.bufferFactory = bufferFactory;
        this.attributeMapFilter = attributeMapFilterFactory.create();
        this.requestAuthenticator = requestAuthenticator;
        this.proxyRequestConfigProvider = proxyRequestConfigProvider;
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
        final ProxyRequestConfig proxyRequestConfig = proxyRequestConfigProvider.get();
        int returnCode = HttpServletResponse.SC_OK;

        final long startTimeMs = System.currentTimeMillis();
        final AttributeMap attributeMap = AttributeMapUtil.create(request);
        final String authorisationHeader = attributeMap.get(HttpHeaders.AUTHORIZATION);

        // If token authentication is required but no token is supplied then error.
        if (proxyRequestConfig.isRequireTokenAuthentication() &&
                (authorisationHeader == null || authorisationHeader.isBlank())) {
            throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_REQUIRED, attributeMap);
        }

        // Authenticate the request token if there is one.
        final Optional<UserIdentity> optionalUserIdentity = requestAuthenticator.authenticate(request);

        // Add the user identified in the token (if present) to the attribute map.
        optionalUserIdentity
                .map(UserIdentity::getId)
                .ifPresent(id -> attributeMap.put("UploadUser", id));

        if (proxyRequestConfig.isRequireTokenAuthentication() && optionalUserIdentity.isEmpty()) {
            // If token authentication is required, but we could not verify the token then error.
            throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_NOT_AUTHORISED, attributeMap);

        } else {
            // Remove authorization header from attributes.
            attributeMap.remove(HttpHeaders.AUTHORIZATION);

            try {
                // Validate the supplied attributes.
                AttributeMapValidator.validate(
                        attributeMap,
                        () -> proxyRequestConfigProvider.get().getMetaTypes());

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

                            final byte[] buffer = bufferFactory.create();
                            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                                    attributeMap, handlers, buffer, "DataFeedServlet");

                            stroomStreamProcessor.processRequestHeader(request);

                            for (final StreamHandler streamHandler : handlers) {
                                streamHandler.handleHeader();
                            }

                            stroomStreamProcessor.process(inputStream, "");

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
                        logStream.log(
                                RECEIVE_LOG,
                                attributeMap,
                                "RECEIVE",
                                request.getRequestURI(),
                                returnCode,
                                inputStream.getCount(),
                                duration);

                    } else {
                        // Just read the stream in and ignore it
                        final byte[] buffer = bufferFactory.create();
                        while (inputStream.read(buffer) >= 0) {
                            // Ignore data.
                        }
                        returnCode = HttpServletResponse.SC_OK;
                        LOGGER.warn("\"Dropped stream\",{}", CSVFormatter.format(attributeMap));

                        final long duration = System.currentTimeMillis() - startTimeMs;
                        logStream.log(
                                RECEIVE_LOG,
                                attributeMap,
                                "DROP",
                                request.getRequestURI(),
                                returnCode,
                                inputStream.getCount(),
                                duration);
                    }
                }
            } catch (final StroomStreamException e) {
                StroomStreamException.sendErrorResponse(request, response, e);
                returnCode = e.getStroomStatusCode().getCode();

                LOGGER.warn("\"handleException()\",{},\"{}\"",
                        CSVFormatter.format(attributeMap),
                        CSVFormatter.escape(e.getMessage()));

                final long duration = System.currentTimeMillis() - startTimeMs;
                if (StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA.equals(e.getStroomStatusCode())) {
                    logStream.log(
                            RECEIVE_LOG,
                            attributeMap,
                            "REJECT",
                            request.getRequestURI(),
                            returnCode,
                            -1,
                            duration);
                } else {
                    logStream.log(
                            RECEIVE_LOG,
                            attributeMap,
                            "ERROR",
                            request.getRequestURI(),
                            returnCode,
                            -1,
                            duration);
                }

            } catch (final IOException | RuntimeException e) {
                RuntimeException unwrappedException = StroomStreamException.sendErrorResponse(request, response, e);
                returnCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

                LOGGER.error("\"handleException()\",{}", CSVFormatter.format(attributeMap), unwrappedException);
                final long duration = System.currentTimeMillis() - startTimeMs;
                logStream.log(
                        RECEIVE_LOG,
                        attributeMap,
                        "ERROR",
                        request.getRequestURI(),
                        returnCode,
                        -1,
                        duration);
            }

            response.setStatus(returnCode);
        }
    }
}
