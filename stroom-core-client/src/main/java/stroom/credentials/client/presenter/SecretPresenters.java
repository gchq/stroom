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
import stroom.credentials.shared.CredentialType;
import stroom.widget.component.client.ControllerNode;
import stroom.widget.component.client.HasControllerNode;

import com.google.inject.Provider;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

/// Provides the presenter for the secret of each credential type.
///
/// Each presenter is only created the first time its type is needed, and the same one is
/// returned after that, so that values typed into it are kept if the user switches type and
/// back again. Not a singleton: each dialog gets its own set of empty secret presenters.
public class SecretPresenters {

    private final Provider<AccessTokenSecretPresenter> accessTokenSecretPresenterProvider;
    private final Provider<SshKeySecretPresenter> sshKeySecretPresenterProvider;
    private final Provider<KeyStoreSecretPresenter> keyStoreSecretPresenterProvider;
    private final Provider<KeyStoreFileSecretPresenter> keyStoreFileSecretPresenterProvider;
    private final Provider<UsernamePasswordSecretPresenter> usernamePasswordSecretPresenterProvider;

    private final Map<CredentialType, SecretPresenter<?>> presenters = new HashMap<>();
    // What owns the presenters made (the dialog that shows them), so that they go with it
    private ControllerNode owner;

    @Inject
    public SecretPresenters(
            final Provider<AccessTokenSecretPresenter> accessTokenSecretPresenterProvider,
            final Provider<SshKeySecretPresenter> sshKeySecretPresenterProvider,
            final Provider<KeyStoreSecretPresenter> keyStoreSecretPresenterProvider,
            final Provider<KeyStoreFileSecretPresenter> keyStoreFileSecretPresenterProvider,
            final Provider<UsernamePasswordSecretPresenter> usernamePasswordSecretPresenterProvider) {
        this.accessTokenSecretPresenterProvider = accessTokenSecretPresenterProvider;
        this.sshKeySecretPresenterProvider = sshKeySecretPresenterProvider;
        this.keyStoreSecretPresenterProvider = keyStoreSecretPresenterProvider;
        this.keyStoreFileSecretPresenterProvider = keyStoreFileSecretPresenterProvider;
        this.usernamePasswordSecretPresenterProvider = usernamePasswordSecretPresenterProvider;
    }

    /// @param owner What owns the presenters made, e.g. the dialog that shows them.
    public void setOwner(final HasControllerNode owner) {
        this.owner = owner.getControllerNode();
    }

    /// Returns the presenter for the secret of the given credential type, creating it if needed.
    ///
    /// @param credentialType The type of credential.
    /// @return The presenter for that type's secret.
    public SecretPresenter<?> get(final CredentialType credentialType) {
        SecretPresenter<?> presenter = presenters.get(credentialType);
        if (presenter == null) {
            presenter = create(credentialType);
            if (owner != null && presenter instanceof final HasControllerNode hasComponentNode) {
                owner.adopt(hasComponentNode.getControllerNode());
            }
            presenters.put(credentialType, presenter);
        }
        return presenter;
    }

    private SecretPresenter<?> create(final CredentialType credentialType) {
        return switch (credentialType) {
            case ACCESS_TOKEN -> accessTokenSecretPresenterProvider.get();
            case KEY_STORE -> {
                final KeyStoreSecretPresenter keyStoreSecretPresenter = keyStoreSecretPresenterProvider.get();
                keyStoreSecretPresenter.setType(KeyStoreType.PKCS12);
                yield keyStoreSecretPresenter;
            }
            case KEY_STORE_FILE -> {
                final KeyStoreFileSecretPresenter keyStoreFileSecretPresenter =
                        keyStoreFileSecretPresenterProvider.get();
                keyStoreFileSecretPresenter.setType(KeyStoreType.PKCS12);
                yield keyStoreFileSecretPresenter;
            }
            case SSH_KEY -> sshKeySecretPresenterProvider.get();
            case USERNAME_PASSWORD -> usernamePasswordSecretPresenterProvider.get();
        };
    }
}
