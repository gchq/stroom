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

package stroom.langchain.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.langchain.api.OpenAIModelStore;
import stroom.langchain.api.OpenAIService;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.openai.shared.OpenAIModelResource;
import stroom.openai.shared.OpenAIModelTestResponse;
import stroom.util.date.DateUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.NullSafe;

import com.openai.models.models.Model;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.NoSuchElementException;

@AutoLogged
public class OpenAIModelResourceImpl implements OpenAIModelResource, FetchWithUuid<OpenAIModelDoc> {

    private final Provider<OpenAIModelStore> openAIModelStoreProvider;
    private final Provider<OpenAIService> openAIServiceProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    OpenAIModelResourceImpl(
            final Provider<OpenAIModelStore> openAIModelStoreProvider,
            final Provider<OpenAIService> openAIServiceProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.openAIModelStoreProvider = openAIModelStoreProvider;
        this.openAIServiceProvider = openAIServiceProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public OpenAIModelDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(openAIModelStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public OpenAIModelDoc update(final String uuid, final OpenAIModelDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(openAIModelStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(OpenAIModelDoc.TYPE)
                .build();
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Validate OpenAI model")
    public OpenAIModelTestResponse validateModel(final OpenAIModelDoc modelDoc) {
        try {
            if (NullSafe.isEmptyString(modelDoc.getModelId())) {
                throw new IllegalArgumentException("Model ID must not be empty");
            }

            final Model model = openAIServiceProvider.get().getModel(modelDoc);
            final StringBuilder sb = new StringBuilder()
                    .append("Model ID: ")
                    .append(model.id())
                    .append("\nCreated: ")
                    .append(DateUtil.createNormalDateTimeString(model.created()))
                    .append("\nOwner: ")
                    .append(model.ownedBy())
                    .append("\nValid: ")
                    .append(model.isValid());

            return new OpenAIModelTestResponse(model.isValid(), sb.toString());
        } catch (final NoSuchElementException e) {
            return new OpenAIModelTestResponse(false, "Model " + modelDoc.getModelId() + " not found");
        }
    }
}
