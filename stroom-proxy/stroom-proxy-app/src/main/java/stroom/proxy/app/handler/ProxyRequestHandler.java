package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.DataReceiptMetrics;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.RequestAuthenticator;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.CommonSecurityContext;
import stroom.security.api.UserIdentity;
import stroom.util.cert.CertificateExtractor;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.HttpStatus;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Main entry point to handling datafeed requests into Stroom-Proxy.
 * Stroom has its own handler.
 */
public class ProxyRequestHandler implements RequestHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyRequestHandler.class);
    private static final String ZERO_CONTENT = "0";

    private final RequestAuthenticator requestAuthenticator;
    private final CertificateExtractor certificateExtractor;
    private final ReceiverFactory receiverFactory;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final DataReceiptMetrics dataReceiptMetrics;
    private final CommonSecurityContext commonSecurityContext;

    @Inject
    public ProxyRequestHandler(final RequestAuthenticator requestAuthenticator,
                               final CertificateExtractor certificateExtractor,
                               final ReceiverFactory receiverFactory,
                               final ReceiptIdGenerator receiptIdGenerator,
                               final DataReceiptMetrics dataReceiptMetrics,
                               final CommonSecurityContext commonSecurityContext) {
        this.requestAuthenticator = requestAuthenticator;
        this.certificateExtractor = certificateExtractor;
        this.receiverFactory = receiverFactory;
        this.receiptIdGenerator = receiptIdGenerator;
        this.dataReceiptMetrics = dataReceiptMetrics;
        this.commonSecurityContext = commonSecurityContext;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        dataReceiptMetrics.timeRequest(() -> {
            doHandle(request, response);
        });
    }

    private void doHandle(final HttpServletRequest request, final HttpServletResponse response) {
        try {
            final Instant receiveTime = Instant.now();

            // Create a new proxy id for the request, so we can track progress of the stream
            // through the various proxies and into stroom and report back the ID to the sender,
            final UniqueId receiptId = receiptIdGenerator.generateId();

            // Create attribute map from headers.
            final AttributeMap attributeMap = AttributeMapUtil.create(
                    request,
                    certificateExtractor,
                    receiveTime,
                    receiptId);

            LOGGER.debug(() -> LogUtil.message(
                    "handle() - requestUri: {}, remoteHost/Addr: {}, attributeMap: {}, ",
                    request.getRequestURI(),
                    Objects.requireNonNullElseGet(
                            request.getRemoteHost(),
                            request::getRemoteAddr),
                    attributeMap));

            // Authorise request.
            final UserIdentity userIdentity = requestAuthenticator.authenticate(request, attributeMap);

            LOGGER.debug("handle() - userIdentity: {}", userIdentity);

            // Treat differently depending on compression type.
            final String compression = AttributeMapUtil.validateAndNormaliseCompression(
                    attributeMap,
                    compressionVal -> new StroomStreamException(
                            StroomStatusCode.UNKNOWN_COMPRESSION, attributeMap, compressionVal));

            final Receiver receiver;
            final String contentLength = attributeMap.get(StandardHeaderArguments.CONTENT_LENGTH);
            dataReceiptMetrics.recordContentLength(contentLength);
            if (ZERO_CONTENT.equals(contentLength)) {
                LOGGER.warn("process() - Skipping Zero Content " + attributeMap);
                receiver = null;
            } else {
                // We have authenticated the user to accept the data, but from here on,
                // we run as the processing user as the request user won't have perms to do
                // things like check feed status.
                receiver = commonSecurityContext.asProcessingUserResult(() -> {
                    final Receiver receiver2 = receiverFactory.get(attributeMap);
                    receiver2.receive(
                            receiveTime,
                            attributeMap,
                            request.getRequestURI(),
                            request::getInputStream);
                    return receiver2;
                });
            }

            response.setStatus(HttpStatus.SC_OK);

            LOGGER.debug(() -> LogUtil.message(
                    "Writing proxy receipt id {} to response. Receiver: {}, duration: {}, compression: '{}'",
                    receiptId,
                    NullSafe.get(receiver, Object::getClass, Class::getSimpleName),
                    Duration.between(receiveTime, Instant.now()),
                    compression));
            try (final PrintWriter writer = response.getWriter()) {
                writer.println(receiptId);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        } catch (final StroomStreamException e) {
            e.sendErrorResponse(response);
        }
    }
}
