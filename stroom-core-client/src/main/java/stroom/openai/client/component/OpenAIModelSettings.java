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

import stroom.credentials.shared.Credential;
import stroom.item.client.SelectionBox;
import stroom.item.client.SelectionListModel;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// An OpenAI model's Settings tab: the API's base URL, the API key credential, the model's id, its
/// context window, reasoning effort and embedding dimensions, and buttons to set the HTTP client
/// configuration and to test the model.
public class OpenAIModelSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox baseUrl;
    @UiField
    SelectionBox<Credential> apiKey;
    @UiField
    TextBox modelId;
    @UiField
    ValueSpinner maxContextWindowTokens;
    @UiField
    TextBox reasoningEffort;
    @UiField
    Button testModel;
    @UiField
    ValueSpinner embeddingModelDimensions;
    @UiField
    Button setHttpClientConfig;

    private Runnable onChange = () -> {
    };

    /// Makes the tab, with nothing set.
    public OpenAIModelSettings() {
        initWidget(BINDER.createAndBindUi(this));
        baseUrl.addValueChangeHandler(e -> onChange.run());
        apiKey.addValueChangeHandler(e -> onChange.run());
        modelId.addValueChangeHandler(e -> onChange.run());
        maxContextWindowTokens.addValueChangeHandler(e -> onChange.run());
        reasoningEffort.addValueChangeHandler(e -> onChange.run());
        embeddingModelDimensions.addValueChangeHandler(e -> onChange.run());
    }

    /// @return The API's base URL, blank for OpenAI's.
    public String getBaseUrl() {
        return baseUrl.getValue();
    }

    /// @param baseUrl The API's base URL, blank for OpenAI's.
    public void setBaseUrl(final String baseUrl) {
        this.baseUrl.setValue(baseUrl);
    }

    /// @param model The credentials the API key can be chosen from.
    public void setApiKeyModel(final SelectionListModel<Credential, SimpleSelectionItemWrapper<Credential>> model) {
        apiKey.setModel(model);
    }

    /// @param credential The API key credential, or `null` for none.
    public void setApiKey(final Credential credential) {
        apiKey.setValue(credential);
    }

    /// @return The API key credential chosen, or `null` for none.
    public Credential getApiKey() {
        return apiKey.getValue();
    }

    /// @return The model's id.
    public String getModelId() {
        return modelId.getValue();
    }

    /// @param modelId The model's id.
    public void setModelId(final String modelId) {
        this.modelId.setValue(modelId);
    }

    /// @return The largest number of tokens the model accepts at once, 0 if not known.
    public int getMaxContextWindowTokens() {
        return maxContextWindowTokens.getIntValue();
    }

    /// @param maxContextWindowTokens The largest number of tokens the model accepts at once, 0 if
    ///                               not known.
    public void setMaxContextWindowTokens(final int maxContextWindowTokens) {
        this.maxContextWindowTokens.setValue(maxContextWindowTokens);
    }

    /// @return The reasoning effort, blank for the model's default.
    public String getReasoningEffort() {
        return reasoningEffort.getValue();
    }

    /// @param reasoningEffort The reasoning effort, blank for the model's default.
    public void setReasoningEffort(final String reasoningEffort) {
        this.reasoningEffort.setValue(reasoningEffort);
    }

    /// @return The number of dimensions of embedding vectors, 0 for the model's default.
    public int getEmbeddingModelDimensions() {
        return embeddingModelDimensions.getIntValue();
    }

    /// @param embeddingModelDimensions The number of dimensions of embedding vectors, 0 for the
    ///                                 model's default.
    public void setEmbeddingModelDimensions(final int embeddingModelDimensions) {
        this.embeddingModelDimensions.setValue(embeddingModelDimensions);
    }

    /// @param onChange Run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onSetHttpClientConfig Run when Set HTTP Client Config is pressed.
    public void setOnSetHttpClientConfig(final Runnable onSetHttpClientConfig) {
        setHttpClientConfig.setOnClick(onSetHttpClientConfig);
    }

    /// @param onTestModel Run when Test Model is pressed.
    public void setOnTestModel(final Runnable onTestModel) {
        testModel.setOnClick(onTestModel);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        baseUrl.setReadOnly(readOnly);
        apiKey.setReadOnly(readOnly);
        modelId.setReadOnly(readOnly);
        maxContextWindowTokens.setReadOnly(readOnly);
        reasoningEffort.setReadOnly(readOnly);
        embeddingModelDimensions.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, OpenAIModelSettings> {

    }
}
