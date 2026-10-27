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

package stroom.openai.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.http.client.component.HttpClientConfigEditorController;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.openai.shared.OpenAIModelResource;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/// An OpenAI model's Settings tab ([OpenAIModelSettings]): how to reach the model and what it can
/// do, and a test of the model with them. Read only as its document is.
public class OpenAIModelSettingsController extends Controller {

    private static final OpenAIModelResource OPEN_AI_MODEL_RESOURCE = GWT.create(OpenAIModelResource.class);

    private final OpenAIModelSettings view;
    private final RestFactory restFactory;
    private final CredentialClient credentialClient;
    private final Provider<HttpClientConfigEditorController> httpClientConfigProvider;
    private HttpClientConfig httpClientConfiguration;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The tab.
    /// @param restFactory              Tests the model and fetches the default HTTP client
    ///                                 configuration.
    /// @param credentialClient         Finds the API key credentials.
    /// @param httpClientConfigProvider Makes the dialog that edits the HTTP client configuration.
    @Inject
    public OpenAIModelSettingsController(
            final EventBus eventBus,
            final OpenAIModelSettings view,
            final RestFactory restFactory,
            final CredentialClient credentialClient,
            final Provider<HttpClientConfigEditorController> httpClientConfigProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.credentialClient = credentialClient;
        this.httpClientConfigProvider = httpClientConfigProvider;
        initWidget(view);

        final CredentialListModel credentialListModel = new CredentialListModel(
                eventBus,
                credentialClient,
                Set.of(CredentialType.ACCESS_TOKEN));
        credentialListModel.setTaskMonitorFactory(taskMonitor());
        view.setApiKeyModel(credentialListModel);

        view.setOnChange(this::markChanged);
        view.setOnSetHttpClientConfig(this::onSetHttpClientConfiguration);
        view.setOnTestModel(this::onTestModel);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    private void onTestModel() {
        final OpenAIModelDoc model = write(OpenAIModelDoc.builder().uuid(UUID.randomUUID().toString()).build());
        restFactory
                .create(OPEN_AI_MODEL_RESOURCE)
                .method(res -> res.validateModel(model))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Model Validation Successful", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Model Validation Failed", result.getMessage(), null);
                    }
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    /// Shows a model's settings.
    ///
    /// @param docRef The model.
    /// @param model  The model.
    public void read(final DocRef docRef, final OpenAIModelDoc model) {
        view.setBaseUrl(model.getBaseUrl());
        if (model.getApiKeyName() != null) {
            credentialClient.getCredentialByName(model.getApiKeyName(), guard(view::setApiKey), taskMonitor());
        } else {
            view.setApiKey(null);
        }
        view.setModelId(model.getModelId());
        view.setMaxContextWindowTokens(model.getMaxContextWindowTokens());
        view.setReasoningEffort(model.getReasoningEffort());
        view.setEmbeddingModelDimensions(model.getEmbeddingModelDimensions());

        httpClientConfiguration = model.getHttpClientConfiguration();
        if (httpClientConfiguration == null) {
            restFactory
                    .create(OPEN_AI_MODEL_RESOURCE)
                    .method(OpenAIModelResource::getDefaultHttpClientConfig)
                    .onSuccess(result -> httpClientConfiguration = result)
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    /// Writes the settings shown into a copy of a model.
    ///
    /// @param model The model.
    /// @return A copy of it with the settings shown.
    public OpenAIModelDoc write(final OpenAIModelDoc model) {
        return model
                .copy()
                .baseUrl(view.getBaseUrl())
                .apiKey(NullSafe.get(view.getApiKey(), Credential::getName))
                .modelId(view.getModelId())
                .maxContextWindowTokens(view.getMaxContextWindowTokens())
                .reasoningEffort(view.getReasoningEffort())
                .embeddingModelDimensions(view.getEmbeddingModelDimensions())
                .httpClientConfiguration(httpClientConfiguration)
                .build();
    }

    private void onSetHttpClientConfiguration() {
        httpClientConfigProvider.get().show(this, httpClientConfiguration, updated -> {
            if (!Objects.equals(httpClientConfiguration, updated)) {
                httpClientConfiguration = updated;
                markChanged();
            }
        });
    }

    // Lazily, as it is first used after this is owned
    private TaskMonitorFactory taskMonitor() {
        return () -> {
            final TaskMonitorFactory provided = getProvidedTaskMonitor();
            return provided != null
                    ? provided.createTaskMonitor()
                    : new DefaultTaskMonitorFactory(this).createTaskMonitor();
        };
    }
}
