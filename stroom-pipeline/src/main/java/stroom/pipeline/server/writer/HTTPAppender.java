package stroom.pipeline.server.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.feed.MetaMap;
import stroom.feed.MetaMapFactory;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.StroomStreamException;
import stroom.pipeline.destination.Destination;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaDataHolder;
import stroom.util.cert.SSLConfig;
import stroom.util.cert.SSLUtil;
import stroom.util.io.ByteCountOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
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
@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(
        type = "HTTPAppender",
        category = Category.DESTINATION,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.STREAM)
public class HTTPAppender extends AbstractAppender {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HTTPAppender.class);
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

    private boolean useJvmSslConfig = true;
    private final SSLConfig sslConfig = new SSLConfig();

    private String requestMethod = "POST";
    private String contentType = "application/json";

    @Inject
    public HTTPAppender(final ErrorReceiverProxy errorReceiverProxy,
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
            final MetaMap metaMap = metaDataHolder.getMetaData();

            LOGGER.info(() -> "createOutputStream() - " + forwardUrl + " Sending request " + metaMap);
            startTimeMs = System.currentTimeMillis();
            metaMap.computeIfAbsent(StroomHeaderArguments.GUID, k -> UUID.randomUUID().toString());

            URL url = new URL(forwardUrl);
            connection = (HttpURLConnection) url.openConnection();

            if (connection instanceof HttpsURLConnection) {
                final HttpsURLConnection httpsURLConnection = (HttpsURLConnection) connection;
                if (!useJvmSslConfig) {
                    LOGGER.info(() -> "Configuring SSLSocketFactory for destination " + forwardUrl);
                    final SSLSocketFactory sslSocketFactory = SSLUtil.createSslSocketFactory(sslConfig);
                    SSLUtil.applySSLConfiguration(connection, sslSocketFactory, sslConfig);
                } else if (!sslConfig.isHostnameVerificationEnabled()) {
                    SSLUtil.disableHostnameVerification(httpsURLConnection);
                }
            }

            if (connectionTimeout != null) {
                connection.setConnectTimeout(connectionTimeout.intValue());
                connection.setReadTimeout(0);
                // Don't set a read time out else big files will fail
                // connection.setReadTimeout(forwardTimeoutMs);
            }

            connection.setRequestMethod(requestMethod);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setDoOutput(true);
            connection.setDoInput(true);

            if (useCompression) {
                connection.addRequestProperty(StroomHeaderArguments.COMPRESSION, StroomHeaderArguments.COMPRESSION_ZIP);
            }

            MetaMap sendHeader = MetaMapFactory.cloneAllowable(metaMap);
            for (Entry<String, String> entry : sendHeader.entrySet()) {
                connection.addRequestProperty(entry.getKey(), entry.getValue());
            }

            if (forwardChunkSize != null) {
                LOGGER.debug(() -> "handleHeader() - setting ChunkedStreamingMode = " + forwardChunkSize);
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
            LOGGER.debug(e::getMessage, e);
            closeConnection();
            throw e;
        }
    }

    @Override
    long getCurrentOutputSize() {
        if (byteCountOutputStream == null) {
            return 0;
        }
        return byteCountOutputStream.getCount();
    }

    private void nextEntry() throws IOException {
        if (zipOutputStream != null) {
            count++;
            final MetaMap metaMap = metaDataHolder.getMetaData();
            String fileName = metaMap.get("fileName");
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
        int responseCode = -1;

        if (connection != null) {
            LOGGER.debug(() -> "closeConnection() - header fields " + connection.getHeaderFields());
            try {
                responseCode = StroomStreamException.checkConnectionResponse(connection);
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
                throw e;
            } finally {
                long bytes = 0;
                if (byteCountOutputStream != null) {
                    bytes = byteCountOutputStream.getCount();
                }

                final long duration = System.currentTimeMillis() - startTimeMs;
                final MetaMap metaMap = metaDataHolder.getMetaData();
                log(SEND_LOG, metaMap, "SEND", forwardUrl, responseCode, bytes, duration);

                connection.disconnect();
                connection = null;
            }
        }

    }

    public void log(final Logger logger, final MetaMap metaMap, final String type, final String url, final int responseCode, final long bytes, final long duration) {
        if (logger.isInfoEnabled() && metaKeySet.size() > 0) {
            final Map<String, String> filteredMap = metaMap.entrySet().stream()
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

    @PipelineProperty(description = "When the current output exceeds this size it will be closed and a new one created.")
    public void setRollSize(final String size) {
        super.setRollSize(size);
    }

    @PipelineProperty(description = "Choose if you want to split aggregated streams into separate output.", defaultValue = "false")
    public void setSplitAggregatedStreams(final boolean splitAggregatedStreams) {
        super.setSplitAggregatedStreams(splitAggregatedStreams);
    }

    @PipelineProperty(description = "Choose if you want to split individual records into separate output.", defaultValue = "false")
    public void setSplitRecords(final boolean splitRecords) {
        super.setSplitRecords(splitRecords);
    }

    @PipelineProperty(description = "The URL to send data to")
    public void setForwardUrl(final String forwardUrl) {
        this.forwardUrl = forwardUrl;
    }

    @PipelineProperty(description = "How long to wait before we abort sending data due to connection timeout")
    public void setConnectionTimeout(final String string) {
        connectionTimeout = null;
        if (string != null && string.length() > 0) {
            connectionTimeout = ModelStringUtil.parseDurationString(string);
        }
    }

    @PipelineProperty(description = "Should data be sent in chunks and if so how big should the chunks be")
    public void setForwardChunkSize(final String string) {
        this.forwardChunkSize = ModelStringUtil.parseIECByteSizeString(string);
    }

    @PipelineProperty(description = "Should data be compressed when sending", defaultValue = "true")
    public void setUseCompression(final boolean useCompression) {
        this.useCompression = useCompression;
    }

    @PipelineProperty(description = "Which meta data values will be logged in the send log", defaultValue = "guid,feed,system,environment,remotehost,remoteaddress")
    public void setLogMetaKeys(final String string) {
        metaKeySet = getMetaKeySet(string);
    }

    @PipelineProperty(description = "Use JVM SSL config", defaultValue = "true")
    public void setUseJvmSslConfig(final boolean useJvmSslConfig) {
        this.useJvmSslConfig = useJvmSslConfig;
    }

    @PipelineProperty(description = "The key store file path on the server")
    public void setKeyStorePath(final String keyStorePath) {
        sslConfig.setKeyStorePath(keyStorePath);
    }

    @PipelineProperty(description = "The key store type", defaultValue = "JKS")
    public void setKeyStoreType(final String keyStoreType) {
        sslConfig.setKeyStoreType(keyStoreType);
    }

    @PipelineProperty(description = "The key store password")
    public void setKeyStorePassword(final String keyStorePassword) {
        sslConfig.setKeyStorePassword(keyStorePassword);
    }

    @PipelineProperty(description = "The trust store file path on the server")
    public void setTrustStorePath(final String trustStorePath) {
        sslConfig.setTrustStorePath(trustStorePath);
    }

    @PipelineProperty(description = "The trust store type", defaultValue = "JKS")
    public void setTrustStoreType(final String trustStoreType) {
        sslConfig.setTrustStoreType(trustStoreType);
    }

    @PipelineProperty(description = "The trust store password")
    public void setTrustStorePassword(final String trustStorePassword) {
        sslConfig.setTrustStorePassword(trustStorePassword);
    }

    @PipelineProperty(description = "Verify host names", defaultValue = "true")
    public void setHostnameVerificationEnabled(final boolean hostnameVerificationEnabled) {
        sslConfig.setHostnameVerificationEnabled(hostnameVerificationEnabled);
    }

    @PipelineProperty(description = "The SSL protocol to use", defaultValue = "TLSv1.2")
    public void setSslProtocol(final String sslProtocol) {
        sslConfig.setSslProtocol(sslProtocol);
    }

    @PipelineProperty(description = "The request method, e.g. POST", defaultValue = "POST")
    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    @PipelineProperty(description = "The content type", defaultValue = "application/json")
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
