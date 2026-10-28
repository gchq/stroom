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

package stroom.http.client.component;

import stroom.credentials.client.CredentialClient;
import stroom.credentials.client.CredentialListModel;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;
import java.util.function.Consumer;

/// Edits an HTTP client's TLS settings ([HttpTlsConfigEditor]) in a dialog made for one use.
public class HttpTlsConfigEditorController extends Controller {

    private final HttpTlsConfigEditor view;
    private final CredentialClient credentialClient;

    /// Made by GIN, with its form.
    ///
    /// @param eventBus         The application's event bus.
    /// @param view             The form.
    /// @param credentialClient Lists and finds the key stores.
    @Inject
    public HttpTlsConfigEditorController(final EventBus eventBus,
                                         final HttpTlsConfigEditor view,
                                         final CredentialClient credentialClient) {
        super(eventBus);
        this.view = view;
        this.credentialClient = credentialClient;
        initWidget(view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, view::setReadOnly);

        view.setKeyStoreModel(credentialListModel(eventBus));
        view.setTrustStoreModel(credentialListModel(eventBus));
    }

    /// Shows a TLS config in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener        What shows it.
    /// @param httpTlsConfig The config, or `null` for none yet.
    /// @param consumer      Given the edited config on OK, or `httpTlsConfig` on Cancel.
    public void show(final HasControllerNode opener,
                     final HttpTlsConfig httpTlsConfig,
                     final Consumer<HttpTlsConfig> consumer) {
        read(httpTlsConfig);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Edit HTTP TLS Configuration")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        consumer.accept(write());
                    } else {
                        consumer.accept(httpTlsConfig);
                    }
                    e.hide();
                })
                .fire();
    }

    /// Shows a TLS config in the form.
    ///
    /// @param httpTlsConfig The config, or null for none yet, which shows the defaults (so that OK
    ///                      saves them rather than whatever the form's controls start as).
    void read(final HttpTlsConfig httpTlsConfig) {
        final HttpTlsConfig config = httpTlsConfig != null
                ? httpTlsConfig
                : HttpTlsConfig.builder().build();
        view.setProtocol(config.getProtocol());
        view.setProvider(config.getProvider());
        setCredential(config.getKeyStoreName(), view::setKeyStore);
        setCredential(config.getTrustStoreName(), view::setTrustStore);
        view.setTrustSelfSignedCertificates(config.isTrustSelfSignedCertificates());
        view.setVerifyHostname(config.isVerifyHostname());
        view.setSupportedProtocols(config.getSupportedProtocols());
        view.setSupportedCiphers(config.getSupportedCiphers());
        view.setCertAlias(config.getCertAlias());
    }

    private HttpTlsConfig write() {
        return HttpTlsConfig
                .builder()
                .protocol(view.getProtocol())
                .provider(view.getProvider())
                .keyStoreName(view.getKeyStoreName())
                .trustStoreName(view.getTrustStoreName())
                .trustSelfSignedCertificates(view.isTrustSelfSignedCertificates())
                .verifyHostname(view.isVerifyHostname())
                .supportedProtocols(view.getSupportedProtocols())
                .supportedCiphers(view.getSupportedCiphers())
                .certAlias(view.getCertAlias())
                .build();
    }

    private void setCredential(final String credentialName, final Consumer<Credential> setter) {
        if (credentialName != null) {
            credentialClient.getCredentialByName(credentialName, guard(setter), taskMonitor());
        } else {
            setter.accept(null);
        }
    }

    private CredentialListModel credentialListModel(final EventBus eventBus) {
        final CredentialListModel model = new CredentialListModel(
                eventBus,
                credentialClient,
                Set.of(CredentialType.KEY_STORE, CredentialType.KEY_STORE_FILE));
        model.setTaskMonitorFactory(() -> taskMonitor().createTaskMonitor());
        return model;
    }

}
