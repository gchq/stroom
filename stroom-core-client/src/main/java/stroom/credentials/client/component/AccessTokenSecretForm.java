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

import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.Secret;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The secret of an access token credential, e.g. a Git personal access token: the token, which is
/// required.
public final class AccessTokenSecretForm extends Component implements SecretForm {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox accessToken;

    /// Makes an empty form.
    public AccessTokenSecretForm() {
        initWidget(BINDER.createAndBindUi(this));
    }

    @Override
    public Secret getSecret() {
        return new AccessTokenSecret(accessToken.getValue());
    }

    @Override
    public String validate() {
        if (accessToken.getValue().isBlank()) {
            return "Access token must not be empty";
        }
        return null;
    }

    @Override
    public void focus() {
        accessToken.setFocus(true);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AccessTokenSecretForm> {

    }
}
