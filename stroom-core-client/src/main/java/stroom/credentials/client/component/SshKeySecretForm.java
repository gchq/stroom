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

import stroom.credentials.shared.Secret;
import stroom.credentials.shared.SshKeySecret;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The secret of an SSH key credential: the private key, which is required, its pass phrase, and
/// the known hosts it is verified against.
public final class SshKeySecretForm extends Component implements SecretForm {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PasswordBox passPhrase;
    @UiField
    TextArea privateKey;
    @UiField
    TickBox verifyHosts;
    @UiField
    TextArea knownHosts;

    /// Makes an empty form, verifying hosts.
    public SshKeySecretForm() {
        initWidget(BINDER.createAndBindUi(this));
        verifyHosts.setValue(true);
    }

    @Override
    public Secret getSecret() {
        return new SshKeySecret(
                passPhrase.getValue(),
                privateKey.getValue(),
                verifyHosts.getValue(),
                knownHosts.getValue());
    }

    @Override
    public String validate() {
        if (privateKey.getValue().isBlank()) {
            return "Private key must not be empty";
        }
        return null;
    }

    @Override
    public void focus() {
        passPhrase.focus();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SshKeySecretForm> {

    }
}
