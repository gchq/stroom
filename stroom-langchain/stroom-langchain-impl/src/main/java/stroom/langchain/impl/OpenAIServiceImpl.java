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

import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.langchain.api.OpenAIModelStore;
import stroom.langchain.api.OpenAIService;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.http.HttpClientCache;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.shared.NullSafe;

import dev.langchain4j.http.client.HttpClientBuilder;
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
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Singleton
public class OpenAIServiceImpl implements OpenAIService {

    private final Provider<OpenAIModelStore> openAIModelStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<StoredSecrets> storedSecretsProvider;
    private final Provider<HttpClientCache> httpClientCacheProvider;

    @Inject
    OpenAIServiceImpl(final Provider<OpenAIModelStore> openAIModelStoreProvider,
                      final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                      final Provider<StoredSecrets> storedSecretsProvider,
                      final Provider<HttpClientCache> httpClientCacheProvider) {
        this.openAIModelStoreProvider = openAIModelStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.storedSecretsProvider = storedSecretsProvider;
        this.httpClientCacheProvider = httpClientCacheProvider;
    }

    @Override
    public String getModel(final OpenAIModelDoc modelDoc) {
        try {
            /// curl https://api.openai.com/v1/models \
            ///   -H "Authorization: Bearer $OPENAI_API_KEY"


            final HttpClientCache httpClientCache = httpClientCacheProvider.get();
            final HttpClientConfiguration httpClientConfiguration = new HttpClientConfiguration();
            final HttpClient httpClient = httpClientCache.get(httpClientConfiguration);


            final String url = getUrl(modelDoc, "models");
            final String apiKey = getApiKey(modelDoc);

            final HttpGet httpGet = new HttpGet(url);
            httpGet.addHeader("Content-Type", "application/audit");
            if (NullSafe.isNonBlankString(apiKey)) {
                httpGet.addHeader("Authorization", "Bearer " + apiKey);
            }

            return httpClient.execute(httpGet, response -> {
//                        final StringBuilder sb = new StringBuilder()
//                    .append("Model ID: ")
//                    .append(model.id())
//                    .append("\nCreated: ")
//                    .append(DateUtil.createNormalDateTimeString(model.created()))
//                    .append("\nOwner: ")
//                    .append(model.ownedBy())
//                    .append("\nValid: ")
//                    .append(model.isValid());

                if (response.getCode() != 200) {
                    return response.toString();
                }

                final byte[] bytes = response.getEntity().getContent().readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            });

//        final OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
//                .fromEnv();
//
//        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
//            // Override the base URL
//            clientBuilder.baseUrl(modelDoc.getBaseUrl());
//        }
//
//        final String apiKey = getApiKey(modelDoc);
//        // Provide a bearer token
//        clientBuilder.credential(BearerTokenCredential.create(apiKey));
//
//        final OpenAIClient client = clientBuilder.build();
//        return client.models().list().items().stream()
//                .filter(model -> modelDoc.getModelId().equals(model.id()))
//                .findFirst().orElseThrow();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getApiKey(final OpenAIModelDoc doc) {
        return getApiKey(doc.getApiKeyName());
    }

    private String getApiKey(final String apiKeyName) {
        if (NullSafe.isNonBlankString(apiKeyName)) {
            final StoredSecret storedSecret = storedSecretsProvider.get().get(apiKeyName);
            if (storedSecret != null) {
                if (storedSecret.secret() instanceof final AccessTokenSecret accessTokenSecret) {
                    if (accessTokenSecret.getAccessToken() != null) {
                        return accessTokenSecret.getAccessToken();
                    }
                }
            }
        }
        return "";
    }

    private String getUrl(final OpenAIModelDoc modelDoc, final String path) {
        String url = Objects.requireNonNullElse(modelDoc.getBaseUrl(), "https://api.openai.com/v1");
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        if (NullSafe.isNonBlankString(path)) {
            url = url + path;
        }
        return url;
    }

    @Override
    public OpenAIModelDoc getOpenAIModelDoc(final DocRef docRef) {
        return documentResourceHelperProvider.get().read(openAIModelStoreProvider.get(), docRef);
    }

    @Override
    public ChatModel getChatModel(final OpenAIModelDoc modelDoc) {
        final String apiKey = getApiKey(modelDoc.getApiKeyName());

        // Need to specify HTTP 1.1 for vLLM interoperability
        // Ref: https://github.com/langchain4j/langchain4j/issues/3682
//        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
//                .version(HttpClient.Version.HTTP_1_1);
//
////                .sslContext()authenticator();

//        final HttpClientConfiguration httpClientConfiguration = new HttpClientConfiguration();
//        final CloseableHttpClient httpClient =
//        httpClientFactoryProvider.get().get(modelName, httpClientConfiguration);

//        final ApacheHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
//                .httpClientBuilder(httpClientBuilder)
//
//              ;

        final HttpClientBuilder httpClientBuilder = getClientBuilder(modelDoc);
        final OpenAiChatModelBuilder modelBuilder = OpenAiChatModel.builder()
                .modelName(modelDoc.getModelId())
                .apiKey(apiKey)
                .httpClientBuilder(httpClientBuilder);

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        return modelBuilder.build();
    }

    @Override
    public EmbeddingModel getEmbeddingModel(final OpenAIModelDoc modelDoc) {
        final String apiKey = getApiKey(modelDoc.getApiKeyName());

        // Need to specify HTTP 1.1 for vLLM interoperability
        // Ref: https://github.com/langchain4j/langchain4j/issues/3682
//        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
//                .version(HttpClient.Version.HTTP_1_1);
//        final JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
//                .httpClientBuilder(httpClientBuilder);

        final HttpClientBuilder httpClientBuilder = getClientBuilder(modelDoc);
        final OpenAiEmbeddingModelBuilder modelBuilder = OpenAiEmbeddingModel.builder()
                .modelName(modelDoc.getModelId())
                .apiKey(apiKey)
                .httpClientBuilder(httpClientBuilder);

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        // Provide a bearer token
        modelBuilder.apiKey(getApiKey(modelDoc));

        return modelBuilder.build();
    }

    private HttpClientBuilder getClientBuilder(final OpenAIModelDoc modelDoc) {
        final HttpClientConfiguration httpClientConfiguration = new HttpClientConfiguration();
        final HttpClientCache httpClientCache = httpClientCacheProvider.get();
        final HttpClient httpClient = httpClientCache.get(httpClientConfiguration);
        return new ApacheHttpClientBuilder(httpClient, httpClientConfiguration);
    }

    @Override
    public ScoringModel getCohereScoringModel(final OpenAIModelDoc modelDoc) {
        final CohereScoringModelBuilder modelBuilder = CohereScoringModel.builder()
                .modelName(modelDoc.getModelId());

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        modelBuilder.apiKey(getApiKey(modelDoc));
//        } else {
//            modelBuilder.apiKey("dummy_api_key");
//        }

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

        // Provide a bearer token
        modelBuilder.apiKey(getApiKey(modelDoc));
//        } else {
//            modelBuilder.apiKey("dummy_api_key");
//        }

        return modelBuilder.build();
    }
}
