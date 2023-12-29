package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.UserIdentityFactory;
import stroom.util.NullSafe;
import stroom.util.cert.SSLUtil;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.ByteSize;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.net.ssl.SSLSocketFactory;

/**
 * Handler class that forwards the request to a URL.
 */
public class HttpSender implements StreamDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpSender.class);
    private static final Logger SEND_LOG = LoggerFactory.getLogger("send");

    private final LogStream logStream;
    private final ForwardHttpPostConfig config;
    private final SSLSocketFactory sslSocketFactory;
    private final String userAgent;
    private final UserIdentityFactory userIdentityFactory;
    private final String forwardUrl;
    private final StroomDuration forwardDelay;
    private final String forwarderName;


    public HttpSender(final LogStream logStream,
                      final ForwardHttpPostConfig config,
                      final SSLSocketFactory sslSocketFactory,
                      final String userAgent,
                      final UserIdentityFactory userIdentityFactory) {
        this.logStream = logStream;
        this.config = config;
        this.sslSocketFactory = sslSocketFactory;
        this.userAgent = userAgent;
        this.userIdentityFactory = userIdentityFactory;
        this.forwardUrl = config.getForwardUrl();
        this.forwardDelay = NullSafe.duration(config.getForwardDelay());
        this.forwarderName = config.getName();
    }

    @Override
    public void send(final AttributeMap attributeMap,
                     final InputStream inputStream) throws IOException {
        final StroomDuration forwardTimeout = config.getForwardTimeout();
        final ByteSize forwardChunkSize = config.getForwardChunkSize();

        final Instant startTime = Instant.now();

        if (NullSafe.isEmptyString(attributeMap.get(StandardHeaderArguments.FEED))) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }

        // We need to add the authentication token to our headers
        final Map<String, String> authHeaders = userIdentityFactory.getServiceUserAuthHeaders();
        attributeMap.putAll(authHeaders);

        attributeMap.computeIfAbsent(StandardHeaderArguments.GUID, k -> UUID.randomUUID().toString());

        LOGGER.debug(() -> LogUtil.message(
                "'{}' - Opening connection, forwardUrl: {}, userAgent: {}, forwardTimeout: {}, attributeMap (" +
                        "values truncated):\n{}",
                forwarderName, forwardUrl, userAgent, forwardTimeout, formatAttributeMapLogging(attributeMap)));

        final URL url = new URL(forwardUrl);

        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", userAgent);
        if (sslSocketFactory != null) {
            SSLUtil.applySSLConfiguration(connection, sslSocketFactory, config.getSslConfig());
        }

        if (forwardTimeout != null) {
            connection.setConnectTimeout((int) forwardTimeout.toMillis());
            connection.setReadTimeout(0);
            // Don't set a read time out else big files will fail
            // connection.setReadTimeout(forwardTimeoutMs);
        } else {
            LOGGER.debug(() ->
                    LogUtil.message("'{}' - Using default connect timeout: {}",
                            forwarderName,
                            Duration.ofMillis(connection.getConnectTimeout())));
        }

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/audit");
        connection.setDoOutput(true);
        connection.setDoInput(true);

        connection.addRequestProperty(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

        final AttributeMap sendHeader = AttributeMapUtil.cloneAllowable(attributeMap);
        for (Entry<String, String> entry : sendHeader.entrySet()) {
            connection.addRequestProperty(entry.getKey(), entry.getValue());
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
                    .forEach(connection::setRequestProperty);
        }

        if (forwardChunkSize.isNonZero() && forwardChunkSize.getBytes() <= Integer.MAX_VALUE) {
            LOGGER.debug("'{}' - setting ChunkedStreamingMode: {}", forwarderName, forwardChunkSize);
            connection.setChunkedStreamingMode((int) forwardChunkSize.getBytes());
        }
        connection.connect();
        try {
            // Get a buffer to help us transfer data.
            final byte[] buffer = LocalByteBuffer.get();
            StreamUtil.streamToStream(
                    inputStream,
                    connection.getOutputStream(),
                    buffer,
                    new ProgressHandler("Sending data"));

            if (!forwardDelay.isZero()) {
                LOGGER.trace("'{}' - adding delay {}", forwarderName, forwardDelay);
                ThreadUtil.sleep(forwardDelay);
            }

            LOGGER.debug(() -> LogUtil.message("'{}' - Closing stream, response header fields:\n{}",
                    forwarderName,
                    formatHeaderEntryListForLogging(connection.getHeaderFields())));

        } finally {
            logAndDisconnect(startTime, connection, attributeMap);
        }
    }

    private String formatHeaderEntryListForLogging(final Map<String, List<String>> headerFields) {
        return headerFields
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

    private void logAndDisconnect(final Instant startTime,
                                  final HttpURLConnection connection,
                                  final AttributeMap attributeMap) {
        if (connection != null) {
            int responseCode = -1;
            String errorMsg = null;
            try {
                responseCode = StroomStreamException.checkConnectionResponse(connection, attributeMap);
                LOGGER.debug("'{}' - Response code: {}", forwarderName, responseCode);
            } catch (StroomStreamException e) {
                responseCode = e.getStroomStreamStatus().getStroomStatusCode().getHttpCode();
                errorMsg = e.getMessage();
                throw e;
            } catch (Exception e) {
                try {
                    responseCode = connection.getResponseCode();
                    errorMsg = e.getMessage();
                } catch (IOException ex) {
                    // swallow, not much we can do about this
                }
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

                connection.disconnect();
            }
        }
    }
}
