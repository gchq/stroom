/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.credentials.shared.Secret;

import com.google.gwt.user.client.ui.IsWidget;

import java.util.function.Consumer;

/// The form for the secret of one type of credential, e.g. a user name and password. Its owner (the
/// credential's settings, or the Replace Secret dialog) shows it, asks it what is wrong
/// ([#validate()]) and shows that, then [#submit] it, and takes the secret from it.
public sealed interface SecretForm extends IsWidget
        permits AccessTokenSecretForm,
        KeyStoreFileSecretForm,
        KeyStoreSecretFormController,
        SshKeySecretForm,
        UsernamePasswordSecretForm {

    /// @return The secret, as entered.
    Secret getSecret();

    /// @return What is wrong with the secret entered, to show the user, or null for nothing.
    String validate();

    /// Puts the focus on the form's first field.
    void focus();

    /// Does what the secret needs before it is saved (a key store's file is uploaded), once it is
    /// valid.
    ///
    /// @param onDone Told whether that worked.
    default void submit(final Consumer<Boolean> onDone) {
        onDone.accept(true);
    }
}
