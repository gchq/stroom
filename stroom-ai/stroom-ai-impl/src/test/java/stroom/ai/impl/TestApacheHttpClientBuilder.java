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
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.time.StroomDuration;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestApacheHttpClientBuilder {

    private static final Duration CONFIGURED_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration CONFIGURED_CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final String STOP_MESSAGE = "Stop before any network access";

    @Mock
    private HttpClientProviderCache httpClientProviderCache;

    @Test
    void testTimeoutGettersReturnConfiguredValues() {
        final ApacheHttpClientBuilder builder = createBuilder();

        assertThat(builder.readTimeout())
                .isEqualTo(CONFIGURED_TIMEOUT);
        assertThat(builder.connectTimeout())
                .isEqualTo(CONFIGURED_CONNECTION_TIMEOUT);
    }

    @Test
    void testTimeoutSettersDoNotOverrideConfiguredValues() {
        final ApacheHttpClientBuilder builder = createBuilder();

        // These are the values langchain4j pushes in when no timeout is set on the model builder.
        final Object returned = builder
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(60));

        assertThat(returned)
                .isSameAs(builder);
        assertThat(builder.readTimeout())
                .isEqualTo(CONFIGURED_TIMEOUT);
        assertThat(builder.connectTimeout())
                .isEqualTo(CONFIGURED_CONNECTION_TIMEOUT);
    }

    @Test
    void testTimeoutsWithNullConfigurationUseDefaults() {
        final ApacheHttpClientBuilder builder = new ApacheHttpClientBuilder(httpClientProviderCache, null);

        assertThat(builder.readTimeout())
                .isEqualTo(HttpClientConfiguration.DEFAULT_TIMEOUT.getDuration());
        assertThat(builder.connectTimeout())
                .isEqualTo(HttpClientConfiguration.DEFAULT_CONNECTION_TIMEOUT.getDuration());
    }

    @Test
    void testChatModelUsesConfiguredTimeouts() {
        // Regression test for gh-6017. OpenAiChatModel pushes a 60s read timeout into the builder,
        // which used to replace the timeout configured on the OpenAI Model document.
        when(httpClientProviderCache.get(any()))
                .thenThrow(new IllegalStateException(STOP_MESSAGE));

        final ChatModel chatModel = OpenAiChatModel.builder()
                .httpClientBuilder(createBuilder())
                .baseUrl("http://localhost:1/v1")
                .apiKey("dummy")
                .modelName("dummy")
                .maxRetries(0)
                .build();

        assertThatThrownBy(() -> chatModel.chat(List.of(UserMessage.from("Hello"))))
                .hasMessageContaining(STOP_MESSAGE);

        assertClientConfigurationsUseConfiguredTimeouts();
    }

    @Test
    void testEmbeddingModelUsesConfiguredTimeouts() {
        when(httpClientProviderCache.get(any()))
                .thenThrow(new IllegalStateException(STOP_MESSAGE));

        final EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .httpClientBuilder(createBuilder())
                .baseUrl("http://localhost:1/v1")
                .apiKey("dummy")
                .modelName("dummy")
                .maxRetries(0)
                .build();

        assertThatThrownBy(() -> embeddingModel.embed("Hello"))
                .hasMessageContaining(STOP_MESSAGE);

        assertClientConfigurationsUseConfiguredTimeouts();
    }

    private ApacheHttpClientBuilder createBuilder() {
        final HttpClientConfiguration httpClientConfiguration = HttpClientConfiguration.builder()
                .timeout(StroomDuration.of(CONFIGURED_TIMEOUT))
                .connectionTimeout(StroomDuration.of(CONFIGURED_CONNECTION_TIMEOUT))
                .build();
        return new ApacheHttpClientBuilder(httpClientProviderCache, httpClientConfiguration);
    }

    private void assertClientConfigurationsUseConfiguredTimeouts() {
        final ArgumentCaptor<HttpClientConfiguration> captor =
                ArgumentCaptor.forClass(HttpClientConfiguration.class);
        verify(httpClientProviderCache, atLeastOnce()).get(captor.capture());

        assertThat(captor.getAllValues())
                .isNotEmpty()
                .allSatisfy(config -> {
                    assertThat(config.getTimeout().getDuration())
                            .isEqualTo(CONFIGURED_TIMEOUT);
                    assertThat(config.getConnectionTimeout().getDuration())
                            .isEqualTo(CONFIGURED_CONNECTION_TIMEOUT);
                });
    }
}
