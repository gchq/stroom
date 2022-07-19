package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
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
                           final Handler consumeHandler,
                           final Handler dropHandler) throws StroomStreamException {
        final long startTimeMs = System.currentTimeMillis();

        // Create attribute map from headers.
        final AttributeMap attributeMap = AttributeMapUtil.create(request);

        // Create a new proxy id for the request so we can track progress and report back the UUID to the sender,
        final String requestUuid = UUID.randomUUID().toString();
        final String proxyIdString = proxyId.getId();
        final String result = proxyIdString + ": " + requestUuid;

        try {
            Metrics.measure("ProxyRequestHandler - stream", () -> {
                final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();

                // Authorise request.
                authorise(request, attributeMap);

                Metrics.measure("ProxyRequestHandler - handle1", () -> {
                    // Validate the supplied attributes.
                    AttributeMapValidator.validate(
                            attributeMap,
                            receiveDataConfig::getMetaTypes);

                    // Test to see if we are going to accept this stream or drop the data.
                    if (attributeMapFilter.filter(attributeMap)) {
                        consumeHandler.handle(request, attributeMap, requestUuid);

                    } else {
                        dropHandler.handle(request, attributeMap, requestUuid);
                    }
                });
            });
        } catch (final Throwable e) {
            // Add the proxy request id to help error diagnosis.
            attributeMap.put(proxyIdString, requestUuid);

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

            throw stroomStreamException;
        }

        return result;
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

    public interface Handler {

        void handle(HttpServletRequest request,
                    AttributeMap attributeMap,
                    String requestUuid);
    }
}
