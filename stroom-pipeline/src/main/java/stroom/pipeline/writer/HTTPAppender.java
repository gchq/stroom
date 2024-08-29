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

package stroom.pipeline.writer;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.state.MetaDataHolder;
import stroom.svg.shared.SvgImage;
import stroom.util.NullSafe;
import stroom.util.cert.SSLConfig;
import stroom.util.cert.SSLUtil;
import stroom.util.io.CompressionUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Severity;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

// TODO: 03/05/2023 Consider changing this to use Jersey clients for consistency with the rest of the app.

/**
 * Handler class that forwards the request to a URL.
 */
@ConfigurableElement(
        type = "HTTPAppender",
        description = """
                A destination used to write an output stream to a remote HTTP(S) server.

                This element should be preferred over the deprecated {{< pipe-elm "HttpPostFilter" >}}.
                """,
        category = Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_STREAM)
public class HTTPAppender extends AbstractAppender {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HTTPAppender.class);
    private static final Logger SEND_LOG = LoggerFactory.getLogger("send");
    private static final String DEFAULT_USE_COMPRESSION_PROP_VALUE = "true";
    private static final String DEFAULT_USE_CONTENT_ENCODING_PROP_VALUE = "false";
    @SuppressWarnings("ConstantValue")
    private static final boolean DEFAULT_USE_CONTENT_ENCODING_PROP_VALUE_BOOL =
            Boolean.parseBoolean(DEFAULT_USE_CONTENT_ENCODING_PROP_VALUE);
    private static final String DEFAULT_COMPRESSION_METHOD_PROP_VALUE = CompressorStreamFactory.GZIP;
    private static final String DEFAULT_REQUEST_METHOD_PROP_VALUE = "POST";
    private static final String META_KEYS_DELIMITER = ",";

    // BROTLI is not in our list of support compression algos, see CompressionUtil
    private static final Map<String, String> COMPRESSION_TO_ENCODING_MAP = Map.of(
            CompressorStreamFactory.GZIP, StandardHeaderArguments.CONTENT_ENCODING_GZIP,
            CompressorStreamFactory.DEFLATE, StandardHeaderArguments.CONTENT_ENCODING_DEFLATE,
//            CompressorStreamFactory.BROTLI, StandardHeaderArguments.CONTENT_ENCODING_BROTLI,
            CompressorStreamFactory.ZSTANDARD, StandardHeaderArguments.CONTENT_ENCODING_ZSTD);

    private static final Map<String, String> COMPRESSION_TO_STROOM_COMPRESSION_MAP = Map.of(
            OutputFactory.COMPRESSION_ZIP, StandardHeaderArguments.COMPRESSION_ZIP,
            CompressorStreamFactory.GZIP, StandardHeaderArguments.COMPRESSION_GZIP);

    private static final Set<String> VALID_REQUEST_METHODS = Set.of(
            "GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE");

    private final MetaDataHolder metaDataHolder;
    private final PathCreator pathCreator;
    private final ErrorReceiverProxy errorReceiverProxy;

    private String forwardUrl;
    private Long connectionTimeout;
    private Long readTimeout;
    private Long forwardChunkSize;
    private Set<CIKey> metaKeySet = getMetaKeySet("guid,feed,system,environment,remotehost,remoteaddress");

    private HttpURLConnection connection;
    private final OutputFactory outputStreamSupport;
    private long startTimeMs;

    private boolean useJvmSslConfig = true;
    private final SSLConfig.Builder sslConfigBuilder = SSLConfig.builder();

    private String requestMethod = DEFAULT_REQUEST_METHOD_PROP_VALUE;
    private String contentType = "application/json";

    private boolean httpHeadersIncludeStreamMetaData = true;
    private String httpHeadersUserDefinedHeader1;
    private String httpHeadersUserDefinedHeader2;
    private String httpHeadersUserDefinedHeader3;
    private final Map<CIKey, Set<String>> requestProperties = new HashMap<>();
    // Comma delimited meta keys
    private String httpHeadersStreamMetaDataAllowList;
    // Comma delimited meta keys
    private String httpHeadersStreamMetaDataDenyList;
    private boolean useContentEncodingHeader = DEFAULT_USE_CONTENT_ENCODING_PROP_VALUE_BOOL;


    @Inject
    HTTPAppender(final ErrorReceiverProxy errorReceiverProxy,
                 final MetaDataHolder metaDataHolder,
                 final PathCreator pathCreator) {
        super(errorReceiverProxy);
        this.metaDataHolder = metaDataHolder;
        this.pathCreator = pathCreator;
        this.outputStreamSupport = new OutputFactory(metaDataHolder);
        this.errorReceiverProxy = errorReceiverProxy;

        // Ensure outputStreamSupport has the defaults for HttpAppender
        //noinspection ConstantValue
        setUseCompression(Boolean.parseBoolean(DEFAULT_USE_COMPRESSION_PROP_VALUE));
        setCompressionMethod(DEFAULT_COMPRESSION_METHOD_PROP_VALUE);
        setUseContentEncodingHeader(DEFAULT_USE_CONTENT_ENCODING_PROP_VALUE_BOOL);
        this.outputStreamSupport.setUseCompression(true);
    }

    @Override
    protected Output createOutput() {
        try {
            startTimeMs = System.currentTimeMillis();
            validateElementProperties();
            final AttributeMap effectiveAttributeMap = httpHeadersIncludeStreamMetaData
                    ? cloneFromStreamMeta()
                    : new AttributeMap();

            addAttributeIfHeaderDefined(effectiveAttributeMap, httpHeadersUserDefinedHeader1);
            addAttributeIfHeaderDefined(effectiveAttributeMap, httpHeadersUserDefinedHeader2);
            addAttributeIfHeaderDefined(effectiveAttributeMap, httpHeadersUserDefinedHeader3);

            LOGGER.info(() -> "createOutputStream() - " + forwardUrl + " Sending request " + effectiveAttributeMap);

            URL url = URI.create(forwardUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            if (LOGGER.isDebugEnabled()) {
                requestProperties.clear();
            }

            if (connection instanceof final HttpsURLConnection httpsURLConnection) {
                final SSLConfig sslConfig = sslConfigBuilder.build();
                if (!useJvmSslConfig) {
                    LOGGER.info(() -> "Configuring SSLSocketFactory for destination " + forwardUrl);
                    final SSLSocketFactory sslSocketFactory = SSLUtil.createSslSocketFactory(
                            sslConfig, pathCreator);
                    SSLUtil.applySSLConfiguration(connection, sslSocketFactory, sslConfig);
                } else if (!sslConfig.isHostnameVerificationEnabled()) {
                    SSLUtil.disableHostnameVerification(httpsURLConnection);
                }
            }

            if (connectionTimeout != null) {
                connection.setConnectTimeout(connectionTimeout.intValue());
            }
            if (readTimeout != null) {
                connection.setReadTimeout(readTimeout.intValue());
            } else {
                connection.setReadTimeout(0);
            }

            connection.setRequestMethod(requestMethod);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setDoOutput(true);
            connection.setDoInput(true);

            setCompressionProperties(outputStreamSupport, connection);

            for (Entry<CIKey, String> entry : effectiveAttributeMap.entrySet()) {
                addRequestProperty(connection, entry.getKey(), entry.getValue());
            }

            if (forwardChunkSize != null) {
                LOGGER.debug(() -> "handleHeader() - setting ChunkedStreamingMode = " + forwardChunkSize);
                connection.setChunkedStreamingMode(forwardChunkSize.intValue());
            }
            if (LOGGER.isDebugEnabled()) {
                logConnectionToDebug();
            }
            // Be careful! Lots of methods on HttpURLConnection will do an implicit connect(), e.g.
            // getResponseCode(), and some will throw if already connected.
            connection.connect();

            final FilterOutputStream filterOutputStream = createOutputStream(connection);

            return outputStreamSupport.create(filterOutputStream, effectiveAttributeMap);
        } catch (final IOException e) {
            LOGGER.debug(e::getMessage, e);
            closeConnection();
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            closeConnection();
            throw e;
        }
    }

    private void validateElementProperties() {
        if (NullSafe.isBlankString(forwardUrl)) {
            fatal("Property forwardUrl has no value.");
        }

        if (NullSafe.isEmptyString(requestMethod)) {
            fatal(LogUtil.message(
                    "Property requestMethod has no value. You must set a destination URL for {} to forward to.",
                    getElementId()));
        } else if (!VALID_REQUEST_METHODS.contains(requestMethod)) {
            fatal(LogUtil.message("Property requestMethod has an invalid value '{}'. " +
                            "Valid values are [{}]",
                    requestMethod, setToSortedCsvStr(VALID_REQUEST_METHODS)));
        }

        final String sslProtocol = sslConfigBuilder.getSslProtocol();
        if (NullSafe.isNonEmptyString(sslProtocol)) {
            final Set<String> activeSSLProtocols = getActiveSSLProtocols();
            if (!NullSafe.isEmptyCollection(activeSSLProtocols)
                    && !activeSSLProtocols.contains(sslProtocol)) {
                fatal(LogUtil.message("Property sslProtocol has an invalid value '{}'. Valid values are [{}]",
                        sslProtocol, setToSortedCsvStr(activeSSLProtocols)));
            }
        }

        if (httpHeadersIncludeStreamMetaData) {
            if (NullSafe.isNonBlankString(httpHeadersStreamMetaDataAllowList)
                    && NullSafe.isNonBlankString(httpHeadersStreamMetaDataDenyList)) {
                warn("Properties httpHeadersStreamMetaDataAllowList and httpHeadersStreamMetaDataDenyList both " +
                        "have a value. The value of httpHeadersStreamMetaDataDenyList will be ignored.");
            }
        } else {
            if (NullSafe.isNonBlankString(httpHeadersStreamMetaDataAllowList)) {
                warn(LogUtil.message(
                        "Property httpHeadersStreamMetaDataAllowList has value '{}' but this will be ignored " +
                                "because httpHeadersIncludeStreamMetaData is false.",
                        httpHeadersStreamMetaDataAllowList));
            }
            if (NullSafe.isNonBlankString(httpHeadersStreamMetaDataDenyList)) {
                warn(LogUtil.message(
                        "Property httpHeadersStreamMetaDataDenyList has value '{}' but this will be ignored " +
                                "because httpHeadersIncludeStreamMetaData is false.",
                        httpHeadersStreamMetaDataDenyList));
            }
        }
    }

    private void warn(final String message) {
        errorReceiverProxy.log(Severity.WARNING, null, getElementId(), message, null);
    }

    /**
     * A slightly hacky way of checking if connection is actually connected, as {@link URLConnection}
     * doesn't provide a method to do that. Lot of methods on {@link HttpURLConnection} will do
     * an implicit connect() so it is otherwise hard to know if a connection is established or not.
     */
    public static boolean isConnected(final URLConnection connection) {
        try {
            // Essentially a no-op if not currently connected
            connection.setDoOutput(connection.getDoOutput()); // throws IllegalStateException if connected
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    @NotNull
    private AttributeMap cloneFromStreamMeta() {

        final AttributeMap streamMetaAttributeMap = metaDataHolder.getMetaData();
        LOGGER.debug(() -> LogUtil.message(
                "streamMetaAttributeMap:\n{}\nstreamMetaDataAllowList: {}\nstreamMetaDataDenyList: {}",
                attributeMapToLines(streamMetaAttributeMap, "  "),
                httpHeadersStreamMetaDataAllowList, httpHeadersStreamMetaDataDenyList));

        streamMetaAttributeMap.computeIfAbsent(StandardHeaderArguments.GUID, k -> UUID.randomUUID().toString());
        final AttributeMap clonedAttributeMap = AttributeMapUtil.cloneAllowable(streamMetaAttributeMap);
        final AttributeMap effectiveAttributeMap;

        // Allow trumps deny
        if (NullSafe.isNonBlankString(httpHeadersStreamMetaDataAllowList)) {
            effectiveAttributeMap = new AttributeMap();
            final Set<CIKey> allowSet = keysToSet(httpHeadersStreamMetaDataAllowList);
            clonedAttributeMap.forEach((key, value) -> {
                if (allowSet.contains(key)) {
                    effectiveAttributeMap.put(key, value);
                }
            });
        } else {
            effectiveAttributeMap = clonedAttributeMap;
            if (NullSafe.isNonBlankString(httpHeadersStreamMetaDataDenyList)) {
                final Set<CIKey> denySet = keysToSet(httpHeadersStreamMetaDataDenyList);
                effectiveAttributeMap.removeAll(denySet);
            }
        }

        LOGGER.debug(() -> LogUtil.message("effectiveAttributeMap:\n{}",
                attributeMapToLines(effectiveAttributeMap, "  ")));
        return effectiveAttributeMap;
    }

    private String attributeMapToLines(final AttributeMap attributeMap, final String indent) {
        if (NullSafe.isTrue(attributeMap, AttributeMap::isEmpty)) {
            return "";
        } else {
            return attributeMap.entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey())
                    .map(entry ->
                            NullSafe.string(indent) + entry.getKey() + ": " + entry.getValue())
                    .collect(Collectors.joining("\n"));
        }
    }

    private Set<CIKey> keysToSet(final String keys) {
        if (NullSafe.isBlankString(keys)) {
            return Collections.emptySet();
        } else {
            return NullSafe.stream(keys.split(META_KEYS_DELIMITER))
                    .filter(Objects::nonNull)
                    .filter(Predicate.not(String::isBlank))
                    .map(String::trim)
                    .map(CIKey::of)
                    .collect(Collectors.toSet());
        }
    }

    private void setCompressionProperties(final OutputFactory outputStreamSupport,
                                          final HttpURLConnection connection) {
        if (outputStreamSupport.isUseCompression()) {
            // This is the method as configured in the pipe elm. It may correspond to a different
            // value in 'Compression' and 'Content-Encoding' headers, e.g. `gz' => `gzip`.
            // Not all compressionMethods are valid as a 'Compression' or 'Content-Encoding' header value.
            final String compressionMethod = outputStreamSupport.getCompressionMethod();
            final String contentEncoding = COMPRESSION_TO_ENCODING_MAP.get(compressionMethod);
            final String stroomCompression = COMPRESSION_TO_STROOM_COMPRESSION_MAP.get(compressionMethod);
            LOGGER.debug("compressionMethod: {}, contentEncoding: {}, stroomCompression: {}",
                    compressionMethod, contentEncoding, stroomCompression);

            // We can't set both else the server may decode using Content-Encoding then stroom will
            // try to decode plain text. User can decide which header to use based on the destination
            // and what it supports.
            if (useContentEncodingHeader) {
                // use Content-Encoding if sending to a non-stroom end point as it is a HTTP standard
                // Only some of the supported compression methods are valid content-encoding values.
                if (NullSafe.isNonEmptyString(contentEncoding)) {
                    addRequestProperty(connection, StandardHeaderArguments.CONTENT_ENCODING, contentEncoding);
                } else {
                    final String validCompressionMethods = keySetToSortedCsvStr(COMPRESSION_TO_ENCODING_MAP);
                    fatal("Properties useCompression and useContentEncodingHeader are both 'true', but '" +
                            compressionMethod + "' is not a valid compressionMethod for use with the '" +
                            StandardHeaderArguments.CONTENT_ENCODING + "' HTTP header. Valid " +
                            "compressionMethod values are [" + validCompressionMethods + "].");
                }
            } else {
                // use 'Compression' header for sending to a stroom(-proxy)?
                // Stroom only supports gzip/zip at the mo.
                if (NullSafe.isNonEmptyString(stroomCompression)) {
                    addRequestProperty(connection, StandardHeaderArguments.COMPRESSION, stroomCompression);
                } else {
                    final String validCompressionMethods = keySetToSortedCsvStr(COMPRESSION_TO_STROOM_COMPRESSION_MAP);
                    fatal("Property useCompression is 'true' and useContentEncodingHeader is 'false', but '" +
                            compressionMethod + "' is not a valid compressionMethod for use with the '" +
                            StandardHeaderArguments.COMPRESSION + "' HTTP header. Valid " +
                            "compressionMethod values are [" + validCompressionMethods + "].");
                }
            }
        } else {
            LOGGER.debug("useCompression is false");
        }
    }

    private static String keySetToSortedCsvStr(final Map<String, ?> map) {
        if (NullSafe.isEmptyMap(map)) {
            return "";
        } else {
            return setToSortedCsvStr(map.keySet());
        }
    }

    private static String setToSortedCsvStr(final Set<String> set) {
        if (NullSafe.isEmptyCollection(set)) {
            return "";
        } else {
            return set.stream()
                    .sorted()
                    .map(str -> "'" + str + "'")
                    .collect(Collectors.joining(", "));
        }
    }

    @NotNull
    private FilterOutputStream createOutputStream(final HttpURLConnection connection) throws IOException {
        return new FilterOutputStream(connection.getOutputStream()) {
            @Override
            public void close() throws IOException {
                super.close();
                closeConnection();
            }
        };
    }

    private void addRequestProperty(final HttpURLConnection connection,
                                    final CIKey key,
                                    final String value) {
        connection.addRequestProperty(key.get(), value);

        // It's not possible to inspect the connection to see what req props have been set
        // as that implicitly opens the connection, so store them in our own map for logging later
        if (LOGGER.isDebugEnabled()) {
            if (!CIKey.isNull(key)) {
                requestProperties.computeIfAbsent(key, k -> new HashSet<>())
                        .add(value);
            } else {
                LOGGER.debug("Null key with value '{}'", value);
            }
        }
    }

    private void logConnectionToDebug() {
        LOGGER.debug(() -> LogUtil.message("About to connect to {} with requestMethod: {}, contentType: {}, " +
                        "readTimeout: {}, connectionTimeout: {}, useCompression: {}, compressionMethod: {}, " +
                        "forwardChunkSize: {}, request properties:\n{}",
                forwardUrl,
                requestMethod,
                contentType,
                readTimeout,
                connectionTimeout,
                outputStreamSupport.isUseCompression(),
                outputStreamSupport.getCompressionMethod(),
                forwardChunkSize,
                NullSafe.map(requestProperties)
                        .entrySet()
                        .stream()
                        .map(entry -> "  "
                                + entry.getKey() + ": ["
                                + String.join(", ", NullSafe.set(entry.getValue()))
                                + "]")
                        .collect(Collectors.joining("\n"))));
    }

    private void addAttributeIfHeaderDefined(final AttributeMap attributeMap, final String headerText) {
        if (headerText == null || headerText.length() < 3) {
            return;
        }
        if (!headerText.contains(":")) {
            throw new IllegalArgumentException("Additional Headers must be specified as 'Name: Value', but '"
                    + headerText + "' supplied.");
        }

        int delimiterPos = headerText.indexOf(':');
        attributeMap.put(headerText.substring(0, delimiterPos), headerText.substring(delimiterPos + 1));
        LOGGER.debug("Added '{}' to {}", headerText, attributeMap);
    }

    private void closeConnection() {
        // Exception may have happened before the connection was opened
        if (connection != null) {
            if (isConnected(connection)) {
                LOGGER.debug(() -> "closeConnection() - header fields " + connection.getHeaderFields());
                int responseCode = -1;
                try {
                    // This will call getResponseCode() which implicitly calls connect(). Not what we
                    // want if we haven't already connected.
                    responseCode = StroomStreamException.checkConnectionResponse(connection);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } finally {
                    long bytes = getCurrentOutputSize();
                    final long duration = System.currentTimeMillis() - startTimeMs;
                    final AttributeMap attributeMap = metaDataHolder.getMetaData();
                    log(SEND_LOG, attributeMap, "SEND", forwardUrl, responseCode, bytes, duration);

                    connection.disconnect();
                    connection = null;
                }
            } else {
                LOGGER.debug("Not connected");
            }
        } else {
            LOGGER.debug("connection is null");
        }
    }

    private void log(final Logger logger,
                     final AttributeMap attributeMap,
                     final String type,
                     final String url,
                     final int responseCode,
                     final long bytes,
                     final long duration) {
        if (logger.isInfoEnabled() && !metaKeySet.isEmpty()) {
            final AttributeMap filteredMap = attributeMap.filterIncluding(metaKeySet);
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

    private Set<CIKey> getMetaKeySet(final String csv) {
        if (NullSafe.isEmptyString(csv)) {
            return Collections.emptySet();
        }

        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(CIKey::ofIgnoringCase)
                .collect(Collectors.toSet());
    }

    private static Set<String> getActiveSSLProtocols() {
        try {
            return NullSafe.asSet(SSLContext.getDefault().createSSLEngine().getEnabledProtocols());
        } catch (Exception e) {
            LOGGER.debug("Unable to determine SSL protocols", e);
            return Collections.emptySet();
        }
    }

    @Override
    @PipelineProperty(description = "When the current output exceeds this size it will be closed and a " +
            "new one created. " +
            "Size is either specified in bytes e.g. '1024' or with a IEC unit suffix, e.g. '1K', '1M', '1G', etc.",
            displayPriority = 2)
    public void setRollSize(final String size) {
        super.setRollSize(size);
    }

    @Override
    @PipelineProperty(description = "Choose if you want to split aggregated streams into separate output.",
            defaultValue = "false",
            displayPriority = 3)
    public void setSplitAggregatedStreams(final boolean splitAggregatedStreams) {
        super.setSplitAggregatedStreams(splitAggregatedStreams);
    }

    @Override
    @PipelineProperty(description = "Choose if you want to split individual records into separate output.",
            defaultValue = "false",
            displayPriority = 4)
    public void setSplitRecords(final boolean splitRecords) {
        super.setSplitRecords(splitRecords);
    }

    @PipelineProperty(description = "The URL to send data to.",
            displayPriority = 5)
    public void setForwardUrl(final String forwardUrl) {
        this.forwardUrl = forwardUrl;
    }

    @PipelineProperty(description = "How long to wait before we abort sending data due to connection timeout. " +
            "The timeout is specified as either milliseconds, e.g. '60000' or with a duration suffix, e.g. '500ms', " +
            "'2s', '1m', etc.",
            displayPriority = 6)
    public void setConnectionTimeout(final String string) {
        connectionTimeout = null;
        if (NullSafe.isNonEmptyString(string)) {
            connectionTimeout = ModelStringUtil.parseDurationString(string);
        }
    }

    @PipelineProperty(description = "How long to wait for data to be available before closing the connection. " +
            "The timeout is specified as either milliseconds, e.g. '60000' or with a duration suffix, e.g. '500ms', " +
            "'2s', '1m', etc.",
            displayPriority = 7)
    public void setReadTimeout(final String string) {
        readTimeout = null;
        if (NullSafe.isNonEmptyString(string)) {
            readTimeout = ModelStringUtil.parseDurationString(string);
        }
    }

    @PipelineProperty(description = "Should data be sent in chunks and if so how big should the chunks be. " +
            "Size is either specified in bytes e.g. '1024' or with a IEC unit suffix, e.g. '1K', '1M', '1G', etc.",
            displayPriority = 8)
    public void setForwardChunkSize(final String string) {
        this.forwardChunkSize = ModelStringUtil.parseIECByteSizeString(string);
    }

    @PipelineProperty(description = "Should data be compressed when sending",
            defaultValue = DEFAULT_USE_COMPRESSION_PROP_VALUE,
            displayPriority = 9)
    public void setUseCompression(final boolean useCompression) {
        outputStreamSupport.setUseCompression(useCompression);
    }

    @PipelineProperty(description = "Whether to use the 'Content-Encoding' HTTP header when " +
            "useCompression is 'true'. If 'false' (the default), the 'Compression' header will be used, " +
            "which is supported by ." +
            "Stroom/Stroom-Proxy destinations. 'Content-Encoding' would be required for other destinations, " +
            "but is only applicable for compression types 'gz', 'zstd' or 'deflate'.",
            defaultValue = DEFAULT_USE_CONTENT_ENCODING_PROP_VALUE,
            displayPriority = 10)
    public void setUseContentEncodingHeader(final boolean useContentEncodingHeader) {
        this.useContentEncodingHeader = useContentEncodingHeader;
    }

    @PipelineProperty(
            description = "Compression method to apply, if compression is enabled. Supported values: " +
                    CompressionUtil.SUPPORTED_COMPRESSORS + ".",
            defaultValue = DEFAULT_COMPRESSION_METHOD_PROP_VALUE,
            displayPriority = 11)
    public void setCompressionMethod(final String compressionMethod) {
        try {
            outputStreamSupport.setCompressionMethod(compressionMethod);
        } catch (final RuntimeException e) {
            error(e.getMessage(), e);
            throw e;
        }
    }

    @PipelineProperty(
            description = "Specifies Which meta data keys will have their values logged in the send log. A Comma " +
                    "delimited string of keys.",
            defaultValue = "guid,feed,system,environment,remotehost,remoteaddress",
            displayPriority = 12)
    public void setLogMetaKeys(final String string) {
        metaKeySet = getMetaKeySet(string);
    }

    @PipelineProperty(description = "Use JVM SSL config. " +
            "Set this to true if the Stroom node has been configured with key/trust stores using java system " +
            "properties like 'javax.net.ssl.keyStore'." +
            "Set this to false if you are explicitly setting key/trust store properties on this HttpAppender.",
            defaultValue = "true",
            displayPriority = 13)
    public void setUseJvmSslConfig(final boolean useJvmSslConfig) {
        this.useJvmSslConfig = useJvmSslConfig;
    }

    @PipelineProperty(description = "The key store file path on the server",
            displayPriority = 14)
    public void setKeyStorePath(final String keyStorePath) {
        sslConfigBuilder.withKeyStorePath(keyStorePath);
    }

    @PipelineProperty(description = "The key store type. " +
            "Valid values are ['JCEKS', 'JKS', 'DKS', 'PKCS11', 'PKCS12'].",
            defaultValue = "JKS",
            displayPriority = 15)
    public void setKeyStoreType(final String keyStoreType) {
        sslConfigBuilder.withKeyStoreType(keyStoreType);
    }

    @PipelineProperty(description = "The key store password",
            displayPriority = 16)
    public void setKeyStorePassword(final String keyStorePassword) {
        sslConfigBuilder.withKeyStorePassword(keyStorePassword);
    }

    @PipelineProperty(description = "The trust store file path on the server",
            displayPriority = 17)
    public void setTrustStorePath(final String trustStorePath) {
        sslConfigBuilder.withTrustStorePath(trustStorePath);
    }

    @PipelineProperty(description = "The trust store type " +
            "Valid values are ['JCEKS', 'JKS', 'DKS', 'PKCS11', 'PKCS12'].",
            defaultValue = "JKS",
            displayPriority = 18)
    public void setTrustStoreType(final String trustStoreType) {
        sslConfigBuilder.withTrustStoreType(trustStoreType);
    }

    @PipelineProperty(description = "The trust store password",
            displayPriority = 19)
    public void setTrustStorePassword(final String trustStorePassword) {
        sslConfigBuilder.withTrustStorePassword(trustStorePassword);
    }

    @PipelineProperty(description = "Set this to true to verify that the destination host name matches against " +
            "the host names in the certificate supplied by the destination server.",
            defaultValue = "true",
            displayPriority = 20)
    public void setHostnameVerificationEnabled(final boolean hostnameVerificationEnabled) {
        sslConfigBuilder.withHostnameVerificationEnabled(hostnameVerificationEnabled);
    }

    @PipelineProperty(description = "The SSL protocol to use",
            defaultValue = "TLSv1.2",
            displayPriority = 21)
    public void setSslProtocol(final String sslProtocol) {
        sslConfigBuilder.withSslProtocol(NullSafe.get(sslProtocol, String::trim));
    }

    @PipelineProperty(description = "The HTTP request method. Valid values are " +
            "GET, POST, HEAD, OPTIONS, PUT, DELETE and TRACE.",
            defaultValue = DEFAULT_REQUEST_METHOD_PROP_VALUE,
            displayPriority = 22)
    public void setRequestMethod(String requestMethod) {
        this.requestMethod = NullSafe.get(requestMethod, String::trim, String::toUpperCase);
    }

    @PipelineProperty(description = "The content type",
            defaultValue = "application/json",
            displayPriority = 23)
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @PipelineProperty(description = "Provide stream metadata as HTTP headers",
            defaultValue = "true",
            displayPriority = 24)
    public void setHttpHeadersIncludeStreamMetaData(final boolean newValue) {
        this.httpHeadersIncludeStreamMetaData = newValue;
    }

    @PipelineProperty(description = "Comma delimited list of stream meta data keys to include as HTTP headers. " +
            "Only works when httpHeadersIncludeStreamMetaData is set to true. If empty all headers are sent, " +
            "unless httpHeadersStreamMetaDataDenyList is used. " +
            "If httpHeadersStreamMetaDataAllowList contains keys, httpHeadersStreamMetaDataDenyList is ignored.",
            defaultValue = "",
            displayPriority = 25)
    public void setHttpHeadersStreamMetaDataAllowList(final String newValue) {
        this.httpHeadersStreamMetaDataAllowList = newValue;
    }

    @PipelineProperty(description = "Comma delimited list of stream meta data keys to exclude as HTTP headers. " +
            "Only works when httpHeadersIncludeStreamMetaData is set to true. If empty all headers are sent, " +
            "unless httpHeadersStreamMetaDataAllowList is used. " +
            "If httpHeadersStreamMetaDataAllowList contains keys, httpHeadersStreamMetaDataDenyList is ignored.",
            defaultValue = "",
            displayPriority = 26)
    public void setHttpHeadersStreamMetaDataDenyList(final String newValue) {
        this.httpHeadersStreamMetaDataDenyList = newValue;
    }

    @PipelineProperty(description = "Additional HTTP Header 1, format is 'HeaderName: HeaderValue'",
            displayPriority = 27)
    public void setHttpHeadersUserDefinedHeader1(final String headerText) {
        this.httpHeadersUserDefinedHeader1 = headerText;
    }

    @PipelineProperty(description = "Additional HTTP Header 2, format is 'HeaderName: HeaderValue'",
            displayPriority = 28)
    public void setHttpHeadersUserDefinedHeader2(final String headerText) {
        this.httpHeadersUserDefinedHeader2 = headerText;
    }

    @PipelineProperty(description = "Additional HTTP Header 3, format is 'HeaderName: HeaderValue'",
            displayPriority = 29)
    public void setHttpHeadersUserDefinedHeader3(final String headerText) {
        this.httpHeadersUserDefinedHeader3 = headerText;
    }
}
