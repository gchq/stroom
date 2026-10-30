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
import stroom.credentials.shared.CredentialType;
import stroom.widget.component.client.ControllerNode;
import stroom.widget.component.client.HasControllerNode;

import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.HashMap;
import java.util.Map;

/// Provides the form for the secret of each credential type.
///
/// Each form is only made the first time its type is needed, and the same one is returned after
/// that, so that values typed into it are kept if the user switches type and back again. Not a
/// singleton: each dialog gets its own set of empty forms.
public class SecretForms {

    private final Provider<KeyStoreSecretFormController> keyStoreSecretFormControllerProvider;
    private final Map<CredentialType, SecretForm> forms = new HashMap<>();
    // What owns the forms' controllers (the dialog that shows them), so that they go with it
    private ControllerNode owner;

    /// Made by GIN, for one dialog.
    ///
    /// @param keyStoreSecretFormControllerProvider Makes a key store's form, the one that uploads.
    @Inject
    public SecretForms(final Provider<KeyStoreSecretFormController> keyStoreSecretFormControllerProvider) {
        this.keyStoreSecretFormControllerProvider = keyStoreSecretFormControllerProvider;
    }

    /// @param owner What owns the forms' controllers, e.g. the dialog that shows them.
    public void setOwner(final HasControllerNode owner) {
        this.owner = owner.getControllerNode();
    }

    /// Returns the form for the secret of a credential type, making it if needed.
    ///
    /// @param credentialType The type of credential.
    /// @return The form for that type's secret.
    public SecretForm get(final CredentialType credentialType) {
        SecretForm form = forms.get(credentialType);
        if (form == null) {
            form = create(credentialType);
            if (owner != null && form instanceof final HasControllerNode hasControllerNode) {
                owner.adopt(hasControllerNode.getControllerNode());
            }
            forms.put(credentialType, form);
        }
        return form;
    }

    private SecretForm create(final CredentialType credentialType) {
        return switch (credentialType) {
            case ACCESS_TOKEN -> new AccessTokenSecretForm();
            case KEY_STORE -> {
                final KeyStoreSecretFormController keyStoreSecretForm = keyStoreSecretFormControllerProvider.get();
                keyStoreSecretForm.setType(KeyStoreType.PKCS12);
                yield keyStoreSecretForm;
            }
            case KEY_STORE_FILE -> {
                final KeyStoreFileSecretForm keyStoreFileSecretForm = new KeyStoreFileSecretForm();
                keyStoreFileSecretForm.setType(KeyStoreType.PKCS12);
                yield keyStoreFileSecretForm;
            }
            case SSH_KEY -> new SshKeySecretForm();
            case USERNAME_PASSWORD -> new UsernamePasswordSecretForm();
        };
    }
}
