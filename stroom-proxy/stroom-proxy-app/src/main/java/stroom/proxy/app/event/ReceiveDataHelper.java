package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.CSVFormatter;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.AttributeMapValidator;
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
import stroom.util.logging.Metrics;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReceiveDataHelper {

    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataHelper.class);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final RequestAuthenticator requestAuthenticator;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final CertificateExtractor certificateExtractor;
    private final LogStream logStream;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final AttributeMapValidator attributeMapValidator;

    @Inject
    public ReceiveDataHelper(final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                             final RequestAuthenticator requestAuthenticator,
                             final AttributeMapFilterFactory attributeMapFilterFactory,
                             final CertificateExtractor certificateExtractor,
                             final LogStream logStream,
                             final ReceiptIdGenerator receiptIdGenerator,
                             final AttributeMapValidator attributeMapValidator) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.requestAuthenticator = requestAuthenticator;
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.certificateExtractor = certificateExtractor;
        this.logStream = logStream;
        this.receiptIdGenerator = receiptIdGenerator;
        this.attributeMapValidator = attributeMapValidator;
    }

    public UniqueId process(final HttpServletRequest request,
                            final Handler consumeHandler,
                            final Handler dropHandler) throws StroomStreamException {
        final long startTimeMs = System.currentTimeMillis();

        // Create attribute map from headers.
        final AttributeMap attributeMap = AttributeMapUtil.create(request, certificateExtractor);

        // Create a new proxy id for the request, so we can track progress and report back the UUID to the sender,
        final UniqueId receiptId = receiptIdGenerator.generateId();

        try {
            Metrics.measure("ProxyRequestHandler - stream", () -> {
                // Authorise request.
                requestAuthenticator.authenticate(request, attributeMap);

                Metrics.measure("ProxyRequestHandler - handle1", () -> {
                    // TODO The following validate call was commented out on master by 66 for some reason
                    // Validate the supplied attributes.
                    attributeMapValidator.validate(
                            attributeMap,
                            () -> receiveDataConfigProvider.get().getMetaTypes());

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
            final String receiptIdStr = receiptId.toString();
            attributeMap.put(StandardHeaderArguments.RECEIPT_ID, receiptIdStr);
            attributeMap.appendItem(StandardHeaderArguments.RECEIPT_ID_PATH, receiptIdStr);

            final StroomStreamException stroomStreamException = StroomStreamException.create(
                    e, AttributeMapUtil.create(request, certificateExtractor));

            final StroomStreamStatus status = stroomStreamException.getStroomStreamStatus();
            LOGGER.debug(() -> LogUtil.message("\"handleException()\",{},\"{}\"",
                    CSVFormatter.format(status.getAttributeMap(), true),
                    CSVFormatter.escape(stroomStreamException.getMessage())));

            final long duration = System.currentTimeMillis() - startTimeMs;
            if (StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA.equals(status.getStroomStatusCode())) {
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

        return receiptId;
    }


    // --------------------------------------------------------------------------------


    public interface Handler {

        void handle(HttpServletRequest request,
                    AttributeMap attributeMap,
                    UniqueId receiptId);
    }
}
