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
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The secret of a user name and password credential: both are required.
public final class UsernamePasswordSecretForm extends Component implements SecretForm {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox username;
    @UiField
    PasswordBox password;

    /// Makes an empty form.
    public UsernamePasswordSecretForm() {
        initWidget(BINDER.createAndBindUi(this));
    }

    @Override
    public Secret getSecret() {
        return new UsernamePasswordSecret(username.getValue(), password.getValue());
    }

    @Override
    public String validate() {
        if (username.getValue().isBlank()) {
            return "Username must not be empty";
        } else if (password.getValue().isBlank()) {
            return "Password must not be empty";
        }
        return null;
    }

    @Override
    public void focus() {
        username.setFocus(true);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UsernamePasswordSecretForm> {

    }
}
