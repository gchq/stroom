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

package stroom.feed;

import stroom.data.meta.api.AttributeMap;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Map;
import java.util.StringTokenizer;

public class AttributeMapUtil {
    private static final String HEADER_DELIMITER = ":";
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    public static AttributeMap cloneAllowable(final AttributeMap in) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.putAll(in);
        attributeMap.removeAll(StroomHeaderArguments.HEADER_CLONE_EXCLUDE_SET);
        return attributeMap;
    }

    public static AttributeMap create(final HttpServletRequest httpServletRequest) {
        AttributeMap attributeMap = new AttributeMap();
        addAllHeaders(httpServletRequest, attributeMap);
        addAllQueryString(httpServletRequest, attributeMap);

        return attributeMap;
    }

    public static void read(final InputStream inputStream, final boolean close, final AttributeMap attributeMap) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, DEFAULT_CHARSET));
        String line;
        while ((line = reader.readLine()) != null) {
            final int splitPos = line.indexOf(HEADER_DELIMITER);
            if (splitPos != -1) {
                final String key = line.substring(0, splitPos);
                final String value = line.substring(splitPos + 1);
                attributeMap.put(key, value);
            } else {
                attributeMap.put(line.trim(), null);
            }
        }

        if (close) {
            inputStream.close();
        }
    }

    public static void read(final byte[] data, final AttributeMap attributeMap) throws IOException {
        read(new ByteArrayInputStream(data), true, attributeMap);
    }

    public static void write(final AttributeMap attributeMap, final OutputStream outputStream, final boolean close) throws IOException {
        write(attributeMap, new OutputStreamWriter(outputStream, DEFAULT_CHARSET), close);
    }

    public static void write(final AttributeMap attributeMap, final Writer writer, final boolean close) throws IOException {
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
            if (close) {
                writer.close();
            } else {
                writer.flush();
            }
        }
    }

    public static byte[] toByteArray(final AttributeMap attributeMap) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        write(attributeMap, byteArrayOutputStream, true);
        return byteArrayOutputStream.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static void addAllHeaders(HttpServletRequest httpServletRequest, AttributeMap attributeMap) {
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            attributeMap.put(header, httpServletRequest.getHeader(header));
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
                    String val = pair.substring(pos + 1, pair.length());

                    attributeMap.put(key, val);
                }
            }
        }
    }
}
