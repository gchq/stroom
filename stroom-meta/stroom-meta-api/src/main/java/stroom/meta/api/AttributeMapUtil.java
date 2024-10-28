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

package stroom.meta.api;

import stroom.util.NullSafe;
import stroom.util.cert.CertificateExtractor;
import stroom.util.io.StreamUtil;
import stroom.util.shared.string.CIKey;

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
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    // Delimiter within a value
    static final String VALUE_DELIMITER = ",";

    static final Pattern VALUE_DELIMITER_PATTERN = Pattern.compile(Pattern.quote(VALUE_DELIMITER));
    // Delimiter between attributes
    private static final String ATTRIBUTE_DELIMITER = "\n";
    static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static AttributeMap cloneAllowable(final AttributeMap in) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.putAll(in);
        attributeMap.removeAll(StandardHeaderArguments.HEADER_CLONE_EXCLUDE_SET);
        return attributeMap;
    }

    public static AttributeMap create(final HttpServletRequest httpServletRequest,
                                      final CertificateExtractor certificateExtractor) {
        final AttributeMap attributeMap = new AttributeMap();
        addAllSecureTokens(httpServletRequest, certificateExtractor, attributeMap);
        addAllHeaders(httpServletRequest, attributeMap);
        addAllQueryString(httpServletRequest, attributeMap);
        addRemoteClientDetails(httpServletRequest, attributeMap);
        return attributeMap;
    }

    public static AttributeMap create(final InputStream inputStream) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        if (inputStream != null) {
            read(inputStream, attributeMap);
        }
        return attributeMap;
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
        data.lines()
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .forEach(line -> {
                    final int splitPos = line.indexOf(HEADER_DELIMITER);
                    if (splitPos != -1) {
                        final String key = line.substring(0, splitPos);
                        String value = line.substring(splitPos + 1);
                        attributeMap.put(key, value);
                    } else {
                        attributeMap.put(line, null);
                    }
                });
    }

    public static void read(final byte[] data, final AttributeMap attributeMap) throws IOException {
        read(new ByteArrayInputStream(data), attributeMap);
    }

    public static void write(final AttributeMap attributeMap, final OutputStream outputStream) throws IOException {
        write(attributeMap, new OutputStreamWriter(outputStream, DEFAULT_CHARSET));
    }

    public static void appendAttributes(final AttributeMap attributeMap,
                                        final StringBuilder builder,
                                        final CIKey... attributeKeys) {

        if (builder != null && attributeMap != null && attributeKeys != null) {

            final String attributesStr = Arrays.stream(attributeKeys)
                    .map(key ->
                            getAttributeStr(attributeMap, key))
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));

            if (!attributesStr.isBlank()) {
                builder.append(" [")
                        .append(attributesStr)
                        .append("]");
            }
        }
    }

    /**
     * Splits the attributeValue using {@link AttributeMapUtil#VALUE_DELIMITER}.
     *
     * @return A non-null list
     */
    public static List<String> valueAsList(final String attributeValue) {
        if (NullSafe.isEmptyString(attributeValue)) {
            return Collections.emptyList();
        } else {
            return VALUE_DELIMITER_PATTERN.splitAsStream(attributeValue)
                    .toList();
        }
    }

    private static String getAttributeStr(final AttributeMap attributeMap, final CIKey attributeKey) {
        final String attributeValue = attributeMap.get(attributeKey);
        final String str;
        if (attributeValue != null && !attributeValue.isBlank()) {
            str = attributeKey + ": " + attributeValue;
        } else {
            str = null;
        }
        return str;
    }

    public static void write(final AttributeMap attributeMap, final Writer writer) throws IOException {
        try {
            attributeMap.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEachOrdered(e -> {
                        try {
                            writer.write(e.getKey().get());
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
            // If we get here it means SSL has been terminated by DropWizard so we need to add meta items
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
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            putHeader(header, httpServletRequest, attributeMap);
        }
    }

    private static void addRemoteClientDetails(final HttpServletRequest httpServletRequest,
                                               final AttributeMap attributeMap) {
        attributeMap.computeIfAbsent(StandardHeaderArguments.REMOTE_HOST, key ->
                nullIfBlank(httpServletRequest.getRemoteHost()));
        attributeMap.computeIfAbsent(StandardHeaderArguments.REMOTE_ADDRESS, key ->
                nullIfBlank(httpServletRequest.getRemoteHost()));
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

            } catch (Exception e) {
                LOGGER.error("Unable to create header {} from header {} with value [{}].",
                        StandardHeaderArguments.REMOTE_CERT_EXPIRY, CERT_EXPIRY_HEADER_TOKEN, headerValue, e);
            }
        }
    }

    private static void addAllQueryString(HttpServletRequest httpServletRequest, AttributeMap attributeMap) {
        String queryString = httpServletRequest.getQueryString();
        if (queryString != null) {
            StringTokenizer st = new StringTokenizer(httpServletRequest.getQueryString(), "&");
            while (st.hasMoreTokens()) {
                String pair = st.nextToken();
                int pos = pair.indexOf('=');
                if (pos != -1) {
                    String key = pair.substring(0, pos);
                    String val = pair.substring(pos + 1);

                    attributeMap.put(key, val);
                }
            }
        }
    }

    public static void addFeedAndType(final AttributeMap attributeMap,
                                      final String feedName,
                                      final String typeName) {
        attributeMap.put(StandardHeaderArguments.FEED, feedName.trim());
        if (typeName != null && !typeName.isBlank()) {
            attributeMap.put(StandardHeaderArguments.TYPE, typeName.trim());
        }
    }
}
