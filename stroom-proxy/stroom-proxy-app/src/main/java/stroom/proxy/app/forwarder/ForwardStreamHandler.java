package stroom.proxy.app.forwarder;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.StreamHandler;
import stroom.receive.common.StroomStreamException;
import stroom.util.cert.SSLUtil;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Consumer;
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
    private final Integer forwardDelayMs;
    private final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];
    private HttpURLConnection connection;
    private final ZipOutputStream zipOutputStream;
    private final long startTimeMs;
    private long totalBytesSent = 0;

    public ForwardStreamHandler(final LogStream logStream,
                                final ForwardHttpPostConfig config,
                                final SSLSocketFactory sslSocketFactory,
                                final String userAgent,
                                final AttributeMap attributeMap) throws IOException {
        this.logStream = logStream;
        this.forwardUrl = config.getForwardUrl();
        this.forwardDelayMs = config.getForwardDelayMs();
        this.attributeMap = attributeMap;

        final Integer forwardTimeoutMs = config.getForwardTimeoutMs();
        final Integer forwardChunkSize = config.getForwardChunkSize();

        startTimeMs = System.currentTimeMillis();
        attributeMap.computeIfAbsent(StandardHeaderArguments.GUID, k -> UUID.randomUUID().toString());

        LOGGER.debug(() -> "handleHeader() - " + forwardUrl + " Sending request " + attributeMap);

        final URL url = new URL(forwardUrl);
        connection = (HttpURLConnection) url.openConnection();

        connection.setRequestProperty("User-Agent", userAgent);
        if (sslSocketFactory != null) {
            SSLUtil.applySSLConfiguration(connection, sslSocketFactory, config.getSslConfig());
        }

        if (forwardTimeoutMs != null) {
            connection.setConnectTimeout(forwardTimeoutMs);
            connection.setReadTimeout(0);
            // Don't set a read time out else big files will fail
            // connection.setReadTimeout(forwardTimeoutMs);
        }

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/audit");
        connection.setDoOutput(true);
        connection.setDoInput(true);

        connection.addRequestProperty(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

        AttributeMap sendHeader = AttributeMapUtil.cloneAllowable(attributeMap);
        for (Entry<String, String> entry : sendHeader.entrySet()) {
            connection.addRequestProperty(entry.getKey(), entry.getValue());
        }

        if (forwardChunkSize != null) {
            LOGGER.debug(() -> "handleHeader() - setting ChunkedStreamingMode = " + forwardChunkSize);
            connection.setChunkedStreamingMode(forwardChunkSize);
        }
        connection.connect();
        zipOutputStream = new ZipOutputStream(connection.getOutputStream());
    }

    @Override
    public long addEntry(final String entry,
                         final InputStream inputStream,
                         final Consumer<Long> progressHandler) throws IOException {
        // First call we set up if we are going to do chunked streaming
        zipOutputStream.putNextEntry(new ZipEntry(entry));

        final long bytesSent = StreamUtil.streamToStream(inputStream, zipOutputStream, buffer, progressHandler);
        totalBytesSent += bytesSent;

        if (forwardDelayMs != null) {
            LOGGER.debug(() -> "handleEntryData() - adding delay " + forwardDelayMs);
            ThreadUtil.sleep(forwardDelayMs);
        }

        zipOutputStream.closeEntry();

        return bytesSent;
    }

    void error() {
        LOGGER.debug(() -> "error() - " + forwardUrl);
        logAndDisconnect();
    }

    void close() throws IOException {
        zipOutputStream.close();
        LOGGER.debug(() -> "handleFooter() - header fields " + connection.getHeaderFields());
        logAndDisconnect();
    }

    private void logAndDisconnect() {
        if (connection != null) {
            int responseCode = -1;
            try {
                responseCode = StroomStreamException.checkConnectionResponse(connection, attributeMap);
            } finally {
                final long duration = System.currentTimeMillis() - startTimeMs;
                logStream.log(SEND_LOG, attributeMap, "SEND", forwardUrl, responseCode, totalBytesSent, duration);

                connection.disconnect();
                connection = null;
            }
        }
    }
}
