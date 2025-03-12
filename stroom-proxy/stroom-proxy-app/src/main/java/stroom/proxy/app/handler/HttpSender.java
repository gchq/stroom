package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.proxy.repo.ProxyServices;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.UserIdentityFactory;
import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.ByteCountInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Handler class that forwards the request to a URL.
 */
public class HttpSender implements StreamDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpSender.class);
    private static final Logger SEND_LOG = LoggerFactory.getLogger("send");
    private static final int ONE_SECOND = 1_000;

    // TODO Consider whether a UNKNOWN_ERROR(500) is recoverable or not
    private static final Set<StroomStatusCode> NON_RECOVERABLE_STATUS_CODES = EnumSet.of(
            StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA,
            StroomStatusCode.UNEXPECTED_DATA_TYPE,
            StroomStatusCode.FEED_MUST_BE_SPECIFIED);

    private final LogStream logStream;
    private final ForwardHttpPostConfig config;
    private final String userAgent;
    private final UserIdentityFactory userIdentityFactory;
    private final HttpClient httpClient;
    private final String forwardUrl;
    private final StroomDuration forwardDelay;
    private final String forwarderName;
    private final ProxyServices proxyServices;

    public HttpSender(final LogStream logStream,
                      final ForwardHttpPostConfig config,
                      final String userAgent,
                      final UserIdentityFactory userIdentityFactory,
                      final HttpClient httpClient,
                      final ProxyServices proxyServices) {
        this.logStream = logStream;
        this.config = config;
        this.userAgent = userAgent;
        this.userIdentityFactory = userIdentityFactory;
        this.httpClient = httpClient;
        this.forwardUrl = config.getForwardUrl();
        this.forwardDelay = NullSafe.duration(config.getForwardDelay());
        this.forwarderName = config.getName();
        this.proxyServices = proxyServices;
    }

    @Override
    public void send(final AttributeMap attributeMap,
                     final InputStream inputStream) throws ForwardException {
        final Instant startTime = Instant.now();

        if (NullSafe.isEmptyString(attributeMap.get(StandardHeaderArguments.FEED))) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }

        // We need to add the authentication token to our headers
        final Map<String, String> authHeaders = userIdentityFactory.getServiceUserAuthHeaders();
        attributeMap.putAll(authHeaders);

        attributeMap.computeIfAbsent(StandardHeaderArguments.GUID, k -> UUID.randomUUID().toString());

        LOGGER.debug(() -> LogUtil.message(
                "'{}' - Opening connection, forwardUrl: {}, userAgent: {}, attributeMap (" +
                "values truncated):\n{}",
                forwarderName, forwardUrl, userAgent, formatAttributeMapLogging(attributeMap)));

        final HttpPost httpPost = createHttpPost(attributeMap);

        final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(inputStream);
        httpPost.setEntity(new BasicHttpEntity(
                byteCountInputStream,
                ContentType.create("application/audit"),
                true));

        // Execute and get the response.
        final ResponseStatus responseStatus = post(
                httpPost, startTime, attributeMap, byteCountInputStream::getCount);
        LOGGER.debug("responseStatus: {}", responseStatus);
    }

    @Override
    public boolean performLivenessCheck() throws Exception {
        final String url = config.getLivenessCheckUrl();
        boolean isLive;

        if (NullSafe.isNonBlankString(url)) {
            final HttpGet httpGet = new HttpGet(url);
            httpGet.addHeader("User-Agent", userAgent);
            addAuthHeaders(httpGet);

            try {
                final int responseCode = httpClient.execute(httpGet, response -> {
                    final int code = response.getCode();
                    LOGGER.debug("Liveness check, code: {}, response: '{}'", code, response);
                    consumeAndCloseResponseContent(response);
                    return code;
                });

                isLive = responseCode == HttpStatus.SC_OK;
                if (!isLive) {
                    throw new Exception(LogUtil.message("Got response code {} from livenessCheckUrl '{}'",
                            responseCode, url));
                }
            } catch (IOException e) {
                final String msg = LogUtil.message("Error calling livenessCheckUrl '{}': {}",
                        url, LogUtil.exceptionMessage(e));
                LOGGER.debug(msg, e);
                // Consider it not live
                throw new Exception(msg, e);
            }
        } else {
            isLive = true;
        }
        return isLive;
    }

    private HttpPost createHttpPost(final AttributeMap attributeMap) {
        final HttpPost httpPost = new HttpPost(forwardUrl);
        httpPost.addHeader("User-Agent", userAgent);
        httpPost.addHeader("Content-Type", "application/audit");

        addAuthHeaders(httpPost);

        final AttributeMap sendHeader = AttributeMapUtil.cloneAllowable(attributeMap);
        for (Entry<String, String> entry : sendHeader.entrySet()) {
            httpPost.addHeader(entry.getKey(), entry.getValue());
        }

        // We may be doing an instant forward so need to pass on the compression type.
        // If it is a forward after a store/agg then the caller should have set it in
        // the attr map
        final String compression = attributeMap.get(StandardHeaderArguments.COMPRESSION);
        if (NullSafe.isNonBlankString(compression)) {
            httpPost.addHeader(StandardHeaderArguments.COMPRESSION, compression);
        }

        return httpPost;
    }

    private void addAuthHeaders(final BasicHttpRequest request) {
        Objects.requireNonNull(request);
        final String apiKey = config.getApiKey();
        if (NullSafe.isNonBlankString(apiKey)) {
            request.addHeader("Authorization", "Bearer " + apiKey.trim());
        }

        // Allows sending to systems on the same OpenId realm as us using an access token
        if (config.isAddOpenIdAccessToken()) {
            LOGGER.debug(() -> LogUtil.message(
                    "'{}' - Setting request props (values truncated):\n{}",
                    forwarderName,
                    userIdentityFactory.getServiceUserAuthHeaders()
                            .entrySet()
                            .stream()
                            .sorted(Entry.comparingByKey())
                            .map(entry ->
                                    "  " + String.join(":",
                                            entry.getKey(),
                                            LogUtil.truncateUnless(
                                                    entry.getValue(),
                                                    50,
                                                    LOGGER.isTraceEnabled())))
                            .collect(Collectors.joining("\n"))));

            userIdentityFactory.getServiceUserAuthHeaders()
                    .forEach(request::addHeader);
        }
    }

    private void delayPost(final Instant startTime) {
        LOGGER.trace("'{}' - adding delay {}", forwarderName, forwardDelay);
        final long notBeforeEpochMs = startTime.plus(forwardDelay).toEpochMilli();
        long delay = notBeforeEpochMs - System.currentTimeMillis();

        // Loop in case the delay is long and proxy shuts down part way through
        while (delay > 0 && !proxyServices.isShuttingDown()) {
            final long sleepMs = Math.min(ONE_SECOND, delay);
            ThreadUtil.sleep(sleepMs);
            if (sleepMs == ONE_SECOND) {
                delay = notBeforeEpochMs - System.currentTimeMillis();
            } else {
                break;
            }
        }
        if (proxyServices.isShuttingDown()) {
            throw new RuntimeException("Proxy is shutting down");
        }
    }

    private ResponseStatus post(final HttpPost httpPost,
                                final Instant startTime,
                                final AttributeMap attributeMap,
                                final LongSupplier contentLengthSupplier) throws ForwardException {
        // Execute and get the response.
        try {
            if (!forwardDelay.isZero()) {
                delayPost(startTime);
            }

            final ResponseStatus responseStatus = httpClient.execute(httpPost, response -> {
                LOGGER.debug(() -> LogUtil.message(
                        "'{}' - Closing stream, response header fields:\n{}",
                        forwarderName, formatHeaderEntryListForLogging(response.getHeaders())));
                return logResponseToSendLog(startTime, response, attributeMap, contentLengthSupplier);
            });

            LOGGER.debug("'{}' - responseStatus: {}", forwarderName, responseStatus);

            // There is no point retrying with these
            final StroomStatusCode stroomStatusCode = responseStatus.stroomStatusCode;
            if (stroomStatusCode == StroomStatusCode.OK) {
                return responseStatus;
            } else if (NON_RECOVERABLE_STATUS_CODES.contains(stroomStatusCode)) {
                throw ForwardException.nonRecoverable(stroomStatusCode);
            } else {
                throw ForwardException.recoverable(stroomStatusCode);
            }
        } catch (final ForwardException e) {
            // Created above so we will have already logged
            throw e;
        } catch (final Exception e) {
            logErrorToSendLog(startTime, e, attributeMap);
            // Have to assume that any exception is recoverable
            throw ForwardException.recoverable(StroomStatusCode.UNKNOWN_ERROR, e.getMessage(), e);
        }
    }

    private String formatHeaderEntryListForLogging(final Header[] headers) {
        return NullSafe.stream(headers)
                .map(header -> new SimpleEntry<>(
                        Objects.requireNonNullElse(header.getName(), "null"),
                        header.getValue())
                )
                .sorted(Entry.comparingByKey())
                .map(entry -> "  " + String.join(
                        ":",
                        NullSafe.string(entry.getKey()),
                        NullSafe.stream(entry.getValue())
                                .filter(Objects::nonNull)
                                .map(val -> "'" + val + "'")
                                .collect(Collectors.joining(", "))))
                .collect(Collectors.joining("\n"));
    }

    private String formatAttributeMapLogging(final AttributeMap attributeMap) {
        return attributeMap
                .entrySet()
                .stream()
                .map(entry -> new SimpleEntry<>(
                        Objects.requireNonNullElse(entry.getKey(), "null"),
                        entry.getValue())
                )
                .sorted(Entry.comparingByKey())
                .map(entry -> "  " + String.join(
                        ":",
                        NullSafe.string(entry.getKey()),
                        LogUtil.truncateUnless(entry.getValue(), 50, LOGGER.isTraceEnabled())))
                .collect(Collectors.joining("\n"));
    }

    private void logErrorToSendLog(final Instant startTime,
                                   final Throwable e,
                                   final AttributeMap attributeMap) {
        LOGGER.debug(() -> LogUtil.message("'{}' - {}", forwarderName, LogUtil.exceptionMessage(e), e));
        logStream.log(
                SEND_LOG,
                attributeMap,
                EventType.ERROR,
                forwardUrl,
                StroomStatusCode.UNKNOWN_ERROR,
                null,
                0,
                Duration.between(startTime, Instant.now()).toMillis(),
                LogUtil.exceptionMessage(e));
    }

    private ResponseStatus logResponseToSendLog(final Instant startTime,
                                                final ClassicHttpResponse response,
                                                final AttributeMap attributeMap,
                                                final LongSupplier contentLengthSupplier) {
//        StroomStatusCode stroomStatusCode;
//        String receiptId;
////        String errorMsg = null;
//        EventType eventType = EventType.SEND;
        final long contentLength = contentLengthSupplier.getAsLong();
        try {
            final ResponseStatus responseStatus = checkConnectionResponse(response, attributeMap);
            final StroomStatusCode stroomStatusCode = responseStatus.stroomStatusCode;
            final String receiptId = responseStatus.receiptId;
            LOGGER.debug("'{}' - stroomStatusCode: {}, receiptId {}, contentLength: {}",
                    forwarderName, stroomStatusCode, receiptId, contentLength);

            final EventType eventType = stroomStatusCode == StroomStatusCode.OK
                    ? EventType.SEND
                    : EventType.ERROR;

            logStream.log(
                    SEND_LOG,
                    attributeMap,
                    eventType,
                    forwardUrl,
                    stroomStatusCode,
                    receiptId,
                    contentLength,
                    Duration.between(startTime, Instant.now()).toMillis());

            return responseStatus;
//        } catch (StroomStreamException e) {
//            LOGGER.debug(() -> LogUtil.message("'{}' - stroomStatusCode: {}",
//                    forwarderName, e.getStroomStreamStatus().getStroomStatusCode()));
//            stroomStatusCode = e.getStroomStreamStatus().getStroomStatusCode();
//            if (stroomStatusCode == StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA) {
//                LOGGER.debug("stroomStatusCode: {}, rejected by destination, so no point in retrying",
//                        stroomStatusCode);
//            } else {
//                errorMsg = e.getMessage();
//                throw e;
//            }
        } catch (Exception e) {
            LOGGER.debug(() ->
                    LogUtil.message("'{}' - Exception reading response {}",
                            forwarderName, LogUtil.exceptionMessage(e), e));
//            eventType = EventType.ERROR;
//            errorMsg = e.getMessage();
            throw e;
//        } finally {
//            final Duration duration = Duration.between(startTime, Instant.now());
//            if (stroomStatusCode == null) {
//                stroomStatusCode = StroomStatusCode.fromHttpCode(response.getCode());
//            }
////            switch (stroomStatusCode) {
////                case OK -> EventType.SEND;
////                case FEED_IS_NOT_SET_TO_RECEIVE_DATA -> EventType.REJECT
////            }
//            logStream.log(
//                    SEND_LOG,
//                    attributeMap,
//                    eventType,
//                    forwardUrl,
//                    stroomStatusCode,
//                    receiptId,
//                    contentLength,
//                    Duration.between(startTime, Instant.now()).toMillis(),
//                    errorMsg);
        }
    }

    private String getHeader(final ClassicHttpResponse response,
                             final String headerName) {
        try {
            final Header header = response.getHeader(headerName);
            return NullSafe.get(header, Header::getValue);
        } catch (final ProtocolException e) {
            LOGGER.error("Error getting header '{}': {}", headerName, LogUtil.exceptionMessage(e), e);
        }
        return null;
    }

    private int getHeaderInt(final ClassicHttpResponse response,
                             final String headerName,
                             final int def) {
        try {
            final String value = getHeader(response, headerName);
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            LOGGER.error(e::getMessage, e);
        }
        return def;
    }

    private StroomStatusCode getStroomStatusCode(
            final ClassicHttpResponse response) {
        final String header = StandardHeaderArguments.STROOM_STATUS;
        final String value = getHeader(response, header);
        try {
            if (value != null) {
                final int code = Integer.parseInt(value);
                return StroomStatusCode.fromCode(code);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("Error parsing stroom status code from header '{}' with value '{}': {}",
                    header, value, LogUtil.exceptionMessage(e), e);
        }
        return null;
    }

    /**
     * Checks the response code and stroom status for the connection and attributeMap.
     * Either returns 200 or throws a {@link StroomStreamException}.
     *
     * @return The HTTP response code
     * @throws StroomStreamException if a non-200 response is received
     */
    public ResponseStatus checkConnectionResponse(final ClassicHttpResponse response,
                                                  final AttributeMap attributeMap) {
        StroomStatusCode stroomStatusCode;
        int httpResponseCode;
        String receiptId;
        String responseMessage;
        try {
            httpResponseCode = response.getCode();
            responseMessage = NullSafe.nonBlankStringElseGet(
                    getHeader(response, StandardHeaderArguments.STROOM_ERROR),
                    response::getReasonPhrase);

            stroomStatusCode = getStroomStatusCode(response);
            if (stroomStatusCode == null) {
                stroomStatusCode = StroomStatusCode.fromHttpCode(httpResponseCode);
                LOGGER.warn("Null stroomStatusCode, httpResponseCode: {}, responseMessage: '{}'",
                        httpResponseCode, responseMessage);
            }

            // Response payload should be a plain text receipt ID
            // TODO Should we get receiptId from content or from a resp header?
            receiptId = readResponseContent(response);
//            consumeAndCloseResponseContent(response);
//            receiptId = getHeader(response, StandardHeaderArguments.RECEIPT_ID);

            LOGGER.debug("httpResponseCode: {}, stroomStatusCode: {}, receiptId: {}, responseMessage: {}",
                    httpResponseCode, stroomStatusCode, receiptId, responseMessage);

            if (httpResponseCode == 200) {
                if (httpResponseCode != stroomStatusCode.getHttpCode()) {
                    // Shouldn't really happen but warn if it does
                    LOGGER.warn("httpResponseCode {} is different to the stroomStatusCode.httpCode {}, " +
                                "(stroomStatusCode.code {}, stroomStatusCode.message '{}')",
                            httpResponseCode,
                            stroomStatusCode.getHttpCode(),
                            stroomStatusCode.getCode(),
                            stroomStatusCode.getMessage());
                }
            }
            return new ResponseStatus(stroomStatusCode, receiptId, responseMessage);
        } catch (final Exception ioEx) {
            LOGGER.debug(() -> LogUtil.message("Error sending to forwardUrl '{}': {}",
                    forwardUrl, LogUtil.exceptionMessage(ioEx)));
            throw ioEx;
//            throw new StroomStreamException(
//                    StroomStatusCode.UNKNOWN_ERROR,
//                    attributeMap,
//                    LogUtil.exceptionMessage(ioEx));
        }
    }

    private void consumeAndCloseResponseContent(final ClassicHttpResponse response) {
        final byte[] buffer = new byte[1024];
        try (final InputStream inputStream = response.getEntity().getContent()) {
            if (inputStream != null) {
                //noinspection StatementWithEmptyBody
                while (inputStream.read(buffer) > 0) {
                }
            }
        } catch (final IOException ioex) {
            LOGGER.debug(ioex.getMessage(), ioex);
        }
    }

    private String readResponseContent(final ClassicHttpResponse response) {

        try (final InputStream inputStream = response.getEntity().getContent()) {
            if (inputStream != null) {
                return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }
        } catch (final IOException ioex) {
            LOGGER.debug(ioex.getMessage(), ioex);
        }
        return "";
    }


    // --------------------------------------------------------------------------------


    public record ResponseStatus(StroomStatusCode stroomStatusCode,
                                 String receiptId,
                                 String message) {

    }
}
