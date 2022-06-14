package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.ReceiveDataConfig;
import stroom.proxy.app.handler.AttributeMapFilterFactory;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.repo.CSVFormatter;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapValidator;
import stroom.receive.common.StroomStreamException;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

public class ReceiveDataHelper {

    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataHelper.class);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final RequestAuthenticator requestAuthenticator;
    private final AttributeMapFilter attributeMapFilter;
    private final ProxyId proxyId;
    private final LogStream logStream;

    @Inject
    public ReceiveDataHelper(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                             final RequestAuthenticator requestAuthenticator,
                             final AttributeMapFilterFactory attributeMapFilterFactory,
                             final ProxyId proxyId,
                             final LogStream logStream) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.requestAuthenticator = requestAuthenticator;
        this.attributeMapFilter = attributeMapFilterFactory.create();
        this.proxyId = proxyId;
        this.logStream = logStream;
    }

    public String process(final HttpServletRequest request,
                          final Consumer<AttributeMap> consumer) {
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();

        final long startTimeMs = System.currentTimeMillis();
        final AttributeMap attributeMap = AttributeMapUtil.create(request);

        // Authorise request.
        authorise(request, attributeMap);

        // Create a new proxy id for the stream and report it back to the sender,
        final String idProperty = addProxyId(attributeMap);

        Metrics.measure("ReceiveDataHelper", () -> {
            try {
                // Validate the supplied attributes.
                AttributeMapValidator.validate(
                        attributeMap,
                        receiveDataConfig::getMetaTypes);

                final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
                if (feedName == null || feedName.trim().isEmpty()) {
                    throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
                }
//                final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);

                try (final ByteCountInputStream inputStream =
                        new ByteCountInputStream(request.getInputStream())) {
                    // Test to see if we are going to accept this stream or drop the data.
                    if (attributeMapFilter.filter(attributeMap)) {
                        // Consume the data
                        consumer.accept(attributeMap);

                        final long duration = System.currentTimeMillis() - startTimeMs;
                        logStream.log(
                                RECEIVE_LOG,
                                attributeMap,
                                "RECEIVE",
                                request.getRequestURI(),
                                HttpStatus.SC_OK,
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
                        LOGGER.warn("\"Dropped event\",{}", CSVFormatter.format(attributeMap));

                        final long duration = System.currentTimeMillis() - startTimeMs;
                        logStream.log(
                                RECEIVE_LOG,
                                attributeMap,
                                "DROP",
                                request.getRequestURI(),
                                HttpStatus.SC_OK,
                                inputStream.getCount(),
                                duration);
                    }
                }
            } catch (final StroomStreamException e) {
                final StroomStreamStatus status = e.getStroomStreamStatus();
                final int returnCode = status.getStroomStatusCode().getCode();

                LOGGER.warn("\"handleException()\",{},\"{}\"",
                        CSVFormatter.format(attributeMap),
                        CSVFormatter.escape(e.getMessage()));

                final long duration = System.currentTimeMillis() - startTimeMs;
                if (StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA.equals(status.getStroomStatusCode())) {
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

                throw e;

            } catch (final IOException | RuntimeException e) {
                final StroomStreamException stroomStreamException =
                        new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR,
                                attributeMap,
                                e.getMessage());

                LOGGER.error("\"handleException()\",{}", CSVFormatter.format(attributeMap), stroomStreamException);
                final long duration = System.currentTimeMillis() - startTimeMs;
                logStream.log(
                        RECEIVE_LOG,
                        attributeMap,
                        "ERROR",
                        request.getRequestURI(),
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        -1,
                        duration);

                throw stroomStreamException;
            }
        });

        return idProperty;
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
