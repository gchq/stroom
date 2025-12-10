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

package stroom.util.http;

import stroom.test.common.TemporaryPathCreator;
import stroom.util.config.SampleObjectCreator;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestHttpClientConfigConverter {

    @Test
    void test() {
        final PathCreator pathCreator = new TemporaryPathCreator();
        final HttpClientConfigConverter restClientConfigConverter = new HttpClientConfigConverter(pathCreator);

        HttpClientConfiguration httpClientConfiguration =
                SampleObjectCreator.createPopulatedObject(HttpClientConfiguration.class, null);
        httpClientConfiguration = httpClientConfiguration
                .copy()
                .tlsConfiguration(httpClientConfiguration
                        .getTlsConfiguration()
                        .copy()
                        .keyStorePath("/keystorepath")
                        .trustStorePath("/trustStorepath")
                        .build())
                .build();

        final io.dropwizard.client.HttpClientConfiguration out = restClientConfigConverter.convert(
                httpClientConfiguration,
                io.dropwizard.client.HttpClientConfiguration.class);

        final HttpClientConfiguration reverse = restClientConfigConverter.convert(out,
                stroom.util.http.HttpClientConfiguration.class);

        assertThat(httpClientConfiguration).isEqualTo(reverse);
    }
}
