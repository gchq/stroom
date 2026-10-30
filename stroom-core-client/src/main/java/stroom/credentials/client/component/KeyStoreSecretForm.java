/*
 * Copyright 2024 Crown Copyright
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
import stroom.widget.component.client.Component;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.form.client.component.FileChooser;
import stroom.widget.selection.client.SelectionBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.stream.Collectors;

/// The fields of a key store credential's secret: the key store's type, its pass phrase and the
/// file to upload. Its [KeyStoreSecretFormController] uploads the file.
public final class KeyStoreSecretForm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<KeyStoreType> type;
    @UiField
    FileChooser fileChooser;
    @UiField
    PasswordBox password;

    /// Makes an empty form, for a PKCS12 key store.
    public KeyStoreSecretForm() {
        initWidget(BINDER.createAndBindUi(this));
        type.addItems(Arrays.stream(KeyStoreType.values()).sorted().collect(Collectors.toList()));
        type.setValue(KeyStoreType.PKCS12);
    }

    /// @param type The key store's type, e.g. that of the secret being replaced.
    public void setType(final KeyStoreType type) {
        this.type.setValue(type);
    }

    /// @return The key store's type, or null for none chosen.
    public KeyStoreType getType() {
        return type.getValue();
    }

    /// @return The key store's pass phrase, as entered.
    public String getPassword() {
        return password.getValue();
    }

    /// @return The chooser of the file to upload, which the controller's file chooser attaches to.
    public FileChooser getFileChooser() {
        return fileChooser;
    }

    /// Puts the focus on the form's first field.
    public void focus() {
        type.focus();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, KeyStoreSecretForm> {

    }
}
