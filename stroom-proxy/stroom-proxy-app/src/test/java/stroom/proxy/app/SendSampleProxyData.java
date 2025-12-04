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

package stroom.proxy.app;

import stroom.meta.api.StandardHeaderArguments;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

public class SendSampleProxyData {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SendSampleProxyData.class);

    private SendSampleProxyData() {
    }

    public static void main(final String[] args) {
        try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
            doWork("VERY_SIMPLE_DATA_SPLITTER-EVENTS", httpClient);
            doWork("VERY_SIMPLE_DATA_SPLITTER-EVENTS-V2", httpClient);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void doWork(final String feed, final HttpClient httpClient) {
        try {
            final String url = "http://some.server.co.uk/stroom/datafeed";
            for (int i = 0; i < 200; i++) {
                final HttpPost httpPost = getHttpPost(url, feed);
                httpClient.execute(httpPost, response -> {
                    final int code = response.getCode();
                    final String msg = response.getReasonPhrase();
                    System.out.println("Client Got Response " + response);
                    if (msg != null && !msg.isEmpty()) {
                        System.out.println(msg);
                    }
                    return code;
                });
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private static HttpPost getHttpPost(final String url,
                                        final String feed) throws IOException {
        final HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("Content-Type", "application/audit");
        httpPost.addHeader("Feed", feed);
        httpPost.addHeader(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
        httpPost.addHeader("Connection", "Keep-Alive");

        final InputStream inputStream = getInputStream();
        httpPost.setEntity(new BasicHttpEntity(inputStream, ContentType.create("application/audit"), true));
        return httpPost;
    }

    private static InputStream getInputStream() throws IOException {
        final byte[] bytes;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (final PrintWriter printWriter =
                    new PrintWriter(
                            new OutputStreamWriter(
                                    new GzipCompressorOutputStream(baos),
                                    StreamUtil.DEFAULT_CHARSET))) {
                printWriter.println("Time,Action,User,File");
                printWriter.println("01/01/2009:00:00:01,OPEN,userone,proxyload.txt");
            }
            baos.close();
            bytes = baos.toByteArray();
        }

        final InputStream inputStream = new ByteArrayInputStream(bytes);
        return inputStream;
    }
}
