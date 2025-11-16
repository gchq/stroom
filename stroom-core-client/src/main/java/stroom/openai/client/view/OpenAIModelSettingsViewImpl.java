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

package stroom.openai.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.openai.client.presenter.OpenAIModelSettingsPresenter.OpenAIModelSettingsView;
import stroom.openai.client.presenter.OpenAIModelSettingsUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class OpenAIModelSettingsViewImpl extends ViewWithUiHandlers<OpenAIModelSettingsUiHandlers>
        implements OpenAIModelSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextBox baseUrl;
    @UiField
    TextBox apiKey;
    @UiField
    TextBox modelId;
    @UiField
    ValueSpinner maxContextWindowTokens;
    @UiField
    Button testModel;

    @Inject
    public OpenAIModelSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        testModel.setIcon(SvgImage.OK);

        maxContextWindowTokens.setMin(0L);
        maxContextWindowTokens.setMax(Integer.MAX_VALUE);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl.getValue();
    }

    @Override
    public void setBaseUrl(final String baseUrl) {
        this.baseUrl.setValue(baseUrl);
    }

    @Override
    public String getApiKey() {
        return apiKey.getValue();
    }

    @Override
    public void setApiKey(final String authToken) {
        this.apiKey.setValue(authToken);
    }

    @Override
    public String getModelId() {
        return modelId.getValue();
    }

    @Override
    public void setModelId(final String modelId) {
        this.modelId.setValue(modelId);
    }

    @Override
    public int getMaxContextWindowTokens() {
        return maxContextWindowTokens.getIntValue();
    }

    @Override
    public void setMaxContextWindowTokens(final int maxContextWindowTokens) {
        this.maxContextWindowTokens.setValue(maxContextWindowTokens);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        baseUrl.setEnabled(!readOnly);
        apiKey.setEnabled(!readOnly);
        modelId.setEnabled(!readOnly);
        maxContextWindowTokens.setEnabled(!readOnly);
    }

    private void fireChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("baseUrl")
    public void onBaseUrl(final ValueChangeEvent<String> event) {
        fireChange();
    }

    @UiHandler("apiKey")
    public void onApiKey(final ValueChangeEvent<String> event) {
        fireChange();
    }

    @UiHandler("modelId")
    public void onModelId(final ValueChangeEvent<String> event) {
        fireChange();
    }

    @UiHandler("maxContextWindowTokens")
    public void onMaxContextWindowTokensValueChange(final ValueChangeEvent<Long> event) {
        fireChange();
    }

    @UiHandler("testModel")
    public void onTestModelClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTestModel();
        }
    }

    public interface Binder extends UiBinder<Widget, OpenAIModelSettingsViewImpl> {

    }
}
