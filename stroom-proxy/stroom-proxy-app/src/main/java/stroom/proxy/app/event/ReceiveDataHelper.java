package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.handler.AttributeMapFilterFactory;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.repo.CSVFormatter;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapValidator;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.RequestAuthenticator;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamStatus;
import stroom.security.api.UserIdentity;
import stroom.util.cert.CertificateExtractor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ReceiveDataHelper {

    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataHelper.class);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final RequestAuthenticator requestAuthenticator;
    private final AttributeMapFilter attributeMapFilter;
    private final CertificateExtractor certificateExtractor;
    private final ProxyId proxyId;
    private final LogStream logStream;

    @Inject
    public ReceiveDataHelper(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                             final RequestAuthenticator requestAuthenticator,
                             final AttributeMapFilterFactory attributeMapFilterFactory,
                             final CertificateExtractor certificateExtractor,
                             final ProxyId proxyId,
                             final LogStream logStream) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.requestAuthenticator = requestAuthenticator;
        this.attributeMapFilter = attributeMapFilterFactory.create();
        this.certificateExtractor = certificateExtractor;
        this.proxyId = proxyId;
        this.logStream = logStream;
    }

    public String process(final HttpServletRequest request,
                          final Handler consumeHandler,
                          final Handler dropHandler) throws StroomStreamException {
        final long startTimeMs = System.currentTimeMillis();

        // Create attribute map from headers.
        final AttributeMap attributeMap = AttributeMapUtil.create(request, certificateExtractor);

        // Create a new proxy id for the request so we can track progress and report back the UUID to the sender,
        final String requestUuid = UUID.randomUUID().toString();
        final String proxyIdString = proxyId.getId();
        final String result = proxyIdString + ": " + requestUuid;

        try {
            Metrics.measure("ProxyRequestHandler - stream", () -> {
                final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();

                // Authorise request.
                final UserIdentity userIdentity = requestAuthenticator.authenticate(request, attributeMap);

                Metrics.measure("ProxyRequestHandler - handle1", () -> {
                    // Validate the supplied attributes.
                    AttributeMapValidator.validate(
                            attributeMap,
                            receiveDataConfig::getMetaTypes);

                    // Test to see if we are going to accept this stream or drop the data.
                    if (attributeMapFilter.filter(attributeMap, userIdentity)) {
                        consumeHandler.handle(request, attributeMap, requestUuid);

                    } else {
                        dropHandler.handle(request, attributeMap, requestUuid);
                    }
                });
            });
        } catch (final Throwable e) {
            // Add the proxy request id to help error diagnosis.
            attributeMap.put(proxyIdString, requestUuid);

            final StroomStreamException stroomStreamException = StroomStreamException.create(
                    e, AttributeMapUtil.create(request, certificateExtractor));

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
                        duration,
                        e.getMessage());
            } else {
                logStream.log(
                        RECEIVE_LOG,
                        status.getAttributeMap(),
                        "ERROR",
                        request.getRequestURI(),
                        status.getStroomStatusCode().getCode(),
                        -1,
                        duration,
                        e.getMessage());
            }

            throw stroomStreamException;
        }

        return result;
    }

    public interface Handler {

        void handle(HttpServletRequest request,
                    AttributeMap attributeMap,
                    String requestUuid);
    }
}
