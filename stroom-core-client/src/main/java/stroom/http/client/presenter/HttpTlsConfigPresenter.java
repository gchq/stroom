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

package stroom.http.client.presenter;

import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.client.presenter.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.http.client.presenter.HttpTlsConfigPresenter.HttpTlsConfigView;
import stroom.item.client.SelectionBox;
import stroom.util.shared.http.HttpTlsConfig;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Set;

public class HttpTlsConfigPresenter
        extends MyPresenterWidget<HttpTlsConfigView> {

    private final CredentialClient credentialClient;
    private String keyStoreNameRead;
    private String trustStoreNameRead;
    private boolean keyStoreResolved = true;
    private boolean trustStoreResolved = true;

    @Inject
    public HttpTlsConfigPresenter(
            final EventBus eventBus,
            final HttpTlsConfigView view,
            final CredentialClient credentialClient) {
        super(eventBus, view);
        this.credentialClient = credentialClient;

        final CredentialListModel keyStoreListModel = new CredentialListModel(
                eventBus,
                credentialClient,
                Set.of(CredentialType.KEY_STORE, CredentialType.KEY_STORE_FILE));
        keyStoreListModel.setTaskMonitorFactory(this);
        view.getKeyStoreSelectionBox().setModel(keyStoreListModel);

        final CredentialListModel trustStoreListModel = new CredentialListModel(
                eventBus,
                credentialClient,
                Set.of(CredentialType.KEY_STORE, CredentialType.KEY_STORE_FILE));
        trustStoreListModel.setTaskMonitorFactory(this);
        view.getTrustStoreSelectionBox().setModel(trustStoreListModel);
    }

    /// @param readOnly True if the settings can only be looked at.
    public void setReadOnly(final boolean readOnly) {
        getView().onReadOnly(readOnly);
    }

    // The store is looked up by name, which takes a moment, and may no longer exist. Until the lookup has
    // answered, the name read is kept, so that saving straight away (or with a missing credential) does not
    // lose it.
    private void setCredential(final SelectionBox<Credential> selectionBox,
                               final String credentialName,
                               final boolean keyStore) {
        setResolved(keyStore, credentialName == null);
        selectionBox.setValue(null);
        if (credentialName != null) {
            credentialClient.getCredentialByName(credentialName, credential -> {
                if (credential != null) {
                    selectionBox.setValue(credential);
                    setResolved(keyStore, true);
                }
            }, this);
        }
    }

    private void setResolved(final boolean keyStore, final boolean resolved) {
        if (keyStore) {
            keyStoreResolved = resolved;
        } else {
            trustStoreResolved = resolved;
        }
    }

    // The chosen store's name, or the name read if it has not been looked up yet and nothing else is chosen.
    private static String storeName(final SelectionBox<Credential> selectionBox,
                                    final boolean resolved,
                                    final String nameRead) {
        final Credential chosen = selectionBox.getValue();
        if (chosen != null) {
            return chosen.getName();
        }
        return resolved
                ? null
                : nameRead;
    }

    /// Shows the settings.
    ///
    /// @param config The settings, or null to leave the form as it is.
    public void read(final HttpTlsConfig config) {
        if (config != null) {
            keyStoreNameRead = config.getKeyStoreName();
            trustStoreNameRead = config.getTrustStoreName();
            getView().setProtocol(config.getProtocol());
            getView().setProvider(config.getProvider());
            setCredential(getView().getKeyStoreSelectionBox(), keyStoreNameRead, true);
            setCredential(getView().getTrustStoreSelectionBox(), trustStoreNameRead, false);
            getView().setTrustSelfSignedCertificates(config.isTrustSelfSignedCertificates());
            getView().setVerifyHostname(config.isVerifyHostname());
            getView().setSupportedProtocols(config.getSupportedProtocols());
            getView().setSupportedCiphers(config.getSupportedCiphers());
            getView().setCertAlias(config.getCertAlias());
        }
    }

    /// @return The settings as they are in the form.
    public HttpTlsConfig write() {
        return HttpTlsConfig
                .builder()
                .protocol(getView().getProtocol())
                .provider(getView().getProvider())
                .keyStoreName(storeName(getView().getKeyStoreSelectionBox(), keyStoreResolved, keyStoreNameRead))
                .trustStoreName(storeName(
                        getView().getTrustStoreSelectionBox(), trustStoreResolved, trustStoreNameRead))
                .trustSelfSignedCertificates(getView().isTrustSelfSignedCertificates())
                .verifyHostname(getView().isVerifyHostname())
                .supportedProtocols(getView().getSupportedProtocols())
                .supportedCiphers(getView().getSupportedCiphers())
                .certAlias(getView().getCertAlias())
                .build();
    }

    public interface HttpTlsConfigView
            extends View, Focus, ReadOnlyChangeHandler {

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
