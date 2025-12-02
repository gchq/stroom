/*
 * Copyright 2016 Crown Copyright
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

package stroom.search.elastic.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.search.elastic.client.presenter.ElasticClusterSettingsPresenter.ElasticClusterSettingsView;
import stroom.search.elastic.client.presenter.ElasticClusterSettingsUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.IntegerBox;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ElasticClusterSettingsViewImpl extends ViewWithUiHandlers<ElasticClusterSettingsUiHandlers>
        implements ElasticClusterSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextArea connectionUrls;
    @UiField
    TextArea caCertificate;
    @UiField
    CustomCheckBox useAuthentication;
    @UiField
    TextBox apiKeyId;
    @UiField
    PasswordTextBox apiKeySecret;
    @UiField
    IntegerBox connectionTimeoutMillis;
    @UiField
    IntegerBox responseTimeoutMillis;
    @UiField
    Button testConnection;

    @Inject
    public ElasticClusterSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        testConnection.setIcon(SvgImage.OK);

        updateAuthenticationControlEnabledState(useAuthentication.getValue());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public List<String> getConnectionUrls() {
        return Arrays.stream(connectionUrls.getText().split("\n"))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @Override
    public void setConnectionUrls(final List<String> connectionUrls) {
        this.connectionUrls.setText(String.join("\n", connectionUrls));
    }

    @Override
    public String getCaCertificate() {
        return caCertificate.getText().trim();
    }

    @Override
    public void setCaCertificate(final String caCertificate) {
        this.caCertificate.setText(caCertificate);
    }

    @Override
    public boolean getUseAuthentication() {
        return useAuthentication.getValue();
    }

    @Override
    public void setUseAuthentication(final boolean useAuthentication) {
        this.useAuthentication.setValue(useAuthentication);
        updateAuthenticationControlEnabledState(useAuthentication);
    }

    @Override
    public String getApiKeyId() {
        return apiKeyId.getText().trim();
    }

    @Override
    public void setApiKeyId(final String apiKeyId) {
        this.apiKeyId.setText(apiKeyId);
    }

    @Override
    public String getApiKeySecret() {
        return apiKeySecret.getText().trim();
    }

    @Override
    public void setApiKeySecret(final String apiKeySecret) {
        this.apiKeySecret.setText(apiKeySecret);
    }

    @Override
    public int getConnectionTimeoutMillis() {
        return connectionTimeoutMillis.getValue();
    }

    @Override
    public void setConnectionTimeoutMillis(final int connectionTimeoutMillis) {
        this.connectionTimeoutMillis.setValue(connectionTimeoutMillis);
    }

    @Override
    public int getResponseTimeoutMillis() {
        return responseTimeoutMillis.getValue();
    }

    @Override
    public void setResponseTimeoutMillis(final int responseTimeoutMillis) {
        this.responseTimeoutMillis.setValue(responseTimeoutMillis);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        connectionUrls.setEnabled(!readOnly);
        caCertificate.setEnabled(!readOnly);
        useAuthentication.setEnabled(!readOnly);
        if (readOnly) {
            apiKeyId.setEnabled(false);
            apiKeySecret.setEnabled(false);
        }
        connectionTimeoutMillis.setEnabled(!readOnly);
        responseTimeoutMillis.setEnabled(!readOnly);
    }

    @UiHandler("connectionUrls")
    public void onConnectionUrls(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("caCertificate")
    public void onCaCertificate(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("apiKeyId")
    public void onApiKeyId(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("apiKeySecret")
    public void onApiKeySecret(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("connectionTimeoutMillis")
    public void onConnectionTimeoutMillis(final ValueChangeEvent<Integer> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("responseTimeoutMillis")
    public void onResponseTimeoutMillis(final ValueChangeEvent<Integer> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("testConnection")
    public void onTestConnectionClick(final ClickEvent event) {
        getUiHandlers().onTestConnection(testConnection);
    }

    @UiHandler("useAuthentication")
    public void onUseAuthenticationChange(final ValueChangeEvent<Boolean> event) {
        updateAuthenticationControlEnabledState(event.getValue());
        getUiHandlers().onChange();
    }

    private void updateAuthenticationControlEnabledState(final boolean useAuthentication) {
        apiKeyId.setEnabled(useAuthentication);
        apiKeySecret.setEnabled(useAuthentication);
    }

    public interface Binder extends UiBinder<Widget, ElasticClusterSettingsViewImpl> {

    }
}
