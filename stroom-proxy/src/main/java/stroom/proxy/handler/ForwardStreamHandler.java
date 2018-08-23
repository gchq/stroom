package stroom.proxy.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.api.AttributeMap;
import stroom.feed.AttributeMapUtil;
import stroom.feed.StroomHeaderArguments;
import stroom.datafeed.StroomStreamException;
import stroom.proxy.repo.StroomZipEntry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
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
class ForwardStreamHandler implements StreamHandler, HostnameVerifier {
    private static Logger LOGGER = LoggerFactory.getLogger(ForwardStreamHandler.class);
    private static final Logger SEND_LOG = LoggerFactory.getLogger("send");

    private final LogStream logStream;
    private final String forwardUrl;
    private final Integer forwardTimeoutMs;
    private final Integer forwardDelayMs;
    private final Integer forwardChunkSize;

    private String guid = null;
    private HttpURLConnection connection = null;
    private ZipOutputStream zipOutputStream;
    private long startTimeMs;
    private long bytesSent = 0;

    private AttributeMap attributeMap;

    public ForwardStreamHandler(final LogStream logStream,
                                final String forwardUrl,
                                final Integer forwardTimeoutMs,
                                final Integer forwardDelayMs,
                                final Integer forwardChunkSize) {
        this.logStream = logStream;
        this.forwardUrl = forwardUrl;
        this.forwardTimeoutMs = forwardTimeoutMs;
        this.forwardDelayMs = forwardDelayMs;
        this.forwardChunkSize = forwardChunkSize;
    }

    @Override
    public void setAttributeMap(final AttributeMap attributeMap) {
        this.attributeMap = attributeMap;
    }

    @Override
    public void handleHeader() throws IOException {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("handleHeader() - " + forwardUrl + " Sending request " + attributeMap);
        }
        startTimeMs = System.currentTimeMillis();
        guid = attributeMap.computeIfAbsent(StroomHeaderArguments.GUID, k -> UUID.randomUUID().toString());

        URL url = new URL(forwardUrl);
        connection = (HttpURLConnection) url.openConnection();

        sslCheck();

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

        connection.addRequestProperty(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);

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

    private void sslCheck() {
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setHostnameVerifier(this);
        }

    }

    @Override
    public boolean verify(final String s, final SSLSession sslSession) {
        return true;
    }

    @Override
    public void handleError() throws IOException {
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
