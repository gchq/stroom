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

package stroom.pipeline.xsltfunctions;

import stroom.util.http.BasicHttpClientFactory;
import stroom.util.http.HttpClientConfigConverter;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientFactory;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.StreamUtil;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;

@Disabled
class TestHttpCall {

    @Test
    void test(@TempDir final Path tempDir) throws Exception {
        final HttpTlsConfiguration tlsConfiguration = HttpTlsConfiguration.builder()
                .keyStorePath("/Users/stroomdev66/work/stroom-6.0/stroom-ssl-test/client.jks")
                .keyStorePassword("password")
                .trustStorePath("/Users/stroomdev66/work/stroom-6.0/stroom-ssl-test/ca.jks")
                .trustStorePassword("password")
                .trustSelfSignedCertificates(false)
                .build();
        final HttpClientConfiguration httpClientConfiguration = HttpClientConfiguration.builder()
                .tlsConfiguration(tlsConfiguration)
                .build();

        final PathCreator pathCreator = new SimplePathCreator(
                () -> tempDir.resolve("home"),
                () -> tempDir);

        final HttpClientFactory httpClientFactory =
                new BasicHttpClientFactory(new HttpClientConfigConverter(pathCreator));
        final HttpCall httpCall = new HttpCall(null);
        try (final CloseableHttpClient httpClient = httpClientFactory.get("test", httpClientConfiguration)) {
            httpCall.execute("https://localhost:5443/", "", "", "", httpClient,
                    response -> {
                        try (final InputStream inputStream = response.getEntity().getContent()) {
                            System.out.println(StreamUtil.streamToString(inputStream));
                            return response.getCode();
                        }
                    });
        }
    }
}
