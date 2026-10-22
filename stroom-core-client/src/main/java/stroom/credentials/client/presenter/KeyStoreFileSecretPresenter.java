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
import stroom.credentials.client.presenter.KeyStoreFileSecretPresenter.KeyStoreFileSecretView;
import stroom.credentials.shared.KeyStoreFileSecret;
import stroom.credentials.shared.Secret;
import stroom.util.shared.NullSafe;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;
import javax.inject.Inject;

/// Presents the secret of credentials that refer to a key store file on the local file system.
/// The file must be at the same path on every node. The server checks that the path is in an
/// allowed directory and that the file opens with the password when the credentials are saved.
public final class KeyStoreFileSecretPresenter
        extends MyPresenterWidget<KeyStoreFileSecretView> implements SecretPresenter<KeyStoreFileSecretView> {

    /// @param eventBus The event bus.
    /// @param view     The view holding the key store file fields.
    @Inject
    public KeyStoreFileSecretPresenter(final EventBus eventBus,
                                       final KeyStoreFileSecretView view) {
        super(eventBus, view);
    }

    /// @param type The key store type to show.
    public void setType(final KeyStoreType type) {
        getView().setType(type);
    }

    @Override
    public Secret getSecret() {
        final String password = getView().getPassword();
        return new KeyStoreFileSecret(
                getView().getType(),
                NullSafe.trim(getView().getPath()),
                NullSafe.isEmptyString(password)
                        ? null
                        : password);
    }

    @Override
    public void onOk(final Consumer<Boolean> consumer) {
        final String message = validate();
        if (message != null) {
            AlertEvent.fireError(this, message, () -> consumer.accept(false));
        } else {
            consumer.accept(true);
        }
    }

    private String validate() {
        if (getView().getType() == null) {
            return "You must choose a key store type, e.g. PKCS12";
        }
        if (NullSafe.isBlankString(getView().getPath())) {
            return "You must enter the path of the key store file";
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    /// View for the secret of a key store file credential.
    public interface KeyStoreFileSecretView extends View, Focus {

        /// @param type The key store type.
        void setType(KeyStoreType type);

        /// @return The key store type.
        KeyStoreType getType();

        /// @return The path of the key store file.
        String getPath();

        /// @param path The path of the key store file.
        void setPath(String path);

        /// @return The key store password.
        String getPassword();

        /// @param password The key store password.
        void setPassword(String password);
    }
}
