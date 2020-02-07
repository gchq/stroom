/*
 * Copyright 2017 Crown Copyright
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.meta.shared.AttributeMap;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;

import javax.servlet.http.HttpServletRequest;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Enumeration;
import java.util.Map;
import java.util.StringTokenizer;

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
            .toFormatter();

    private static final String HEADER_DELIMITER = ":";
    static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static AttributeMap cloneAllowable(final AttributeMap in) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.putAll(in);
        attributeMap.removeAll(StandardHeaderArguments.HEADER_CLONE_EXCLUDE_SET);
        return attributeMap;
    }

    public static AttributeMap create(final HttpServletRequest httpServletRequest) {
        AttributeMap attributeMap = new AttributeMap();
        addAllSecureTokens(httpServletRequest, attributeMap);
        addAllHeaders(httpServletRequest, attributeMap);
        addAllQueryString(httpServletRequest, attributeMap);

        return attributeMap;
    }

    public static void read(final InputStream inputStream, final AttributeMap attributeMap) throws IOException {
        final String data = StreamUtil.streamToString(inputStream, DEFAULT_CHARSET, false);
        final String[] lines = data.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 0) {
                final int splitPos = line.indexOf(HEADER_DELIMITER);
                if (splitPos != -1) {
                    final String key = line.substring(0, splitPos);
                    final String value = line.substring(splitPos + 1);
                    attributeMap.put(key.trim(), value.trim());
                } else {
                    attributeMap.put(line, null);
                }
            }
        }
    }

    public static void read(final byte[] data, final AttributeMap attributeMap) throws IOException {
        read(new ByteArrayInputStream(data), attributeMap);
    }

    public static void write(final AttributeMap attributeMap, final OutputStream outputStream) throws IOException {
        write(attributeMap, new OutputStreamWriter(outputStream, DEFAULT_CHARSET));
    }

    private static void write(final AttributeMap attributeMap, final Writer writer) throws IOException {
        try {
            attributeMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(String::compareToIgnoreCase))
                    .forEachOrdered(e -> {
                        try {
                            writer.write(e.getKey());
                            final String value = e.getValue();
                            if (value != null) {
                                writer.write(":");
                                writer.write(value);
                            }
                            writer.write("\n");
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
                                           final AttributeMap attributeMap) {
        final X509Certificate[] certs = (X509Certificate[]) httpServletRequest
                .getAttribute("javax.servlet.request.X509Certificate");

        if (certs != null && certs.length > 0 && certs[0] != null) {
            // If we get here it means SSL has been terminated by DropWizard so we need to add meta items
            // from the certificate
            final X509Certificate cert = certs[0];
            if (cert.getSubjectDN() != null) {
                final String remoteDN = cert.getSubjectDN().toString();
                attributeMap.put(StandardHeaderArguments.REMOTE_DN, remoteDN);
            } else {
                LOGGER.debug("Cert {} doesn't have a subject DN", cert);
            }

            if (cert.getNotAfter() != null) {
                final String remoteCertExpiry = DateUtil.createNormalDateTimeString(cert.getNotAfter().getTime());
                attributeMap.put(StandardHeaderArguments.REMOTE_CERT_EXPIRY, remoteCertExpiry);
            } else {
                LOGGER.debug("Cert {} doesn't have a Not After date", cert);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addAllHeaders(final HttpServletRequest httpServletRequest,
                                      final AttributeMap attributeMap) {
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            putHeader(header, httpServletRequest, attributeMap);
        }
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
                final String newHeaderValue = DateUtil.createNormalDateTimeString(
                        localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli());
                LOGGER.debug("Converting certificate expiry date from [{}] to [{}]", headerValue, newHeaderValue);
                attributeMap.put(StandardHeaderArguments.REMOTE_CERT_EXPIRY, newHeaderValue);
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
}
