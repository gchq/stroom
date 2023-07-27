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
    IntegerBox socketTimeoutMillis;
    @UiField
    Button testConnection;

    @Inject
    public ElasticClusterSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        testConnection.setIcon(SvgImage.OK);

        connectionUrls.addKeyDownHandler(e -> fireChange());
        caCertificate.addKeyDownHandler(e -> fireChange());
        apiKeyId.addKeyDownHandler(e -> fireChange());
        apiKeySecret.addKeyDownHandler(e -> fireChange());
        socketTimeoutMillis.addKeyDownHandler(e -> fireChange());

        updateAuthenticationControlEnabledState(useAuthentication.getValue());
    }

    private void fireChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
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
    public int getSocketTimeoutMillis() {
        return socketTimeoutMillis.getValue();
    }

    @Override
    public void setSocketTimeoutMillis(final int socketTimeoutMillis) {
        this.socketTimeoutMillis.setValue(socketTimeoutMillis);
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
        socketTimeoutMillis.setEnabled(!readOnly);
    }

    @UiHandler("testConnection")
    public void onTestConnectionClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTestConnection();
        }
    }

    @UiHandler("useAuthentication")
    public void onUseAuthenticationChange(final ValueChangeEvent<Boolean> event) {
        updateAuthenticationControlEnabledState(event.getValue());
        fireChange();
    }

    private void updateAuthenticationControlEnabledState(final boolean useAuthentication) {
        apiKeyId.setEnabled(useAuthentication);
        apiKeySecret.setEnabled(useAuthentication);
    }

    public interface Binder extends UiBinder<Widget, ElasticClusterSettingsViewImpl> {

    }
}
