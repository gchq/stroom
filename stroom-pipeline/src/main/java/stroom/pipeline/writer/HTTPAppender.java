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
import stroom.util.cert.SSLConfig;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientUtil;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.io.CompressionUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.jersey.HttpClientCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;

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
    private static final String META_KEYS_DEFAULT = "guid,receiptid,feed,system,environment,remotehost,remoteaddress";

    private final MetaDataHolder metaDataHolder;
    private final TempDirProvider tempDirProvider;
    private final HttpClientCache httpClientCache;
    private final ErrorReceiverProxy errorReceiverProxy;

    private String forwardUrl;
    private Long timeout;
    private Long connectionTimeout;
    private Long connectionRequestTimeout;
    private Long timeToLive;
    private Long forwardChunkSize;
    private Set<String> metaKeySet = getMetaKeySet(META_KEYS_DEFAULT);

    private final OutputFactory outputStreamSupport;
    private long startTimeMs;

    private final SSLConfig.Builder sslConfigBuilder = SSLConfig.builder();

    private String requestMethod = DEFAULT_REQUEST_METHOD_PROP_VALUE;
    private String contentType = "application/json";

    private boolean httpHeadersIncludeStreamMetaData = true;
    private String httpHeadersUserDefinedHeader1;
    private String httpHeadersUserDefinedHeader2;
    private String httpHeadersUserDefinedHeader3;
    private final Map<String, Set<String>> requestProperties = new HashMap<>();
    // Comma delimited meta keys
    private String httpHeadersStreamMetaDataAllowList;
    // Comma delimited meta keys
    private String httpHeadersStreamMetaDataDenyList;
    private boolean useContentEncodingHeader = DEFAULT_USE_CONTENT_ENCODING_PROP_VALUE_BOOL;
    private Path currentPath;

    @Inject
    HTTPAppender(final ErrorReceiverProxy errorReceiverProxy,
                 final MetaDataHolder metaDataHolder,
                 final TempDirProvider tempDirProvider,
                 final HttpClientCache httpClientCache) {
        super(errorReceiverProxy);
        this.metaDataHolder = metaDataHolder;
        this.tempDirProvider = tempDirProvider;
        this.httpClientCache = httpClientCache;
        this.outputStreamSupport = new OutputFactory(metaDataHolder);
        this.errorReceiverProxy = errorReceiverProxy;

        // Ensure outputStreamSupport has the defaults for HttpAppender
        //noinspection ConstantValue
        setUseCompression(Boolean.parseBoolean(DEFAULT_USE_COMPRESSION_PROP_VALUE));
        setCompressionMethod(DEFAULT_COMPRESSION_METHOD_PROP_VALUE);
        setUseContentEncodingHeader(DEFAULT_USE_CONTENT_ENCODING_PROP_VALUE_BOOL);
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


            final HttpTlsConfiguration httpTlsConfiguration = HttpClientUtil
                    .getHttpTlsConfiguration(sslConfigBuilder.build());
            final HttpClientConfiguration.Builder builder = HttpClientConfiguration.builder();
            builder.tlsConfiguration(httpTlsConfiguration);

            if (timeout != null) {
                builder.timeout(StroomDuration.ofMillis(timeout.intValue()));
            }
            if (connectionTimeout != null) {
                builder.connectionTimeout(StroomDuration.ofMillis(connectionTimeout.intValue()));
            }
            if (connectionRequestTimeout != null) {
                builder.connectionRequestTimeout(StroomDuration.ofMillis(connectionRequestTimeout.intValue()));
            }
            if (timeToLive != null) {
                builder.timeToLive(StroomDuration.ofMillis(timeToLive.intValue()));
            }

            final HttpClientConfiguration httpClientConfiguration = builder.build();
            if (LOGGER.isDebugEnabled()) {
                requestProperties.clear();
            }

            currentPath = tempDirProvider.get().resolve("HTTPAppender-" + UUID.randomUUID());
            final FilterOutputStream outputStream = new FilterOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(currentPath))) {
                @Override
                public void close() throws IOException {
                    super.close();
                    postFile(httpClientConfiguration, currentPath, effectiveAttributeMap);
                }
            };
            return outputStreamSupport.create(outputStream, effectiveAttributeMap);
        } catch (final IOException e) {
            LOGGER.debug(e::getMessage, e);
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    private void postFile(final HttpClientConfiguration httpClientConfiguration,
                          final Path file,
                          final AttributeMap effectiveAttributeMap) throws IOException {
        try {
            final HttpUriRequestBase request =
                    new HttpUriRequestBase(requestMethod, URI.create(forwardUrl));
            request.addHeader("Content-Type", contentType);
            request.setEntity(new FileEntity(file.toFile(), ContentType.create(contentType)));

            setCompressionProperties(outputStreamSupport, request);

            for (final Entry<String, String> entry : effectiveAttributeMap.entrySet()) {
                addRequestProperty(request, entry.getKey(), entry.getValue());
            }

//        if (forwardChunkSize != null) {
//            LOGGER.debug(() -> "handleHeader() - setting ChunkedStreamingMode = " + forwardChunkSize);
//            connection.setChunkedStreamingMode(forwardChunkSize.intValue());
//        }
            if (LOGGER.isDebugEnabled()) {
                logConnectionToDebug();
            }


            final HttpClient httpClient = httpClientCache.get(httpClientConfiguration);
            httpClient.execute(request, response -> {

                LOGGER.debug(() -> "closeConnection() - header fields " +
                                   Arrays.toString(response.getHeaders()));
                int responseCode = response.getCode();
                try {
                    responseCode = checkResponse(response);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } finally {
                    final long bytes = getCurrentOutputSize();
                    final long duration = System.currentTimeMillis() - startTimeMs;
                    final AttributeMap attributeMap = metaDataHolder.getMetaData();
                    log(SEND_LOG, attributeMap, "SEND", forwardUrl, responseCode, bytes, duration);
                }

                return response.getCode();
            });
        } finally {
            Files.deleteIfExists(file);
        }
    }

    public static int checkResponse(final HttpResponse response) {
        final int responseCode;
        try {
            LOGGER.debug(() -> "Connection response " + response.getCode() + ": " + response.getReasonPhrase());

            responseCode = response.getCode();
            if (responseCode != 200) {
                final String message = response.getReasonPhrase();

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Connection response " + responseCode + ": " + message);
                }

                int stroomStatus = -1;
                final Header[] headers = response.getHeaders(StandardHeaderArguments.STROOM_STATUS);
                if (headers.length > 0) {
                    final String value = headers[0].getValue();
                    if (value != null) {
                        try {
                            stroomStatus = Integer.parseInt(value);
                        } catch (final NumberFormatException e) {
                            LOGGER.debug(e::getMessage, e);
                        }
                    }
                }

                if (stroomStatus != -1) {
                    throw new StroomStreamException(StroomStatusCode.getStroomStatusCode(stroomStatus), message);
                } else {
                    throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, message);
                }
            }
        } catch (final Exception ioEx) {
            LOGGER.debug(ioEx.getMessage(), ioEx);
            throw new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR,
                    ioEx.getMessage() != null
                            ? ioEx.getMessage()
                            : ioEx.toString());
        }
        return responseCode;
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

    @NotNull
    private AttributeMap cloneFromStreamMeta() {

        final AttributeMap streamMetaAttributeMap = metaDataHolder.getMetaData();
        LOGGER.debug(() -> LogUtil.message(
                "streamMetaAttributeMap:\n{}\nstreamMetaDataAllowList: {}\nstreamMetaDataDenyList: {}",
                attributeMapToLines(streamMetaAttributeMap, "  "),
                httpHeadersStreamMetaDataAllowList, httpHeadersStreamMetaDataDenyList));

        streamMetaAttributeMap.putRandomUuidIfAbsent(StandardHeaderArguments.GUID);
        final AttributeMap clonedAttributeMap = AttributeMapUtil.cloneAllowable(streamMetaAttributeMap);
        final AttributeMap effectiveAttributeMap;

        // Allow trumps deny
        if (NullSafe.isNonBlankString(httpHeadersStreamMetaDataAllowList)) {
            effectiveAttributeMap = new AttributeMap();
            final Set<String> allowSet = keysToSet(httpHeadersStreamMetaDataAllowList);
            clonedAttributeMap.forEach((key, value) -> {
                if (allowSet.contains(key.toLowerCase())) {
                    effectiveAttributeMap.put(key, value);
                }
            });
        } else {
            effectiveAttributeMap = clonedAttributeMap;
            if (NullSafe.isNonBlankString(httpHeadersStreamMetaDataDenyList)) {
                final Set<String> denySet = keysToSet(httpHeadersStreamMetaDataDenyList);
                effectiveAttributeMap.removeAll(denySet);
            }
        }

        LOGGER.debug(() -> LogUtil.message("effectiveAttributeMap:\n{}",
                attributeMapToLines(effectiveAttributeMap, "  ")));
        return effectiveAttributeMap;
    }

    private String attributeMapToLines(final AttributeMap attributeMap, final String indent) {
        if (NullSafe.isEmptyMap(attributeMap)) {
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

    private Set<String> keysToSet(final String keys) {
        if (NullSafe.isBlankString(keys)) {
            return Collections.emptySet();
        } else {
            return NullSafe.stream(keys.split(META_KEYS_DELIMITER))
                    .filter(Objects::nonNull)
                    .filter(Predicate.not(String::isBlank))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        }
    }

    private void setCompressionProperties(final OutputFactory outputStreamSupport,
                                          final HttpUriRequestBase request) {
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
                    addRequestProperty(request, StandardHeaderArguments.CONTENT_ENCODING, contentEncoding);
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
                    addRequestProperty(request, StandardHeaderArguments.COMPRESSION, stroomCompression);
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

//    @NotNull
//    private FilterOutputStream createOutputStream(final HttpURLConnection connection) throws IOException {
//        return new FilterOutputStream(connection.getOutputStream()) {
//            @Override
//            public void close() throws IOException {
//                super.close();
//                closeConnection();
//            }
//        };
//    }

    private void addRequestProperty(final HttpUriRequestBase request,
                                    final String key,
                                    final String value) {
        request.addHeader(key, value);

        // It's not possible to inspect the connection to see what req props have been set
        // as that implicitly opens the connection, so store them in our own map for logging later
        if (LOGGER.isDebugEnabled()) {
            if (key != null) {
                requestProperties.computeIfAbsent(key, k -> new HashSet<>())
                        .add(value);
            } else {
                LOGGER.debug("Null key with value '{}'", value);
            }
        }
    }

    private void logConnectionToDebug() {
        LOGGER.debug(() -> LogUtil.message("About to connect to {} with " +
                                           "requestMethod: {}, " +
                                           "contentType: {}, " +
                                           "timeout: {}, " +
                                           "connectionTimeout: {}, " +
                                           "connectionRequestTimeout: {}, " +
                                           "timeToLive: {}, " +
                                           "useCompression: {}, " +
                                           "compressionMethod: {}, " +
                                           "forwardChunkSize: {}, " +
                                           "request properties:\n{}",
                forwardUrl,
                requestMethod,
                contentType,
                timeout,
                connectionTimeout,
                connectionRequestTimeout,
                timeToLive,
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

        final int delimiterPos = headerText.indexOf(':');
        attributeMap.put(headerText.substring(0, delimiterPos), headerText.substring(delimiterPos + 1));
        LOGGER.debug("Added '{}' to {}", headerText, attributeMap);
    }

    private void log(final Logger logger,
                     final AttributeMap attributeMap,
                     final String type,
                     final String url,
                     final int responseCode,
                     final long bytes,
                     final long duration) {

        if (logger.isInfoEnabled()) {
            final Map<String, String> filteredMap = filterAttributes(attributeMap);
            final String kvPairs = CSVFormatter.format(filteredMap, false);
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

    public Map<String, String> filterAttributes(final AttributeMap attributeMap) {
        // Use a LinkedHashMap to adhere to metaKeySet order, which is a LinkedHashSet
        if (NullSafe.hasItems(metaKeySet)) {
            final Map<String, String> map = new LinkedHashMap<>(metaKeySet.size());
            metaKeySet.forEach(key ->
                    map.put(key, attributeMap.get(key)));
            return map;
        } else {
            return Collections.emptyMap();
        }
    }

    private Set<String> getMetaKeySet(final String csv) {
        if (NullSafe.isEmptyString(csv)) {
            return Collections.emptySet();
        } else {
            // Use LinkedHashSet to preserve order
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(NullSafe::isNonBlankString)
                    .map(String::toLowerCase)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static Set<String> getActiveSSLProtocols() {
        try {
            return NullSafe.asSet(SSLContext.getDefault().createSSLEngine().getEnabledProtocols());
        } catch (final Exception e) {
            LOGGER.debug("Unable to determine SSL protocols", e);
            return Collections.emptySet();
        }
    }

    @Override
    @PipelineProperty(description = "When the current output exceeds this size it will be closed and a " +
                                    "new one created. " +
                                    "Size is either specified in bytes e.g. '1024' or with a IEC unit suffix, e.g. " +
                                    "'1K', '1M', '1G', etc.",
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

    @PipelineProperty(description = "Determines the timeout until arrival of a response from the opposite endpoint. " +
                                    "A timeout value of zero is interpreted as an infinite timeout. " +
                                    "Default: 3 minutes. " +
                                    "The timeout is specified as either milliseconds, e.g. '60000' or with a " +
                                    "duration suffix, e.g. '500ms', '2s', '1m', etc.",
            displayPriority = 6)
    public void setTimeout(final String string) {
        timeout = null;
        if (NullSafe.isNonEmptyString(string)) {
            timeout = ModelStringUtil.parseDurationString(string);
        }
    }

    @PipelineProperty(description = "Determines the timeout until a new connection is fully established. " +
                                    "This may also include transport security negotiation exchanges such as SSL or " +
                                    "TLS protocol negotiation. " +
                                    "A timeout value of zero is interpreted as an infinite timeout. " +
                                    "Default: 3 minutes. " +
                                    "The timeout is specified as either milliseconds, e.g. '60000' or with a " +
                                    "duration suffix, e.g. '500ms', '2s', '1m', etc.",
            displayPriority = 7)
    public void setConnectionTimeout(final String string) {
        connectionTimeout = null;
        if (NullSafe.isNonEmptyString(string)) {
            connectionTimeout = ModelStringUtil.parseDurationString(string);
        }
    }

    @PipelineProperty(description = "Returns the connection lease request timeout used when requesting a connection " +
                                    "from the connection manager. " +
                                    "Default: 3 minutes. " +
                                    "The timeout is specified as either milliseconds, e.g. '60000' or with a " +
                                    "duration suffix, e.g. '500ms', '2s', '1m', etc.",
            displayPriority = 8)
    public void setConnectionRequestTimeout(final String string) {
        connectionRequestTimeout = null;
        if (NullSafe.isNonEmptyString(string)) {
            connectionRequestTimeout = ModelStringUtil.parseDurationString(string);
        }
    }

    @PipelineProperty(description = "How long to wait for data to be available before closing the connection. " +
                                    "The timeout is specified as either milliseconds, e.g. '60000' or with a " +
                                    "duration suffix, e.g. '500ms', '2s', '1m', etc.",
            displayPriority = 8)
    @Deprecated
    public void setReadTimeout(final String string) {
        if (string != null) {
            setConnectionRequestTimeout(string);
        }
    }

    @PipelineProperty(description = "The maximum time a pooled connection can stay idle (not leased to any thread) " +
                                    "before it is shut down. " +
                                    "Default: 1 hour. " +
                                    "The timeout is specified as either milliseconds, e.g. '60000' or with a " +
                                    "duration suffix, e.g. '500ms', '2s', '1m', etc.",
            displayPriority = 9)
    public void setTimeToLive(final String string) {
        timeToLive = null;
        if (NullSafe.isNonEmptyString(string)) {
            timeToLive = ModelStringUtil.parseDurationString(string);
        }
    }

    @PipelineProperty(description = "Should data be sent in chunks and if so how big should the chunks be. " +
                                    "Size is either specified in bytes e.g. '1024' or with a IEC unit suffix, " +
                                    "e.g. '1K', '1M', '1G', etc.",
            displayPriority = 10)
    public void setForwardChunkSize(final String string) {
        this.forwardChunkSize = ModelStringUtil.parseIECByteSizeString(string);
    }

    @PipelineProperty(description = "Should data be compressed when sending",
            defaultValue = DEFAULT_USE_COMPRESSION_PROP_VALUE,
            displayPriority = 11)
    public void setUseCompression(final boolean useCompression) {
        outputStreamSupport.setUseCompression(useCompression);
    }

    @PipelineProperty(description = "Whether to use the 'Content-Encoding' HTTP header when " +
                                    "useCompression is 'true'. If 'false' (the default), the 'Compression' header " +
                                    "will be used, which is supported by ." +
                                    "Stroom/Stroom-Proxy destinations. 'Content-Encoding' would be required for " +
                                    "other destinations, but is only applicable for compression types " +
                                    "'gz', 'zstd' or 'deflate'.",
            defaultValue = DEFAULT_USE_CONTENT_ENCODING_PROP_VALUE,
            displayPriority = 12)
    public void setUseContentEncodingHeader(final boolean useContentEncodingHeader) {
        this.useContentEncodingHeader = useContentEncodingHeader;
    }

    @PipelineProperty(
            description = "Compression method to apply, if compression is enabled. Supported values: " +
                          CompressionUtil.SUPPORTED_COMPRESSORS + ".",
            defaultValue = DEFAULT_COMPRESSION_METHOD_PROP_VALUE,
            displayPriority = 13)
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
            defaultValue = META_KEYS_DEFAULT,
            displayPriority = 14)
    public void setLogMetaKeys(final String string) {
        metaKeySet = getMetaKeySet(string);
    }

    @PipelineProperty(description = "Use JVM SSL config. " +
                                    "Set this to true if the Stroom node has been configured with key/trust stores " +
                                    "using java system properties like 'javax.net.ssl.keyStore'." +
                                    "Set this to false if you are explicitly setting key/trust store properties on " +
                                    "this HttpAppender.",
            defaultValue = "true",
            displayPriority = 15)
    @Deprecated
    public void setUseJvmSslConfig(final boolean useJvmSslConfig) {
//        this.useJvmSslConfig = useJvmSslConfig;
    }

    @PipelineProperty(description = "The key store file path on the server",
            displayPriority = 16)
    public void setKeyStorePath(final String keyStorePath) {
        sslConfigBuilder.withKeyStorePath(keyStorePath);
    }

    @PipelineProperty(description = "The key store type. " +
                                    "Valid values are ['JCEKS', 'JKS', 'DKS', 'PKCS11', 'PKCS12'].",
            defaultValue = "JKS",
            displayPriority = 17)
    public void setKeyStoreType(final String keyStoreType) {
        sslConfigBuilder.withKeyStoreType(keyStoreType);
    }

    @PipelineProperty(description = "The key store password",
            displayPriority = 18)
    public void setKeyStorePassword(final String keyStorePassword) {
        sslConfigBuilder.withKeyStorePassword(keyStorePassword);
    }

    @PipelineProperty(description = "The trust store file path on the server",
            displayPriority = 19)
    public void setTrustStorePath(final String trustStorePath) {
        sslConfigBuilder.withTrustStorePath(trustStorePath);
    }

    @PipelineProperty(description = "The trust store type " +
                                    "Valid values are ['JCEKS', 'JKS', 'DKS', 'PKCS11', 'PKCS12'].",
            defaultValue = "JKS",
            displayPriority = 20)
    public void setTrustStoreType(final String trustStoreType) {
        sslConfigBuilder.withTrustStoreType(trustStoreType);
    }

    @PipelineProperty(description = "The trust store password",
            displayPriority = 21)
    public void setTrustStorePassword(final String trustStorePassword) {
        sslConfigBuilder.withTrustStorePassword(trustStorePassword);
    }

    @PipelineProperty(description = "Set this to true to verify that the destination host name matches against " +
                                    "the host names in the certificate supplied by the destination server.",
            defaultValue = "true",
            displayPriority = 22)
    public void setHostnameVerificationEnabled(final boolean hostnameVerificationEnabled) {
        sslConfigBuilder.withHostnameVerificationEnabled(hostnameVerificationEnabled);
    }

    @PipelineProperty(description = "The SSL protocol to use",
            defaultValue = "TLSv1.2",
            displayPriority = 23)
    public void setSslProtocol(final String sslProtocol) {
        sslConfigBuilder.withSslProtocol(NullSafe.get(sslProtocol, String::trim));
    }

    @PipelineProperty(description = "The HTTP request method. Valid values are " +
                                    "GET, POST, HEAD, OPTIONS, PUT, DELETE and TRACE.",
            defaultValue = DEFAULT_REQUEST_METHOD_PROP_VALUE,
            displayPriority = 24)
    public void setRequestMethod(final String requestMethod) {
        this.requestMethod = NullSafe.get(requestMethod, String::trim, String::toUpperCase);
    }

    @PipelineProperty(description = "The content type",
            defaultValue = "application/json",
            displayPriority = 25)
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    @PipelineProperty(description = "Provide stream metadata as HTTP headers",
            defaultValue = "true",
            displayPriority = 26)
    public void setHttpHeadersIncludeStreamMetaData(final boolean newValue) {
        this.httpHeadersIncludeStreamMetaData = newValue;
    }

    @PipelineProperty(description = "Comma delimited list of stream meta data keys to include as HTTP headers. " +
                                    "Only works when httpHeadersIncludeStreamMetaData is set to true. If empty all " +
                                    "headers are sent, unless httpHeadersStreamMetaDataDenyList is used. " +
                                    "If httpHeadersStreamMetaDataAllowList contains keys, " +
                                    "httpHeadersStreamMetaDataDenyList is ignored.",
            defaultValue = "",
            displayPriority = 27)
    public void setHttpHeadersStreamMetaDataAllowList(final String newValue) {
        this.httpHeadersStreamMetaDataAllowList = newValue;
    }

    @PipelineProperty(description = "Comma delimited list of stream meta data keys to exclude as HTTP headers. " +
                                    "Only works when httpHeadersIncludeStreamMetaData is set to true. If empty all " +
                                    "headers are sent, unless httpHeadersStreamMetaDataAllowList is used. " +
                                    "If httpHeadersStreamMetaDataAllowList contains keys, " +
                                    "httpHeadersStreamMetaDataDenyList is ignored.",
            defaultValue = "",
            displayPriority = 28)
    public void setHttpHeadersStreamMetaDataDenyList(final String newValue) {
        this.httpHeadersStreamMetaDataDenyList = newValue;
    }

    @PipelineProperty(description = "Additional HTTP Header 1, format is 'HeaderName: HeaderValue'",
            displayPriority = 29)
    public void setHttpHeadersUserDefinedHeader1(final String headerText) {
        this.httpHeadersUserDefinedHeader1 = headerText;
    }

    @PipelineProperty(description = "Additional HTTP Header 2, format is 'HeaderName: HeaderValue'",
            displayPriority = 30)
    public void setHttpHeadersUserDefinedHeader2(final String headerText) {
        this.httpHeadersUserDefinedHeader2 = headerText;
    }

    @PipelineProperty(description = "Additional HTTP Header 3, format is 'HeaderName: HeaderValue'",
            displayPriority = 31)
    public void setHttpHeadersUserDefinedHeader3(final String headerText) {
        this.httpHeadersUserDefinedHeader3 = headerText;
    }
}
