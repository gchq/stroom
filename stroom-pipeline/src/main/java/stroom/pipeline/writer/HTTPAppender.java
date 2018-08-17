package stroom.pipeline.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.api.AttributeMap;
import stroom.feed.AttributeMapUtil;
import stroom.feed.StroomHeaderArguments;
import stroom.datafeed.StroomStreamException;
import stroom.pipeline.destination.ByteCountOutputStream;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaDataHolder;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Handler class that forwards the request to a URL.
 */
@ConfigurableElement(
        type = "HTTPAppender",
        category = Category.DESTINATION,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.STREAM)
public class HTTPAppender extends AbstractAppender {
    private static final Logger LOGGER = LoggerFactory.getLogger(HTTPAppender.class);
    private static final Logger SEND_LOG = LoggerFactory.getLogger("send");

    private final MetaDataHolder metaDataHolder;

    private String forwardUrl;
    private Long connectionTimeout;
    private Long forwardChunkSize;
    private boolean useCompression = true;
    private Set<String> metaKeySet = getMetaKeySet("guid,feed,system,environment,remotehost,remoteaddress");

    private HttpURLConnection connection;
    private ZipOutputStream zipOutputStream;
    private ByteCountOutputStream byteCountOutputStream;
    private long startTimeMs;
    private long count;

    @Inject
    HTTPAppender(final ErrorReceiverProxy errorReceiverProxy,
                 final MetaDataHolder metaDataHolder) {
        super(errorReceiverProxy);
        this.metaDataHolder = metaDataHolder;
    }

    @Override
    public Destination borrowDestination() throws IOException {
        nextEntry();
        return super.borrowDestination();
    }

    @Override
    public void returnDestination(final Destination destination) throws IOException {
        closeEntry();
        super.returnDestination(destination);
    }

    @Override
    protected OutputStream createOutputStream() throws IOException {
        try {
            OutputStream outputStream;
            final AttributeMap attributeMap = metaDataHolder.getMetaData();

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("handleHeader() - " + forwardUrl + " Sending request " + attributeMap);
            }
            startTimeMs = System.currentTimeMillis();
            attributeMap.computeIfAbsent(StroomHeaderArguments.GUID, k -> UUID.randomUUID().toString());

            URL url = new URL(forwardUrl);
            connection = (HttpURLConnection) url.openConnection();

            sslCheck();

            if (connectionTimeout != null) {
                connection.setConnectTimeout(connectionTimeout.intValue());
                connection.setReadTimeout(0);
                // Don't set a read time out else big files will fail
                // connection.setReadTimeout(forwardTimeoutMs);
            }

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/audit");
            connection.setDoOutput(true);
            connection.setDoInput(true);

            if (useCompression) {
                connection.addRequestProperty(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);
            }

            AttributeMap sendHeader = AttributeMapUtil.cloneAllowable(attributeMap);
            for (Entry<String, String> entry : sendHeader.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }

            if (forwardChunkSize != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("handleHeader() - setting ChunkedStreamingMode = " + forwardChunkSize);
                }
                connection.setChunkedStreamingMode(forwardChunkSize.intValue());
            }
            connection.connect();

            if (useCompression) {
                zipOutputStream = new ZipOutputStream(connection.getOutputStream());
                outputStream = zipOutputStream;
                nextEntry();
            } else {
                outputStream = connection.getOutputStream();
            }

            byteCountOutputStream = new ByteCountOutputStream(outputStream) {
                @Override
                public void close() throws IOException {
                    super.close();
                    closeConnection();
                }
            };

            return byteCountOutputStream;

        } catch (final IOException | RuntimeException e) {
            closeConnection();
            throw e;
        }
    }

    private void nextEntry() throws IOException {
        if (zipOutputStream != null) {
            count++;
            final AttributeMap attributeMap = metaDataHolder.getMetaData();
            String fileName = attributeMap.get("fileName");
            if (fileName == null) {
                fileName = ModelStringUtil.zeroPad(3, String.valueOf(count)) + ".dat";
            }

            zipOutputStream.putNextEntry(new ZipEntry(fileName));
        }
    }

    private void closeEntry() throws IOException {
        if (zipOutputStream != null) {
            zipOutputStream.closeEntry();
        }
    }

    private void closeConnection() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("handleFooter() - header fields " + connection.getHeaderFields());
        }
        int responseCode = -1;

        if (connection != null) {
            try {
                responseCode = StroomStreamException.checkConnectionResponse(connection);
            } finally {
                final long duration = System.currentTimeMillis() - startTimeMs;
                final AttributeMap attributeMap = metaDataHolder.getMetaData();
                log(SEND_LOG, attributeMap, "SEND", forwardUrl, responseCode, byteCountOutputStream.getBytesWritten(), duration);

                connection.disconnect();
                connection = null;
            }
        }

    }

