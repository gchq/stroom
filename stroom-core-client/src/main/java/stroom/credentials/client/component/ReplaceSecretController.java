/*
 * Copyright 2026 Crown Copyright
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

package stroom.credentials.client.component;

import stroom.ai.shared.KeyStoreType;
import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.presenter.CredentialClient;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.ReplaceSecretRequest;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Replaces the secret held by an existing credential ([ReplaceSecret]), in a dialog made for one
/// use.
///
/// Secrets are never sent back to the browser, so they cannot be viewed or edited. Instead the
/// whole secret is replaced, in place, so everything that refers to the credential carries on
/// working with the new secret. The expiry is set here as it describes the secret.
public class ReplaceSecretController extends Controller {

    private static final int DIALOG_WIDTH = 750;
    private static final int DIALOG_HEIGHT = 600;

    private final ReplaceSecret view;
    private final CredentialClient credentialClient;
    private final SecretForms secretForms;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param credentialClient              Replaces the secret.
    /// @param secretForms                   Gives the form for each credential type's secret.
    /// @param dateTimeBoxControllerProvider Makes the expiry box's controller.
    @Inject
    public ReplaceSecretController(final EventBus eventBus,
                                   final ReplaceSecret view,
                                   final CredentialClient credentialClient,
                                   final SecretForms secretForms,
                                   final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        this.credentialClient = credentialClient;
        this.secretForms = secretForms;
        initWidget(view);
        make(dateTimeBoxControllerProvider).attach(view.getExpiryTimeBox());
        secretForms.setOwner(this);
    }

    /// Shows the dialog to replace the secret of a credential, owned by `opener` until it is
    /// closed.
    ///
    /// @param opener     What shows it.
    /// @param credential The credential whose secret is to be replaced.
    /// @param consumer   Receives the updated credential once the secret has been replaced.
    public void show(final HasControllerNode opener,
                     final Credential credential,
                     final Consumer<Credential> consumer) {
        final CredentialType credentialType = credential.getCredentialType();
        final SecretForm secretForm = secretForms.get(credentialType);
        if (secretForm instanceof final KeyStoreSecretFormController keyStoreSecretForm) {
            // Default to the type of the key store being replaced
            keyStoreSecretForm.setType(NullSafe.getOrElse(
                    credential,
                    Credential::getKeyStoreType,
                    KeyStoreType.PKCS12));
        } else if (secretForm instanceof final KeyStoreFileSecretForm keyStoreFileSecretForm) {
            // The type and path are not secret, so start from the current ones
            keyStoreFileSecretForm.setType(NullSafe.getOrElse(
                    credential,
                    Credential::getKeyStoreType,
                    KeyStoreType.PKCS12));
            keyStoreFileSecretForm.setPath(credential.getKeyStorePath());
        }

        view.setCredentialName(credential.getName());
        view.setSecretForm(secretForm);
        // A new secret has a new expiry, so start from no expiry rather than the old secret's
        view.setCredExpire(false);
        view.setExpiryTime(System.currentTimeMillis());

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Replace " + credentialType.getDisplayValue() + " Secret")
                .modal(true)
                .onShow(e -> secretForm.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk(secretForm, valid -> {
                            if (valid) {
                                final ReplaceSecretRequest request = new ReplaceSecretRequest(
                                        credential.getUuid(),
                                        secretForm.getSecret(),
                                        view.isCredExpire()
                                                ? view.getExpiryTime()
                                                : null);
                                credentialClient.replaceSecret(request,
                                        guard(result -> {
                                            consumer.accept(result);
                                            e.hide();
                                        }),
                                        new DefaultErrorHandler(this, e::reset),
                                        taskMonitor());
                            } else {
                                e.reset();
                            }
                        });
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    // Shows what is wrong with the expiry or the secret, if anything, else submits the secret (a key
    // store's file is uploaded); tells the consumer whether the secret can be saved
    private void onOk(final SecretForm secretForm,
                      final Consumer<Boolean> consumer) {
        final String message = view.isCredExpire() && !view.isExpiryTimeValid()
                ? "You must set a valid expiry time"
                : secretForm.validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            secretForm.submit(consumer);
        }
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
