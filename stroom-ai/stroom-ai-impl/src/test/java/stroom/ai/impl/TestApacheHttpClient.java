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

package stroom.ai.impl;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestApacheHttpClient {

    @Test
    void configuredHeadersAreSentUnlessTheRequestSetsThem() {
        final ApacheHttpClient client = new ApacheHttpClient(null, null, Map.of(
                "anthropic-version", "2023-06-01",
                "Authorization", "Bearer configured"));
        final HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("http://localhost/v1/chat/completions")
                .addHeader("Authorization", "Bearer api-key")
                .body("{}")
                .build();

        final ClassicHttpRequest apacheRequest = client.createApacheRequest(request);

        assertThat(apacheRequest.getFirstHeader("anthropic-version").getValue()).isEqualTo("2023-06-01");
        // The request's own authorisation wins.
        assertThat(apacheRequest.getHeaders("Authorization"))
                .extracting(Header::getValue)
                .containsExactly("Bearer api-key");
    }
}
