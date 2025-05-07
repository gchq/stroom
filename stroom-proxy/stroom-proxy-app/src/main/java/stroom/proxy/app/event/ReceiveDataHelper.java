package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.CSVFormatter;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.RequestAuthenticator;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamStatus;
import stroom.util.cert.CertificateExtractor;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.logging.SimpleMetrics;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class ReceiveDataHelper {

    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataHelper.class);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final RequestAuthenticator requestAuthenticator;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final CertificateExtractor certificateExtractor;
    private final LogStream logStream;
    private final ReceiptIdGenerator receiptIdGenerator;

    @Inject
    public ReceiveDataHelper(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                             final RequestAuthenticator requestAuthenticator,
                             final AttributeMapFilterFactory attributeMapFilterFactory,
                             final CertificateExtractor certificateExtractor,
                             final LogStream logStream,
                             final ReceiptIdGenerator receiptIdGenerator) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.requestAuthenticator = requestAuthenticator;
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.certificateExtractor = certificateExtractor;
        this.logStream = logStream;
        this.receiptIdGenerator = receiptIdGenerator;
    }

    public UniqueId process(final HttpServletRequest request,
                            final Handler consumeHandler,
                            final Handler dropHandler) throws StroomStreamException {
        final Instant startTime = Instant.now();
        // Create a new proxy id for the request, so we can track progress and report back the UUID to the sender,
        final UniqueId receiptId = receiptIdGenerator.generateId();

        // Create attribute map from headers.
        final AttributeMap attributeMap = AttributeMapUtil.create(
                request,
                certificateExtractor,
                startTime,
                receiptId);
        try {
            // TODO convert to DW Metrics in 7.9+
            SimpleMetrics.measure("ProxyRequestHandler - stream", () -> {
                // Authorise request.
                requestAuthenticator.authenticate(request, attributeMap);

                // TODO convert to DW Metrics in 7.9+
                SimpleMetrics.measure("ProxyRequestHandler - handle1", () -> {
                    // Test to see if we are going to accept this stream or drop the data.
                    final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();
                    if (attributeMapFilter.filter(attributeMap)) {
                        consumeHandler.handle(request, attributeMap, receiptId);
                    } else {
                        dropHandler.handle(request, attributeMap, receiptId);
                    }
                });
            });
        } catch (final Throwable e) {
            // Add the proxy request id to help error diagnosis.
            final AttributeMap errAttributeMap = AttributeMapUtil.create(
                    request,
                    certificateExtractor,
                    startTime,
                    receiptId);
            final StroomStreamException stroomStreamException = StroomStreamException.create(
                    e, errAttributeMap);

            final StroomStreamStatus status = stroomStreamException.getStroomStreamStatus();
            LOGGER.debug(() -> LogUtil.message("\"handleException()\",{},\"{}\"",
                    CSVFormatter.format(status.getAttributeMap(), true),
                    CSVFormatter.escape(stroomStreamException.getMessage())));

            final long durationMs = System.currentTimeMillis() - startTime.toEpochMilli();
            final StroomStatusCode stroomStatusCode = status.getStroomStatusCode();
            final EventType eventType = StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA.equals(stroomStatusCode)
                    ? EventType.REJECT
                    : EventType.ERROR;

            logStream.log(
                    RECEIVE_LOG,
                    status.getAttributeMap(),
                    eventType,
                    request.getRequestURI(),
                    stroomStatusCode,
                    receiptId.toString(),
                    -1,
                    durationMs,
                    e.getMessage());

            throw stroomStreamException;
        }

        return receiptId;
    }


    // --------------------------------------------------------------------------------


    public interface Handler {

        void handle(HttpServletRequest request,
                    AttributeMap attributeMap,
                    UniqueId receiptId);
    }
}
