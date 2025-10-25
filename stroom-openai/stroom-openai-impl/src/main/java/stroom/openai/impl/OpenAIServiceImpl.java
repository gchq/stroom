/*
 * Copyright 2025 Crown Copyright
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

package stroom.openai.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.openai.api.OpenAIService;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.shared.NullSafe;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.credential.BearerTokenCredential;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;

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
    public OpenAIModelDoc getOpenAIModelDoc(final DocRef docRef) {
        return documentResourceHelperProvider.get().read(openAIModelStoreProvider.get(), docRef);
    }

    @Override
    public OpenAIClient createOpenAIClient(final OpenAIModelDoc modelDoc) {
        final OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
                .fromEnv();

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            clientBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        if (modelDoc.getAuthToken() != null) {
            // Provide a bearer token
            clientBuilder.credential(BearerTokenCredential.create(modelDoc.getAuthToken()));
        } else {
            clientBuilder.credential(BearerTokenCredential.create(""));
        }

        return clientBuilder.build();
    }

    @Override
    public List<Float> getVectorEmbeddings(final OpenAIClient client,
                                           final OpenAIModelDoc modelDoc,
                                           final String expression) {
        final EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(modelDoc.getModelId())
                .input(expression)
                .build();

        final CreateEmbeddingResponse response = client.embeddings().create(params);
        return response.data().getFirst().embedding();
    }
}
