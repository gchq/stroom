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
import stroom.credentials.shared.KeyStoreFileSecret;
import stroom.credentials.shared.Secret;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.stream.Collectors;

/// The secret of a key store file credential: the key store's type and the path of its file on
/// every node, which are required, and its pass phrase.
public final class KeyStoreFileSecretForm extends Component implements SecretForm {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<KeyStoreType> type;
    @UiField
    TextBox path;
    @UiField
    PasswordBox password;

    /// Makes an empty form, for a PKCS12 key store.
    public KeyStoreFileSecretForm() {
        initWidget(BINDER.createAndBindUi(this));
        type.addItems(Arrays.stream(KeyStoreType.values()).sorted().collect(Collectors.toList()));
        type.setValue(KeyStoreType.PKCS12);
    }

    /// @param type The key store's type, e.g. that of the secret being replaced.
    public void setType(final KeyStoreType type) {
        this.type.setValue(type);
    }

    /// @param path The path of the key store's file, e.g. that of the secret being replaced (it isn't
    ///             secret).
    public void setPath(final String path) {
        this.path.setValue(path);
    }

    @Override
    public Secret getSecret() {
        final String passwordValue = password.getValue();
        return new KeyStoreFileSecret(
                type.getValue(),
                NullSafe.trim(path.getValue()),
                NullSafe.isEmptyString(passwordValue)
                        ? null
                        : passwordValue);
    }

    @Override
    public String validate() {
        if (type.getValue() == null) {
            return "You must choose a key store type, e.g. PKCS12";
        }
        if (NullSafe.isBlankString(path.getValue())) {
            return "You must enter the path of the key store file";
        }
        return null;
    }

    @Override
    public void focus() {
        type.focus();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, KeyStoreFileSecretForm> {

    }
}