//    /**
//     * Handle some pay load.
//     */
//    @Override
//    public void handleEntryData(final byte[] buffer, final int off, final int length) throws IOException {
//        bytesSent += length;
//        zipOutputStream.write(buffer, off, length);
//        if (forwardDelayMs != null) {
//            LOGGER.debug("handleEntryData() - adding delay {}", forwardDelayMs);
//            ThreadUtil.sleep(forwardDelayMs);
//        }
//    }

    private void sslCheck() {
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setHostnameVerifier((s, sslSession) -> true);
        }
    }

//    @Override
//    public void handleError() throws IOException {
//        LOGGER.info("handleError() - " + forwardUrl);
//        if (connection != null) {
//            connection.disconnect();
//        }
//    }
//
//    @Override
//    public void validate() {
//        try {
//            URL url = new URL(forwardUrl);
//            connection = (HttpURLConnection) url.openConnection();
//            connection.disconnect();
//        } catch (IOException ex) {
//            throw new RuntimeException(ex);
//        }
//    }
//


//    public LogStream(final LogStreamConfig logStreamConfig) {
//        if (logStreamConfig != null) {
//            metaKeySet = getMetaKeySet(logStreamConfig.getMetaKeys());
//        } else {
//            metaKeySet = Collections.emptySet();
//        }
//    }

    public void log(final Logger logger, final AttributeMap attributeMap, final String type, final String url, final int responseCode, final long bytes, final long duration) {
        if (logger.isInfoEnabled() && metaKeySet.size() > 0) {
            final Map<String, String> filteredMap = attributeMap.entrySet().stream()
                    .filter(entry -> metaKeySet.contains(entry.getKey().toLowerCase()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
            final String kvPairs = CSVFormatter.format(filteredMap);
            final String message = CSVFormatter.escape(type) +
                    "," +
                    CSVFormatter.escape(url) +
                    "," +
                    responseCode +
                    "," +
                    bytes +
                    "," +
                    duration +
                    "," +
                    kvPairs;
            logger.info(message);
        }
    }

    private Set<String> getMetaKeySet(final String csv) {
        if (csv == null || csv.length() == 0) {
            return Collections.emptySet();
        }

        return Arrays.stream(csv.toLowerCase().split(",")).collect(Collectors.toSet());
    }


    @PipelineProperty(description = "The URL to send data to", displayPriority = 1)
    public void setForwardUrl(final String forwardUrl) {
        this.forwardUrl = forwardUrl;
    }

    @PipelineProperty(description = "How long to wait before we abort sending data due to connection timeout", displayPriority = 3)
    public void setConnectionTimeout(final String string) {
        connectionTimeout = null;
        if (string != null && !string.isEmpty()) {
            connectionTimeout = ModelStringUtil.parseDurationString(string);
        }
    }

    @PipelineProperty(description = "Should data be sent in chunks and if so how big should the chunks be", displayPriority = 4)
    public void setForwardChunkSize(final String string) {
        this.forwardChunkSize = ModelStringUtil.parseIECByteSizeString(string);
    }

    @PipelineProperty(description = "Should data be compressed when sending", defaultValue = "true", displayPriority = 2)
    public void setUseCompression(final boolean useCompression) {
        this.useCompression = useCompression;
    }

    @PipelineProperty(
            description = "Which meta data values will be logged in the send log",
            defaultValue = "guid,feed,system,environment,remotehost,remoteaddress",
            displayPriority = 5)
    public void setLogMetaKeys(final String string) {
        metaKeySet = getMetaKeySet(string);
    }
}
