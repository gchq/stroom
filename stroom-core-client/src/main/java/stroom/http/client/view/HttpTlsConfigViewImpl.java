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

package stroom.http.client.view;

import stroom.credentials.shared.Credential;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.http.client.presenter.HttpTlsConfigPresenter.HttpTlsConfigView;
import stroom.item.client.SelectionBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HttpTlsConfigViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements HttpTlsConfigView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextBox protocol;
    @UiField
    TextBox provider;
    @UiField
    SelectionBox<Credential> keyStore;
    @UiField
    SelectionBox<Credential> trustStore;
    @UiField
    CustomCheckBox trustSelfSignedCertificates;
    @UiField
    CustomCheckBox verifyHostname;
    @UiField
    TextArea supportedProtocols;
    @UiField
    TextArea supportedCiphers;
    @UiField
    TextBox certAlias;

    @Inject
    public HttpTlsConfigViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        protocol.setEnabled(!readOnly);
        provider.setEnabled(!readOnly);
        keyStore.setEnabled(!readOnly);
        trustStore.setEnabled(!readOnly);
        trustSelfSignedCertificates.setEnabled(!readOnly);
        verifyHostname.setEnabled(!readOnly);
        supportedProtocols.setEnabled(!readOnly);
        supportedCiphers.setEnabled(!readOnly);
        certAlias.setEnabled(!readOnly);
    }

    @Override
    public void focus() {
        protocol.setFocus(true);
    }

    @Override
    public void setProtocol(final String protocol) {
        this.protocol.setValue(protocol);
    }

    @Override
    public String getProtocol() {
        return protocol.getValue();
    }

    @Override
    public void setProvider(final String provider) {
        this.provider.setValue(provider);
    }

    @Override
    public String getProvider() {
        return provider.getValue();
    }

    @Override
    public SelectionBox<Credential> getKeyStoreSelectionBox() {
        return keyStore;
    }

    @Override
    public SelectionBox<Credential> getTrustStoreSelectionBox() {
        return trustStore;
    }

    @Override
    public void setTrustSelfSignedCertificates(final boolean trustSelfSignedCertificates) {
        this.trustSelfSignedCertificates.setValue(trustSelfSignedCertificates);
    }

    @Override
    public boolean isTrustSelfSignedCertificates() {
        return trustSelfSignedCertificates.getValue();
    }

    @Override
    public void setVerifyHostname(final boolean verifyHostname) {
        this.verifyHostname.setValue(verifyHostname);
    }

    @Override
    public boolean isVerifyHostname() {
        return verifyHostname.getValue();
    }

    @Override
    public void setSupportedProtocols(final List<String> supportedProtocols) {
        if (supportedProtocols == null) {
            this.supportedProtocols.setValue("");
        } else {
            this.supportedProtocols.setValue(supportedProtocols.stream().collect(Collectors.joining("\n")));
        }
    }

    @Override
    public List<String> getSupportedProtocols() {
        return Arrays.stream(supportedProtocols.getValue().split("\n")).collect(Collectors.toList());
    }

    @Override
    public void setSupportedCiphers(final List<String> supportedCiphers) {
        if (supportedCiphers == null) {
            this.supportedCiphers.setValue("");
        } else {
            this.supportedCiphers.setValue(supportedCiphers.stream().collect(Collectors.joining("\n")));
        }
    }

    @Override
    public List<String> getSupportedCiphers() {
        return Arrays.stream(supportedCiphers.getValue().split("\n")).collect(Collectors.toList());
    }

    @Override
    public void setCertAlias(final String certAlias) {
        this.certAlias.setValue(certAlias);
    }

    @Override
    public String getCertAlias() {
        return certAlias.getValue();
    }

    @UiHandler({
            "protocol",
            "provider",
            "keyStore",
            "trustStore",
            "trustSelfSignedCertificates",
            "verifyHostname",
            "supportedProtocols",
            "supportedCiphers",
            "certAlias",
    })
    public void onChange(final ValueChangeEvent<?> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    public interface Binder extends UiBinder<Widget, HttpTlsConfigViewImpl> {

    }
}
