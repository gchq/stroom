/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.proxy.repo.ProxyServices;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.UserIdentityFactory;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.ByteSize;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;

import com.codahale.metrics.Timer;
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
import java.util.AbstractMap.SimpleEntry;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
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
            StroomStatusCode.REJECTED_BY_POLICY_RULES,
            StroomStatusCode.UNEXPECTED_DATA_TYPE,
            StroomStatusCode.FEED_MUST_BE_SPECIFIED);

    private final LogStream logStream;
    private final ForwardHttpPostConfig forwardHttpPostConfig;
    private final String userAgent;
    private final UserIdentityFactory userIdentityFactory;
    private final HttpClient httpClient;
    private final String forwardUrl;
    private final String livenessCheckUrl;
    private final String forwarderName;
    private final ProxyServices proxyServices;
    private final Timer sendTimer;
    private final Set<CIKey> headerAllowSet;

    public HttpSender(final LogStream logStream,
                      final DownstreamHostConfig downstreamHostConfig,
                      final ForwardHttpPostConfig forwardHttpPostConfig,
                      final String userAgent,
                      final UserIdentityFactory userIdentityFactory,
                      final HttpClient httpClient,
                      final Metrics metrics,
                      final ProxyServices proxyServices) {
        this.logStream = logStream;
        this.forwardHttpPostConfig = forwardHttpPostConfig;
        this.userAgent = userAgent;
        this.userIdentityFactory = userIdentityFactory;
        this.httpClient = httpClient;
        this.forwardUrl = forwardHttpPostConfig.createForwardUrl(downstreamHostConfig);
        this.livenessCheckUrl = forwardHttpPostConfig.createLivenessCheckUrl(downstreamHostConfig);
        this.forwarderName = forwardHttpPostConfig.getName();
        this.proxyServices = proxyServices;
        this.sendTimer = metrics.registrationBuilder(getClass())
                .addNamePart(forwarderName)
                .addNamePart(Metrics.SEND)
                .timer()
                .createAndRegister();
        this.headerAllowSet = buildHeaderAllowSet(forwardHttpPostConfig);
    }

    @Override
    public void send(final AttributeMap attributeMap,
                     final InputStream inputStream) throws ForwardException {
        if (NullSafe.isEmptyString(attributeMap.get(StandardHeaderArguments.FEED))) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }

        attributeMap.putRandomUuidIfAbsent(StandardHeaderArguments.GUID);

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
        final ResponseStatus responseStatus = sendTimer.timeSupplier(() ->
                post(httpPost, attributeMap, byteCountInputStream::getCount));
        LOGGER.debug("responseStatus: {}", responseStatus);
    }

    @Override
    public boolean performLivenessCheck() throws Exception {
        final String url = livenessCheckUrl;
        LOGGER.debug("performLivenessCheck() - url: '{}'", url);

        if (NullSafe.isNonBlankString(url)) {
            final HttpGet httpGet = new HttpGet(url);
            httpGet.addHeader("User-Agent", userAgent);
            addAuthHeaders(httpGet);

            try {
                final int responseCode = httpClient.execute(httpGet, response -> {
                    final int code = response.getCode();
                    LOGGER.debug("performLivenessCheck() - code: {}, response: '{}'", code, response);
                    consumeAndCloseResponseContent(response);
                    return code;
                });

                final boolean isLive = responseCode == HttpStatus.SC_OK;
                if (!isLive) {
                    throw new Exception(LogUtil.message("Got response code {} from livenessCheckUrl '{}'",
                            responseCode, url));
                }
            } catch (final IOException e) {
                final String msg = LogUtil.message("Error calling livenessCheckUrl '{}': {}",
                        url, LogUtil.exceptionMessage(e));
                LOGGER.debug(msg, e);
                // Consider it not live
                throw new Exception(msg, e);
            }
        }
        return true;
    }

    @Override
    public boolean hasLivenessCheck() {
        return forwardHttpPostConfig.isLivenessCheckEnabled()
               && NullSafe.isNonBlankString(livenessCheckUrl);
    }

    private HttpPost createHttpPost(final AttributeMap attributeMap) {
        final HttpPost httpPost = new HttpPost(forwardUrl);
        httpPost.addHeader("User-Agent", userAgent);
        httpPost.addHeader("Content-Type", "application/audit");

        // Add the header(s) for authenticating with the downstream (e.g. API key/OAuth token)
        addAuthHeaders(httpPost);

        // Add meta entries that we are allowed to include
        attributeMap.forEach((k, v) -> {
            final CIKey ciKey = CIKey.of(k);
            if (headerAllowSet.contains(ciKey)) {
                httpPost.addHeader(k, v);
            }
        });

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
        final String apiKey = NullSafe.trim(forwardHttpPostConfig.getApiKey());
        if (!apiKey.isEmpty()) {
            LOGGER.debug(() -> LogUtil.message("addAuthHeaders() - Using configured apiKey {}",
                    NullSafe.subString(apiKey, 0, 15)));
            userIdentityFactory.getAuthHeaders(apiKey)
                    .forEach(request::addHeader);
        } else if (forwardHttpPostConfig.isAddOpenIdAccessToken()) {
            // Allows sending to systems on the same OpenId realm as us using an access token
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
        } else {
            LOGGER.debug("authHeaders() - No headers added");
        }
    }

    private ResponseStatus post(final HttpPost httpPost,
                                final AttributeMap attributeMap,
                                final LongSupplier contentLengthSupplier) throws ForwardException {
        // Execute and get the response.
        final DurationTimer timer = DurationTimer.start();
        try {
            final ResponseStatus responseStatus = httpClient.execute(httpPost, response -> {
                LOGGER.debug(() -> LogUtil.message(
                        "'{}' - Closing stream, response header fields:\n{}",
                        forwarderName, formatHeaderEntryListForLogging(response.getHeaders())));
                return logResponseToSendLog(timer.get(), response, attributeMap, contentLengthSupplier);
            });

            LOGGER.debug("'{}' - responseStatus: {}, duration: {}", forwarderName, responseStatus, timer);

            // There is no point retrying with these
            final StroomStatusCode stroomStatusCode = responseStatus.stroomStatusCode;
            if (stroomStatusCode == StroomStatusCode.OK) {
                return responseStatus;
            } else if (NON_RECOVERABLE_STATUS_CODES.contains(stroomStatusCode)) {
                throw ForwardException.nonRecoverable(responseStatus, attributeMap);
            } else {
                throw ForwardException.recoverable(responseStatus, attributeMap);
            }
        } catch (final ForwardException e) {
            // Created above so we will have already logged
            throw e;
        } catch (final Exception e) {
            final Duration duration = timer.get();
            final long byteCount = LogUtil.swallowExceptions(contentLengthSupplier)
                    .orElse(0);
            // Have to assume that any exception is recoverable
            final String msg = LogUtil.message("Error during HTTP POST, data sent: {}, duration: {}, error: {}",
                    ByteSize.ofBytes(byteCount), duration, LogUtil.exceptionMessage(e));
            logErrorToSendLog(duration, e, msg, attributeMap);
            throw ForwardException.recoverable(
                    StroomStatusCode.UNKNOWN_ERROR, attributeMap, msg, e);
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

    private void logErrorToSendLog(final Duration duration,
                                   final Exception e,
                                   final String exceptionMessage,
                                   final AttributeMap attributeMap) {
        LOGGER.debug(() -> LogUtil.message("'{}' - {}", forwarderName, exceptionMessage, e));
        logStream.log(
                SEND_LOG,
                attributeMap,
                EventType.ERROR,
                forwardUrl,
                StroomStatusCode.UNKNOWN_ERROR,
                null,
                0,
                duration.toMillis(),
                LogUtil.exceptionMessage(e));
    }

    private ResponseStatus logResponseToSendLog(final Duration duration,
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
            LOGGER.debug(() -> LogUtil.message(
                    "'{}' - stroomStatusCode: {}, receiptId {}, contentLength: {}, compression: '{}'",
                    forwarderName,
                    stroomStatusCode,
                    receiptId,
                    ByteSize.ofBytes(contentLength),
                    attributeMap.get(StandardHeaderArguments.COMPRESSION)));

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
                    duration.toMillis());

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
        } catch (final Exception e) {
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
        } catch (final NumberFormatException e) {
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
        } catch (final NumberFormatException e) {
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
        final int httpResponseCode;
        final String receiptId;
        final String responseMessage;
        try {
            httpResponseCode = response.getCode();
            responseMessage = NullSafe.nonBlankStringElseGet(
                    getHeader(response, StandardHeaderArguments.STROOM_ERROR),
                    response::getReasonPhrase);

            stroomStatusCode = getStroomStatusCode(response);
            if (stroomStatusCode == null) {
                stroomStatusCode = StroomStatusCode.fromHttpCode(httpResponseCode);
                LOGGER.debug("Null stroomStatusCode, httpResponseCode: {}, responseMessage: '{}'",
                        httpResponseCode, responseMessage);
            }

            // Response payload should be a plain text receipt ID
            // TODO Should we get receiptId from content or from a resp header?
            receiptId = readResponseContent(response);

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
            return new ResponseStatus(stroomStatusCode, receiptId, responseMessage, httpResponseCode);
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
                return NullSafe.trim(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
            }
        } catch (final IOException ioex) {
            LOGGER.debug(ioex.getMessage(), ioex);
        }
        return "";
    }

    private Set<CIKey> buildHeaderAllowSet(final ForwardHttpPostConfig config) {
        final Set<CIKey> baseSet = StandardHeaderArguments.HTTP_POST_BASE_META_ALLOW_SET;
        final Set<String> additionalSet = NullSafe.set(NullSafe.get(
                config,
                ForwardHttpPostConfig::getForwardHeadersAdditionalAllowSet));

        final Set<CIKey> combinedSet = new HashSet<>(baseSet.size() + additionalSet.size());
        combinedSet.addAll(baseSet);
        config.getForwardHeadersAdditionalAllowSet()
                .stream()
                .filter(NullSafe::isNonBlankString)
                .map(CIKey::of)
                .forEach(combinedSet::add);

        LOGGER.debug("buildHeaderAllowSet() - combinedSet: {}", combinedSet);
        return combinedSet;
    }


    // --------------------------------------------------------------------------------


    public record ResponseStatus(StroomStatusCode stroomStatusCode,
                                 String receiptId,
                                 String message,
                                 int httpResponseCode) {

    }
}
