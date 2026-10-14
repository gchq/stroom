/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.credentials.shared.Credential;
import stroom.item.client.SelectionBox;
import stroom.openai.client.presenter.McpToolServerEditPresenter.McpToolServerEditView;
import stroom.openai.client.presenter.McpToolServerEditUiHandlers;
import stroom.openai.shared.McpAuthMode;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class McpToolServerEditViewImpl
        extends ViewWithUiHandlers<McpToolServerEditUiHandlers>
        implements McpToolServerEditView {

    private final Widget widget;

    @UiField
    CustomCheckBox enabled;
    @UiField
    TextBox name;
    @UiField
    TextBox endpoint;
    @UiField
    SelectionBox<McpAuthMode> authMode;
    @UiField
    SelectionBox<Credential> credential;
    @UiField
    FormGroup apiKeyHeaderNameGroup;
    @UiField
    TextBox apiKeyHeaderName;
    @UiField
    FormGroup apiKeyValuePrefixGroup;
    @UiField
    TextBox apiKeyValuePrefix;
    @UiField
    TextBox allowedTools;
    @UiField
    TextBox deniedTools;
    @UiField
    CustomCheckBox trusted;
    @UiField
    TextArea toolPolicies;
    @UiField
    stroom.widget.button.client.Button setHttpClientConfig;

    @Inject
    public McpToolServerEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        enabled.setEnabled(!readOnly);
        name.setEnabled(!readOnly);
        endpoint.setEnabled(!readOnly);
        authMode.setEnabled(!readOnly);
        credential.setEnabled(!readOnly);
        apiKeyHeaderName.setEnabled(!readOnly);
        apiKeyValuePrefix.setEnabled(!readOnly);
        allowedTools.setEnabled(!readOnly);
        deniedTools.setEnabled(!readOnly);
        trusted.setEnabled(!readOnly);
        toolPolicies.setEnabled(!readOnly);
        setHttpClientConfig.setEnabled(!readOnly);
    }

    @Override
    public boolean isEnabled() {
        return enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    @Override
    public String getName() {
        return name.getValue();
    }

    @Override
    public void setName(final String name) {
        this.name.setValue(name);
    }

    @Override
    public String getEndpoint() {
        return endpoint.getValue();
    }

    @Override
    public void setEndpoint(final String endpoint) {
        this.endpoint.setValue(endpoint);
    }

    @Override
    public SelectionBox<McpAuthMode> getAuthModeSelectionBox() {
        return authMode;
    }

    @Override
    public SelectionBox<Credential> getCredentialSelectionBox() {
        return credential;
    }

    @Override
    public void setCredentialEnabled(final boolean enabled) {
        credential.setEnabled(enabled);
    }

    @Override
    public String getApiKeyHeaderName() {
        return apiKeyHeaderName.getValue();
    }

    @Override
    public void setApiKeyHeaderName(final String apiKeyHeaderName) {
        this.apiKeyHeaderName.setValue(apiKeyHeaderName);
    }

    @Override
    public String getApiKeyValuePrefix() {
        return apiKeyValuePrefix.getValue();
    }

    @Override
    public void setApiKeyValuePrefix(final String apiKeyValuePrefix) {
        this.apiKeyValuePrefix.setValue(apiKeyValuePrefix);
    }

    @Override
    public boolean isTrusted() {
        return trusted.getValue();
    }

    @Override
    public void setTrusted(final boolean trusted) {
        this.trusted.setValue(trusted);
    }

    @Override
    public String getToolPolicies() {
        return toolPolicies.getValue();
    }

    @Override
    public void setToolPolicies(final String toolPolicies) {
        this.toolPolicies.setValue(toolPolicies);
    }

    @Override
    public String getAllowedTools() {
        return allowedTools.getValue();
    }

    @Override
    public void setAllowedTools(final String allowedTools) {
        this.allowedTools.setValue(allowedTools);
    }

    @Override
    public String getDeniedTools() {
        return deniedTools.getValue();
    }

    @Override
    public void setDeniedTools(final String deniedTools) {
        this.deniedTools.setValue(deniedTools);
    }

    @Override
    public void setApiKeyFieldsVisible(final boolean visible) {
        apiKeyHeaderNameGroup.setVisible(visible);
        apiKeyValuePrefixGroup.setVisible(visible);
    }

    @UiHandler("authMode")
    public void onAuthMode(final ValueChangeEvent<McpAuthMode> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onAuthModeChange();
        }
    }

    @UiHandler("setHttpClientConfig")
    public void onSetHttpClientConfig(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetHttpClientConfiguration();
        }
    }

    public interface Binder extends UiBinder<Widget, McpToolServerEditViewImpl> {

    }
}
