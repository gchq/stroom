package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.UserIdentityFactory;
import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;
import stroom.util.time.StroomDuration;

import com.codahale.metrics.Timer;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handler class that forwards the request to a URL.
 */
public class HttpSender implements StreamDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpSender.class);
    private static final Logger SEND_LOG = LoggerFactory.getLogger("send");

    private final LogStream logStream;
    private final ForwardHttpPostConfig config;
    private final String userAgent;
    private final UserIdentityFactory userIdentityFactory;
    private final HttpClient httpClient;
    private final String forwardUrl;
    private final StroomDuration forwardDelay;
    private final String forwarderName;
    private final Timer sendTimer;

    public HttpSender(final LogStream logStream,
                      final ForwardHttpPostConfig config,
                      final String userAgent,
                      final UserIdentityFactory userIdentityFactory,
                      final HttpClient httpClient,
                      final Metrics metrics) {
        this.logStream = logStream;
        this.config = config;
        this.userAgent = userAgent;
        this.userIdentityFactory = userIdentityFactory;
        this.httpClient = httpClient;
        this.forwardUrl = config.getForwardUrl();
        this.forwardDelay = NullSafe.duration(config.getForwardDelay());
        this.forwarderName = config.getName();
        this.sendTimer = metrics.registrationBuilder(getClass())
                .addNamePart(forwarderName)
                .addNamePart("send")
                .timer()
                .createAndRegister();
    }

    @Override
    public void send(final AttributeMap attributeMap,
                     final InputStream inputStream) throws IOException {
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

        final HttpPost httpPost = new HttpPost(forwardUrl);
        httpPost.addHeader("User-Agent", userAgent);
        httpPost.addHeader("Content-Type", "application/audit");
        final String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            httpPost.addHeader("Authorization", "Bearer " + apiKey.trim());
        }
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
                    .forEach(httpPost::addHeader);
        }

        httpPost.setEntity(new BasicHttpEntity(inputStream, ContentType.create("application/audit"), true));

        // Execute and get the response.
        final int code = sendTimer.timeSupplier(() ->
                post(httpPost, startTime, attributeMap));

        if (code != 200) {
            // We technically shouldn't get here but put here to be extra safe.
            throw new RuntimeException("Bad response");
        }
    }

    private int post(final HttpPost httpPost,
                     final Instant startTime,
                     final AttributeMap attributeMap) {
        // Execute and get the response.
        try {
            if (!forwardDelay.isZero()) {
                LOGGER.trace("'{}' - adding delay {}", forwarderName, forwardDelay);
                ThreadUtil.sleep(forwardDelay);
            }

            return httpClient.execute(httpPost, response -> {
                try {
                    LOGGER.debug(() -> LogUtil.message("'{}' - Closing stream, response header fields:\n{}",
                            forwarderName,
                            formatHeaderEntryListForLogging(response.getHeaders())));
                } finally {
                    logResponse(startTime, response, attributeMap);
                }
                return response.getCode();
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String formatHeaderEntryListForLogging(final Header[] headers) {
        return Arrays
                .stream(headers)
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

    private void logResponse(final Instant startTime,
                             final ClassicHttpResponse response,
                             final AttributeMap attributeMap) {
        int responseCode = -1;
        String errorMsg = null;
        try {
            responseCode = checkConnectionResponse(response, attributeMap);
            LOGGER.debug("'{}' - Response code: {}", forwarderName, responseCode);
        } catch (StroomStreamException e) {
            responseCode = e.getStroomStreamStatus().getStroomStatusCode().getHttpCode();
            errorMsg = e.getMessage();
            throw e;
        } catch (Exception e) {
            responseCode = response.getCode();
            errorMsg = e.getMessage();
            throw e;
        } finally {
            final Duration duration = Duration.between(startTime, Instant.now());
            long totalBytesSent = 0;
            logStream.log(
                    SEND_LOG,
                    attributeMap,
                    "SEND",
                    forwardUrl,
                    responseCode,
                    totalBytesSent,
                    duration.toMillis(),
                    errorMsg);
        }
    }

    private String getHeader(final ClassicHttpResponse response, final String name) {
        try {
            final Header header = response.getHeader(name);
            return NullSafe.get(header, Header::getValue);
        } catch (final ProtocolException e) {
            LOGGER.error(e::getMessage, e);
        }
        return null;
    }

    private int getHeaderInt(final ClassicHttpResponse response, final String name, final int def) {
        try {
            final String value = getHeader(response, name);
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            LOGGER.error(e::getMessage, e);
        }
        return def;
    }

    /**
     * Checks the response code and stroom status for the connection and attributeMap.
     * Either returns 200 or throws a {@link StroomStreamException}.
     *
     * @return The HTTP response code
     * @throws StroomStreamException if a non-200 response is received
     */
    public int checkConnectionResponse(final ClassicHttpResponse response,
                                       final AttributeMap attributeMap) {
        int responseCode;
        int stroomStatus;
        try {
            responseCode = response.getCode();
            final String stroomError = getHeader(response, StandardHeaderArguments.STROOM_ERROR);

            final String responseMessage = stroomError != null && !NullSafe.isBlankString(stroomError)
                    ? stroomError
                    : response.getReasonPhrase();

            stroomStatus = getHeaderInt(response, StandardHeaderArguments.STROOM_STATUS, -1);

            final InputStream inputStream = response.getEntity().getContent();
            if (responseCode == 200) {
                readAndCloseStream(inputStream);
            } else {
//                final InputStream errorStream = connection.getErrorStream();
//                final String errorDetail = readInputStream(errorStream);
//                final String body = readInputStream(connection.getInputStream());
//                LOGGER.info("errorDetail: {}", errorDetail);
//                LOGGER.info("body: {}", body);
                readAndCloseStream(inputStream);

                if (stroomStatus != -1) {
                    throw new StroomStreamException(
                            StroomStatusCode.getStroomStatusCode(stroomStatus),
                            attributeMap,
                            responseMessage);
                } else {
                    throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR,
                            attributeMap,
                            responseMessage);
                }
            }
        } catch (final Exception ioEx) {
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR,
                    attributeMap,
                    ioEx.getMessage());
        }
        return responseCode;
    }

    private void readAndCloseStream(final InputStream inputStream) {
        final byte[] buffer = new byte[1024];
        try {
            if (inputStream != null) {
                while (inputStream.read(buffer) > 0) {
                }
                inputStream.close();
            }
        } catch (final IOException ioex) {
            LOGGER.debug(ioex.getMessage(), ioex);
        }
    }
}
