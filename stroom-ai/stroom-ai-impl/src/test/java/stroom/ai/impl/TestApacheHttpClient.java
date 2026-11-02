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

import stroom.util.jersey.HttpClientProvider;
import stroom.util.jersey.HttpClientProviderCache;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestApacheHttpClient {

    @Test
    void streamingDoesNotCloseTheSharedClient() throws Exception {
        // The client comes from a cache and is shared by every request with the same configuration. Closing
        // it after a streamed answer made the next request fail with "Connection pool shut down".
        final CloseableHttpClient sharedClient = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(sharedClient.execute(Mockito.any(ClassicHttpRequest.class),
                        Mockito.<HttpClientResponseHandler<Object>>any()))
                .thenAnswer(invocation -> {
                    final BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
                    response.setEntity(new StringEntity("data: {}\n\n", ContentType.TEXT_EVENT_STREAM));
                    return invocation.<HttpClientResponseHandler<Object>>getArgument(1).handleResponse(response);
                });
        final HttpClientProvider provider = Mockito.mock(HttpClientProvider.class);
        Mockito.when(provider.get()).thenReturn(sharedClient);
        final HttpClientProviderCache cache = Mockito.mock(HttpClientProviderCache.class);
        Mockito.when(cache.get(Mockito.any())).thenReturn(provider);
        final ApacheHttpClient client = new ApacheHttpClient(cache, null, Map.of());

        for (int i = 0; i < 2; i++) {
            client.execute(HttpRequest.builder()
                            .method(HttpMethod.POST)
                            .url("http://localhost/v1/chat/completions")
                            .body("{}")
                            .build(),
                    new DefaultServerSentEventParser(),
                    Mockito.mock(ServerSentEventListener.class));
        }

        Mockito.verify(sharedClient, Mockito.never()).close();
        Mockito.verify(sharedClient, Mockito.never()).close(Mockito.any());
        // Our reference is handed back each time instead.
        Mockito.verify(provider, Mockito.times(2)).close();
    }

    @Test
    void theListenerIsToldWhenTheStreamOpensAndCloses() throws Exception {
        // The OpenAI streaming model only completes its answer on close, so without it a chat run never
        // finished: the answer kept its streaming cursor and the next question was taken as sent mid-run.
        final CloseableHttpClient sharedClient = Mockito.mock(CloseableHttpClient.class);
        Mockito.when(sharedClient.execute(Mockito.any(ClassicHttpRequest.class),
                        Mockito.<HttpClientResponseHandler<Object>>any()))
                .thenAnswer(invocation -> {
                    final BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
                    response.setEntity(new StringEntity("data: {\"a\":1}\n\ndata: [DONE]\n\n",
                            ContentType.TEXT_EVENT_STREAM));
                    return invocation.<HttpClientResponseHandler<Object>>getArgument(1).handleResponse(response);
                });
        final HttpClientProvider provider = Mockito.mock(HttpClientProvider.class);
        Mockito.when(provider.get()).thenReturn(sharedClient);
        final HttpClientProviderCache cache = Mockito.mock(HttpClientProviderCache.class);
        Mockito.when(cache.get(Mockito.any())).thenReturn(provider);
        final ServerSentEventListener listener = Mockito.mock(ServerSentEventListener.class);

        new ApacheHttpClient(cache, null, Map.of()).execute(HttpRequest.builder()
                        .method(HttpMethod.POST)
                        .url("http://localhost/v1/chat/completions")
                        .body("{}")
                        .build(),
                new DefaultServerSentEventParser(),
                listener);

        final InOrder inOrder = Mockito.inOrder(listener);
        inOrder.verify(listener).onOpen(Mockito.argThat(response -> response.statusCode() == 200));
        inOrder.verify(listener, Mockito.atLeastOnce()).onEvent(Mockito.any(), Mockito.any());
        inOrder.verify(listener).onClose();
        Mockito.verify(listener, Mockito.never()).onError(Mockito.any());
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
