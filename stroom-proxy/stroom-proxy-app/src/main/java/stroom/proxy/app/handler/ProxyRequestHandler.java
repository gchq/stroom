package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.ReceiveDataConfig;
import stroom.proxy.repo.CSVFormatter;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapValidator;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamProcessor;
import stroom.receive.common.StroomStreamStatus;
import stroom.security.api.RequestAuthenticator;
import stroom.security.api.UserIdentity;
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
import java.util.Optional;
import java.util.UUID;
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

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyRequestHandler.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");
    private static final AtomicInteger concurrentRequestCount = new AtomicInteger(0);

    private final ReceiveStreamHandlers receiveStreamHandlerProvider;
    private final AttributeMapFilter attributeMapFilter;
    private final LogStream logStream;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final RequestAuthenticator requestAuthenticator;
    private final ProxyId proxyId;

    @Inject
    public ProxyRequestHandler(final ReceiveStreamHandlers receiveStreamHandlerProvider,
                               final AttributeMapFilterFactory attributeMapFilterFactory,
                               final LogStream logStream,
                               final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                               final RequestAuthenticator requestAuthenticator,
                               final ProxyId proxyId) {
        this.receiveStreamHandlerProvider = receiveStreamHandlerProvider;
        this.logStream = logStream;
        this.attributeMapFilter = attributeMapFilterFactory.create();
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.requestAuthenticator = requestAuthenticator;
        this.proxyId = proxyId;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        final long startTimeMs = System.currentTimeMillis();

        concurrentRequestCount.incrementAndGet();
        try {
            final String proxyId = stream(request);
            response.setStatus(HttpStatus.SC_OK);

            LOGGER.debug(() -> "Writing proxy id attribute to response: " + proxyId);
            try (final PrintWriter writer = response.getWriter()) {
                writer.println(proxyId);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

        } catch (final Throwable e) {
            final StroomStreamException stroomStreamException;
            if (e instanceof StroomStreamException) {
                stroomStreamException = (StroomStreamException) e;
            } else {
                stroomStreamException = StroomStreamException.create(e, AttributeMapUtil.create(request));
            }

            final StroomStreamStatus status = stroomStreamException.getStroomStreamStatus();
            LOGGER.debug("\"handleException()\",{},\"{}\"",
                    CSVFormatter.format(status.getAttributeMap()),
                    CSVFormatter.escape(stroomStreamException.getMessage()));

            final long duration = System.currentTimeMillis() - startTimeMs;
            if (StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA.equals(status.getStroomStatusCode())) {
                logStream.log(
                        RECEIVE_LOG,
                        status.getAttributeMap(),
                        "REJECT",
                        request.getRequestURI(),
                        status.getStroomStatusCode().getCode(),
                        -1,
                        duration);
            } else {
                logStream.log(
                        RECEIVE_LOG,
                        status.getAttributeMap(),
                        "ERROR",
                        request.getRequestURI(),
                        status.getStroomStatusCode().getCode(),
                        -1,
                        duration);
            }

            stroomStreamException.sendErrorResponse(response);

        } finally {
            concurrentRequestCount.decrementAndGet();
        }
    }

    private ReceiveDataConfig getConfig() {
        return receiveDataConfigProvider.get();
    }

    private String stream(final HttpServletRequest request) {
        final long startTimeMs = System.currentTimeMillis();

        return Metrics.measure("ProxyRequestHandler - stream", () -> {
            final ReceiveDataConfig receiveDataConfig = getConfig();
            final AttributeMap attributeMap = AttributeMapUtil.create(request);

            // Authorise request.
            authorise(request, attributeMap);

            // Create a new proxy id for the stream and report it back to the sender,
            final String proxyId = addProxyId(attributeMap);

            Metrics.measure("ProxyRequestHandler - handle1", () -> {
                // Validate the supplied attributes.
                AttributeMapValidator.validate(
                        attributeMap,
                        receiveDataConfig::getMetaTypes);

                final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
                if (feedName == null || feedName.trim().isEmpty()) {
                    throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
                }
                final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);

                try (final ByteCountInputStream inputStream =
                        new ByteCountInputStream(request.getInputStream())) {
                    // Test to see if we are going to accept this stream or drop the data.
                    if (attributeMapFilter.filter(attributeMap)) {
                        // Consume the data
                        Metrics.measure("ProxyRequestHandler - handle", () ->
                                receiveStreamHandlerProvider.handle(feedName, typeName, attributeMap, handler -> {
                                    final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                                            attributeMap,
                                            handler,
                                            new ProgressHandler("Receiving data"));
                                    stroomStreamProcessor.processRequestHeader(request);
                                    stroomStreamProcessor.processInputStream(inputStream, "");
                                }));

                        final long duration = System.currentTimeMillis() - startTimeMs;
                        logStream.log(
                                RECEIVE_LOG,
                                attributeMap,
                                "RECEIVE",
                                request.getRequestURI(),
                                HttpServletResponse.SC_OK,
                                inputStream.getCount(),
                                duration);

                    } else {
                        // Just read the stream in and ignore it
                        final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];
                        while (inputStream.read(buffer) >= 0) {
                            // Ignore data.
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace(new String(buffer));
                            }
                        }
                        LOGGER.warn("\"Dropped stream\",{}", CSVFormatter.format(attributeMap));

                        final long duration = System.currentTimeMillis() - startTimeMs;
                        logStream.log(
                                RECEIVE_LOG,
                                attributeMap,
                                "DROP",
                                request.getRequestURI(),
                                HttpServletResponse.SC_OK,
                                inputStream.getCount(),
                                duration);
                    }
                } catch (final IOException e) {
                    throw StroomStreamException.create(e, attributeMap);
                }
            });

            return proxyId;
        });
    }

    private String addProxyId(final AttributeMap attributeMap) {
        final String attributeKey = proxyId.getId();
        final String attributeName = UUID.randomUUID().toString();
        attributeMap.put(attributeKey, attributeName);
        final String idProperty = attributeKey + ": " + attributeName;
        LOGGER.debug(() -> "Adding proxy id attribute: " + idProperty);
        return idProperty;
    }

    private void authorise(final HttpServletRequest request, final AttributeMap attributeMap) {
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();
        final String authorisationHeader = attributeMap.get(HttpHeaders.AUTHORIZATION);

        // If token authentication is required but no token is supplied then error.
        if (receiveDataConfig.isRequireTokenAuthentication() &&
                (authorisationHeader == null || authorisationHeader.isBlank())) {
            throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_REQUIRED, attributeMap);
        }

        // Authenticate the request token if there is one.
        final Optional<UserIdentity> optionalUserIdentity = requestAuthenticator.authenticate(request);

        // Add the user identified in the token (if present) to the attribute map.
        optionalUserIdentity
                .map(UserIdentity::getId)
                .ifPresent(id -> attributeMap.put("UploadUser", id));

        if (receiveDataConfig.isRequireTokenAuthentication() && optionalUserIdentity.isEmpty()) {
            // If token authentication is required, but we could not verify the token then error.
            throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_NOT_AUTHORISED, attributeMap);

        } else {
            // Remove authorization header from attributes.
            attributeMap.remove(HttpHeaders.AUTHORIZATION);
        }
    }
}
