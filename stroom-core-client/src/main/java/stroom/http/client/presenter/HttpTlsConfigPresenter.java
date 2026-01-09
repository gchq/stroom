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

package stroom.http.client.presenter;

import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.http.client.presenter.HttpTlsConfigPresenter.HttpTlsConfigView;
import stroom.item.client.SelectionBox;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class HttpTlsConfigPresenter
        extends MyPresenterWidget<HttpTlsConfigView>
        implements DirtyUiHandlers {

    private final CredentialClient credentialClient;

    @Inject
    public HttpTlsConfigPresenter(
            final EventBus eventBus,
            final HttpTlsConfigView view,
            final CredentialClient credentialClient) {
        super(eventBus, view);
        this.credentialClient = credentialClient;
        view.setUiHandlers(this);

        final CredentialListModel keyStoreListModel = new CredentialListModel(
                eventBus,
                credentialClient,
                Set.of(CredentialType.KEY_STORE));
        keyStoreListModel.setTaskMonitorFactory(this);
        view.getKeyStoreSelectionBox().setModel(keyStoreListModel);

        final CredentialListModel trustStoreListModel = new CredentialListModel(
                eventBus,
                credentialClient,
                Set.of(CredentialType.KEY_STORE));
        trustStoreListModel.setTaskMonitorFactory(this);
        view.getTrustStoreSelectionBox().setModel(trustStoreListModel);
    }

    public void show(final HttpTlsConfig httpTlsConfiguration,
                     final Consumer<HttpTlsConfig> consumer) {
        read(httpTlsConfiguration);
//        final PopupSize popupSize = PopupSize.resizable(430, 480);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
//                .popupSize(PopupSize.)
                .caption("Edit HTTP TLS Configuration")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        consumer.accept(write());
                    } else {
                        consumer.accept(httpTlsConfiguration);
                    }
                    e.hide();
                })
                .fire();
    }

    @Override
    public void onDirty() {

    }

    private void setCredential(final SelectionBox<Credential> selectionBox, final String credentialName) {
        if (credentialName != null) {
            credentialClient.getCredentialByName(credentialName, selectionBox::setValue, this);
        } else {
            selectionBox.setValue(null);
        }
    }

    public void read(final HttpTlsConfig config) {
        getView().setProtocol(config.getProtocol());
        getView().setProvider(config.getProvider());
        setCredential(getView().getKeyStoreSelectionBox(), config.getKeyStoreName());
        setCredential(getView().getTrustStoreSelectionBox(), config.getTrustStoreName());
        getView().setTrustSelfSignedCertificates(config.isTrustSelfSignedCertificates());
        getView().setVerifyHostname(config.isVerifyHostname());
        getView().setSupportedProtocols(config.getSupportedProtocols());
        getView().setSupportedCiphers(config.getSupportedCiphers());
        getView().setCertAlias(config.getCertAlias());
    }

    public HttpTlsConfig write() {
        return HttpTlsConfig
                .builder()
                .protocol(getView().getProtocol())
                .provider(getView().getProvider())
                .keyStoreName(NullSafe.get(
                        getView(),
                        HttpTlsConfigView::getKeyStoreSelectionBox,
                        SelectionBox::getValue,
                        Credential::getName))
                .trustStoreName(NullSafe.get(
                        getView(),
                        HttpTlsConfigView::getTrustStoreSelectionBox,
                        SelectionBox::getValue,
                        Credential::getName))
                .trustSelfSignedCertificates(getView().isTrustSelfSignedCertificates())
                .verifyHostname(getView().isVerifyHostname())
                .supportedProtocols(getView().getSupportedProtocols())
                .supportedCiphers(getView().getSupportedCiphers())
                .certAlias(getView().getCertAlias())
                .build();
    }

    public interface HttpTlsConfigView
            extends View, Focus, HasUiHandlers<DirtyUiHandlers> {

        void setProtocol(String protocol);

        String getProtocol();

        void setProvider(String provider);

        String getProvider();

        SelectionBox<Credential> getKeyStoreSelectionBox();

        SelectionBox<Credential> getTrustStoreSelectionBox();

        void setTrustSelfSignedCertificates(boolean trustSelfSignedCertificates);

        boolean isTrustSelfSignedCertificates();

        void setVerifyHostname(boolean verifyHostname);

        boolean isVerifyHostname();

        void setSupportedProtocols(List<String> supportedProtocols);

        List<String> getSupportedProtocols();

        void setSupportedCiphers(List<String> supportedCiphers);

        List<String> getSupportedCiphers();

        void setCertAlias(String certAlias);

        String getCertAlias();
    }
}
