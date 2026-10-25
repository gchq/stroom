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

package stroom.credentials.client.presenter;

import stroom.ai.shared.KeyStoreType;
import stroom.alert.client.event.AlertEvent;
import stroom.credentials.client.component.KeyStoreFileSecretForm;
import stroom.credentials.client.component.KeyStoreSecretFormController;
import stroom.credentials.client.component.SecretForm;
import stroom.credentials.client.component.SecretForms;
import stroom.credentials.client.presenter.ReplaceSecretPresenter.ReplaceSecretView;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.ReplaceSecretRequest;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.util.shared.NullSafe;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Inject;

/// Dialog to replace the secret held by an existing credential.
///
/// Secrets are never sent back to the browser, so they cannot be viewed or edited. Instead the
/// whole secret is replaced, in place, so everything that refers to the credential carries on
/// working with the new secret. The expiry is set here as it describes the secret.
public class ReplaceSecretPresenter extends MyPresenterWidget<ReplaceSecretView> {

    private static final int DIALOG_WIDTH = 750;
    private static final int DIALOG_HEIGHT = 600;

    private final CredentialClient credentialClient;
    private final SecretForms secretForms;

    @Inject
    public ReplaceSecretPresenter(final EventBus eventBus,
                                  final ReplaceSecretView view,
                                  final CredentialClient credentialClient,
                                  final SecretForms secretForms,
                                  final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus, view);
        make(dateTimeBoxControllerProvider).attach(view.getExpiryTime());
        this.credentialClient = credentialClient;
        this.secretForms = secretForms;
        secretForms.setOwner(this);
    }

    /// Shows the dialog to replace the secret of the given credential.
    ///
    /// @param credential The credential whose secret is to be replaced.
    /// @param consumer   Receives the updated credential once the secret has been replaced.
    public void show(final Credential credential,
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

        getView().setCredentialName(credential.getName());
        getView().setSecretForm(secretForm);
        // A new secret has a new expiry, so start from no expiry rather than the old secret's
        getView().setCredExpire(false);
        getView().getExpiryTime().setValue(System.currentTimeMillis());

        ShowPopupEvent
                .builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Replace " + credentialType.getDisplayValue() + " Secret")
                .modal(true)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk(secretForm, valid -> {
                            if (valid) {
                                final ReplaceSecretRequest request = new ReplaceSecretRequest(
                                        credential.getUuid(),
                                        secretForm.getSecret(),
                                        getView().isCredExpire()
                                                ? getView().getExpiryTime().getValue()
                                                : null);
                                credentialClient.replaceSecret(request,
                                        result -> {
                                            consumer.accept(result);
                                            e.hide();
                                        },
                                        new DefaultErrorHandler(this, e::reset),
                                        this);
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
        final String message = getView().isCredExpire() && !getView().getExpiryTime().isValid()
                ? "You must set a valid expiry time"
                : secretForm.validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            secretForm.submit(consumer);
        }
    }


    // --------------------------------------------------------------------------------


    /// View for the replace secret dialog.
    public interface ReplaceSecretView extends View {

        /// @param name The name of the credential, shown in the warning.
        void setCredentialName(String name);

        /// @param view The view holding the fields for the new secret.
        /// Shows the form for the secret of the credential's type.
        ///
        /// @param form The form.
        void setSecretForm(SecretForm form);

        /// @return True if the new secret expires.
        boolean isCredExpire();

        /// @param expire True if the new secret expires.
        void setCredExpire(boolean expire);

        /// @return The box holding the expiry date of the new secret.
        DateTimeBox getExpiryTime();

        /// Gives focus to the first secret field.
        void focus();
    }
}
