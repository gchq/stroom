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

import stroom.credentials.shared.CredentialType;
import stroom.item.client.SelectionBox;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;

/// The Settings tab of the credentials' dialog: their name, whether and when they expire, their
/// type and the form for their secret. Existing credentials' secrets are never shown: the tab says
/// so instead of showing the form, and the type can't be changed.
public class CredentialSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    SelectionBox<CredentialType> credentialType;
    @UiField
    TickBox credsExpire;
    @UiField
    FormGroup expiryTimeFormGroup;
    @UiField
    DateTimeBox expiryTime;
    @UiField
    FormGroup secretFormGroup;
    @UiField
    SimplePanel secret;
    @UiField
    FormGroup keyStorePathFormGroup;
    @UiField
    Label keyStorePath;
    @UiField
    FormGroup storedSecretFormGroup;

    private Consumer<CredentialType> onTypeChange;

    /// Makes an empty tab.
    public CredentialSettings() {
        initWidget(BINDER.createAndBindUi(this));
        Arrays.stream(CredentialType.values())
                .sorted(Comparator.comparing(CredentialType::getDisplayValue))
                .forEach(credentialType::addItem);

        credentialType.addValueChangeHandler(e -> {
            if (onTypeChange != null) {
                onTypeChange.accept(credentialType.getValue());
            }
        });
        credsExpire.addValueChangeHandler(e -> updateState());
    }

    /// @return The name entered.
    public String getName() {
        return name.getValue();
    }

    /// @param name The credentials' name.
    public void setName(final String name) {
        this.name.setValue(name);
    }

    /// @return The type chosen.
    public CredentialType getCredentialType() {
        return credentialType.getValue();
    }

    /// @param credentialType The credentials' type.
    public void setCredentialType(final CredentialType credentialType) {
        this.credentialType.setValue(credentialType);
    }

    /// @return True if the credentials are to expire.
    public boolean isCredExpire() {
        return credsExpire.getValue();
    }

    /// @param expire True if the credentials expire; the expiry time is enabled only if they do.
    public void setCredExpire(final boolean expire) {
        this.credsExpire.setValue(expire);
        updateState();
    }

    /// @return The expiry time's box, for its controller to attach to and to read and set.
    public DateTimeBox getExpiryTime() {
        return expiryTime;
    }

    /// Shows the form for the secret of the credentials' type.
    ///
    /// @param form The form.
    public void setSecretForm(final SecretForm form) {
        secret.setWidget(form);
    }

    /// Sets whether existing credentials are being edited. If so the type is fixed and the
    /// secret's form is replaced by a note that the secret can't be shown.
    ///
    /// @param existing True if editing existing credentials, false if creating new ones.
    public void setExistingCredential(final boolean existing) {
        credentialType.setEnabled(!existing);
        secretFormGroup.setVisible(!existing);
        storedSecretFormGroup.setVisible(existing);
    }

    /// Shows the path of the key store file of existing key store file credentials.
    ///
    /// @param keyStorePath The path, or null to hide it.
    public void setKeyStorePath(final String keyStorePath) {
        this.keyStorePath.setText(keyStorePath);
        keyStorePathFormGroup.setVisible(!NullSafe.isBlankString(keyStorePath));
    }

    /// @param onTypeChange What to run, with the type now chosen, when the user changes the type.
    public void setOnTypeChange(final Consumer<CredentialType> onTypeChange) {
        this.onTypeChange = onTypeChange;
    }

    private void updateState() {
        // Expiry is shown either way but only enabled if the tick box is ticked, as in the global
        // property dialog, so the dialog doesn't change shape
        final boolean expires = credsExpire.getValue();
        expiryTime.setEnabled(expires);
        expiryTimeFormGroup.setDisabled(!expires);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, CredentialSettings> {

    }
}
