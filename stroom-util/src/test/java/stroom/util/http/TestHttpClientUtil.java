/*
 * Copyright 2026 Crown Copyright
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

import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpHeader;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestHttpClientUtil {

    @Test
    void headersAreTrimmedAndBlankNamesLeftOut() {
        final HttpClientConfig config = HttpClientConfig.builder()
                .headers(Arrays.asList(
                        new HttpHeader(" anthropic-version ", "2023-06-01"),
                        new HttpHeader("", "ignored"),
                        new HttpHeader("   ", "ignored"),
                        new HttpHeader(null, "ignored"),
                        new HttpHeader("x-empty", null),
                        null))
                .build();

        assertThat(HttpClientUtil.headers(config))
                .containsExactly(Map.entry("anthropic-version", "2023-06-01"), Map.entry("x-empty", ""));
    }

    @Test
    void aDuplicateThatGetsThroughIsNeverSentTwice() {
        // Header names are not case-sensitive, so these are one header, and the later one wins.
        final HttpClientConfig config = HttpClientConfig.builder()
                .headers(Arrays.asList(
                        new HttpHeader("X-A", "1"),
                        new HttpHeader("x-b", "b"),
                        new HttpHeader("x-a", "2")))
                .build();

        assertThat(HttpClientUtil.headers(config))
                .containsExactly(Map.entry("x-b", "b"), Map.entry("x-a", "2"));
    }

    @Test
    void noHeaders() {
        assertThat(HttpClientUtil.headers(null)).isEmpty();
        assertThat(HttpClientUtil.headers(HttpClientConfig.builder().build())).isEmpty();
        assertThat(HttpClientConfig.builder().headers(java.util.List.of()).build().getHeaders()).isNull();
    }

    @Test
    void headerValuesAreNotShownInToString() {
        final HttpClientConfig config = HttpClientConfig.builder()
                .headers(Arrays.asList(new HttpHeader("x-api-key", "secret")))
                .build();

        assertThat(config.toString())
                .contains("x-api-key")
                .doesNotContain("secret");
    }
}
