/*
 * Copyright 2024 Crown Copyright
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

package stroom.proxy.app.forwarder;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.StreamHandler;
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
import stroom.util.shared.string.CIKey;
import stroom.util.time.StroomDuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.net.ssl.SSLSocketFactory;

/**
 * Handler class that forwards the request to a URL.
 */
public class ForwardStreamHandler implements StreamHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardStreamHandler.class);
    private static final Logger SEND_LOG = LoggerFactory.getLogger("send");

    private final LogStream logStream;
    private final AttributeMap attributeMap;
    private final String forwardUrl;
    private final StroomDuration forwardDelay;
    private final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];
    private final String forwarderName;
    private HttpURLConnection connection;
    private final ZipOutputStream zipOutputStream;
    private final long startTimeMs;
    private long totalBytesSent = 0;

    public ForwardStreamHandler(final LogStream logStream,
                                final ForwardHttpPostConfig config,
                                final SSLSocketFactory sslSocketFactory,
                                final String userAgent,
                                final AttributeMap attributeMap,
                                final UserIdentityFactory userIdentityFactory) throws IOException {
        this.logStream = logStream;
        this.forwardUrl = config.getForwardUrl();
        this.forwardDelay = NullSafe.duration(config.getForwardDelay());
        this.forwarderName = config.getName();
        this.attributeMap = attributeMap;

        final StroomDuration forwardTimeout = config.getForwardTimeout();
        final ByteSize forwardChunkSize = config.getForwardChunkSize();

        startTimeMs = System.currentTimeMillis();
        attributeMap.computeIfAbsent(StandardHeaderArguments.GUID, k -> UUID.randomUUID().toString());

        LOGGER.debug(() -> LogUtil.message(
                "'{}' - Opening connection, forwardUrl: {}, userAgent: {}, forwardTimeout: {}, attributeMap (" +
                        "values truncated):\n{}",
                forwarderName, forwardUrl, userAgent, forwardTimeout, formatAttributeMapLogging(attributeMap)));

        final URL url = new URL(forwardUrl);
        connection = (HttpURLConnection) url.openConnection();

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

        connection.addRequestProperty(
                StandardHeaderArguments.COMPRESSION.get(),
                StandardHeaderArguments.COMPRESSION_ZIP);

        final AttributeMap sendHeader = AttributeMapUtil.cloneAllowable(attributeMap);
        for (Entry<CIKey, String> entry : sendHeader.entrySet()) {
            connection.addRequestProperty(entry.getKey().get(), entry.getValue());
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
                    .forEach((key, value) -> {
                        connection.setRequestProperty(key, value);
                    });
        }

        if (forwardChunkSize.isNonZero() && forwardChunkSize.getBytes() <= Integer.MAX_VALUE) {
            LOGGER.debug("'{}' - setting ChunkedStreamingMode: {}", forwarderName, forwardChunkSize);
            connection.setChunkedStreamingMode((int) forwardChunkSize.getBytes());
        }
        connection.connect();
        zipOutputStream = new ZipOutputStream(connection.getOutputStream());
    }

    @Override
    public long addEntry(final String entry,
                         final InputStream inputStream,
                         final Consumer<Long> progressHandler) throws IOException {
        LOGGER.trace("'{}' - adding entry {}, forwardDelay: {}", forwarderName, entry, forwardDelay);
        // First call we set up if we are going to do chunked streaming
        zipOutputStream.putNextEntry(new ZipEntry(entry));

        final long bytesSent = StreamUtil.streamToStream(inputStream, zipOutputStream, buffer, progressHandler);
        totalBytesSent += bytesSent;

        if (!forwardDelay.isZero()) {
            LOGGER.trace("'{}' - adding delay {}", forwarderName, forwardDelay);
            ThreadUtil.sleep(forwardDelay);
        }

        zipOutputStream.closeEntry();

        return bytesSent;
    }

    void error() {
        LOGGER.debug("'{}' - error(), forwardUrl: {}", forwarderName, forwardUrl);
        logAndDisconnect();
    }

    void close() throws IOException {
        zipOutputStream.close();

        LOGGER.debug(() -> LogUtil.message("'{}' - Closing stream, response header fields:\n{}",
                forwarderName,
                formatHeaderEntryListForLogging(connection.getHeaderFields())));

        logAndDisconnect();
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
                .sorted(Entry.comparingByKey())
                .map(entry -> "  " + String.join(
                        ":",
                        NullSafe.getOrElse(entry.getKey(), CIKey::get, "null"),
                        LogUtil.truncateUnless(entry.getValue(), 50, LOGGER.isTraceEnabled())))
                .collect(Collectors.joining("\n"));
    }

    private void logAndDisconnect() {
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
                final long duration = System.currentTimeMillis() - startTimeMs;
                logStream.log(
                        SEND_LOG,
                        attributeMap,
                        "SEND",
                        forwardUrl,
                        responseCode,
                        totalBytesSent,
                        duration,
                        errorMsg);

                connection.disconnect();
                connection = null;
            }
        }
    }
}
