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

package stroom.langchain.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.langchain.api.OpenAIModelStore;
import stroom.langchain.api.OpenAIService;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.shared.NullSafe;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.credential.BearerTokenCredential;
import com.openai.models.models.Model;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.cohere.CohereScoringModel.CohereScoringModelBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.jina.JinaScoringModel;
import dev.langchain4j.model.jina.JinaScoringModel.JinaScoringModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder;
import dev.langchain4j.model.scoring.ScoringModel;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.net.http.HttpClient;

@Singleton
public class OpenAIServiceImpl implements OpenAIService {

    private final Provider<OpenAIModelStore> openAIModelStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    OpenAIServiceImpl(final Provider<OpenAIModelStore> openAIModelStoreProvider,
                      final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.openAIModelStoreProvider = openAIModelStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public Model getModel(final OpenAIModelDoc modelDoc) {
        final OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
                .fromEnv();

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            clientBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        if (modelDoc.getApiKey() != null) {
            // Provide a bearer token
            clientBuilder.credential(BearerTokenCredential.create(modelDoc.getApiKey()));
        } else {
            clientBuilder.credential(BearerTokenCredential.create(""));
        }

        final OpenAIClient client = clientBuilder.build();
        return client.models().list().items().stream()
                .filter(model -> modelDoc.getModelId().equals(model.id()))
                .findFirst().orElseThrow();
    }

    @Override
    public OpenAIModelDoc getOpenAIModelDoc(final DocRef docRef) {
        return documentResourceHelperProvider.get().read(openAIModelStoreProvider.get(), docRef);
    }

    @Override
    public ChatModel getChatModel(final OpenAIModelDoc modelDoc) {
        return getChatModel(modelDoc.getModelId(), modelDoc.getBaseUrl(), modelDoc.getApiKey());
    }

    @Override
    public ChatModel getChatModel(final String modelId, final String baseUrl, final String apiKey) {
        // Need to specify HTTP 1.1 for vLLM interoperability
        // Ref: https://github.com/langchain4j/langchain4j/issues/3682
        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);
        final JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
                .httpClientBuilder(httpClientBuilder);

        final OpenAiChatModelBuilder modelBuilder = OpenAiChatModel.builder()
                .modelName(modelId)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .httpClientBuilder(jdkHttpClientBuilder);

        return modelBuilder.build();
    }

    @Override
    public EmbeddingModel getEmbeddingModel(final OpenAIModelDoc modelDoc) {
        // Need to specify HTTP 1.1 for vLLM interoperability
        // Ref: https://github.com/langchain4j/langchain4j/issues/3682
        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1);
        final JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
                .httpClientBuilder(httpClientBuilder);

        final OpenAiEmbeddingModelBuilder modelBuilder = OpenAiEmbeddingModel.builder()
                .modelName(modelDoc.getModelId())
                .httpClientBuilder(jdkHttpClientBuilder);

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        if (NullSafe.isNonEmptyString(modelDoc.getApiKey())) {
            // Provide a bearer token
            modelBuilder.apiKey(modelDoc.getApiKey());
        }

        return modelBuilder.build();
    }

    @Override
    public ScoringModel getCohereScoringModel(final OpenAIModelDoc modelDoc) {
        final CohereScoringModelBuilder modelBuilder = CohereScoringModel.builder()
                .modelName(modelDoc.getModelId());

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        if (NullSafe.isNonEmptyString(modelDoc.getApiKey())) {
            // Provide a bearer token
            modelBuilder.apiKey(modelDoc.getApiKey());
        } else {
            modelBuilder.apiKey("dummy_api_key");
        }

        return modelBuilder.build();
    }

    @Override
    public ScoringModel getJinaScoringModel(final OpenAIModelDoc modelDoc) {
        final JinaScoringModelBuilder modelBuilder = JinaScoringModel.builder()
                .modelName(modelDoc.getModelId());

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        if (NullSafe.isNonEmptyString(modelDoc.getApiKey())) {
            // Provide a bearer token
            modelBuilder.apiKey(modelDoc.getApiKey());
        } else {
            modelBuilder.apiKey("dummy_api_key");
        }

        return modelBuilder.build();
    }
}
