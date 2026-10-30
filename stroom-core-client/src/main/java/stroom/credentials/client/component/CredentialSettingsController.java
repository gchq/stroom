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

package stroom.credentials.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.credentials.shared.Secret;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.datepicker.client.component.DateTimeBoxController;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The Settings tab of the credentials' dialog ([CredentialSettings]): shows the form for the
/// secret of the type chosen, checks what is entered, and warns before a rename.
public class CredentialSettingsController extends Controller {

    private final CredentialSettings view;
    private final SecretForms secretForms;

    private DocRef docRef;

    /// The credentials being edited, or null if new credentials are being created.
    private Credential existingCredential;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The tab.
    /// @param secretForms                   Gives the form for each type's secret.
    /// @param dateTimeBoxControllerProvider Makes the expiry time's controller.
    @Inject
    public CredentialSettingsController(final EventBus eventBus,
                                        final CredentialSettings view,
                                        final SecretForms secretForms,
                                        final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        this.secretForms = secretForms;
        initWidget(view);
        make(dateTimeBoxControllerProvider).attach(view.getExpiryTime());
        secretForms.setOwner(this);
        view.setOnTypeChange(this::showSecretForm);
    }

    /// Shows credentials, copying all their data out so that they are never changed in place.
    ///
    /// @param docRef The credentials' document.
    /// @param cwp    The credentials, or null for new ones, which are shown empty.
    public void read(final DocRef docRef, final CredentialWithPerms cwp) {
        this.docRef = docRef;
        this.existingCredential = NullSafe.get(cwp, CredentialWithPerms::getCredential);
        // Existing secrets are never sent to the browser so can't be shown or edited here.
        // They can only be replaced, using the separate Replace Secret dialog.
        view.setExistingCredential(existingCredential != null);
        view.setKeyStorePath(NullSafe.get(existingCredential, Credential::getKeyStorePath));
        if (cwp == null) {
            view.setName("");
            view.setCredExpire(false);
            view.getExpiryTime().setValue(System.currentTimeMillis());
            view.setCredentialType(CredentialType.USERNAME_PASSWORD);
            showSecretForm(view.getCredentialType());
        } else {
            final Credential credential = cwp.getCredential();
            view.setName(credential.getName());
            view.setCredExpire(credential.getExpiryTimeMs() != null);
            if (credential.getExpiryTimeMs() != null) {
                view.getExpiryTime().setValue(credential.getExpiryTimeMs());
            } else {
                view.getExpiryTime().setValue(System.currentTimeMillis());
            }
            // No secret form is needed as the secret is hidden for existing credentials
            view.setCredentialType(credential.getCredentialType());
        }
    }

    /// Checks what is entered when the user presses OK: shows what is wrong, if anything; for new
    /// credentials, submits their secret (a key store's file is uploaded); for a rename, asks the
    /// user to confirm it.
    ///
    /// @param consumer Told whether the credentials can be saved.
    public void onOk(final Consumer<Boolean> consumer) {
        final String message = validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else if (existingCredential == null) {
            submitSecret(getSecretForm(view.getCredentialType()), consumer);
        } else if (!Objects.equals(existingCredential.getName(), view.getName())) {
            // Other settings refer to credentials by name, so a rename breaks them
            ConfirmEvent.fireWarn(this,
                    "Other settings, such as HTTP TLS key stores, refer to credentials by name. "
                    + "Anything that refers to '" + existingCredential.getName()
                    + "' will stop working until it is changed to use '" + view.getName()
                    + "'. Are you sure you want to rename these credentials?",
                    consumer::accept);
        } else {
            consumer.accept(true);
        }
    }

    /// @return The request that saves the credentials shown, with the secret entered for new ones.
    public PutCredentialRequest write() {
        // The secret of existing credentials is left as it is
        final Secret secret = existingCredential == null
                ? getSecretForm(view.getCredentialType()).getSecret()
                : null;

        // The key store type and path are not sent as the server takes them from the secret
        final Credential credential = new Credential(
                docRef.getUuid(),
                view.getName(),
                null,
                null,
                null,
                null,
                view.getCredentialType(),
                null,
                null,
                view.isCredExpire()
                        ? view.getExpiryTime().getValue()
                        : null);

        return new PutCredentialRequest(credential, secret);
    }

    private void showSecretForm(final CredentialType type) {
        view.setSecretForm(getSecretForm(type));
    }

    private SecretForm getSecretForm(final CredentialType credentialType) {
        return secretForms.get(credentialType);
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

    private String validate() {
        if (NullSafe.isBlankString(view.getName())) {
            return "Name cannot be empty";
        }
        if (view.getCredentialType() == null) {
            return "You must specify a credential type";
        }
        if (view.isCredExpire() && !view.getExpiryTime().isValid()) {
            return "You must set a valid expiry time";
        }
        return null;
    }
}
