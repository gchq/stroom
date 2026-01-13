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

package stroom.openai.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.http.client.presenter.HttpClientConfigPresenter;
import stroom.item.client.SelectionBox;
import stroom.openai.client.presenter.OpenAIModelSettingsPresenter.OpenAIModelSettingsView;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.openai.shared.OpenAIModelResource;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class OpenAIModelSettingsPresenter extends DocumentEditPresenter<OpenAIModelSettingsView, OpenAIModelDoc>
        implements OpenAIModelSettingsUiHandlers {

    private static final OpenAIModelResource OPEN_AI_MODEL_RESOURCE = GWT.create(OpenAIModelResource.class);

    private final RestFactory restFactory;
    private final CredentialClient credentialClient;
    private final Provider<HttpClientConfigPresenter> httpClientSettingsPresenterProvider;
    private HttpClientConfig httpClientConfiguration;

    @Inject
    public OpenAIModelSettingsPresenter(
            final EventBus eventBus,
            final OpenAIModelSettingsView view,
            final RestFactory restFactory,
            final CredentialClient credentialClient,
            final Provider<HttpClientConfigPresenter> httpClientSettingsPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.credentialClient = credentialClient;
        this.httpClientSettingsPresenterProvider = httpClientSettingsPresenterProvider;
        final CredentialListModel credentialListModel = new CredentialListModel(
                eventBus,
                credentialClient,
                Set.of(CredentialType.ACCESS_TOKEN));
        credentialListModel.setTaskMonitorFactory(this);
        view.getApiKeySelectionBox().setModel(credentialListModel);
        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    public void onTestModel() {
        final OpenAIModelDoc model = onWrite(OpenAIModelDoc.builder().uuid(UUID.randomUUID().toString()).build());
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
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    protected void onRead(final DocRef docRef, final OpenAIModelDoc model, final boolean readOnly) {
        getView().setBaseUrl(model.getBaseUrl());
        if (model.getApiKeyName() != null) {
            credentialClient.getCredentialByName(model.getApiKeyName(), credential ->
                    getView().getApiKeySelectionBox().setValue(credential), this);
        } else {
            getView().getApiKeySelectionBox().setValue(null);
        }
        getView().setModelId(model.getModelId());
        getView().setMaxContextWindowTokens(model.getMaxContextWindowTokens());

        httpClientConfiguration = model.getHttpClientConfiguration();
        if (httpClientConfiguration == null) {
            restFactory
                    .create(OPEN_AI_MODEL_RESOURCE)
                    .method(OpenAIModelResource::getDefaultHttpClientConfig)
                    .onSuccess(result -> httpClientConfiguration = result)
                    .taskMonitorFactory(this)
                    .exec();
        }
    }

    @Override
    protected OpenAIModelDoc onWrite(final OpenAIModelDoc model) {
        model.setBaseUrl(getView().getBaseUrl());
        model.setApiKeyName(NullSafe.get(
                getView(),
                OpenAIModelSettingsView::getApiKeySelectionBox,
                SelectionBox::getValue,
                Credential::getName));
        model.setModelId(getView().getModelId());
        model.setMaxContextWindowTokens(getView().getMaxContextWindowTokens());
        model.setHttpClientConfiguration(httpClientConfiguration);
        return model;
    }

    @Override
    public void onSetHttpClientConfiguration() {
        httpClientSettingsPresenterProvider.get().show(httpClientConfiguration, updated -> {
            if (!Objects.equals(httpClientConfiguration, updated)) {
                setDirty(true);
                httpClientConfiguration = updated;
            }
        });
    }

    public interface OpenAIModelSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<OpenAIModelSettingsUiHandlers> {

        String getBaseUrl();

        void setBaseUrl(String baseUrl);

        SelectionBox<Credential> getApiKeySelectionBox();

        String getModelId();

        void setModelId(String modelId);

        int getMaxContextWindowTokens();

        void setMaxContextWindowTokens(int maxContextWindowTokens);
    }
}
