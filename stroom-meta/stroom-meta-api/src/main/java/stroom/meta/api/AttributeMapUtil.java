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

package stroom.meta.api;

import stroom.util.cert.CertificateExtractor;
import stroom.util.concurrent.UniqueId;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.net.HostNameUtil;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: 08/12/2022 This should be an injectable class with instance methods to make test mocking possible
public class AttributeMapUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeMapUtil.class);

    private static final String CERT_EXPIRY_HEADER_TOKEN = "X-SSL-Client-V-End";

    // This formatter is used to parse the date in nginx property '$ssl_client_v_end'
    // I can't find any documentation on the exact format used so can only go on a few examples.
    // 'Sep  9 16:16:45 2292 GMT'
    // 'Sep 10 06:39:20 2292 GMT'
    static DateTimeFormatter CERT_EXPIRY_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral(' ')
            .padNext(2) // for some reason nginx right pads the day of month
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(' ')
            .appendValue(ChronoField.YEAR, 4)
            .appendLiteral(' ')
            .appendZoneText(TextStyle.SHORT)
            .toFormatter(Locale.ENGLISH);

    // Delimiter between key and value
    private static final String HEADER_DELIMITER = ":";

    // Delimiter between attributes
    private static final String ATTRIBUTE_DELIMITER = "\n";
    static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static AttributeMap cloneAllowable(final AttributeMap in) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.putAll(in);
        attributeMap.removeAll(StandardHeaderArguments.HEADER_CLONE_EXCLUDE_SET);
        return attributeMap;
    }

    /**
     * Creates a new {@link AttributeMap} from a {@link HttpServletRequest}.
     * <p>All HTTP headers and query parameters on the request will be added.</p>
     * <p>In addition, entries for the following keys may be set depending on
     * whether values are available:</p>
     * <ul>
     * <li>RemoteDN</li>
     * <li>RemoteCertExpiry</li>
     * <li>GUID</li>
     * <li>RemoteAddress</li>
     * <li>RemoteHost</li>
     * <li>ReceiptId</li>
     * <li>ReceiptIdPath</li>
     * <li>ReceivedTime</li>
     * <li>ReceivedTimeHistory</li>
     * <li>ReceivedPath</li>
     * </ul>
     */
    public static AttributeMap create(final HttpServletRequest httpServletRequest,
                                      final CertificateExtractor certificateExtractor,
                                      final Instant receiveTime,
                                      final UniqueId receiptId) {
        final AttributeMap attributeMap = new AttributeMap();

        addAllSecureTokens(httpServletRequest, certificateExtractor, attributeMap);
        addAllHeaders(httpServletRequest, attributeMap);
        addAllQueryString(httpServletRequest, attributeMap);
        // If GUID is not set, add GUID, RemoteAddress and RemoteHost
        addGuidAndRemoteClientDetails(httpServletRequest, attributeMap);

        addReceiptInfo(attributeMap, receiveTime, receiptId);

        return attributeMap;
    }

    public static void addReceiptInfo(final AttributeMap attributeMap,
                                      final UniqueId receiptId) {
        addReceiptInfo(attributeMap, Instant.now(), receiptId);
    }

    public static void addReceiptInfo(final AttributeMap attributeMap,
                                      final Instant receiveTime,
                                      final UniqueId receiptId) {
        // Add ReceiptId and ReceiptIdPath
        // Create a new receipt id for the request, so we can track progress of the stream
        // through the various proxies and into stroom and report back the ID to the sender,
        AttributeMapUtil.setAndAppendReceiptId(attributeMap, receiptId);

        // Add ReceivedTime and ReceivedTimeHistory
        AttributeMapUtil.setAndAppendReceivedTime(
                attributeMap, Objects.requireNonNullElseGet(receiveTime, Instant::now));

        // Include this host in the ReceivedPath
        attributeMap.appendItemIfDifferent(
                StandardHeaderArguments.RECEIVED_PATH, HostNameUtil.determineHostName());
    }

    public static AttributeMap create(final InputStream inputStream) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        if (inputStream != null) {
            read(inputStream, attributeMap);
        }
        return attributeMap;
    }

    public static void read(final Path file, final AttributeMap attributeMap) throws IOException {
        final String data = Files.readString(file, DEFAULT_CHARSET);
        read(data, attributeMap);
    }

    public static void read(final InputStream inputStream, final AttributeMap attributeMap) throws IOException {
        final String data = StreamUtil.streamToString(inputStream, DEFAULT_CHARSET, false);
        read(data, attributeMap);
    }

    public static AttributeMap create(final String data) {
        final AttributeMap attributeMap = new AttributeMap();
        if (!NullSafe.isBlankString(data)) {
            read(data, attributeMap);
        }
        return attributeMap;
    }

    public static void read(final String data, final AttributeMap attributeMap) {
        try (final Stream<String> linesStream = data.lines()) {
            linesStream.map(String::trim)
                    .filter(Predicate.not(String::isEmpty))
                    .forEach(line -> {
                        final int splitPos = line.indexOf(HEADER_DELIMITER);
                        if (splitPos != -1) {
                            final String key = line.substring(0, splitPos);
                            final String value = line.substring(splitPos + 1);
                            attributeMap.put(key.trim(), value.trim());
                        } else {
                            attributeMap.put(line, null);
                        }
                    });
        }
    }

    /**
     * For when you just want the value for one or more keys.
     * Saves having to de-serialise the whole file to an {@link AttributeMap}.
     *
     * @param data The {@link String} to extract values from.
     * @param keys The keys to find values for. Assumed to be already trimmed.
     * @return A list of values using the same indexing as the supplied keys. The length of the
     * returned list will always match that of the supplied keys list.
     */
    public static List<String> readKeys(final String data,
                                        final List<String> keys) throws IOException {
        if (NullSafe.hasItems(keys) && NullSafe.isNonBlankString(data)) {
            // Meta keys come from headers so should be ascii, and thus we don't have to
            // worry about multibyte 'chars' and other such oddities.
            try (final Stream<String> linesStream = data.lines()) {
                return readKeys(keys, linesStream);
            }
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * For when you just want the value for one or more keys.
     * Saves having to de-serialise the whole file to an {@link AttributeMap}.
     *
     * @param path The file to extract values from.
     * @param keys The keys to find values for. Assumed to be already trimmed.
     * @return A list of values using the same indexing as the supplied keys. The length of the
     * returned list will always match that of the supplied keys list.
     */
    public static List<String> readKeys(final Path path,
                                        final List<String> keys) throws IOException {
        Objects.requireNonNull(path);
        if (NullSafe.hasItems(keys)) {
            // Meta keys come from headers so should be ascii, and thus we don't have to
            // worry about multibyte 'chars' and other such oddities.
            try (final Stream<String> linesStream = Files.lines(path, DEFAULT_CHARSET)) {
                return readKeys(keys, linesStream);
            }
        } else {
            return Collections.emptyList();
        }
    }

    private static List<String> readKeys(final List<String> keys,
                                         final Stream<String> linesStream) {
        final List<String> keysToFind = new ArrayList<>(keys);
        final List<String> values = new ArrayList<>(keys.size());
        // Ensure we have a null value in all indexes, in case we don't find the key
        for (final String ignored : keys) {
            values.add(null);
        }

        final AtomicInteger keysRemaining = new AtomicInteger(keysToFind.size());
        linesStream
                .takeWhile(ignored -> keysRemaining.get() > 0)
                .filter(NullSafe::isNonBlankString)
                .map(String::trim)
                .forEach(line -> {
                    for (int keyIdx = 0; keyIdx < keysToFind.size(); keyIdx++) {
                        final String keyToFind = keysToFind.get(keyIdx);
                        if (NullSafe.isNonBlankString(keyToFind)) {
                            final boolean foundKey = line.regionMatches(
                                    true,
                                    0,
                                    keyToFind,
                                    0,
                                    keyToFind.length());
                            if (foundKey) {
                                // Extract the value. Null out keysToFind, so we don't look for
                                // this key again
                                keysToFind.set(keyIdx, null);
                                keysRemaining.decrementAndGet();
                                final int splitPos = line.indexOf(HEADER_DELIMITER);
                                final String value;
                                if (splitPos != -1) {
                                    value = line.substring(splitPos + 1);
                                    values.set(keyIdx, value.trim());
                                }
                                // break out to look for the next key in keysToFind
                                break;
                            }
                        }
                    }
                });
        return Collections.unmodifiableList(values);
    }

    public static void read(final byte[] data, final AttributeMap attributeMap) throws IOException {
        read(new ByteArrayInputStream(data), attributeMap);
    }

    public static void appendAttributes(final AttributeMap attributeMap,
                                        final StringBuilder builder,
                                        final String... attributeKeys) {

        if (builder != null && attributeMap != null && attributeKeys != null) {

            final String attributesStr = Arrays.stream(attributeKeys)
                    .map(key ->
                            getAttributeStr(attributeMap, key))
                    .filter(NullSafe::isNonBlankString)
                    .collect(Collectors.joining(", "));

            if (!attributesStr.isBlank()) {
                builder.append(" [")
                        .append(attributesStr)
                        .append("]");
            }
        }
    }

    /**
     * Splits the attributeValue using {@link AttributeMap#VALUE_DELIMITER}.
     *
     * @return A non-null list
     */
    public static List<String> valueAsList(final String attributeValue) {
        if (NullSafe.isEmptyString(attributeValue)) {
            return Collections.emptyList();
        } else {
            return AttributeMap.VALUE_DELIMITER_PATTERN.splitAsStream(attributeValue)
                    .toList();
        }
    }

    private static String getAttributeStr(final AttributeMap attributeMap, final String attributeKey) {
        final String attributeValue = attributeMap.get(attributeKey);
        final String str;
        if (attributeValue != null && !attributeValue.isBlank()) {
            str = attributeKey + ": " + attributeValue;
        } else {
            str = null;
        }
        return str;
    }

    public static void write(final AttributeMap attributeMap, final Path path) throws IOException {
        try (final Writer writer = Files.newBufferedWriter(path)) {
            AttributeMapUtil.write(attributeMap, writer);
        }
    }

    public static void write(final AttributeMap attributeMap, final OutputStream outputStream) throws IOException {
        write(attributeMap, new OutputStreamWriter(outputStream, DEFAULT_CHARSET));
    }

    public static void write(final AttributeMap attributeMap, final Writer writer) throws IOException {
        try {
            attributeMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(String::compareToIgnoreCase))
                    .forEachOrdered(e -> {
                        try {
                            writer.write(e.getKey());
                            final String value = e.getValue();
                            if (value != null) {
                                writer.write(HEADER_DELIMITER);
                                writer.write(value);
                            }
                            writer.write(ATTRIBUTE_DELIMITER);
                        } catch (final IOException ioe) {
                            throw new UncheckedIOException(ioe);
                        }
                    });
        } finally {
            writer.flush();
        }
    }

    public static byte[] toByteArray(final AttributeMap attributeMap) throws IOException {
        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            write(attributeMap, byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    private static void addAllSecureTokens(final HttpServletRequest httpServletRequest,
                                           final CertificateExtractor certificateExtractor,
                                           final AttributeMap attributeMap) {
        final Optional<X509Certificate> optional = certificateExtractor.extractCertificate(httpServletRequest);
        optional.ifPresent(cert -> {
            // If we get here it means SSL has been terminated by DropWizard, so we need to add meta items
            // from the certificate
            if (cert.getSubjectDN() != null) {
                final String remoteDN = cert.getSubjectDN().toString();
                attributeMap.put(StandardHeaderArguments.REMOTE_DN, remoteDN);
            } else {
                LOGGER.debug("Cert {} doesn't have a subject DN", cert);
            }

            if (cert.getNotAfter() != null) {
                final long timeEpochMs = cert.getNotAfter().getTime();
                attributeMap.putDateTime(StandardHeaderArguments.REMOTE_CERT_EXPIRY, timeEpochMs);
            } else {
                LOGGER.debug("Cert {} doesn't have a Not After date", cert);
            }
        });
    }

    private static void addAllHeaders(final HttpServletRequest httpServletRequest,
                                      final AttributeMap attributeMap) {
        final Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            final String header = headerNames.nextElement();
            putHeader(header, httpServletRequest, attributeMap);
        }
    }

    private static void addGuidAndRemoteClientDetails(final HttpServletRequest httpServletRequest,
                                                      final AttributeMap attributeMap) {

        final String existingGuid = attributeMap.get(StandardHeaderArguments.GUID);

        // Allocate a GUID if we have not got one.
        if (NullSafe.isBlankString(existingGuid)) {
            final String newGuid = UUID.randomUUID().toString();
            attributeMap.put(StandardHeaderArguments.GUID, newGuid);

            // Only allocate RemoteXxx details if the GUID has not been
            // allocated. This is to prevent us setting them to proxy's addr/host
            // when it has already set them to the addr/host of the actual client.
            // We want them to be for the original client.

            // Allocate remote address if not set.
            final String remoteAddr = httpServletRequest.getRemoteAddr();
            if (NullSafe.isNonBlankString(remoteAddr)) {
                attributeMap.put(StandardHeaderArguments.REMOTE_ADDRESS, remoteAddr);
            }

            // Allocate remote address if not set.
            final String remoteHost = httpServletRequest.getRemoteHost();
            if (NullSafe.isNonBlankString(remoteHost)) {
                attributeMap.put(StandardHeaderArguments.REMOTE_HOST, remoteHost);
            }
        }
    }

    private static String nullIfBlank(final String str) {
        return (str == null || str.isBlank())
                ? null
                : str;
    }

    private static void putHeader(final String headerToken,
                                  final HttpServletRequest httpServletRequest,
                                  final AttributeMap attributeMap) {

        final String headerValue = httpServletRequest.getHeader(headerToken);
        attributeMap.put(headerToken, headerValue);

        // If this is the cert expiry header added by nginx then translate it to a sensible date format
        // and at it to the meta using a new header token
        // RemoteDN is added by nginx
        if (CERT_EXPIRY_HEADER_TOKEN.equals(headerToken)) {
            try {
                final LocalDateTime localDateTime = LocalDateTime.parse(headerValue, CERT_EXPIRY_DATE_FORMATTER);
                final Instant instant = localDateTime.toInstant(ZoneOffset.UTC);
                LOGGER.debug("Converting certificate expiry date from [{}] to [{}]", headerValue, instant);
                attributeMap.putDateTime(StandardHeaderArguments.REMOTE_CERT_EXPIRY, instant);

            } catch (final Exception e) {
                LOGGER.error("Unable to create header {} from header {} with value [{}].",
                        StandardHeaderArguments.REMOTE_CERT_EXPIRY, CERT_EXPIRY_HEADER_TOKEN, headerValue, e);
            }
        }
    }

    private static void addAllQueryString(final HttpServletRequest httpServletRequest,
                                          final AttributeMap attributeMap) {
        final String queryString = httpServletRequest.getQueryString();
        if (queryString != null) {
            final StringTokenizer st = new StringTokenizer(httpServletRequest.getQueryString(), "&");
            while (st.hasMoreTokens()) {
                final String pair = st.nextToken();
                final int pos = pair.indexOf('=');
                if (pos != -1) {
                    final String key = pair.substring(0, pos);
                    final String val = pair.substring(pos + 1);

                    attributeMap.put(key, val);
                }
            }
        }
    }

    public static void addFeedAndType(final AttributeMap attributeMap,
                                      final String feedName,
                                      final String typeName) {
        // AttributeMap trims keys/vals
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        if (NullSafe.isNonBlankString(typeName)) {
            attributeMap.put(StandardHeaderArguments.TYPE, typeName);
        } else {
            attributeMap.remove(StandardHeaderArguments.TYPE);
        }
    }

    public static void setAndAppendReceiptId(final AttributeMap attributeMap,
                                             final UniqueId receiptId) {
        if (receiptId != null) {
            setAndAppendReceiptId(attributeMap, receiptId.toString());
        }
    }

    public static void setAndAppendReceiptId(final AttributeMap attributeMap,
                                             final String receiptId) {
        final String receiptIdKey = StandardHeaderArguments.RECEIPT_ID;
        final String receiptIdPathKey = StandardHeaderArguments.RECEIPT_ID_PATH;

        // Make sure any existing receiptId is in receiptIdPath
        final String currReceiptId = attributeMap.get(receiptIdKey);
        if (NullSafe.isNonBlankString(currReceiptId)) {
            attributeMap.appendItemIfDifferent(receiptIdPathKey, currReceiptId);
        }

        // Now add the new one
        if (NullSafe.isNonBlankString(receiptId)) {
            attributeMap.put(receiptIdKey, receiptId);
            attributeMap.appendItemIfDifferent(receiptIdPathKey, receiptId);
        }
    }

    public static void setAndAppendReceivedTime(final AttributeMap attributeMap, final Instant receivedTime) {
        final String prevReceivedTime = attributeMap.get(StandardHeaderArguments.RECEIVED_TIME);

        if (NullSafe.isNonEmptyString(prevReceivedTime)) {
            // If prev time is not in history, add it, but ensure it is in a normal form
            final String normalisedPrevReceivedTime = DateUtil.normaliseDate(prevReceivedTime, true);
            attributeMap.appendItemIf(
                    StandardHeaderArguments.RECEIVED_TIME_HISTORY,
                    normalisedPrevReceivedTime,
                    curVal ->
                            !(NullSafe.contains(curVal, prevReceivedTime)
                              || NullSafe.contains(curVal, normalisedPrevReceivedTime)));
        }
        // Add our new time to the end of the history
        attributeMap.appendDateTime(StandardHeaderArguments.RECEIVED_TIME_HISTORY, receivedTime);
        // Now overwrite the receivedTime with the new time
        attributeMap.putDateTime(StandardHeaderArguments.RECEIVED_TIME, receivedTime);
    }

    /**
     * Creates a new {@link AttributeMap} that is initially populated with copies of the attributes
     * in baseAttributeMap (that are allowed to be cloned, i.e. not security ones).
     * {@code attributeMapWriter} is then called to write any attributes that need to be merged in
     * on top, i.e. from a ZIP .meta entry. Thus, any attributes (with a value or explicitly set to null)
     * set by attributeMapWriter will trump those in baseAttributeMap.
     * <p>
     * After the above, it will set/append the following attributes using values from baseAttributeMap.
     * This is because the values for these attributes will be more up-to-date in attributeMapWriter than
     * in baseAttributeMap.
     * <ul>
     * <li>ReceiptId</li>
     * <li>ReceiptIdPath</li>
     * <li>ReceivedTime</li>
     * <li>ReceivedTimeHistory</li>
     * <li>ReceivedPath</li>
     * </ul>
     * </p>
     * <p>
     * Assumes that ReceiptId, ReceivedTime and ReceivedPath have all been set in baseAttributeMap
     * on receipt, i.e. these are the latest values for these attributes.
     * </p>
     *
     * @return A new {@link AttributeMap} instance containing the merged attributes.
     */
    public static AttributeMap mergeAttributeMaps(final AttributeMap baseAttributeMap,
                                                  final Consumer<AttributeMap> attributeMapWriter) {

        Objects.requireNonNull(attributeMapWriter);
        // Add the meta from headers first, then read the entry meta on top,
        // so the values from the headers act as a fallback
        final AttributeMap outputAttributeMap =
                AttributeMapUtil.cloneAllowable(baseAttributeMap);

        // Now write attributes on top.
        // defaultFeedName/defaultTypeName are in attributeMap, so act as fallbacks
        // unless they are explicitly set to null in the .meta
        attributeMapWriter.accept(outputAttributeMap);

        // attributeMap contains the receiptId generated when we received this zip, so we
        // need to set/append it to each meta in the zip
        final String receiptId = baseAttributeMap.get(StandardHeaderArguments.RECEIPT_ID);
        AttributeMapUtil.setAndAppendReceiptId(outputAttributeMap, receiptId);

        // This value was set by ProxyRequestHandler, so we trust the format, thus don't need
        // to worry about normalising the date format
        final String receiptTimeStr = baseAttributeMap.get(StandardHeaderArguments.RECEIVED_TIME);
        if (NullSafe.isNonBlankString(receiptTimeStr)) {
            outputAttributeMap.put(StandardHeaderArguments.RECEIVED_TIME, receiptTimeStr);
            outputAttributeMap.appendItemIfDifferent(StandardHeaderArguments.RECEIVED_TIME_HISTORY, receiptTimeStr);
        }

        final List<String> receivedPathItems = baseAttributeMap.getAsList(StandardHeaderArguments.RECEIVED_PATH);
        if (!receivedPathItems.isEmpty()) {
            outputAttributeMap.appendItemIfDifferent(
                    StandardHeaderArguments.RECEIVED_PATH,
                    receivedPathItems.getLast());
        }

        LOGGER.debug("""
                mergeAttributeMaps()
                baseAttributeMap: {}
                outputAttributeMap: {}""", baseAttributeMap, outputAttributeMap);

        return outputAttributeMap;
    }

    /**
     * @param exceptionFunction Called if the normalised value is not valid.
     *                          The compression value is passed into exceptionSupplier.
     * @return The normalised value, if valid, or null if the entry is not present in the
     * {@link AttributeMap}
     */
    public static <X extends RuntimeException> String validateAndNormaliseCompression(
            final AttributeMap attributeMap,
            final Function<String, ? extends X> exceptionFunction) {

        Objects.requireNonNull(attributeMap, "attributeMap not supplied");
        final String key = StandardHeaderArguments.COMPRESSION;
        String compression = attributeMap.get(key);
        if (NullSafe.isNonEmptyString(compression)) {
            if (!StandardHeaderArguments.VALID_COMPRESSION_SET.contains(compression)) {
                // Try to normalise it
                // AttributeMap values are already trimmed
                compression = compression.toUpperCase(StreamUtil.DEFAULT_LOCALE);
                // Put the normalised value back in the map
                attributeMap.put(key, compression);
            }

            // Now check again
            if (!StandardHeaderArguments.VALID_COMPRESSION_SET.contains(compression)) {
                Objects.requireNonNull(exceptionFunction, "no exceptionSupplier provided");
                throw exceptionFunction.apply(attributeMap.get(key));
            }
        }
        return compression;
    }
}
