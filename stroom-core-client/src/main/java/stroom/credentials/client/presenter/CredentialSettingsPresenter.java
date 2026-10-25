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

package stroom.credentials.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.credentials.client.component.SecretForm;
import stroom.credentials.client.component.SecretForms;
import stroom.credentials.client.presenter.CredentialSettingsPresenter.CredentialSettingsView;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.Secret;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;

public class CredentialSettingsPresenter
        extends MyPresenterWidget<CredentialSettingsView>
        implements CredentialSettingsUiHandlers {

    private final SecretForms secretForms;

    private DocRef docRef;

    /// The credentials being edited, or null if new credentials are being created.
    private Credential existingCredential;

    @Inject
    public CredentialSettingsPresenter(final EventBus eventBus,
                                       final CredentialSettingsView view,
                                       final SecretForms secretForms,
                                       final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus, view);
        view.setUiHandlers(this);
        make(dateTimeBoxControllerProvider).attach(view.getExpiryTime());

        this.secretForms = secretForms;
        secretForms.setOwner(this);
    }

    @Override
    public void onTypeChange(final CredentialType type) {
        getView().setSecretForm(getSecretForm(type));
    }

    private SecretForm getSecretForm(final CredentialType credentialType) {
        return secretForms.get(credentialType);
    }

    /**
     * Set the credentials into the dialog. Copies all data out of the
     * credentials object so no danger of overwriting the data in place.
     *
     * @param cwp The credentials to display in the UI. Can be null
     *            in which case empty values will be displayed.
     */
    public void read(final DocRef docRef,
                     final CredentialWithPerms cwp) {
        this.docRef = docRef;
        this.existingCredential = NullSafe.get(cwp, CredentialWithPerms::getCredential);
        // Existing secrets are never sent to the browser so can't be shown or edited here.
        // They can only be replaced, using the separate Replace Secret dialog.
        getView().setExistingCredential(existingCredential != null);
        getView().setKeyStorePath(NullSafe.get(existingCredential, Credential::getKeyStorePath));
        if (cwp == null) {
            getView().setName("");
            getView().setCredExpire(false);
            getView().getExpiryTime().setValue(System.currentTimeMillis());
            getView().setCredentialType(CredentialType.USERNAME_PASSWORD);
            onTypeChange(getView().getCredentialType());
        } else {
            final Credential credential = cwp.getCredential();
            getView().setName(credential.getName());
            getView().setCredExpire(credential.getExpiryTimeMs() != null);
            if (credential.getExpiryTimeMs() != null) {
                getView().getExpiryTime().setValue(credential.getExpiryTimeMs());
            } else {
                getView().getExpiryTime().setValue(System.currentTimeMillis());
            }
            // No secret form is needed as the secret is hidden for existing credentials
            getView().setCredentialType(credential.getCredentialType());
        }
    }

    private Secret getSecret() {
        return getSecretForm(getView().getCredentialType()).getSecret();
    }

    // Shows what is wrong with the secret, if anything, else submits it (a key store's file is
    // uploaded); tells the consumer whether the secret can be saved
    private void submitSecret(final SecretForm form, final Consumer<Boolean> consumer) {
        final String message = form.validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            form.submit(consumer);
        }
    }

    public void onOk(final Consumer<Boolean> consumer) {
        final String message = validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else if (existingCredential == null) {
            submitSecret(getSecretForm(getView().getCredentialType()), consumer);
        } else if (!Objects.equals(existingCredential.getName(), getView().getName())) {
            // Other settings refer to credentials by name, so a rename breaks them
            ConfirmEvent.fireWarn(this,
                    "Other settings, such as HTTP TLS key stores, refer to credentials by name. "
                    + "Anything that refers to '" + existingCredential.getName()
                    + "' will stop working until it is changed to use '" + getView().getName()
                    + "'. Are you sure you want to rename these credentials?",
                    consumer::accept);
        } else {
            consumer.accept(true);
        }
    }

    private String validate() {
        if (NullSafe.isBlankString(getView().getName())) {
            return "Name cannot be empty";
        }
        if (getView().getCredentialType() == null) {
            return "You must specify a credential type";
        }
        if (getView().isCredExpire() && !getView().getExpiryTime().isValid()) {
            return "You must set a valid expiry time";
        }
        return null;
    }

    public PutCredentialRequest write() {
        // The secret of existing credentials is left as it is
        final Secret secret = existingCredential == null
                ? getSecret()
                : null;

        // The key store type and path are not sent as the server takes them from the secret
        final Credential credential = new Credential(
                docRef.getUuid(),
                getView().getName(),
                null,
                null,
                null,
                null,
                getView().getCredentialType(),
                null,
                null,
                getView().isCredExpire()
                        ? getView().getExpiryTime().getValue()
                        : null);

        return new PutCredentialRequest(credential, secret);
    }


    // --------------------------------------------------------------------------------


    /**
     * View that this presents. Provides access to the data in the UI.
     */
    public interface CredentialSettingsView extends View, HasUiHandlers<CredentialSettingsUiHandlers> {

        String getName();

        void setName(String name);

        CredentialType getCredentialType();

        void setCredentialType(CredentialType credentialType);

        boolean isCredExpire();

        void setCredExpire(boolean expire);

        DateTimeBox getExpiryTime();

        /// Shows the form for the secret of the credential's type.
        ///
        /// @param form The form.
        void setSecretForm(SecretForm form);

        /// Sets whether existing credentials are being edited. If so the type is fixed and the
        /// secret fields are replaced by a note that the secret can't be shown.
        ///
        /// @param existing True if editing existing credentials, false if creating new ones.
        void setExistingCredential(boolean existing);

        /// Shows the path of the key store file of existing key store file credentials.
        ///
        /// @param keyStorePath The path, or null to hide it.
        void setKeyStorePath(String keyStorePath);
    }
}
