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

import stroom.util.http.HttpClientConfiguration;
import stroom.util.jersey.HttpClientProvider;
import stroom.util.jersey.HttpClientProviderCache;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestApacheHttpClient {

    @Mock
    private HttpClientProviderCache httpClientProviderCache;
    @Mock
    private HttpClientProvider httpClientProvider;
    @Mock
    private CloseableHttpClient closeableHttpClient;
    @Mock
    private ClassicHttpResponse response;
    @Mock
    private ServerSentEventParser parser;
    @Mock
    private ServerSentEventListener listener;

    @Test
    void testServerSentEventsDoNotCloseSharedClient() throws Exception {
        // The Apache client comes from a shared cache, so a streaming request must release the
        // provider but never close the client itself, or other users of the client would break.
        final HttpClientConfiguration httpClientConfiguration = HttpClientConfiguration.builder().build();
        when(httpClientProviderCache.get(httpClientConfiguration))
                .thenReturn(httpClientProvider);
        when(httpClientProvider.get())
                .thenReturn(closeableHttpClient);
        when(response.getCode())
                .thenReturn(200);
        when(response.getEntity())
                .thenReturn(new StringEntity("data: hello\n\n"));
        when(closeableHttpClient.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    final HttpClientResponseHandler<?> handler = invocation.getArgument(1);
                    return handler.handleResponse(response);
                });

        final ApacheHttpClient apacheHttpClient = new ApacheHttpClient(
                httpClientProviderCache, httpClientConfiguration);
        final HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("http://localhost:1/v1/chat/completions")
                .body("{}")
                .build();

        apacheHttpClient.execute(request, parser, listener);

        verify(parser).parse(any(InputStream.class), any(ServerSentEventListener.class));
        verify(listener, never()).onError(any());
        verify(closeableHttpClient, never()).close();
        verify(closeableHttpClient, never()).close(any(CloseMode.class));
        verify(httpClientProvider).close();
    }

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
