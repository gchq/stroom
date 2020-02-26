package stroom.proxy.app.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.zip.StroomZipEntry;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.StreamHandler;
import stroom.receive.common.StroomStreamException;
import stroom.util.cert.SSLUtil;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Handler class that forwards the request to a URL.
 */
class ForwardStreamHandler implements StreamHandler {
    private static Logger LOGGER = LoggerFactory.getLogger(ForwardStreamHandler.class);
    private static final Logger SEND_LOG = LoggerFactory.getLogger("send");

    private final LogStream logStream;
    private final SSLSocketFactory sslSocketFactory;
    private final ForwardDestinationConfig forwardDestinationConfig;
    private final String userAgent;
    private final String forwardUrl;
    private final Integer forwardTimeoutMs;
    private final Integer forwardDelayMs;
    private final Integer forwardChunkSize;

    private HttpURLConnection connection = null;
    private ZipOutputStream zipOutputStream;
    private long startTimeMs;
    private long bytesSent = 0;

    private AttributeMap attributeMap;

    ForwardStreamHandler(final LogStream logStream,
                         final ForwardDestinationConfig forwardDestinationConfig,
                         final SSLSocketFactory sslSocketFactory,
                         final String userAgent) {
        this.logStream = logStream;
        this.sslSocketFactory = sslSocketFactory;
        this.forwardDestinationConfig = forwardDestinationConfig;
        this.forwardUrl = forwardDestinationConfig.getForwardUrl();
        this.forwardTimeoutMs = forwardDestinationConfig.getForwardTimeoutMs();
        this.forwardDelayMs = forwardDestinationConfig.getForwardDelayMs();
        this.forwardChunkSize = forwardDestinationConfig.getForwardChunkSize();
        this.userAgent = userAgent;
    }

    @Override
    public void setAttributeMap(final AttributeMap attributeMap) {
        this.attributeMap = attributeMap;
    }

    @Override
    public void handleHeader() throws IOException {
        startTimeMs = System.currentTimeMillis();
        attributeMap.computeIfAbsent(StandardHeaderArguments.GUID, k -> UUID.randomUUID().toString());

        LOGGER.info("handleHeader() - {} Sending request {}", forwardUrl, attributeMap);

        URL url = new URL(forwardUrl);
        connection = (HttpURLConnection) url.openConnection();

        connection.setRequestProperty("User-Agent", userAgent);

        SSLUtil.applySSLConfiguration(connection, sslSocketFactory, forwardDestinationConfig.getSslConfig());

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
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("handleHeader() - setting ChunkedStreamingMode = " + forwardChunkSize);
            }
            connection.setChunkedStreamingMode(forwardChunkSize);
        }
        connection.connect();
        zipOutputStream = new ZipOutputStream(connection.getOutputStream());
    }

    @Override
    public void handleFooter() throws IOException {
        zipOutputStream.close();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("handleFooter() - header fields " + connection.getHeaderFields());
        }
        int responseCode = -1;

        if (connection != null) {
            try {
                responseCode = StroomStreamException.checkConnectionResponse(connection);
            } finally {
                final long duration = System.currentTimeMillis() - startTimeMs;
                logStream.log(SEND_LOG, attributeMap, "SEND", forwardUrl, responseCode, bytesSent, duration);

                connection.disconnect();
                connection = null;
            }
        }
    }

    @Override
    public void handleEntryStart(final StroomZipEntry stroomZipEntry) throws IOException {
        // First call we set up if we are going to do chunked streaming
        zipOutputStream.putNextEntry(new ZipEntry(stroomZipEntry.getFullName()));
    }

    @Override
    public void handleEntryEnd() throws IOException {
        zipOutputStream.closeEntry();
    }

    /**
     * Handle some pay load.
     */
    @Override
    public void handleEntryData(final byte[] buffer, final int off, final int length) throws IOException {
        bytesSent += length;
        zipOutputStream.write(buffer, off, length);
        if (forwardDelayMs != null) {
            try {
                LOGGER.debug("handleEntryData() - adding delay {}", forwardDelayMs);
                Thread.sleep(forwardDelayMs);
            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);

                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void handleError() {
        LOGGER.info("handleError() - " + forwardUrl);
        if (connection != null) {
            connection.disconnect();
        }
    }

    @Override
    public void validate() {
        try {
            URL url = new URL(forwardUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.disconnect();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    String getForwardUrl() {
        return forwardUrl;
    }
}
