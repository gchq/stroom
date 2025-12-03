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

package stroom.util.config;

import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class TestOkHttpClientConfig {

    @Test
    void testDeser_empty() throws JsonProcessingException {

        final String json = "{}";
        final OkHttpClientConfig clientConfig = new ObjectMapper()
                .readerFor(OkHttpClientConfig.class)
                .readValue(json);

        Assertions.assertThat(clientConfig)
                .isNotNull();
        Assertions.assertThat(clientConfig.getSslConfig())
                .isNull();
        Assertions.assertThat(clientConfig.getHttpProtocols())
                .isNull();
        Assertions.assertThat(clientConfig.getCallTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getConnectionTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getReadTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getWriteTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.isFollowRedirects())
                .isNull();
        Assertions.assertThat(clientConfig.isFollowSslRedirects())
                .isNull();
        Assertions.assertThat(clientConfig.isRetryOnConnectionFailure())
                .isNull();
    }

    @Test
    void testDeser_withValues() throws JsonProcessingException {

        final String json = """
                {
                    "httpProtocols": [
                        "http/1.1",
                        "http/2"],
                    "callTimeout": "1d",
                    "followRedirects": true
                }
                """;
        final OkHttpClientConfig clientConfig = new ObjectMapper()
                .readerFor(OkHttpClientConfig.class)
                .readValue(json);

        Assertions.assertThat(clientConfig)
                .isNotNull();
        Assertions.assertThat(clientConfig.getHttpProtocols())
                .isEqualTo(List.of("http/1.1", "http/2"));
        Assertions.assertThat(clientConfig.getSslConfig())
                .isNull();
        Assertions.assertThat(clientConfig.getCallTimeout())
                .isEqualTo(StroomDuration.ofDays(1));
        Assertions.assertThat(clientConfig.getConnectionTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getReadTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getWriteTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.isFollowRedirects())
                .isTrue();
        Assertions.assertThat(clientConfig.isFollowSslRedirects())
                .isNull();
        Assertions.assertThat(clientConfig.isRetryOnConnectionFailure())
                .isNull();
    }
}
